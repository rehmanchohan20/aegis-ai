package ai.aegis.strategy;

import ai.aegis.feature.FeatureQuality;
import ai.aegis.feature.FeatureSnapshot;
import ai.aegis.feature.FeatureValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StrategyEvaluatorTest {
    private final StrategyEvaluator evaluator = new StrategyEvaluator();

    @Test
    void approvesWhenRequiredRulesAndScorePass() {
        StrategyDefinition strategy = new StrategyDefinition("trend-long", "Trend Long", "LONG", 70, List.of(
                new StrategyRule("emaSlope", ComparisonOperator.GREATER_THAN, BigDecimal.ZERO, 60, true),
                new StrategyRule("rsi", ComparisonOperator.GREATER_THAN_OR_EQUAL, new BigDecimal("55"), 40, false)
        ));
        FeatureSnapshot snapshot = new FeatureSnapshot("BTCUSDT", "5m", Instant.now(), Map.of(
                "emaSlope", new FeatureValue("emaSlope", new BigDecimal("0.8"), Instant.now(), FeatureQuality.GOOD, "test"),
                "rsi", new FeatureValue("rsi", new BigDecimal("61"), Instant.now(), FeatureQuality.GOOD, "test")
        ));

        CandidateSignal result = evaluator.evaluate(strategy, snapshot);

        assertTrue(result.eligible());
        assertEquals(100, result.score());
        assertTrue(result.rejections().isEmpty());
    }

    @Test
    void rejectsMissingRequiredFeature() {
        StrategyDefinition strategy = new StrategyDefinition("trend-long", "Trend Long", "LONG", 50, List.of(
                new StrategyRule("emaSlope", ComparisonOperator.GREATER_THAN, BigDecimal.ZERO, 100, true)
        ));
        FeatureSnapshot snapshot = new FeatureSnapshot("BTCUSDT", "5m", Instant.now(), Map.of());

        CandidateSignal result = evaluator.evaluate(strategy, snapshot);

        assertFalse(result.eligible());
        assertTrue(result.rejections().stream().anyMatch(reason -> reason.contains("Required feature unavailable")));
    }
}
