package ai.aegis.monitoring;

import ai.aegis.health.StrategyHealthDecision;
import ai.aegis.journal.PerformanceSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StrategyFeedback(
        PerformanceSummary performance,
        StrategyHealthDecision health,
        BigDecimal liveMeanPnl,
        BigDecimal liveVolatility,
        BigDecimal estimatedDrawdownPercent,
        List<String> actions,
        Instant generatedAt
) {
    public StrategyFeedback {
        actions = List.copyOf(actions == null ? List.of() : actions);
    }
}
