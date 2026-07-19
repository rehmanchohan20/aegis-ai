package ai.aegis.ml;

import ai.aegis.feature.FeatureSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MlTrainingExampleCaptureService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public MlTrainingExampleCaptureService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public boolean capture(FeatureSnapshot snapshot, String strategyId, Instant observationTime) {
        if (snapshot == null || observationTime == null) throw new IllegalArgumentException("snapshot and observation time are required");
        if (!snapshot.unusableFeatures().isEmpty()) return false;
        Map<String, BigDecimal> features = new LinkedHashMap<>();
        snapshot.features().forEach((name, feature) -> {
            if (feature.usable()) features.put(name, feature.value());
        });
        if (features.isEmpty()) throw new IllegalArgumentException("cannot persist an empty usable feature vector");
        return jdbc.update("""
                INSERT INTO intelligence.ml_examples
                    (id, symbol, interval_name, strategy_id, features, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?)
                ON CONFLICT (symbol, interval_name, created_at) DO NOTHING
                """, UUID.randomUUID(), snapshot.symbol(), snapshot.interval(),
                strategyId == null || strategyId.isBlank() ? "NO_ELIGIBLE_STRATEGY" : strategyId,
                json(features), Timestamp.from(observationTime)) == 1;
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to serialize ML training feature vector", error);
        }
    }
}
