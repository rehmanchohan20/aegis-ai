package ai.aegis.api;

import ai.aegis.journal.PerformanceAnalyticsService;
import ai.aegis.journal.PerformanceSummary;
import ai.aegis.journal.TradeJournalEntry;
import ai.aegis.journal.TradeJournalStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/journal")
public class JournalController {
    private final TradeJournalStore store;
    private final PerformanceAnalyticsService analytics;

    public JournalController(TradeJournalStore store, PerformanceAnalyticsService analytics) {
        this.store = store;
        this.analytics = analytics;
    }

    @GetMapping
    public List<TradeJournalEntry> latest(@RequestParam(defaultValue = "100") int limit) {
        return store.latest(Math.max(1, Math.min(limit, 1000)));
    }

    @GetMapping("/performance")
    public PerformanceSummary performance() {
        return analytics.summarize();
    }
}
