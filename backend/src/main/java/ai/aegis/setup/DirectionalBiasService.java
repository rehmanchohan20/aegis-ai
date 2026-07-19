package ai.aegis.setup;

import ai.aegis.market.Candle;
import ai.aegis.market.CandleStore;
import ai.aegis.structure.MarketStructureService;
import ai.aegis.structure.MarketStructureSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DirectionalBiasService {
    private static final MathContext MC = new MathContext(14, RoundingMode.HALF_UP);
    private final CandleStore candles;
    private final MarketStructureService structure;

    public DirectionalBiasService(CandleStore candles, MarketStructureService structure) {
        this.candles = candles; this.structure = structure;
    }

    public DirectionalBias analyze(String symbol) {
        Map<String, List<Candle>> histories = new LinkedHashMap<>();
        for (String timeframe : List.of("15m", "1h", "4h")) histories.put(timeframe, candles.latest(symbol, timeframe, 300));
        return analyze(symbol, histories);
    }

    public DirectionalBias analyze(String symbol, Map<String, List<Candle>> histories) {
        Map<String, DirectionalBias.TimeframeBias> components = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();
        double weightedScore = 0, totalWeight = 0;
        for (String timeframe : List.of("15m", "1h", "4h")) {
            List<Candle> history = histories.getOrDefault(timeframe, List.of()).stream().filter(Candle::closed).toList();
            if (history.size() < 60) { reasons.add(timeframe + " bias unavailable: fewer than 60 closed candles"); continue; }
            BigDecimal fast = ema(history, 20, history.size()); BigDecimal slow = ema(history, 50, history.size());
            BigDecimal priorFast = ema(history, 20, history.size() - 5);
            BigDecimal distance = fast.subtract(slow, MC).divide(history.getLast().close(), MC);
            BigDecimal slope = fast.subtract(priorFast, MC).divide(priorFast, MC);
            MarketStructureSnapshot marketStructure = structure.analyze(history);
            double score = Math.tanh(distance.doubleValue() * 120) * .45
                    + Math.tanh(slope.doubleValue() * 180) * .30 + structureScore(marketStructure.structureState()) * .25;
            String direction = score > .12 ? "LONG" : score < -.12 ? "SHORT" : "WAIT";
            components.put(timeframe, new DirectionalBias.TimeframeBias(timeframe, direction,
                    BigDecimal.valueOf(score), distance, slope, marketStructure.structureState()));
            double weight = "4h".equals(timeframe) ? 4 : "1h".equals(timeframe) ? 3 : 1;
            weightedScore += score * weight; totalWeight += weight;
        }
        double normalized = totalWeight == 0 ? 0 : weightedScore / totalWeight;
        String direction = normalized > .15 ? "LONG" : normalized < -.15 ? "SHORT" : "WAIT";
        DirectionalBias.TimeframeBias oneHour = components.get("1h"), fourHour = components.get("4h");
        boolean aligned = oneHour != null && fourHour != null && direction.equals(oneHour.direction()) && direction.equals(fourHour.direction());
        if (aligned) reasons.add("1h and 4h trend, EMA slope, and confirmed structure align " + direction);
        else reasons.add("1h and 4h directional evidence is not aligned; entry search remains neutral");
        return new DirectionalBias(symbol.toUpperCase(), aligned ? direction : "WAIT",
                BigDecimal.valueOf(Math.min(1, Math.abs(normalized))), components, aligned,
                List.copyOf(reasons), Instant.now());
    }

    private static double structureScore(String state) {
        if (state == null) return 0;
        if (state.startsWith("BULL")) return 1;
        if (state.startsWith("BEAR")) return -1;
        return 0;
    }

    private static BigDecimal ema(List<Candle> values, int period, int exclusiveEnd) {
        int end = Math.min(exclusiveEnd, values.size()); int start = Math.max(0, end - period * 4);
        BigDecimal result = values.get(start).close(); BigDecimal alpha = BigDecimal.valueOf(2.0 / (period + 1));
        for (int i = start + 1; i < end; i++) result = values.get(i).close().subtract(result, MC).multiply(alpha, MC).add(result, MC);
        return result;
    }
}
