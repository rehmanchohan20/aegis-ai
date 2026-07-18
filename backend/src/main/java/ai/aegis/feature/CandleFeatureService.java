package ai.aegis.feature;

import ai.aegis.market.Candle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CandleFeatureService {
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    public FeatureSnapshot build(List<Candle> input) {
        if (input == null || input.size() < 30) {
            throw new IllegalArgumentException("at least 30 candles are required");
        }
        List<Candle> candles = input.stream().filter(Candle::closed).toList();
        if (candles.size() < 30) throw new IllegalArgumentException("at least 30 closed candles are required");

        Candle latest = candles.get(candles.size() - 1);
        Map<String, FeatureValue> features = new LinkedHashMap<>();
        add(features, "return1", pct(candles.get(candles.size()-2).close(), latest.close()), latest);
        add(features, "return5", pct(candles.get(candles.size()-6).close(), latest.close()), latest);
        add(features, "momentum10", pct(candles.get(candles.size()-11).close(), latest.close()), latest);
        add(features, "volatility20", volatility(candles, 20), latest);
        add(features, "zscore20", zScore(candles, 20), latest);
        add(features, "rvol20", relativeVolume(candles, 20), latest);
        add(features, "bodyRatio", ratio(latest.close().subtract(latest.open()).abs(), latest.high().subtract(latest.low())), latest);
        add(features, "upperWickRatio", ratio(latest.high().subtract(latest.open().max(latest.close())), latest.high().subtract(latest.low())), latest);
        add(features, "lowerWickRatio", ratio(latest.open().min(latest.close()).subtract(latest.low()), latest.high().subtract(latest.low())), latest);
        add(features, "emaSlope20", emaSlope(candles, 20), latest);
        add(features, "atrNormalized14", normalizedAtr(candles, 14), latest);
        add(features, "closeLocation", ratio(latest.close().subtract(latest.low()), latest.high().subtract(latest.low())), latest);
        return new FeatureSnapshot(latest.symbol(), latest.interval(), Instant.now(), features);
    }

    private void add(Map<String, FeatureValue> map, String name, BigDecimal value, Candle candle) {
        FeatureQuality quality = value == null ? FeatureQuality.INVALID : FeatureQuality.GOOD;
        map.put(name, new FeatureValue(name, value, candle.closeTime(), quality, "candles"));
    }

    private BigDecimal pct(BigDecimal from, BigDecimal to) {
        return from.signum() == 0 ? null : to.subtract(from).divide(from, MC);
    }

    private BigDecimal ratio(BigDecimal a, BigDecimal b) {
        return b.signum() == 0 ? BigDecimal.ZERO : a.divide(b, MC);
    }

    private BigDecimal volatility(List<Candle> candles, int n) {
        double[] r = returns(candles, n);
        double mean = java.util.Arrays.stream(r).average().orElse(0);
        double variance = java.util.Arrays.stream(r).map(x -> Math.pow(x - mean, 2)).sum() / Math.max(1, r.length - 1);
        return BigDecimal.valueOf(Math.sqrt(variance));
    }

    private BigDecimal zScore(List<Candle> candles, int n) {
        int start = candles.size() - n;
        double mean = candles.subList(start, candles.size()).stream().mapToDouble(c -> c.close().doubleValue()).average().orElse(0);
        double variance = candles.subList(start, candles.size()).stream().mapToDouble(c -> Math.pow(c.close().doubleValue() - mean, 2)).sum() / n;
        double sd = Math.sqrt(variance);
        return BigDecimal.valueOf(sd == 0 ? 0 : (candles.get(candles.size()-1).close().doubleValue() - mean) / sd);
    }

    private BigDecimal relativeVolume(List<Candle> candles, int n) {
        List<Candle> window = candles.subList(candles.size()-n, candles.size());
        double avg = window.stream().limit(n-1).mapToDouble(c -> c.volume().doubleValue()).average().orElse(0);
        return BigDecimal.valueOf(avg == 0 ? 0 : window.get(n-1).volume().doubleValue() / avg);
    }

    private BigDecimal emaSlope(List<Candle> candles, int period) {
        double alpha = 2.0 / (period + 1);
        double previous = candles.get(candles.size()-period-1).close().doubleValue();
        double beforeLast = previous;
        for (int i = candles.size()-period; i < candles.size(); i++) {
            previous = alpha * candles.get(i).close().doubleValue() + (1-alpha) * previous;
            if (i == candles.size()-2) beforeLast = previous;
        }
        return BigDecimal.valueOf(beforeLast == 0 ? 0 : (previous-beforeLast)/beforeLast);
    }

    private BigDecimal normalizedAtr(List<Candle> candles, int period) {
        double sum = 0;
        for (int i = candles.size()-period; i < candles.size(); i++) {
            Candle c = candles.get(i); Candle p = candles.get(i-1);
            double tr = Math.max(c.high().doubleValue()-c.low().doubleValue(), Math.max(Math.abs(c.high().doubleValue()-p.close().doubleValue()), Math.abs(c.low().doubleValue()-p.close().doubleValue())));
            sum += tr;
        }
        double close = candles.get(candles.size()-1).close().doubleValue();
        return BigDecimal.valueOf(close == 0 ? 0 : (sum/period)/close);
    }

    private double[] returns(List<Candle> candles, int n) {
        double[] values = new double[n-1]; int start = candles.size()-n;
        for (int i=1;i<n;i++) values[i-1] = Math.log(candles.get(start+i).close().doubleValue()/candles.get(start+i-1).close().doubleValue());
        return values;
    }
}
