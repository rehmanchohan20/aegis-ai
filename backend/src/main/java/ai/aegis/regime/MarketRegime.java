package ai.aegis.regime;

import java.math.BigDecimal;
import java.util.List;

public record MarketRegime(
        String trend,
        String volatility,
        String structure,
        BigDecimal trendStrength,
        BigDecimal rangePercent,
        BigDecimal support,
        BigDecimal resistance,
        List<String> reasons
) {
}
