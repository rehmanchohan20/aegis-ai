package ai.aegis.ml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DeploymentApprovalService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ModelRegistryService registry;
    private final BigDecimal minimumBalancedAccuracy;
    private final BigDecimal minimumMacroF1;
    private final BigDecimal minimumDirectionalPrecision;
    private final int minimumValidationSamples;
    private final BigDecimal maximumCalibrationError;
    private final BigDecimal minimumWorstFoldBalancedAccuracy;

    public DeploymentApprovalService(JdbcTemplate jdbc, ObjectMapper mapper, ModelRegistryService registry,
            @Value("${aegis.ml.promotion.minimum-balanced-accuracy:0.55}") BigDecimal minimumBalancedAccuracy,
            @Value("${aegis.ml.promotion.minimum-macro-f1:0.50}") BigDecimal minimumMacroF1,
            @Value("${aegis.ml.promotion.minimum-directional-precision:0.52}") BigDecimal minimumDirectionalPrecision,
            @Value("${aegis.ml.promotion.minimum-validation-samples:150}") int minimumValidationSamples,
            @Value("${aegis.ml.promotion.maximum-calibration-error:0.15}") BigDecimal maximumCalibrationError,
            @Value("${aegis.ml.promotion.minimum-worst-fold-balanced-accuracy:0.40}") BigDecimal minimumWorstFoldBalancedAccuracy) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.registry = registry;
        this.minimumBalancedAccuracy = minimumBalancedAccuracy;
        this.minimumMacroF1 = minimumMacroF1;
        this.minimumDirectionalPrecision = minimumDirectionalPrecision;
        this.minimumValidationSamples = minimumValidationSamples;
        this.maximumCalibrationError = maximumCalibrationError;
        this.minimumWorstFoldBalancedAccuracy = minimumWorstFoldBalancedAccuracy;
    }

    public Approval evaluate(UUID modelId, BigDecimal efficiency, BigDecimal sharpe, BigDecimal maxDrawdown) {
        List<String> reasons = new ArrayList<>();
        MapMetrics metrics = metrics(registry.get(modelId).metrics());
        if (metrics.balancedAccuracyLowerBound() == null || metrics.balancedAccuracyLowerBound().compareTo(minimumBalancedAccuracy) < 0)
            reasons.add("Balanced accuracy 95% lower confidence bound is below " + minimumBalancedAccuracy);
        if (metrics.macroF1LowerBound() == null || metrics.macroF1LowerBound().compareTo(minimumMacroF1) < 0)
            reasons.add("Macro F1 95% lower confidence bound is below " + minimumMacroF1);
        if (metrics.directionalPrecisionLowerBound() == null || metrics.directionalPrecisionLowerBound().compareTo(minimumDirectionalPrecision) < 0)
            reasons.add("Directional precision 95% lower confidence bound is below " + minimumDirectionalPrecision);
        if (metrics.validationSamples() < minimumValidationSamples)
            reasons.add("Validation sample count is below " + minimumValidationSamples);
        if (metrics.calibrationError() == null || metrics.calibrationError().compareTo(maximumCalibrationError) > 0)
            reasons.add("Calibration error exceeds " + maximumCalibrationError);
        if (metrics.worstFoldBalancedAccuracy() == null
                || metrics.worstFoldBalancedAccuracy().compareTo(minimumWorstFoldBalancedAccuracy) < 0)
            reasons.add("Worst chronological fold balanced accuracy is below " + minimumWorstFoldBalancedAccuracy);
        if (efficiency == null || efficiency.compareTo(new BigDecimal("0.60")) < 0)
            reasons.add("Walk-forward efficiency is below 0.60");
        if (sharpe == null || sharpe.compareTo(new BigDecimal("0.75")) < 0)
            reasons.add("Out-of-sample Sharpe is below 0.75");
        if (maxDrawdown == null || maxDrawdown.compareTo(new BigDecimal("0.15")) > 0)
            reasons.add("Maximum drawdown exceeds 15%");
        boolean approved = reasons.isEmpty();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO intelligence.deployment_approval
                (id, model_id, walk_forward_efficiency, out_of_sample_sharpe, maximum_drawdown, approved, reasons, evaluated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """, id, modelId, efficiency, sharpe, maxDrawdown, approved, json(reasons), Timestamp.from(Instant.now()));
        return new Approval(id, modelId, approved, List.copyOf(reasons), Instant.now());
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private MapMetrics metrics(java.util.Map<String, Object> values) {
        java.util.Map<?, ?> intervals = values.get("confidenceIntervals95") instanceof java.util.Map<?, ?> map
                ? map : java.util.Map.of();
        return new MapMetrics(lower(intervals.get("balancedAccuracy"), values.get("balancedAccuracy")),
                lower(intervals.get("macroF1"), values.get("macroF1")),
                lower(intervals.get("directionalPrecision"), values.get("directionalPrecision")),
                integer(values.get("validationSampleCount")),
                decimal(values.get("calibrationError")), decimal(values.get("worstFoldBalancedAccuracy")));
    }

    private BigDecimal lower(Object interval, Object fallback) {
        if (interval instanceof List<?> values && !values.isEmpty()) return decimal(values.getFirst());
        return decimal(fallback);
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return null;
        try { return new BigDecimal(value.toString()); }
        catch (NumberFormatException invalid) { return null; }
    }

    private int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? 0 : Integer.parseInt(value.toString()); }
        catch (NumberFormatException invalid) { return 0; }
    }

    private record MapMetrics(BigDecimal balancedAccuracyLowerBound, BigDecimal macroF1LowerBound,
                              BigDecimal directionalPrecisionLowerBound, int validationSamples,
                              BigDecimal calibrationError, BigDecimal worstFoldBalancedAccuracy) { }

    public record Approval(UUID id, UUID modelId, boolean approved, List<String> reasons, Instant evaluatedAt) { }
}
