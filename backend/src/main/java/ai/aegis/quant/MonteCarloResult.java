package ai.aegis.quant;

import java.math.BigDecimal;

public record MonteCarloResult(
        int simulations,
        int tradesPerSimulation,
        BigDecimal medianEndingBalance,
        BigDecimal percentile5EndingBalance,
        BigDecimal percentile95EndingBalance,
        BigDecimal probabilityOfLoss,
        BigDecimal probabilityOfRuin,
        BigDecimal medianMaxDrawdown,
        BigDecimal percentile95MaxDrawdown
) {
}
