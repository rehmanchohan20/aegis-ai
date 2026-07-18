package ai.aegis.monitoring;

import ai.aegis.health.StrategyHealthDecision;
import ai.aegis.health.StrategyHealthService;
import ai.aegis.journal.PerformanceAnalyticsService;
import ai.aegis.journal.PerformanceSummary;
import ai.aegis.journal.TradeJournalEntry;
import ai.aegis.journal.TradeJournalStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class StrategyFeedbackService {
    private final TradeJournalStore journalStore;
    private final PerformanceAnalyticsService analyticsService;
    private final StrategyHealthService healthService;

    public StrategyFeedbackService(TradeJournalStore journalStore,
                                   PerformanceAnalyticsService analyticsService,
                                   StrategyHealthService healthService) {
        this.journalStore = journalStore;
        this.analyticsService = analyticsService;
        this.healthService = healthService;
    }

    public StrategyFeedback evaluate(double backtestMean,
                                     double backtestVolatility,
                                     double maximumDrawdownPercent,
                                     int minimumLiveTrades) {
        List<TradeJournalEntry> closed = journalStore.latest(1000).stream()
                .filter(t -> t.closedAt() != null && t.realizedPnl() != null)
                .toList();
        PerformanceSummary performance = analyticsService.summarize();

        double liveMean = closed.stream().mapToDouble(t -> t.realizedPnl().doubleValue()).average().orElse(0);
        double variance = closed.size() < 2 ? 0 : closed.stream()
                .mapToDouble(t -> Math.pow(t.realizedPnl().doubleValue() - liveMean, 2))
                .sum() / (closed.size() - 1);
        double liveVolatility = Math.sqrt(variance);
        double drawdown = estimateDrawdownPercent(closed);

        StrategyHealthDecision health = healthService.evaluate(
                backtestMean, liveMean, backtestVolatility, liveVolatility,
                drawdown, maximumDrawdownPercent, performance.currentLosingStreak(),
                minimumLiveTrades, closed.size());

        List<String> actions = new ArrayList<>();
        switch (health.status()) {
            case "HEALTHY" -> actions.add("Continue paper execution under current risk limits");
            case "DEGRADED" -> {
                actions.add("Reduce risk allocation by 50%");
                actions.add("Run walk-forward and bootstrap revalidation");
            }
            case "HALTED" -> {
                actions.add("Disable new strategy entries");
                actions.add("Investigate regime, feature and execution drift before reactivation");
            }
            default -> actions.add("Manual review required");
        }

        return new StrategyFeedback(performance, health, bd(liveMean), bd(liveVolatility),
                bd(drawdown), actions, Instant.now());
    }

    private double estimateDrawdownPercent(List<TradeJournalEntry> trades) {
        double equity = 100.0;
        double peak = equity;
        double maximum = 0;
        List<TradeJournalEntry> chronological = trades.stream()
                .sorted(java.util.Comparator.comparing(TradeJournalEntry::closedAt))
                .toList();
        for (TradeJournalEntry trade : chronological) {
            equity += trade.realizedPnl().doubleValue();
            peak = Math.max(peak, equity);
            if (peak > 0) maximum = Math.max(maximum, ((peak - equity) / peak) * 100.0);
        }
        return maximum;
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}
