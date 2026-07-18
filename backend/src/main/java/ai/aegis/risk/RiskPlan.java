package ai.aegis.risk;

import java.math.BigDecimal;

public record RiskPlan(
        BigDecimal entry,
        BigDecimal stopLoss,
        BigDecimal takeProfit1,
        BigDecimal takeProfit2,
        BigDecimal riskReward,
        BigDecimal riskPercent,
        String status
) {
}
