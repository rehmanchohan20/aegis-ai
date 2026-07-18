package ai.aegis.risk;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PortfolioRiskService {

    public RiskDecision evaluate(
            PortfolioSnapshot snapshot,
            RiskPolicy policy,
            BigDecimal requestedRiskPercent,
            BigDecimal requestedPositionNotional,
            Duration candleDuration,
            Instant now
    ) {
        List<String> reasons = new ArrayList<>();
        boolean approved = true;

        BigDecimal maxRiskPercent = requestedRiskPercent.min(policy.maxRiskPerTradePercent());
        BigDecimal allowedRiskAmount = snapshot.accountBalance()
                .multiply(maxRiskPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);

        BigDecimal dailyLossPercent = snapshot.dailyRealizedPnl().signum() < 0
                ? snapshot.dailyRealizedPnl().abs().multiply(BigDecimal.valueOf(100))
                    .divide(snapshot.accountBalance(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        if (dailyLossPercent.compareTo(policy.maxDailyLossPercent()) >= 0) {
            approved = false;
            reasons.add("Daily loss circuit breaker is active.");
        }

        if (snapshot.openPositions() >= policy.maxConcurrentPositions()) {
            approved = false;
            reasons.add("Maximum concurrent position limit reached.");
        }

        if (snapshot.consecutiveLosses() >= policy.maxConsecutiveLosses()) {
            approved = false;
            reasons.add("Trading paused after the maximum consecutive-loss limit.");
        }

        if (snapshot.lastTradeAt() != null && candleDuration != null) {
            Duration requiredCooldown = candleDuration.multipliedBy(policy.cooldownCandles());
            if (snapshot.lastTradeAt().plus(requiredCooldown).isAfter(now)) {
                approved = false;
                reasons.add("Signal rejected during post-trade cooldown.");
            }
        }

        BigDecimal exposureLimit = snapshot.accountBalance()
                .multiply(policy.maxOpenExposurePercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        BigDecimal remainingExposure = exposureLimit.subtract(snapshot.openExposure()).max(BigDecimal.ZERO);
        BigDecimal allowedPositionNotional = requestedPositionNotional.min(remainingExposure);
        if (remainingExposure.signum() <= 0 || requestedPositionNotional.compareTo(remainingExposure) > 0) {
            approved = false;
            reasons.add("Requested position exceeds remaining portfolio exposure capacity.");
        }

        if (requestedRiskPercent.compareTo(policy.maxRiskPerTradePercent()) > 0) {
            reasons.add("Risk per trade was capped by policy.");
        }
        if (approved) {
            reasons.add("Portfolio risk checks passed.");
        }

        return new RiskDecision(approved, allowedRiskAmount, allowedPositionNotional, List.copyOf(reasons));
    }
}
