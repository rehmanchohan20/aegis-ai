package ai.aegis.quant;

import java.math.BigDecimal;
import java.util.List;

public record QuantMetrics(
        int observations,
        BigDecimal meanReturn,
        BigDecimal volatility,
        BigDecimal downsideDeviation,
        BigDecimal sharpeRatio,
        BigDecimal sortinoRatio,
        BigDecimal valueAtRisk95,
        BigDecimal conditionalValueAtRisk95,
        BigDecimal skewness,
        BigDecimal excessKurtosis,
        BigDecimal lagOneAutocorrelation,
        BigDecimal latestZScore,
        List<String> warnings
) {
}
