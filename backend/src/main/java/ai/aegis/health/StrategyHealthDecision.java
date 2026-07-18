package ai.aegis.health;

import java.math.BigDecimal;
import java.util.List;

public record StrategyHealthDecision(
        String status,
        BigDecimal score,
        boolean tradingAllowed,
        List<String> reasons
) {}
