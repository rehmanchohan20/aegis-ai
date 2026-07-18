package ai.aegis.execution;

import java.math.BigDecimal;

public record ExecutionEstimate(
        BigDecimal requestedPrice,
        BigDecimal expectedFillPrice,
        BigDecimal notional,
        BigDecimal fee,
        BigDecimal slippageCost,
        BigDecimal totalCost,
        BigDecimal breakEvenMovePercent
) {
}
