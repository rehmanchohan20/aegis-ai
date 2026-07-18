package ai.aegis.analysis;

import ai.aegis.market.Candle;
import ai.aegis.market.CandleStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MultiTimeframeAnalysisService {
    private static final List<String> TIMEFRAMES = List.of("1m", "5m", "15m");

    private final CandleStore candleStore;
    private final MarketAnalysisService analysisService;

    public MultiTimeframeAnalysisService(CandleStore candleStore, MarketAnalysisService analysisService) {
        this.candleStore = candleStore;
        this.analysisService = analysisService;
    }

    public MultiTimeframeDecision analyze(String symbol) {
        Map<String, String> decisions = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();
        int longVotes = 0;
        int shortVotes = 0;

        for (String timeframe : TIMEFRAMES) {
            List<Candle> candles = candleStore.latest(symbol, timeframe, 200);
            if (candles.size() < 60) {
                decisions.put(timeframe, "INSUFFICIENT_DATA");
                reasons.add(timeframe + " requires at least 60 candles.");
                continue;
            }
            MarketAnalysis analysis = analysisService.analyze(candles);
            decisions.put(timeframe, analysis.decision());
            if ("LONG".equals(analysis.decision())) longVotes++;
            if ("SHORT".equals(analysis.decision())) shortVotes++;
        }

        String decision = longVotes >= 2 ? "LONG" : shortVotes >= 2 ? "SHORT" : "WAIT";
        int alignmentScore = Math.max(longVotes, shortVotes) * 33;
        boolean approved = alignmentScore >= 66 && !"WAIT".equals(decision);
        reasons.add(approved
                ? "At least two monitored timeframes confirm the same direction."
                : "Directional agreement is insufficient; trade execution remains blocked.");
        return new MultiTimeframeDecision(symbol.toUpperCase(), decision, alignmentScore, approved,
                Map.copyOf(decisions), List.copyOf(reasons));
    }
}
