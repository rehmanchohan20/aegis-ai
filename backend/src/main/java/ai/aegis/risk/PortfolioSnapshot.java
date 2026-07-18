package ai.aegis.risk;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioSnapshot(
        BigDecimal accountBalance,
        BigDecimal dailyRealizedPnl,
        BigDecimal openExposure,
        int openPositions,
        int consecutiveLosses,
        Instant lastTradeAt
) {
    public PortfolioSnapshot {
        if (accountBalance == null || accountBalance.signum() <= 0) {
            throw new IllegalArgumentException("accountBalance must be positive");
        }
        if (dailyRealizedPnl == null || openExposure == null) {
            throw new IllegalArgumentException("portfolio values are required");
        }
        if (openExposure.signum() < 0 || openPositions < 0 || consecutiveLosses < 0) {
            throw new IllegalArgumentException("portfolio values cannot be negative");
        }
    }
}
