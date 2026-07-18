package ai.aegis.orchestration;

import ai.aegis.feature.CandleFeatureService;
import ai.aegis.feature.FeatureSnapshot;
import ai.aegis.market.Candle;
import ai.aegis.strategy.CandidateSignal;
import ai.aegis.strategy.StrategyEvaluator;
import ai.aegis.strategy.StrategyRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DecisionCycleService {
    private final CandleFeatureService featureService;
    private final StrategyRegistry strategyRegistry;
    private final StrategyEvaluator strategyEvaluator;

    public DecisionCycleService(CandleFeatureService featureService,
                                StrategyRegistry strategyRegistry,
                                StrategyEvaluator strategyEvaluator) {
        this.featureService = featureService;
        this.strategyRegistry = strategyRegistry;
        this.strategyEvaluator = strategyEvaluator;
    }

    public DecisionCycleResult run(List<Candle> candles) {
        FeatureSnapshot snapshot = featureService.build(candles);
        List<CandidateSignal> signals = strategyRegistry.all().stream()
                .map(strategy -> strategyEvaluator.evaluate(strategy, snapshot))
                .sorted(Comparator.comparingInt(CandidateSignal::score).reversed())
                .toList();

        List<CandidateSignal> eligible = signals.stream().filter(CandidateSignal::eligible).toList();
        List<String> reasons = new ArrayList<>();
        String direction = "WAIT";
        int score = 0;

        if (eligible.isEmpty()) {
            reasons.add("No strategy passed its minimum score and required rules");
        } else {
            CandidateSignal best = eligible.get(0);
            boolean conflict = eligible.stream().anyMatch(s -> !s.side().equals(best.side()));
            if (conflict) {
                reasons.add("Conflicting eligible LONG and SHORT strategies");
            } else {
                direction = best.side();
                score = best.score();
                reasons.add("Selected strategy: " + best.strategyId());
                reasons.addAll(best.confirmations());
            }
        }

        return new DecisionCycleResult(snapshot.symbol(), snapshot.interval(), snapshot,
                signals, direction, score, List.copyOf(reasons), Instant.now());
    }
}
