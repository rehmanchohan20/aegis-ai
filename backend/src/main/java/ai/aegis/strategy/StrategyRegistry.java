package ai.aegis.strategy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class StrategyRegistry {
    private final List<StrategyDefinition> strategies = List.of(
            new StrategyDefinition("trend-long", "Trend Continuation Long", "LONG", 70, List.of(
                    new StrategyRule("emaSlope20", ComparisonOperator.GREATER_THAN, BigDecimal.ZERO, 30, true),
                    new StrategyRule("momentum10", ComparisonOperator.GREATER_THAN, BigDecimal.ZERO, 25, true),
                    new StrategyRule("zscore20", ComparisonOperator.LESS_THAN, new BigDecimal("2.5"), 15, false),
                    new StrategyRule("rvol20", ComparisonOperator.GREATER_THAN_OR_EQUAL, new BigDecimal("0.8"), 15, false),
                    new StrategyRule("closeLocation", ComparisonOperator.GREATER_THAN, new BigDecimal("0.55"), 15, false)
            )),
            new StrategyDefinition("trend-short", "Trend Continuation Short", "SHORT", 70, List.of(
                    new StrategyRule("emaSlope20", ComparisonOperator.LESS_THAN, BigDecimal.ZERO, 30, true),
                    new StrategyRule("momentum10", ComparisonOperator.LESS_THAN, BigDecimal.ZERO, 25, true),
                    new StrategyRule("zscore20", ComparisonOperator.GREATER_THAN, new BigDecimal("-2.5"), 15, false),
                    new StrategyRule("rvol20", ComparisonOperator.GREATER_THAN_OR_EQUAL, new BigDecimal("0.8"), 15, false),
                    new StrategyRule("closeLocation", ComparisonOperator.LESS_THAN, new BigDecimal("0.45"), 15, false)
            )),
            new StrategyDefinition("mean-reversion-long", "Mean Reversion Long", "LONG", 75, List.of(
                    new StrategyRule("zscore20", ComparisonOperator.LESS_THAN_OR_EQUAL, new BigDecimal("-1.8"), 40, true),
                    new StrategyRule("lowerWickRatio", ComparisonOperator.GREATER_THAN, new BigDecimal("0.25"), 25, false),
                    new StrategyRule("atrNormalized14", ComparisonOperator.LESS_THAN, new BigDecimal("0.05"), 20, true),
                    new StrategyRule("rvol20", ComparisonOperator.GREATER_THAN, new BigDecimal("1.0"), 15, false)
            )),
            new StrategyDefinition("mean-reversion-short", "Mean Reversion Short", "SHORT", 75, List.of(
                    new StrategyRule("zscore20", ComparisonOperator.GREATER_THAN_OR_EQUAL, new BigDecimal("1.8"), 40, true),
                    new StrategyRule("upperWickRatio", ComparisonOperator.GREATER_THAN, new BigDecimal("0.25"), 25, false),
                    new StrategyRule("atrNormalized14", ComparisonOperator.LESS_THAN, new BigDecimal("0.05"), 20, true),
                    new StrategyRule("rvol20", ComparisonOperator.GREATER_THAN, new BigDecimal("1.0"), 15, false)
            ))
    );

    public List<StrategyDefinition> all() {
        return strategies;
    }
}
