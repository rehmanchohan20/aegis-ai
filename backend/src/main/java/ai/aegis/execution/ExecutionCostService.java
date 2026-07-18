package ai.aegis.execution;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Service
public class ExecutionCostService {
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    public ExecutionEstimate estimate(String side, BigDecimal price, BigDecimal quantity,
                                      BigDecimal feeBps, BigDecimal slippageBps) {
        if (price == null || quantity == null || feeBps == null || slippageBps == null
                || price.signum() <= 0 || quantity.signum() <= 0
                || feeBps.signum() < 0 || slippageBps.signum() < 0) {
            throw new IllegalArgumentException("Positive price and quantity plus non-negative costs are required");
        }
        if (!"LONG".equalsIgnoreCase(side) && !"SHORT".equalsIgnoreCase(side)) {
            throw new IllegalArgumentException("side must be LONG or SHORT");
        }

        BigDecimal slippageRate = slippageBps.divide(BigDecimal.valueOf(10_000), MC);
        BigDecimal fillMultiplier = "LONG".equalsIgnoreCase(side)
                ? BigDecimal.ONE.add(slippageRate, MC)
                : BigDecimal.ONE.subtract(slippageRate, MC);
        BigDecimal fillPrice = price.multiply(fillMultiplier, MC);
        BigDecimal notional = fillPrice.multiply(quantity, MC);
        BigDecimal fee = notional.multiply(feeBps.divide(BigDecimal.valueOf(10_000), MC), MC);
        BigDecimal slippageCost = fillPrice.subtract(price).abs().multiply(quantity, MC);
        BigDecimal totalCost = fee.add(slippageCost, MC);
        BigDecimal breakEvenPercent = totalCost.multiply(BigDecimal.valueOf(100), MC)
                .divide(notional.max(BigDecimal.valueOf(0.00000001)), MC);

        return new ExecutionEstimate(
                price.setScale(8, RoundingMode.HALF_UP),
                fillPrice.setScale(8, RoundingMode.HALF_UP),
                notional.setScale(2, RoundingMode.HALF_UP),
                fee.setScale(4, RoundingMode.HALF_UP),
                slippageCost.setScale(4, RoundingMode.HALF_UP),
                totalCost.setScale(4, RoundingMode.HALF_UP),
                breakEvenPercent.setScale(4, RoundingMode.HALF_UP));
    }
}
