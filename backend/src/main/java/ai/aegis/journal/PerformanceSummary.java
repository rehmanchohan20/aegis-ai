package ai.aegis.journal;

import java.math.BigDecimal;

public record PerformanceSummary(
        int totalTrades,
        int wins,
        int losses,
        BigDecimal winRate,
        BigDecimal netPnl,
        BigDecimal averageWin,
        BigDecimal averageLoss,
        BigDecimal profitFactor,
        int currentLosingStreak
) {
}
