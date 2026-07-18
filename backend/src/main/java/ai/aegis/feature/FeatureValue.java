package ai.aegis.feature;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record FeatureValue(
        String name,
        BigDecimal value,
        Instant observedAt,
        FeatureQuality quality,
        String source
) {
    public FeatureValue {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(quality, "quality");
        source = source == null ? "unknown" : source;
    }

    public boolean usable() {
        return value != null && quality == FeatureQuality.GOOD;
    }
}
