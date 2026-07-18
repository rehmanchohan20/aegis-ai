package ai.aegis.validation;

import java.math.BigDecimal;
import java.util.List;

public record WalkForwardResult(
        int folds,
        BigDecimal inSampleMean,
        BigDecimal outOfSampleMean,
        BigDecimal efficiencyRatio,
        BigDecimal profitableFoldRatio,
        boolean passed,
        List<String> warnings
) {}
