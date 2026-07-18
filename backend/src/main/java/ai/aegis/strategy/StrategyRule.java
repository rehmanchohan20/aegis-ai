package ai.aegis.strategy;

import java.math.BigDecimal;
import java.util.Objects;

public record StrategyRule(
        String feature,
        ComparisonOperator operator,
        BigDecimal threshold,
        int weight,
        boolean required
) {
    public StrategyRule {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(threshold, "threshold");
        if (weight < 0 || weight > 100) {
            throw new IllegalArgumentException("weight must be between 0 and 100");
        }
    }
}
