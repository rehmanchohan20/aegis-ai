package ai.aegis.dashboard;

import ai.aegis.automation.RealtimeAutomationService;
import ai.aegis.journal.PerformanceAnalyticsService;
import ai.aegis.paper.PaperTrade;
import ai.aegis.paper.PaperTradingService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DashboardService {
    private final RealtimeAutomationService automation;
    private final PaperTradingService paperTrading;
    private final PerformanceAnalyticsService analytics;

    public DashboardService(RealtimeAutomationService automation,
                            PaperTradingService paperTrading,
                            PerformanceAnalyticsService analytics) {
        this.automation = automation;
        this.paperTrading = paperTrading;
        this.analytics = analytics;
    }

    public DashboardSnapshot snapshot() {
        List<PaperTrade> trades = paperTrading.list();
        long open = trades.stream().filter(t -> "OPEN".equals(t.status())).count();
        return new DashboardSnapshot(automation.latest(), trades, analytics.summarize(), open, Instant.now());
    }
}
