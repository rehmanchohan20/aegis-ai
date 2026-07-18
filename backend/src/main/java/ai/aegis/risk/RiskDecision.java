package ai.aegis.risk;

import java.math.BigDecimal;
import java.util.List;

public record RiskDecision(
        boolean approved,
        BigDecimal allowedRiskAmount,
        BigDecimal allowedPositionNotional,
        List<String> reasons
) {
}
