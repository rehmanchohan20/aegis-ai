package ai.aegis.dashboard;

import ai.aegis.journal.PerformanceSummary;
import ai.aegis.orchestration.DecisionCycleResult;
import ai.aegis.paper.PaperTrade;

import java.time.Instant;
import java.util.List;

public record DashboardSnapshot(
        DecisionCycleResult decision,
        List<PaperTrade> paperTrades,
        PerformanceSummary performance,
        long openTrades,
        Instant generatedAt
) {
    public DashboardSnapshot {
        paperTrades = List.copyOf(paperTrades == null ? List.of() : paperTrades);
    }
}
