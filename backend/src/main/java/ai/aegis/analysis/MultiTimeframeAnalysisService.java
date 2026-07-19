package ai.aegis.analysis;

import ai.aegis.market.Candle;
import ai.aegis.market.CandleStore;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MultiTimeframeAnalysisService {
    private final CandleStore candleStore;
    private final MarketAnalysisService analysisService;
    private final List<String> timeframes;

    public MultiTimeframeAnalysisService(CandleStore candleStore, MarketAnalysisService analysisService,
                                         @Value("${aegis.market.intervals:1m,3m,5m,15m,1h,4h}") String timeframes) {
        this.candleStore = candleStore;
        this.analysisService = analysisService;
        this.timeframes = java.util.Arrays.stream(timeframes.split(",")).map(String::trim)
                .filter(value -> !value.isBlank()).distinct().toList();
    }

    public MultiTimeframeDecision analyze(String symbol) {
        Map<String, String> decisions = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();
        int longVotes = 0;
        int shortVotes = 0;
        int availableWeight = 0;

        for (String timeframe : timeframes) {
            List<Candle> candles = candleStore.latest(symbol, timeframe, 200);
            if (candles.size() < 60) {
                decisions.put(timeframe, "INSUFFICIENT_DATA");
                reasons.add(timeframe + " requires at least 60 candles.");
                continue;
            }
            MarketAnalysis analysis = analysisService.analyze(candles);
            decisions.put(timeframe, analysis.decision());
            int weight = weight(timeframe);
            availableWeight += weight;
            if ("LONG".equals(analysis.decision())) longVotes += weight;
            if ("SHORT".equals(analysis.decision())) shortVotes += weight;
        }

        String decision = longVotes > shortVotes ? "LONG" : shortVotes > longVotes ? "SHORT" : "WAIT";
        int alignmentScore = availableWeight == 0 ? 0 : (int) Math.round(100.0 * Math.max(longVotes, shortVotes) / availableWeight);
        boolean higherConflict = decisions.entrySet().stream().filter(entry -> weight(entry.getKey()) >= 3)
                .anyMatch(entry -> !"WAIT".equals(entry.getValue()) && !"INSUFFICIENT_DATA".equals(entry.getValue())
                        && !entry.getValue().equals(decision));
        boolean approved = alignmentScore >= 60 && !"WAIT".equals(decision) && !higherConflict;
        reasons.add(approved
                ? "Weighted lower and higher timeframes confirm the same direction."
                : higherConflict ? "A higher timeframe conflicts with the proposed direction."
                : "Weighted directional agreement is insufficient; trade execution remains blocked.");
        return new MultiTimeframeDecision(symbol.toUpperCase(), decision, alignmentScore, approved,
                Map.copyOf(decisions), List.copyOf(reasons));
    }

    private static int weight(String timeframe) {
        return switch (timeframe) {
            case "4h", "1h" -> 3;
            case "15m" -> 2;
            default -> 1;
        };
    }
}
