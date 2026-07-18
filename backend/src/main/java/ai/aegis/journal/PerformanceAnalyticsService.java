package ai.aegis.journal;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PerformanceAnalyticsService {
    private final TradeJournalStore store;

    public PerformanceAnalyticsService(TradeJournalStore store) {
        this.store = store;
    }

    public PerformanceSummary summarize() {
        List<TradeJournalEntry> closed = store.latest(1000).stream()
                .filter(t -> t.closedAt() != null)
                .toList();
        int wins = (int) closed.stream().filter(t -> t.realizedPnl().signum() > 0).count();
        int losses = (int) closed.stream().filter(t -> t.realizedPnl().signum() < 0).count();
        BigDecimal grossWin = closed.stream().map(TradeJournalEntry::realizedPnl)
                .filter(v -> v.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss = closed.stream().map(TradeJournalEntry::realizedPnl)
                .filter(v -> v.signum() < 0).map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = grossWin.subtract(grossLoss);
        BigDecimal winRate = closed.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(wins * 100.0 / closed.size())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal averageWin = wins == 0 ? BigDecimal.ZERO : grossWin.divide(BigDecimal.valueOf(wins), 2, RoundingMode.HALF_UP);
        BigDecimal averageLoss = losses == 0 ? BigDecimal.ZERO : grossLoss.divide(BigDecimal.valueOf(losses), 2, RoundingMode.HALF_UP);
        BigDecimal profitFactor = grossLoss.signum() == 0 ? grossWin : grossWin.divide(grossLoss, 2, RoundingMode.HALF_UP);
        int losingStreak = 0;
        for (TradeJournalEntry trade : closed) {
            if (trade.realizedPnl().signum() < 0) losingStreak++;
            else break;
        }
        return new PerformanceSummary(closed.size(), wins, losses, winRate, net, averageWin, averageLoss,
                profitFactor, losingStreak);
    }
}
