package ai.aegis.portfolio;

import java.math.BigDecimal;
import java.util.Map;

public record PortfolioAllocation(
        Map<String, BigDecimal> weights,
        BigDecimal expectedVolatility,
        BigDecimal diversificationRatio,
        boolean acceptable,
        java.util.List<String> warnings
) {}
