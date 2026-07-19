package ai.aegis.ml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ModelHealthMonitoringService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final int minimumSampleSize;

    public ModelHealthMonitoringService(JdbcTemplate jdbc, ObjectMapper mapper,
                                        @Value("${aegis.ml.health-minimum-sample-size:30}") int minimumSampleSize) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.minimumSampleSize = Math.max(10, minimumSampleSize);
    }

    @Scheduled(fixedDelayString = "${aegis.ml.model-health-delay-ms:300000}")
    public int snapshotActiveModels() {
        List<ModelWindow> windows = jdbc.query("""
                SELECT p.model_version, p.symbol, p.interval_name,
                       COUNT(*) FILTER (WHERE p.resolved_at IS NOT NULL
                           AND p.prediction_time >= CURRENT_TIMESTAMP - INTERVAL '7 days') AS sample_size,
                       COUNT(*) FILTER (WHERE p.resolved_at IS NULL
                           AND p.prediction_time >= CURRENT_TIMESTAMP - INTERVAL '7 days') AS unresolved,
                       AVG(CASE WHEN p.correct THEN 1.0 ELSE 0.0 END)
                           FILTER (WHERE p.resolved_at IS NOT NULL
                               AND p.prediction_time >= CURRENT_TIMESTAMP - INTERVAL '7 days') AS accuracy,
                       AVG(CASE WHEN p.correct THEN 1.0 ELSE 0.0 END)
                           FILTER (WHERE p.resolved_at IS NOT NULL AND p.predicted_direction <> 'WAIT'
                               AND p.prediction_time >= CURRENT_TIMESTAMP - INTERVAL '7 days') AS directional_precision,
                       AVG(p.confidence) FILTER (WHERE p.prediction_time >= CURRENT_TIMESTAMP - INTERVAL '7 days') AS average_confidence,
                       AVG(ABS(p.confidence - CASE WHEN p.correct THEN 1.0 ELSE 0.0 END))
                           FILTER (WHERE p.resolved_at IS NOT NULL
                               AND p.prediction_time >= CURRENT_TIMESTAMP - INTERVAL '7 days') AS calibration_error,
                       AVG(p.feature_drift_score) FILTER (WHERE p.prediction_time >= CURRENT_TIMESTAMP - INTERVAL '7 days') AS feature_drift_score,
                       COUNT(*) FILTER (WHERE p.prediction_time >= CURRENT_TIMESTAMP - INTERVAL '7 days' AND p.predicted_direction='LONG') AS recent_long,
                       COUNT(*) FILTER (WHERE p.prediction_time >= CURRENT_TIMESTAMP - INTERVAL '7 days' AND p.predicted_direction='WAIT') AS recent_wait,
                       COUNT(*) FILTER (WHERE p.prediction_time >= CURRENT_TIMESTAMP - INTERVAL '7 days' AND p.predicted_direction='SHORT') AS recent_short,
                       COUNT(*) FILTER (WHERE p.prediction_time < CURRENT_TIMESTAMP - INTERVAL '7 days' AND p.predicted_direction='LONG') AS baseline_long,
                       COUNT(*) FILTER (WHERE p.prediction_time < CURRENT_TIMESTAMP - INTERVAL '7 days' AND p.predicted_direction='WAIT') AS baseline_wait,
                       COUNT(*) FILTER (WHERE p.prediction_time < CURRENT_TIMESTAMP - INTERVAL '7 days' AND p.predicted_direction='SHORT') AS baseline_short,
                       ARRAY_AGG(p.feature_drift_score) FILTER (WHERE p.prediction_time >= CURRENT_TIMESTAMP - INTERVAL '7 days' AND p.feature_drift_score IS NOT NULL) AS recent_drift,
                       ARRAY_AGG(p.feature_drift_score) FILTER (WHERE p.prediction_time < CURRENT_TIMESTAMP - INTERVAL '7 days' AND p.feature_drift_score IS NOT NULL) AS baseline_drift
                FROM intelligence.prediction_history p
                JOIN intelligence.model_registry m
                  ON p.model_version = (m.model_name || ':' || m.version) AND m.status = 'ACTIVE'
                WHERE p.prediction_time >= CURRENT_TIMESTAMP - INTERVAL '30 days'
                GROUP BY p.model_version, p.symbol, p.interval_name
                """, (rs, row) -> new ModelWindow(rs.getString("model_version"), rs.getString("symbol"),
                rs.getString("interval_name"), rs.getInt("sample_size"), rs.getInt("unresolved"),
                rs.getBigDecimal("accuracy"), rs.getBigDecimal("directional_precision"),
                rs.getBigDecimal("average_confidence"), rs.getBigDecimal("calibration_error"),
                rs.getBigDecimal("feature_drift_score"),
                new long[]{rs.getLong("recent_short"), rs.getLong("recent_wait"), rs.getLong("recent_long")},
                new long[]{rs.getLong("baseline_short"), rs.getLong("baseline_wait"), rs.getLong("baseline_long")},
                decimals(rs.getArray("recent_drift")), decimals(rs.getArray("baseline_drift"))));
        for (ModelWindow window : windows) persist(window);
        return windows.size();
    }

    private void persist(ModelWindow window) {
        BigDecimal classShift = distributionShift(window.recentClasses(), window.baselineClasses());
        BigDecimal featurePsi = populationStabilityIndex(window.recentDrift(), window.baselineDrift());
        HealthAssessment assessment = assess(window.sampleSize(), window.accuracy(),
                window.directionalPrecision(), window.calibrationError(), window.featureDriftScore(),
                classShift, featurePsi, window.unresolved(), minimumSampleSize);
        jdbc.update("""
                INSERT INTO intelligence.model_drift_snapshot
                (id, model_version, symbol, interval_name, sample_size, accuracy, directional_precision,
                 average_confidence, calibration_error, feature_drift_score, status, reasons,
                 unresolved_prediction_count, class_distribution_shift,
                 feature_population_stability_index, calculated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                """, UUID.randomUUID(), window.modelVersion(), window.symbol(), window.interval(),
                window.sampleSize(), window.accuracy(), window.directionalPrecision(),
                window.averageConfidence(), window.calibrationError(), window.featureDriftScore(),
                assessment.status(), json(assessment.reasons()), window.unresolved(), classShift, featurePsi,
                Timestamp.from(Instant.now()));
    }

    static HealthAssessment assess(int samples, BigDecimal accuracy, BigDecimal directionalPrecision,
                                   BigDecimal calibrationError, BigDecimal featureDrift,
                                   BigDecimal classDistributionShift, BigDecimal featurePsi,
                                   int unresolved, int minimumSamples) {
        if (samples == 0 && unresolved >= minimumSamples * 2) return new HealthAssessment("HALTED", List.of("outcomes are not resolving"));
        if (samples < minimumSamples) return new HealthAssessment("WATCH", List.of("insufficient recent resolved samples"));
        if (below(accuracy, "0.40") || below(directionalPrecision, "0.40") || above(calibrationError, "0.30")
                || above(featureDrift, "4.0") || above(classDistributionShift, "0.35") || above(featurePsi, "0.50")) {
            return new HealthAssessment("HALTED", List.of("critical prediction-quality or drift threshold breached"));
        }
        if (below(accuracy, "0.52") || below(directionalPrecision, "0.52") || above(calibrationError, "0.20")
                || above(featureDrift, "2.0") || above(classDistributionShift, "0.20") || above(featurePsi, "0.25")) {
            return new HealthAssessment("DEGRADED", List.of("prediction quality requires review"));
        }
        if (below(accuracy, "0.58") || above(calibrationError, "0.12") || above(featureDrift, "1.0")
                || above(classDistributionShift, "0.10") || above(featurePsi, "0.10")) {
            return new HealthAssessment("WATCH", List.of("early warning threshold breached"));
        }
        return new HealthAssessment("HEALTHY", List.of());
    }

    static BigDecimal distributionShift(long[] recent, long[] baseline) {
        long recentTotal = java.util.Arrays.stream(recent).sum();
        long baselineTotal = java.util.Arrays.stream(baseline).sum();
        if (recentTotal == 0 || baselineTotal == 0) return BigDecimal.ZERO;
        double totalVariation = 0.0;
        for (int index = 0; index < recent.length; index++) {
            totalVariation += Math.abs((double) recent[index] / recentTotal - (double) baseline[index] / baselineTotal);
        }
        return BigDecimal.valueOf(totalVariation / 2.0);
    }

    static BigDecimal populationStabilityIndex(double[] recent, double[] baseline) {
        if (recent.length == 0 || baseline.length == 0) return BigDecimal.ZERO;
        double[] edges = {Double.NEGATIVE_INFINITY, 1.0, 2.0, 4.0, Double.POSITIVE_INFINITY};
        double psi = 0.0;
        for (int bin = 0; bin < edges.length - 1; bin++) {
            double recentShare = Math.max(0.0001, share(recent, edges[bin], edges[bin + 1]));
            double baselineShare = Math.max(0.0001, share(baseline, edges[bin], edges[bin + 1]));
            psi += (recentShare - baselineShare) * Math.log(recentShare / baselineShare);
        }
        return BigDecimal.valueOf(psi);
    }

    private static double share(double[] values, double lower, double upper) {
        return (double) java.util.Arrays.stream(values).filter(value -> value >= lower && value < upper).count() / values.length;
    }

    private static double[] decimals(java.sql.Array sqlArray) throws java.sql.SQLException {
        if (sqlArray == null) return new double[0];
        Object[] values = (Object[]) sqlArray.getArray();
        double[] result = new double[values.length];
        for (int index = 0; index < values.length; index++) result[index] = ((Number) values[index]).doubleValue();
        return result;
    }

    private static boolean below(BigDecimal value, String threshold) { return value == null || value.compareTo(new BigDecimal(threshold)) < 0; }
    private static boolean above(BigDecimal value, String threshold) { return value != null && value.compareTo(new BigDecimal(threshold)) > 0; }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Unable to serialize model health", e); }
    }

    private record ModelWindow(String modelVersion, String symbol, String interval, int sampleSize,
                               int unresolved, BigDecimal accuracy, BigDecimal directionalPrecision,
                               BigDecimal averageConfidence, BigDecimal calibrationError,
                               BigDecimal featureDriftScore, long[] recentClasses, long[] baselineClasses,
                               double[] recentDrift, double[] baselineDrift) { }
    record HealthAssessment(String status, List<String> reasons) { }
}
