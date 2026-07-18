package ai.aegis.backtest;

import java.math.BigDecimal;
import java.time.Instant;

public record BacktestTrade(
        String direction,
        Instant entryTime,
        Instant exitTime,
        BigDecimal entryPrice,
        BigDecimal exitPrice,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        BigDecimal pnl,
        BigDecimal returnPercent,
        String exitReason
) {
}
