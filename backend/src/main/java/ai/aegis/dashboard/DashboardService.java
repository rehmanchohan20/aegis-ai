package ai.aegis.dashboard;

import ai.aegis.automation.RealtimeAutomationService;
import ai.aegis.journal.PerformanceAnalyticsService;
import ai.aegis.paper.PaperTrade;
import ai.aegis.paper.PaperTradingService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import ai.aegis.ml.PredictionAuditService;
import ai.aegis.market.MarketDataStateService;
import ai.aegis.structure.MarketStructureService;
import ai.aegis.analysis.MultiTimeframeAnalysisService;

import java.time.Instant;
import java.util.List;

@Service
public class DashboardService {
    private final RealtimeAutomationService automation;
    private final PaperTradingService paperTrading;
    private final PerformanceAnalyticsService analytics;
    private final PredictionAuditService predictions;
    private final ProductionInsightsService insights;
    private final MarketDataStateService marketData;
    private final MarketStructureService marketStructure;
    private final MultiTimeframeAnalysisService multiTimeframe;
    private final boolean liveExecutionEnabled;

    public DashboardService(RealtimeAutomationService automation,
                            PaperTradingService paperTrading,
                            PerformanceAnalyticsService analytics,
                            PredictionAuditService predictions,
                            ProductionInsightsService insights,
                            MarketDataStateService marketData,
                            MarketStructureService marketStructure,
                            MultiTimeframeAnalysisService multiTimeframe,
                            @Value("${aegis.execution.live-enabled:false}") boolean liveExecutionEnabled) {
        this.automation = automation;
        this.paperTrading = paperTrading;
        this.analytics = analytics;
        this.predictions = predictions;
        this.insights = insights;
        this.marketData = marketData;
        this.marketStructure = marketStructure;
        this.multiTimeframe = multiTimeframe;
        this.liveExecutionEnabled = liveExecutionEnabled;
    }

    public DashboardSnapshot snapshot(String symbol, String interval) {
        List<PaperTrade> trades = paperTrading.list();
        long open = trades.stream().filter(t -> "OPEN".equals(t.status())).count();
        return new DashboardSnapshot(automation.latest(symbol, interval), trades, analytics.summarize(), open,
                predictions.summary(null), predictions.latest(25), insights.snapshot("aegis-direction"),
                marketData.latest(symbol), marketStructure.latestOrCalculate(symbol, interval),
                multiTimeframe.analyze(symbol),
                new DashboardSnapshot.SafetyState(liveExecutionEnabled, !liveExecutionEnabled,
                        liveExecutionEnabled ? "LIVE_GUARDED" : "PAPER_ONLY"), Instant.now());
    }
}
