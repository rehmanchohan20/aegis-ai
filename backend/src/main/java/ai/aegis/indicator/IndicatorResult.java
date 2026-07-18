package ai.aegis.indicator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record IndicatorResult(
        String indicator,
        Instant calculatedAt,
        Map<String, BigDecimal> values
) {
}
