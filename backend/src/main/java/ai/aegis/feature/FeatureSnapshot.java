package ai.aegis.feature;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record FeatureSnapshot(
        String symbol,
        String interval,
        Instant generatedAt,
        Map<String, FeatureValue> features
) {
    public FeatureSnapshot {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(generatedAt, "generatedAt");
        features = Map.copyOf(features == null ? Map.of() : features);
    }

    public Optional<BigDecimal> usableValue(String name) {
        FeatureValue feature = features.get(name);
        return feature != null && feature.usable() ? Optional.of(feature.value()) : Optional.empty();
    }

    public List<String> unusableFeatures() {
        return features.values().stream()
                .filter(feature -> !feature.usable())
                .map(FeatureValue::name)
                .sorted()
                .toList();
    }
}
