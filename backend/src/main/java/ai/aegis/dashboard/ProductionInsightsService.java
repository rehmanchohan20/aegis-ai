package ai.aegis.dashboard;

import ai.aegis.ml.ModelRegistryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

@Service
public class ProductionInsightsService {
    private final JdbcTemplate jdbc;
    private final ModelRegistryService models;

    public ProductionInsightsService(JdbcTemplate jdbc, ModelRegistryService models) {
        this.jdbc = jdbc;
        this.models = models;
    }

    public Insights snapshot(String modelName) {
        ModelRegistryService.ModelVersion active = models.active(modelName);
        List<StrategyPerformance> strategies = jdbc.query("""
                SELECT strategy_id, COUNT(*) AS trades,
                       COALESCE(SUM(realized_pnl),0) AS net_pnl,
                       COALESCE(AVG(realized_pnl),0) AS average_pnl,
                       COALESCE(100.0 * AVG(CASE WHEN realized_pnl > 0 THEN 1 ELSE 0 END),0) AS win_rate
                FROM intelligence.strategy_pnl_attribution
                GROUP BY strategy_id ORDER BY net_pnl DESC
                """, (rs, n) -> new StrategyPerformance(rs.getString("strategy_id"), rs.getInt("trades"),
                rs.getBigDecimal("net_pnl"), rs.getBigDecimal("average_pnl"), rs.getBigDecimal("win_rate")));
        List<ModelPerformance> modelPerformance = jdbc.query("""
                SELECT COALESCE(model_version, 'rules-only') AS model_version, COUNT(*) AS trades,
                       COALESCE(SUM(realized_pnl),0) AS net_pnl,
                       COALESCE(AVG(realized_pnl),0) AS average_pnl,
                       COALESCE(100.0 * AVG(CASE WHEN realized_pnl > 0 THEN 1 ELSE 0 END),0) AS win_rate
                FROM intelligence.strategy_pnl_attribution
                GROUP BY model_version ORDER BY net_pnl DESC
                """, (rs, n) -> new ModelPerformance(rs.getString("model_version"), rs.getInt("trades"),
                rs.getBigDecimal("net_pnl"), rs.getBigDecimal("average_pnl"), rs.getBigDecimal("win_rate")));
        List<Map<String, Object>> healthRows = jdbc.queryForList("""
                SELECT model_version, status, sample_size, accuracy, directional_precision,
                       calibration_error, feature_drift_score, unresolved_prediction_count,
                       class_distribution_shift, feature_population_stability_index, calculated_at
                FROM intelligence.model_drift_snapshot
                ORDER BY calculated_at DESC LIMIT 1
                """);
        long unlabelled = jdbc.queryForObject("SELECT COUNT(*) FROM intelligence.ml_examples WHERE label IS NULL", Long.class);
        long labelled = jdbc.queryForObject("SELECT COUNT(*) FROM intelligence.ml_examples WHERE label IS NOT NULL", Long.class);
        return new Insights(active, List.copyOf(strategies), List.copyOf(modelPerformance),
                healthRows.isEmpty() ? Map.of("status", "WATCH", "reason", "No health snapshot yet")
                        : Collections.unmodifiableMap(new LinkedHashMap<>(healthRows.getFirst())), labelled, unlabelled);
    }

    public record StrategyPerformance(String strategyId, int trades, BigDecimal netPnl,
                                      BigDecimal averagePnl, BigDecimal winRate) { }
    public record ModelPerformance(String modelVersion, int trades, BigDecimal netPnl,
                                   BigDecimal averagePnl, BigDecimal winRate) { }
    public record Insights(ModelRegistryService.ModelVersion activeModel,
                           List<StrategyPerformance> strategies,
                           List<ModelPerformance> models,
                           Map<String, Object> modelHealth,
                           long labelledExamples, long unlabelledExamples) { }
}
