package ai.aegis.setup;

import ai.aegis.market.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class VolumeProfileService {
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal VALUE_AREA = new BigDecimal("0.70");

    public VolumeProfile fixedRange(List<Candle> input, int requestedBins) {
        List<Candle> candles = closed(input);
        if (candles.size() < 20) throw new IllegalArgumentException("volume profile requires at least 20 closed candles");
        return calculate(candles, Math.max(16, Math.min(requestedBins, 120)), "FIXED_RANGE");
    }

    public VolumeProfile session(List<Candle> input, int requestedBins) {
        List<Candle> all = closed(input);
        if (all.isEmpty()) throw new IllegalArgumentException("session profile requires closed candles");
        var sessionDate = all.getLast().openTime().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        List<Candle> session = all.stream().filter(c -> c.openTime().atZone(java.time.ZoneOffset.UTC)
                .toLocalDate().equals(sessionDate)).toList();
        if (session.size() < 5) session = all.subList(Math.max(0, all.size() - 24), all.size());
        return calculate(session, Math.max(12, Math.min(requestedBins, 80)), "UTC_SESSION");
    }

    private VolumeProfile calculate(List<Candle> candles, int binCount, String type) {
        BigDecimal low = candles.stream().map(Candle::low).min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal high = candles.stream().map(Candle::high).max(Comparator.naturalOrder()).orElseThrow();
        BigDecimal width = high.subtract(low, MC).divide(BigDecimal.valueOf(binCount), MC);
        if (width.signum() <= 0) throw new IllegalArgumentException("profile price range must be positive");
        double[] volumes = allocate(candles, low, width, binCount);
        double total = java.util.Arrays.stream(volumes).sum();
        int pocIndex = indexOfMax(volumes);
        boolean[] valueArea = valueArea(volumes, pocIndex, total * VALUE_AREA.doubleValue());
        double highThreshold = percentile(volumes, .75);
        double lowThreshold = percentile(volumes, .25);
        List<VolumeProfile.Node> bins = new ArrayList<>();
        for (int index = 0; index < binCount; index++) {
            BigDecimal lower = low.add(width.multiply(BigDecimal.valueOf(index), MC), MC);
            BigDecimal upper = index == binCount - 1 ? high : lower.add(width, MC);
            String nodeType = index == pocIndex ? "POC" : volumes[index] >= highThreshold ? "HVN"
                    : volumes[index] <= lowThreshold ? "LVN" : valueArea[index] ? "VALUE" : "NORMAL";
            bins.add(new VolumeProfile.Node(lower, upper, lower.add(upper, MC).divide(BigDecimal.TWO, MC),
                    BigDecimal.valueOf(volumes[index]), BigDecimal.valueOf(total == 0 ? 0 : volumes[index] / total), nodeType));
        }
        int firstValue = 0, lastValue = binCount - 1;
        while (firstValue < binCount && !valueArea[firstValue]) firstValue++;
        while (lastValue >= 0 && !valueArea[lastValue]) lastValue--;
        BigDecimal val = bins.get(Math.min(firstValue, binCount - 1)).lower();
        BigDecimal vah = bins.get(Math.max(lastValue, 0)).upper();
        List<VolumeProfile.DevelopingPoint> developing = developingPoc(candles, low, width, binCount);
        String volumeState = volumeState(candles, val, vah);
        BigDecimal atr = atr(candles, 14);
        boolean range = atr.signum() > 0 && high.subtract(low, MC).compareTo(atr.multiply(BigDecimal.valueOf(8), MC)) <= 0
                && candles.getLast().close().subtract(candles.get(Math.max(0, candles.size() - 10)).close()).abs()
                .compareTo(atr.multiply(BigDecimal.valueOf(1.5), MC)) <= 0;
        List<VolumeProfile.Node> hvn = bins.stream().filter(bin -> "HVN".equals(bin.nodeType()) || "POC".equals(bin.nodeType())).toList();
        List<VolumeProfile.Node> lvn = bins.stream().filter(bin -> "LVN".equals(bin.nodeType())).toList();
        return new VolumeProfile(candles.getLast().symbol(), candles.getLast().interval(), type,
                candles.getFirst().openTime(), candles.getLast().closeTime(), low, high,
                bins.get(pocIndex).midpoint(), vah, val, hvn, lvn, bins, developing,
                volumeState, range, VALUE_AREA,
                "Kline volume allocated across intersected price bins with triangular typical-price weighting; not tick-level volume-at-price");
    }

    private static double[] allocate(List<Candle> candles, BigDecimal low, BigDecimal width, int count) {
        double[] result = new double[count];
        for (Candle candle : candles) {
            int from = clamp(candle.low().subtract(low).divide(width, 0, RoundingMode.FLOOR).intValue(), 0, count - 1);
            int to = clamp(candle.high().subtract(low).divide(width, 0, RoundingMode.FLOOR).intValue(), 0, count - 1);
            double typical = candle.high().add(candle.low()).add(candle.close()).divide(BigDecimal.valueOf(3), MC).doubleValue();
            double scale = Math.max(candle.high().subtract(candle.low()).doubleValue(), width.doubleValue());
            double sum = 0;
            double[] weights = new double[to - from + 1];
            for (int index = from; index <= to; index++) {
                double midpoint = low.add(width.multiply(BigDecimal.valueOf(index + .5), MC)).doubleValue();
                weights[index - from] = .25 + Math.max(0, 1 - Math.abs(midpoint - typical) / scale);
                sum += weights[index - from];
            }
            for (int index = from; index <= to; index++) result[index] += candle.volume().doubleValue() * weights[index - from] / sum;
        }
        return result;
    }

    private static List<VolumeProfile.DevelopingPoint> developingPoc(List<Candle> candles, BigDecimal low,
                                                                       BigDecimal width, int bins) {
        List<VolumeProfile.DevelopingPoint> result = new ArrayList<>();
        int step = Math.max(1, candles.size() / 40);
        for (int end = Math.min(5, candles.size()); end <= candles.size(); end += step) {
            double[] allocation = allocate(candles.subList(0, end), low, width, bins);
            int poc = indexOfMax(allocation);
            result.add(new VolumeProfile.DevelopingPoint(candles.get(end - 1).closeTime(),
                    low.add(width.multiply(BigDecimal.valueOf(poc + .5), MC), MC)));
        }
        if (result.isEmpty() || !result.getLast().time().equals(candles.getLast().closeTime())) {
            double[] allocation = allocate(candles, low, width, bins);
            result.add(new VolumeProfile.DevelopingPoint(candles.getLast().closeTime(),
                    low.add(width.multiply(BigDecimal.valueOf(indexOfMax(allocation) + .5), MC), MC)));
        }
        return List.copyOf(result);
    }

    private static boolean[] valueArea(double[] volume, int poc, double target) {
        boolean[] included = new boolean[volume.length]; included[poc] = true;
        double sum = volume[poc]; int left = poc - 1, right = poc + 1;
        while (sum < target && (left >= 0 || right < volume.length)) {
            if (right >= volume.length || left >= 0 && volume[left] >= volume[right]) { included[left] = true; sum += volume[left--]; }
            else { included[right] = true; sum += volume[right++]; }
        }
        return included;
    }

    private static String volumeState(List<Candle> candles, BigDecimal val, BigDecimal vah) {
        Candle latest = candles.getLast();
        boolean accepted = latest.close().compareTo(val) >= 0 && latest.close().compareTo(vah) <= 0;
        if (latest.high().compareTo(vah) > 0 && latest.close().compareTo(vah) < 0) return "REJECTION_ABOVE_VALUE";
        if (latest.low().compareTo(val) < 0 && latest.close().compareTo(val) > 0) return "REJECTION_BELOW_VALUE";
        if (accepted) return "ACCEPTANCE_IN_VALUE";
        return latest.close().compareTo(vah) > 0 ? "ACCEPTANCE_ABOVE_VALUE" : "ACCEPTANCE_BELOW_VALUE";
    }

    private static BigDecimal atr(List<Candle> candles, int period) {
        BigDecimal total = BigDecimal.ZERO; int count = 0;
        for (int i = Math.max(1, candles.size() - period); i < candles.size(); i++) {
            Candle c = candles.get(i); BigDecimal previous = candles.get(i - 1).close();
            total = total.add(c.high().subtract(c.low()).max(c.high().subtract(previous).abs()).max(c.low().subtract(previous).abs())); count++;
        }
        return count == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(count), MC);
    }
    private static double percentile(double[] values, double p) { double[] copy = values.clone(); java.util.Arrays.sort(copy); return copy[(int)Math.floor((copy.length - 1) * p)]; }
    private static int indexOfMax(double[] values) { int best = 0; for (int i=1;i<values.length;i++) if(values[i]>values[best]) best=i; return best; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static List<Candle> closed(List<Candle> input) { return input.stream().filter(Candle::closed).sorted(Comparator.comparing(Candle::openTime)).toList(); }
}
