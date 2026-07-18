package ai.aegis.orchestration;

import ai.aegis.market.Candle;

import java.math.BigDecimal;
import java.util.List;

public record GuardedExecutionRequest(
        List<Candle> candles,
        BigDecimal accountBalance,
        BigDecimal riskPercent,
        boolean riskApproved,
        boolean strategyHealthy,
        boolean executionHealthy,
        boolean executePaperTrade
) {
    public GuardedExecutionRequest {
        candles = List.copyOf(candles == null ? List.of() : candles);
        if (accountBalance == null || accountBalance.signum() <= 0) {
            throw new IllegalArgumentException("positive accountBalance is required");
        }
        if (riskPercent == null || riskPercent.signum() <= 0 || riskPercent.compareTo(BigDecimal.valueOf(2)) > 0) {
            throw new IllegalArgumentException("riskPercent must be greater than 0 and at most 2");
        }
    }
}
