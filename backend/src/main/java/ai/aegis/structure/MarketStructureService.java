package ai.aegis.structure;

import ai.aegis.market.Candle;
import ai.aegis.market.CandleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class MarketStructureService {
    private static final Logger log = LoggerFactory.getLogger(MarketStructureService.class);
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final int PIVOT_WINDOW = 3;

    private final CandleStore candles;
    private final MarketStructureStore store;
    private final List<String> symbols;
    private final List<String> intervals;

    public MarketStructureService(CandleStore candles, MarketStructureStore store,
                                  @Value("${aegis.market.symbols:BTCUSDT}") String symbols,
                                  @Value("${aegis.market.intervals:1m}") String intervals) {
        this.candles = candles;
        this.store = store;
        this.symbols = csv(symbols, true);
        this.intervals = csv(intervals, false);
    }

    @Scheduled(fixedDelayString = "${aegis.structure.delay-ms:15000}", initialDelayString = "${aegis.structure.initial-delay-ms:20000}")
    public void calculateConfiguredMarkets() {
        for (String symbol : symbols) for (String interval : intervals) {
            try {
                List<Candle> history = candles.latest(symbol, interval, 300).stream().filter(Candle::closed).toList();
                if (history.size() < 60) continue;
                store.save(analyze(history));
            } catch (RuntimeException exception) {
                log.warn("Market-structure calculation failed for {} {}", symbol, interval, exception);
            }
        }
    }

    public MarketStructureSnapshot latestOrCalculate(String symbol, String interval) {
        MarketStructureSnapshot latest = store.latest(symbol, interval);
        if (latest != null) return latest;
        List<Candle> history = candles.latest(symbol, interval, 300).stream().filter(Candle::closed).toList();
        if (history.size() < 60) return null;
        MarketStructureSnapshot calculated = analyze(history);
        store.save(calculated);
        return calculated;
    }

    public MarketStructureSnapshot analyze(List<Candle> input) {
        List<Candle> history = input == null ? List.of() : input.stream().filter(Candle::closed).toList();
        if (history.size() < 60) throw new IllegalArgumentException("At least 60 closed candles are required");
        Candle latest = history.getLast();
        BigDecimal atr = atr(history, 14);
        List<MarketStructureSnapshot.Pivot> pivots = pivots(history);
        List<MarketStructureSnapshot.Pivot> highs = pivots.stream().filter(p -> "HIGH".equals(p.type())).toList();
        List<MarketStructureSnapshot.Pivot> lows = pivots.stream().filter(p -> "LOW".equals(p.type())).toList();
        String structure = structure(highs, lows);
        BigDecimal support = nearest(lows, latest.close(), false);
        BigDecimal resistance = nearest(highs, latest.close(), true);
        if (support == null) support = history.subList(history.size() - 20, history.size()).stream()
                .map(Candle::low).min(BigDecimal::compareTo).orElse(latest.low());
        if (resistance == null) resistance = history.subList(history.size() - 20, history.size()).stream()
                .map(Candle::high).max(BigDecimal::compareTo).orElse(latest.high());
        List<MarketStructureSnapshot.TrendLine> trendLines = new ArrayList<>();
        trendLine(latest, lows, atr, "SUPPORT", latest.interval()).ifPresent(trendLines::add);
        trendLine(latest, highs, atr, "RESISTANCE", latest.interval()).ifPresent(trendLines::add);
        boolean consolidation = consolidation(history, atr);
        String breakout = breakout(history, support, resistance, atr);
        List<MarketStructureSnapshot.Marker> markers = markers(history, highs, lows, support, resistance, atr);
        List<MarketStructureSnapshot.Zone> zones = List.of(
                zone("SUPPORT", support, atr, lows), zone("RESISTANCE", resistance, atr, highs));
        String regime = consolidation ? "CONSOLIDATION" : structure.startsWith("BULL") ? "BULL_TREND"
                : structure.startsWith("BEAR") ? "BEAR_TREND" : "TRANSITION";
        List<String> warnings = pivots.size() < 6 ? List.of("Limited confirmed pivot history") : List.of();
        return new MarketStructureSnapshot(UUID.randomUUID(), latest.symbol(), latest.interval(), Instant.now(),
                structure, regime, support, resistance, atr, latest.close().add(atr.multiply(BigDecimal.valueOf(2), MC), MC),
                latest.close().subtract(atr.multiply(BigDecimal.valueOf(2), MC), MC), consolidation, breakout,
                pivots.stream().skip(Math.max(0, pivots.size() - 40L)).toList(), List.copyOf(trendLines), zones,
                List.copyOf(markers), warnings);
    }

    private static List<MarketStructureSnapshot.Pivot> pivots(List<Candle> history) {
        List<MarketStructureSnapshot.Pivot> result = new ArrayList<>();
        for (int index = PIVOT_WINDOW; index < history.size() - PIVOT_WINDOW; index++) {
            Candle candidate = history.get(index);
            boolean high = true;
            boolean low = true;
            int strength = 0;
            for (int offset = 1; offset <= PIVOT_WINDOW; offset++) {
                high &= candidate.high().compareTo(history.get(index - offset).high()) > 0
                        && candidate.high().compareTo(history.get(index + offset).high()) >= 0;
                low &= candidate.low().compareTo(history.get(index - offset).low()) < 0
                        && candidate.low().compareTo(history.get(index + offset).low()) <= 0;
                if (high || low) strength++;
            }
            if (high) result.add(new MarketStructureSnapshot.Pivot(index, candidate.openTime(), candidate.high(), "HIGH", strength));
            if (low) result.add(new MarketStructureSnapshot.Pivot(index, candidate.openTime(), candidate.low(), "LOW", strength));
        }
        return result.stream().sorted(Comparator.comparingInt(MarketStructureSnapshot.Pivot::index)).toList();
    }

    private static java.util.Optional<MarketStructureSnapshot.TrendLine> trendLine(
            Candle latest, List<MarketStructureSnapshot.Pivot> source, BigDecimal atr, String type, String timeframe) {
        if (source.size() < 2) return java.util.Optional.empty();
        List<MarketStructureSnapshot.Pivot> sample = source.subList(Math.max(0, source.size() - 6), source.size());
        double xMean = sample.stream().mapToInt(MarketStructureSnapshot.Pivot::index).average().orElse(0);
        double yMean = sample.stream().mapToDouble(p -> p.price().doubleValue()).average().orElse(0);
        double numerator = 0;
        double denominator = 0;
        for (MarketStructureSnapshot.Pivot pivot : sample) {
            numerator += (pivot.index() - xMean) * (pivot.price().doubleValue() - yMean);
            denominator += Math.pow(pivot.index() - xMean, 2);
        }
        double slope = denominator == 0 ? 0 : numerator / denominator;
        double intercept = yMean - slope * xMean;
        double tolerance = Math.max(atr.doubleValue() * 0.35, latest.close().doubleValue() * 0.0002);
        int touches = (int) sample.stream().filter(p -> Math.abs(p.price().doubleValue()
                - (intercept + slope * p.index())) <= tolerance).count();
        if (touches < 2) return java.util.Optional.empty();
        MarketStructureSnapshot.Pivot start = sample.getFirst();
        MarketStructureSnapshot.Pivot end = sample.getLast();
        double projected = intercept + slope * (end.index() + 1);
        BigDecimal projectedPrice = BigDecimal.valueOf(projected);
        BigDecimal distancePercent = latest.close().subtract(projectedPrice, MC).abs()
                .divide(latest.close(), MC).multiply(BigDecimal.valueOf(100), MC);
        boolean broken = "SUPPORT".equals(type) ? latest.close().compareTo(projectedPrice.subtract(atr.multiply(BigDecimal.valueOf(.15), MC), MC)) < 0
                : latest.close().compareTo(projectedPrice.add(atr.multiply(BigDecimal.valueOf(.15), MC), MC)) > 0;
        BigDecimal confidence = BigDecimal.valueOf(Math.min(1.0, 0.35 + touches * 0.12
                + Math.min(0.25, sample.size() * 0.03)));
        return java.util.Optional.of(new MarketStructureSnapshot.TrendLine(latest.symbol(), timeframe, type,
                start.time(), BigDecimal.valueOf(intercept + slope * start.index()), end.time(),
                BigDecimal.valueOf(intercept + slope * end.index()), BigDecimal.valueOf(slope), touches,
                distancePercent, broken ? "BROKEN" : "ACTIVE", confidence));
    }

    private static String structure(List<MarketStructureSnapshot.Pivot> highs,
                                    List<MarketStructureSnapshot.Pivot> lows) {
        if (highs.size() < 2 || lows.size() < 2) return "UNCONFIRMED";
        boolean hh = highs.getLast().price().compareTo(highs.get(highs.size() - 2).price()) > 0;
        boolean hl = lows.getLast().price().compareTo(lows.get(lows.size() - 2).price()) > 0;
        boolean lh = highs.getLast().price().compareTo(highs.get(highs.size() - 2).price()) < 0;
        boolean ll = lows.getLast().price().compareTo(lows.get(lows.size() - 2).price()) < 0;
        if (hh && hl) return "BULL_HH_HL";
        if (lh && ll) return "BEAR_LH_LL";
        return "TRANSITION_CHOCH_RISK";
    }

    private static BigDecimal nearest(List<MarketStructureSnapshot.Pivot> pivots, BigDecimal price, boolean above) {
        return pivots.stream().map(MarketStructureSnapshot.Pivot::price)
                .filter(value -> above ? value.compareTo(price) >= 0 : value.compareTo(price) <= 0)
                .min((left, right) -> left.subtract(price).abs().compareTo(right.subtract(price).abs())).orElse(null);
    }

    private static boolean consolidation(List<Candle> history, BigDecimal atr) {
        List<Candle> window = history.subList(history.size() - 20, history.size());
        BigDecimal high = window.stream().map(Candle::high).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal low = window.stream().map(Candle::low).min(BigDecimal::compareTo).orElseThrow();
        return high.subtract(low, MC).compareTo(atr.multiply(BigDecimal.valueOf(3), MC)) <= 0;
    }

    private static String breakout(List<Candle> history, BigDecimal support, BigDecimal resistance, BigDecimal atr) {
        Candle latest = history.getLast();
        BigDecimal averageVolume = history.subList(history.size() - 20, history.size()).stream().map(Candle::volume)
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(20), MC);
        boolean volumeConfirmed = latest.volume().compareTo(averageVolume.multiply(BigDecimal.valueOf(1.2), MC)) > 0;
        BigDecimal buffer = atr.multiply(BigDecimal.valueOf(.10), MC);
        if (latest.close().compareTo(resistance.add(buffer, MC)) > 0) return volumeConfirmed ? "BULL_BREAKOUT_CONFIRMED" : "BULL_BREAKOUT_WEAK_VOLUME";
        if (latest.close().compareTo(support.subtract(buffer, MC)) < 0) return volumeConfirmed ? "BEAR_BREAKOUT_CONFIRMED" : "BEAR_BREAKOUT_WEAK_VOLUME";
        if (latest.high().compareTo(resistance) > 0 && latest.close().compareTo(resistance) < 0) return "FALSE_BREAKOUT_OR_LIQUIDITY_SWEEP_HIGH";
        if (latest.low().compareTo(support) < 0 && latest.close().compareTo(support) > 0) return "FALSE_BREAKOUT_OR_LIQUIDITY_SWEEP_LOW";
        return "NONE";
    }

    private static List<MarketStructureSnapshot.Marker> markers(List<Candle> history,
            List<MarketStructureSnapshot.Pivot> highs, List<MarketStructureSnapshot.Pivot> lows,
            BigDecimal support, BigDecimal resistance, BigDecimal atr) {
        List<MarketStructureSnapshot.Marker> markers = new ArrayList<>();
        Candle latest = history.getLast();
        if (latest.close().compareTo(resistance) > 0) markers.add(new MarketStructureSnapshot.Marker(
                latest.closeTime(), latest.close(), "BREAK_OF_STRUCTURE", "LONG", BigDecimal.valueOf(.75)));
        if (latest.close().compareTo(support) < 0) markers.add(new MarketStructureSnapshot.Marker(
                latest.closeTime(), latest.close(), "BREAK_OF_STRUCTURE", "SHORT", BigDecimal.valueOf(.75)));
        if ("TRANSITION_CHOCH_RISK".equals(structure(highs, lows))) markers.add(new MarketStructureSnapshot.Marker(
                latest.closeTime(), latest.close(), "CHANGE_OF_CHARACTER", "WAIT", BigDecimal.valueOf(.60)));
        for (int index = Math.max(2, history.size() - 30); index < history.size(); index++) {
            Candle current = history.get(index);
            Candle twoBack = history.get(index - 2);
            if (current.low().compareTo(twoBack.high()) > 0 && current.low().subtract(twoBack.high(), MC).compareTo(atr.multiply(BigDecimal.valueOf(.15), MC)) > 0) {
                markers.add(new MarketStructureSnapshot.Marker(current.openTime(), current.low(), "FAIR_VALUE_GAP", "LONG", BigDecimal.valueOf(.55)));
            } else if (current.high().compareTo(twoBack.low()) < 0 && twoBack.low().subtract(current.high(), MC).compareTo(atr.multiply(BigDecimal.valueOf(.15), MC)) > 0) {
                markers.add(new MarketStructureSnapshot.Marker(current.openTime(), current.high(), "FAIR_VALUE_GAP", "SHORT", BigDecimal.valueOf(.55)));
            }
        }
        return markers.stream().skip(Math.max(0, markers.size() - 20L)).toList();
    }

    private static MarketStructureSnapshot.Zone zone(String type, BigDecimal price, BigDecimal atr,
            List<MarketStructureSnapshot.Pivot> pivots) {
        BigDecimal halfWidth = atr.multiply(BigDecimal.valueOf(.20), MC);
        int touches = (int) pivots.stream().filter(p -> p.price().subtract(price, MC).abs().compareTo(halfWidth) <= 0).count();
        return new MarketStructureSnapshot.Zone(type, price.subtract(halfWidth, MC), price.add(halfWidth, MC),
                touches, BigDecimal.valueOf(Math.min(1.0, .35 + touches * .12)));
    }

    private static BigDecimal atr(List<Candle> history, int length) {
        BigDecimal total = BigDecimal.ZERO;
        int start = history.size() - length;
        for (int index = start; index < history.size(); index++) {
            Candle candle = history.get(index);
            BigDecimal previousClose = history.get(index - 1).close();
            total = total.add(candle.high().subtract(candle.low(), MC)
                    .max(candle.high().subtract(previousClose, MC).abs())
                    .max(candle.low().subtract(previousClose, MC).abs()), MC);
        }
        return total.divide(BigDecimal.valueOf(length), MC);
    }

    private static List<String> csv(String value, boolean uppercase) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(entry -> !entry.isBlank())
                .map(entry -> uppercase ? entry.toUpperCase() : entry).distinct().toList();
    }
}
