package ai.aegis.dashboard;

import ai.aegis.journal.PerformanceSummary;
import ai.aegis.orchestration.DecisionCycleResult;
import ai.aegis.paper.PaperTrade;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DashboardSnapshot(
        DecisionCycleResult decision,
        List<PaperTrade> paperTrades,
        PerformanceSummary performance,
        long openTrades,
        Map<String, Object> predictionSummary,
        List<Map<String, Object>> recentPredictions,
        ProductionInsightsService.Insights insights,
        SafetyState safety,
        Instant generatedAt
) {
    public DashboardSnapshot {
        paperTrades = List.copyOf(paperTrades == null ? List.of() : paperTrades);
        predictionSummary = Map.copyOf(predictionSummary == null ? Map.of() : predictionSummary);
        recentPredictions = List.copyOf(recentPredictions == null ? List.of() : recentPredictions);
    }

    public record SafetyState(boolean liveExecutionEnabled, boolean killSwitchEngaged,
                              String executionMode) { }
}
