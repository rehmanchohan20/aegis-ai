package ai.aegis.dashboard;

import ai.aegis.automation.RealtimeAutomationService;
import ai.aegis.journal.PerformanceAnalyticsService;
import ai.aegis.paper.PaperTrade;
import ai.aegis.paper.PaperTradingService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import ai.aegis.ml.PredictionAuditService;

import java.time.Instant;
import java.util.List;

@Service
public class DashboardService {
    private final RealtimeAutomationService automation;
    private final PaperTradingService paperTrading;
    private final PerformanceAnalyticsService analytics;
    private final PredictionAuditService predictions;
    private final ProductionInsightsService insights;
    private final boolean liveExecutionEnabled;

    public DashboardService(RealtimeAutomationService automation,
                            PaperTradingService paperTrading,
                            PerformanceAnalyticsService analytics,
                            PredictionAuditService predictions,
                            ProductionInsightsService insights,
                            @Value("${aegis.execution.live-enabled:false}") boolean liveExecutionEnabled) {
        this.automation = automation;
        this.paperTrading = paperTrading;
        this.analytics = analytics;
        this.predictions = predictions;
        this.insights = insights;
        this.liveExecutionEnabled = liveExecutionEnabled;
    }

    public DashboardSnapshot snapshot() {
        List<PaperTrade> trades = paperTrading.list();
        long open = trades.stream().filter(t -> "OPEN".equals(t.status())).count();
        return new DashboardSnapshot(automation.latest(), trades, analytics.summarize(), open,
                predictions.summary(null), predictions.latest(25), insights.snapshot("aegis-direction"),
                new DashboardSnapshot.SafetyState(liveExecutionEnabled, !liveExecutionEnabled,
                        liveExecutionEnabled ? "LIVE_GUARDED" : "PAPER_ONLY"), Instant.now());
    }
}
