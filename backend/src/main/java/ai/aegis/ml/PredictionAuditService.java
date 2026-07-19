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
import java.math.BigDecimal;

@Service
public class PredictionAuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PredictionAuditService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public UUID record(String symbol, String interval, String strategyId, String rulesDirection,
                       MlPrediction prediction, FeatureSnapshot snapshot, BigDecimal startingPrice) {
        if (symbol == null || symbol.isBlank() || interval == null || interval.isBlank()) {
            throw new IllegalArgumentException("symbol and interval are required");
        }
        if (prediction == null || snapshot == null || startingPrice == null || startingPrice.signum() <= 0) {
            throw new IllegalArgumentException("prediction, feature snapshot, and positive starting price are required");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO intelligence.prediction_history
                (id, symbol, interval_name, strategy_id, model_version, rules_direction,
                 predicted_direction, long_probability, wait_probability, short_probability,
                 confidence, confidence_margin, feature_drift_score, drift_status,
                 prediction_entropy, uncertainty_status, prediction_set,
                 feature_snapshot, prediction_time, starting_price)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                """, id, symbol, interval, strategyId, prediction.model(), rulesDirection,
                prediction.decision(), prediction.longProbability(), prediction.waitProbability(),
                prediction.shortProbability(), prediction.confidence(), prediction.confidenceMargin(),
                prediction.featureDriftScore(), prediction.driftStatus(), prediction.entropy(),
                prediction.uncertaintyStatus(), json(prediction.predictionSet()), json(snapshot.features()),
                Timestamp.from(Instant.now()), startingPrice);
        return id;
    }

    public List<Map<String, Object>> latest(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, symbol, interval_name, strategy_id, model_version, rules_direction,
                       predicted_direction, long_probability, wait_probability, short_probability,
                       confidence, confidence_margin, feature_drift_score, drift_status,
                       prediction_entropy, uncertainty_status, prediction_set, starting_price,
                       prediction_time, outcome_label, forward_return, correct, resolved_at
                FROM intelligence.prediction_history
                ORDER BY prediction_time DESC LIMIT ?
                """, safeLimit);
        rows.forEach(row -> {
            Object predictionSet = row.get("prediction_set");
            if (predictionSet != null) row.put("prediction_set", readList(predictionSet.toString()));
        });
        return List.copyOf(rows);
    }

    public Map<String, Object> summary(String modelVersion) {
        return jdbc.queryForMap("""
                WITH filtered AS (
                    SELECT * FROM intelligence.prediction_history
                    WHERE (? IS NULL OR model_version = ?)
                ), calibration_bins AS (
                    SELECT FLOOR(LEAST(GREATEST(confidence, 0), 0.999999) * 10) AS bin,
                           COUNT(*) AS samples,
                           AVG(confidence) AS mean_confidence,
                           AVG(CASE WHEN correct THEN 1.0 ELSE 0.0 END) AS observed_accuracy
                    FROM filtered WHERE resolved_at IS NOT NULL
                    GROUP BY FLOOR(LEAST(GREATEST(confidence, 0), 0.999999) * 10)
                ), calibration AS (
                    SELECT COALESCE(SUM(samples * ABS(mean_confidence - observed_accuracy))
                                    / NULLIF(SUM(samples), 0), 0) AS expected_calibration_error
                    FROM calibration_bins
                )
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE resolved_at IS NULL) AS pending,
                       COUNT(*) FILTER (WHERE resolved_at IS NOT NULL) AS resolved,
                       COUNT(*) FILTER (WHERE correct = TRUE) AS correct,
                       COALESCE(AVG(confidence), 0) AS average_confidence,
                       COALESCE(AVG(CASE WHEN correct THEN 1.0 ELSE 0.0 END)
                           FILTER (WHERE resolved_at IS NOT NULL), 0) AS accuracy,
                       COALESCE(AVG(CASE WHEN correct THEN 1.0 ELSE 0.0 END)
                           FILTER (WHERE resolved_at IS NOT NULL AND predicted_direction <> 'WAIT'), 0)
                           AS directional_precision,
                       calibration.expected_calibration_error AS calibration_error
                FROM filtered CROSS JOIN calibration
                GROUP BY calibration.expected_calibration_error
                """, modelVersion, modelVersion);
    }

    public List<Map<String, Object>> rolling(String modelVersion, int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        return jdbc.queryForList("""
                SELECT prediction_time::date AS result_date, model_version,
                       COUNT(*) FILTER (WHERE resolved_at IS NOT NULL) AS resolved,
                       COUNT(*) FILTER (WHERE resolved_at IS NULL) AS pending,
                       COALESCE(AVG(CASE WHEN correct THEN 1.0 ELSE 0.0 END)
                           FILTER (WHERE resolved_at IS NOT NULL), 0) AS accuracy,
                       COALESCE(AVG(confidence), 0) AS average_confidence
                FROM intelligence.prediction_history
                WHERE prediction_time >= CURRENT_TIMESTAMP - (? * INTERVAL '1 day')
                  AND (? IS NULL OR model_version = ?)
                GROUP BY prediction_time::date, model_version
                ORDER BY result_date DESC, model_version
                """, safeDays, modelVersion, modelVersion);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Unable to serialize prediction", e); }
    }

    @SuppressWarnings("unchecked")
    private List<String> readList(String value) {
        try { return mapper.readValue(value, List.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored prediction set", e); }
    }
}
