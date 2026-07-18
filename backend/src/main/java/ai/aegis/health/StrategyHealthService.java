package ai.aegis.health;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class StrategyHealthService {

    public StrategyHealthDecision evaluate(double backtestMean, double liveMean, double backtestVolatility,
                                           double liveVolatility, double liveDrawdown, double maximumDrawdown,
                                           int consecutiveLosses, int minimumLiveTrades, int liveTrades) {
        if (maximumDrawdown <= 0 || backtestVolatility < 0 || liveVolatility < 0) {
            throw new IllegalArgumentException("valid volatility and drawdown thresholds are required");
        }

        List<String> reasons = new ArrayList<>();
        double score = 100;

        if (liveTrades < minimumLiveTrades) {
            score -= 15;
            reasons.add("Insufficient live sample for strong conclusions");
        }
        double expectancyRatio = Math.abs(backtestMean) < 1e-12 ? 0 : liveMean / backtestMean;
        if (liveMean <= 0) {
            score -= 35;
            reasons.add("Live expectancy is non-positive");
        } else if (expectancyRatio < 0.50) {
            score -= 20;
            reasons.add("Live expectancy has degraded by more than 50% versus backtest");
        }

        double volatilityRatio = backtestVolatility <= 1e-12 ? 1 : liveVolatility / backtestVolatility;
        if (volatilityRatio > 1.75) {
            score -= 20;
            reasons.add("Live volatility materially exceeds backtest volatility");
        }
        if (liveDrawdown >= maximumDrawdown) {
            score -= 50;
            reasons.add("Maximum allowed drawdown has been breached");
        } else if (liveDrawdown >= maximumDrawdown * 0.75) {
            score -= 20;
            reasons.add("Drawdown is approaching the hard limit");
        }
        if (consecutiveLosses >= 5) {
            score -= 25;
            reasons.add("Consecutive-loss kill switch triggered");
        } else if (consecutiveLosses >= 3) {
            score -= 10;
            reasons.add("Elevated consecutive-loss streak");
        }

        score = Math.max(0, Math.min(100, score));
        String status = score >= 75 ? "HEALTHY" : score >= 50 ? "DEGRADED" : "HALTED";
        boolean allowed = score >= 50 && liveDrawdown < maximumDrawdown && consecutiveLosses < 5;
        if (!allowed) reasons.add("New trades are disabled until the strategy is revalidated");

        return new StrategyHealthDecision(status, bd(score), allowed, List.copyOf(reasons));
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}
