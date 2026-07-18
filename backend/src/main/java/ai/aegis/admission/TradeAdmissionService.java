package ai.aegis.admission;

import ai.aegis.analysis.MarketAnalysis;
import ai.aegis.execution.ExecutionCostService;
import ai.aegis.execution.ExecutionEstimate;
import ai.aegis.regime.MarketRegime;
import ai.aegis.regime.MarketRegimeService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class TradeAdmissionService {
    private final MarketRegimeService regimeService;
    private final ExecutionCostService executionCostService;

    public TradeAdmissionService(MarketRegimeService regimeService, ExecutionCostService executionCostService) {
        this.regimeService = regimeService;
        this.executionCostService = executionCostService;
    }

    public TradeAdmissionDecision evaluate(TradeAdmissionRequest request) {
        if (request == null || request.analysis() == null || request.candles() == null) {
            throw new IllegalArgumentException("analysis and candles are required");
        }
        if (request.accountBalance() == null || request.accountBalance().signum() <= 0
                || request.quantity() == null || request.quantity().signum() <= 0) {
            throw new IllegalArgumentException("positive account balance and quantity are required");
        }

        MarketAnalysis analysis = request.analysis();
        BigDecimal price = analysis.indicators().get("price");
        if (price == null) throw new IllegalArgumentException("analysis price indicator is required");

        MarketRegime regime = regimeService.detect(request.candles());
        ExecutionEstimate execution = executionCostService.estimate(
                analysis.decision(), price, request.quantity(),
                defaultValue(request.feeBps(), BigDecimal.valueOf(10)),
                defaultValue(request.slippageBps(), BigDecimal.valueOf(5)));

        List<String> blockers = new ArrayList<>();
        List<String> confirmations = new ArrayList<>();
        int quality = analysis.score();

        if (!"LONG".equals(analysis.decision()) && !"SHORT".equals(analysis.decision())) {
            blockers.add("The analysis engine has not produced a directional trade decision.");
        }

        if (analysis.confidence().compareTo(BigDecimal.valueOf(55)) < 0) {
            blockers.add("Signal confidence is below the 55% admission floor.");
            quality -= 15;
        } else {
            confirmations.add("Signal confidence passed the minimum threshold.");
        }

        if (request.alignedTimeframes() < 2) {
            blockers.add("Fewer than two timeframes confirm the same direction.");
            quality -= 20;
        } else {
            confirmations.add(request.alignedTimeframes() + " timeframes are directionally aligned.");
            quality += Math.min(10, request.alignedTimeframes() * 3);
        }

        boolean regimeAligned = ("LONG".equals(analysis.decision()) && "BULL_TREND".equals(regime.trend()))
                || ("SHORT".equals(analysis.decision()) && "BEAR_TREND".equals(regime.trend()));
        if (!regimeAligned) {
            blockers.add("Trade direction conflicts with the detected " + regime.trend() + " regime.");
            quality -= 20;
        } else {
            confirmations.add("Trade direction agrees with the detected market regime.");
            quality += 10;
        }

        if ("HIGH".equals(regime.volatility())) {
            blockers.add("High-volatility regime blocks new entries until risk is recalibrated.");
            quality -= 15;
        } else {
            confirmations.add("Volatility regime is acceptable for controlled execution.");
        }

        if (request.openPositions() >= 3) {
            blockers.add("Maximum concurrent position limit has been reached.");
        }
        if (request.dailyLossPercent() != null && request.dailyLossPercent().compareTo(BigDecimal.valueOf(3)) >= 0) {
            blockers.add("Daily loss circuit breaker is active.");
        }

        if (execution.breakEvenMovePercent().compareTo(BigDecimal.valueOf(0.20)) > 0) {
            blockers.add("Estimated fees and slippage create an excessive break-even hurdle.");
            quality -= 10;
        } else {
            confirmations.add("Estimated execution costs are within the admission limit.");
        }

        BigDecimal maxNotional = request.accountBalance().multiply(BigDecimal.valueOf(0.25))
                .setScale(2, RoundingMode.HALF_UP);
        if (execution.notional().compareTo(maxNotional) > 0) {
            blockers.add("Requested position exceeds the 25% account-notional limit.");
        }

        quality = Math.max(0, Math.min(100, quality));
        if (quality < 70) blockers.add("Final setup quality is below the 70-point admission floor.");
        boolean approved = blockers.isEmpty();
        String grade = quality >= 90 ? "A+" : quality >= 80 ? "A" : quality >= 70 ? "B" : quality >= 55 ? "C" : "D";

        return new TradeAdmissionDecision(approved,
                approved ? analysis.decision() : "REJECTED",
                quality, grade, maxNotional, regime, execution,
                List.copyOf(blockers), List.copyOf(confirmations));
    }

    private static BigDecimal defaultValue(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }
}
