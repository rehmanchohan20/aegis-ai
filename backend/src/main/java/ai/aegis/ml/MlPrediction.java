package ai.aegis.ml;

import java.math.BigDecimal;

public record MlPrediction(
        BigDecimal longProbability,
        BigDecimal waitProbability,
        BigDecimal shortProbability,
        String decision,
        BigDecimal confidence,
        String model
) {
}