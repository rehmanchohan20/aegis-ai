package ai.aegis.strategy;

import ai.aegis.feature.FeatureSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class StrategyEvaluator {

    public CandidateSignal evaluate(StrategyDefinition strategy, FeatureSnapshot snapshot) {
        int earned = 0;
        int possible = strategy.rules().stream().mapToInt(StrategyRule::weight).sum();
        List<String> confirmations = new ArrayList<>();
        List<String> rejections = new ArrayList<>();

        for (StrategyRule rule : strategy.rules()) {
            var value = snapshot.usableValue(rule.feature());
            if (value.isEmpty()) {
                if (rule.required()) {
                    rejections.add("Required feature unavailable: " + rule.feature());
                }
                continue;
            }

            boolean passed = compare(value.get(), rule.operator(), rule.threshold());
            if (passed) {
                earned += rule.weight();
                confirmations.add(rule.feature() + " passed " + rule.operator());
            } else if (rule.required()) {
                rejections.add(rule.feature() + " failed required rule");
            }
        }

        int score = possible == 0 ? 0 : (int) Math.round((earned * 100.0) / possible);
        if (score < strategy.minimumScore()) {
            rejections.add("Score " + score + " below minimum " + strategy.minimumScore());
        }

        return new CandidateSignal(
                strategy.id(), snapshot.symbol(), snapshot.interval(), strategy.side().toUpperCase(), score,
                rejections.isEmpty(), confirmations, rejections, Instant.now());
    }

    private boolean compare(BigDecimal actual, ComparisonOperator operator, BigDecimal threshold) {
        int comparison = actual.compareTo(threshold);
        return switch (operator) {
            case GREATER_THAN -> comparison > 0;
            case GREATER_THAN_OR_EQUAL -> comparison >= 0;
            case LESS_THAN -> comparison < 0;
            case LESS_THAN_OR_EQUAL -> comparison <= 0;
            case EQUAL -> comparison == 0;
            case NOT_EQUAL -> comparison != 0;
        };
    }
}
