package ai.aegis.quant;

import java.math.BigDecimal;
import java.util.List;

public record KellySizingResult(
        BigDecimal rawKellyFraction,
        BigDecimal halfKellyFraction,
        BigDecimal cappedRiskFraction,
        BigDecimal recommendedRiskAmount,
        List<String> warnings
) {
}
