package ai.aegis.ml;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record MlPrediction(
        BigDecimal longProbability,
        BigDecimal waitProbability,
        BigDecimal shortProbability,
        String decision,
        BigDecimal confidence,
        String model,
        BigDecimal confidenceMargin,
        BigDecimal featureDriftScore,
        String driftStatus,
        BigDecimal entropy,
        List<String> predictionSet,
        String uncertaintyStatus,
        Map<String, BigDecimal> featureDriftContributions,
        BigDecimal expectedReturn,
        BigDecimal expectedVolatility,
        BigDecimal stopHitProbability,
        BigDecimal targetHitProbability,
        BigDecimal tradeQualityScore,
        BigDecimal estimatedExecutionCostRate
) {
    public MlPrediction {
        predictionSet = List.copyOf(predictionSet == null ? List.of() : predictionSet);
        featureDriftContributions = Map.copyOf(featureDriftContributions == null ? Map.of() : featureDriftContributions);
    }

    public MlPrediction(BigDecimal longProbability, BigDecimal waitProbability, BigDecimal shortProbability,
                        String decision, BigDecimal confidence, String model, BigDecimal confidenceMargin,
                        BigDecimal featureDriftScore, String driftStatus) {
        this(longProbability, waitProbability, shortProbability, decision, confidence, model,
                confidenceMargin, featureDriftScore, driftStatus, null, List.of(), null, Map.of(),
                null, null, null, null, null, null);
    }
}
