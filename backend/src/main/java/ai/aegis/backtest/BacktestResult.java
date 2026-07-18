package ai.aegis.backtest;

import java.math.BigDecimal;
import java.util.List;

public record BacktestResult(
        int totalTrades,
        int winningTrades,
        int losingTrades,
        BigDecimal winRate,
        BigDecimal netProfit,
        BigDecimal returnPercent,
        BigDecimal maxDrawdownPercent,
        BigDecimal profitFactor,
        BigDecimal endingBalance,
        boolean strategyAccepted,
        List<String> warnings,
        List<BacktestTrade> trades
) {
}
