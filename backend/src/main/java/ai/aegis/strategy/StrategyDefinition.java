package ai.aegis.strategy;

import java.util.List;
import java.util.Objects;

public record StrategyDefinition(
        String id,
        String name,
        String side,
        int minimumScore,
        List<StrategyRule> rules
) {
    public StrategyDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(side, "side");
        rules = List.copyOf(rules == null ? List.of() : rules);
        if (!"LONG".equalsIgnoreCase(side) && !"SHORT".equalsIgnoreCase(side)) {
            throw new IllegalArgumentException("side must be LONG or SHORT");
        }
        if (minimumScore < 0 || minimumScore > 100) {
            throw new IllegalArgumentException("minimumScore must be between 0 and 100");
        }
    }
}
