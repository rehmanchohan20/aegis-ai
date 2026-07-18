package ai.aegis.ml;

import ai.aegis.feature.FeatureSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PredictionAuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PredictionAuditService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public UUID record(String symbol, String interval, String strategyId, String rulesDirection,
                       MlPrediction prediction, FeatureSnapshot snapshot) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO intelligence.prediction_history
                (id, symbol, interval_name, strategy_id, model_version, rules_direction,
                 predicted_direction, long_probability, wait_probability, short_probability,
                 confidence, feature_snapshot, prediction_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """, id, symbol, interval, strategyId, prediction.model(), rulesDirection,
                prediction.decision(), prediction.longProbability(), prediction.waitProbability(),
                prediction.shortProbability(), prediction.confidence(), json(snapshot.features()),
                Timestamp.from(Instant.now()));
        return id;
    }

    public List<Map<String, Object>> latest(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return jdbc.queryForList("""
                SELECT id, symbol, interval_name, strategy_id, model_version, rules_direction,
                       predicted_direction, long_probability, wait_probability, short_probability,
                       confidence, prediction_time, outcome_label, forward_return, correct, resolved_at
                FROM intelligence.prediction_history
                ORDER BY prediction_time DESC LIMIT ?
                """, safeLimit);
    }

    public Map<String, Object> summary(String modelVersion) {
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE resolved_at IS NOT NULL) AS resolved,
                       COUNT(*) FILTER (WHERE correct = TRUE) AS correct,
                       COALESCE(AVG(confidence), 0) AS average_confidence,
                       COALESCE(AVG(CASE WHEN correct THEN 1.0 ELSE 0.0 END)
                           FILTER (WHERE resolved_at IS NOT NULL), 0) AS accuracy
                FROM intelligence.prediction_history
                WHERE (? IS NULL OR model_version = ?)
                """, modelVersion, modelVersion);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Unable to serialize prediction", e); }
    }
}