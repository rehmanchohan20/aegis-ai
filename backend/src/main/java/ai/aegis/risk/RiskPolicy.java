package ai.aegis.risk;

import java.math.BigDecimal;

public record RiskPolicy(
        BigDecimal maxRiskPerTradePercent,
        BigDecimal maxDailyLossPercent,
        BigDecimal maxOpenExposurePercent,
        int maxConcurrentPositions,
        int cooldownCandles,
        int maxConsecutiveLosses
) {
    public RiskPolicy {
        if (maxRiskPerTradePercent == null || maxRiskPerTradePercent.signum() <= 0) {
            throw new IllegalArgumentException("maxRiskPerTradePercent must be positive");
        }
        if (maxDailyLossPercent == null || maxDailyLossPercent.signum() <= 0) {
            throw new IllegalArgumentException("maxDailyLossPercent must be positive");
        }
        if (maxOpenExposurePercent == null || maxOpenExposurePercent.signum() <= 0) {
            throw new IllegalArgumentException("maxOpenExposurePercent must be positive");
        }
        if (maxConcurrentPositions < 1 || cooldownCandles < 0 || maxConsecutiveLosses < 1) {
            throw new IllegalArgumentException("integer risk controls are invalid");
        }
    }

    public static RiskPolicy conservative() {
        return new RiskPolicy(
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(25),
                3,
                5,
                3
        );
    }
}
