package ai.aegis.ml;

import ai.aegis.feature.FeatureSnapshot;
import ai.aegis.market.MarketSnapshot;
import ai.aegis.risk.RiskPlan;
import ai.aegis.structure.MarketStructureSnapshot;
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
        return record(symbol, interval, strategyId, rulesDirection, prediction, snapshot, startingPrice,
                null, null, null);
    }

    public UUID record(String symbol, String interval, String strategyId, String rulesDirection,
                       MlPrediction prediction, FeatureSnapshot snapshot, BigDecimal startingPrice,
                       RiskPlan riskPlan, MarketStructureSnapshot structure, MarketSnapshot market) {
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
                 feature_snapshot, prediction_time, starting_price, final_approved_direction,
                 expected_return, expected_volatility, trade_quality_score,
                 stop_hit_probability, target_hit_probability, evaluation_horizon_seconds,
                 stop_loss_price, take_profit_price, market_regime, trend_context, order_flow_context)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?::jsonb, ?::jsonb)
                """, id, symbol, interval, strategyId, prediction.model(), rulesDirection,
                prediction.decision(), prediction.longProbability(), prediction.waitProbability(),
                prediction.shortProbability(), prediction.confidence(), prediction.confidenceMargin(),
                prediction.featureDriftScore(), prediction.driftStatus(), prediction.entropy(),
                prediction.uncertaintyStatus(), json(prediction.predictionSet()), json(snapshot.features()),
                Timestamp.from(Instant.now()), startingPrice, prediction.decision(), prediction.expectedReturn(),
                prediction.expectedVolatility(), prediction.tradeQualityScore(), prediction.stopHitProbability(),
                prediction.targetHitProbability(), 900,
                riskPlan == null ? null : riskPlan.stopLoss(), riskPlan == null ? null : riskPlan.takeProfit1(),
                structure == null ? null : structure.regime(),
                json(structure == null ? Map.of() : structure),
                json(market == null ? Map.of() : market));
        return id;
    }

    public List<Map<String, Object>> latest(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, symbol, interval_name, strategy_id, model_version, rules_direction,
                       predicted_direction, long_probability, wait_probability, short_probability,
                       confidence, confidence_margin, feature_drift_score, drift_status,
                       prediction_entropy, uncertainty_status, prediction_set, starting_price,
                       prediction_time, expected_return, expected_volatility, trade_quality_score,
                       stop_hit_probability, target_hit_probability, final_approved_direction,
                       evaluation_horizon_seconds, stop_loss_price, take_profit_price, path_outcome,
                       take_profit_hit_first, stop_loss_hit_first, outcome_label, forward_return, correct, resolved_at
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
                    WHERE (CAST(? AS TEXT) IS NULL OR model_version = CAST(? AS TEXT))
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
                       (SELECT expected_calibration_error FROM calibration) AS calibration_error
                FROM filtered
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
                  AND (CAST(? AS TEXT) IS NULL OR model_version = CAST(? AS TEXT))
                GROUP BY prediction_time::date, model_version
                ORDER BY result_date DESC, model_version
                """, safeDays, modelVersion, modelVersion);
    }

    public Map<String, Object> performance(int limit, String symbol, String timeframe, String regime,
                                           String strategyId, String modelVersion) {
        int safeLimit = Math.max(10, Math.min(limit, 10_000));
        return jdbc.queryForMap("""
                WITH selected AS (
                    SELECT * FROM intelligence.prediction_history
                    WHERE (CAST(? AS TEXT) IS NULL OR symbol=UPPER(CAST(? AS TEXT)))
                      AND (CAST(? AS TEXT) IS NULL OR interval_name=CAST(? AS TEXT))
                      AND (CAST(? AS TEXT) IS NULL OR market_regime=CAST(? AS TEXT))
                      AND (CAST(? AS TEXT) IS NULL OR strategy_id=CAST(? AS TEXT))
                      AND (CAST(? AS TEXT) IS NULL OR model_version=CAST(? AS TEXT))
                    ORDER BY prediction_time DESC LIMIT ?
                ), resolved AS (
                    SELECT *, CASE outcome_label WHEN -1 THEN 'SHORT' WHEN 0 THEN 'WAIT' WHEN 1 THEN 'LONG' END actual_direction
                    FROM selected WHERE resolved_at IS NOT NULL
                ), classes(label) AS (VALUES ('SHORT'), ('WAIT'), ('LONG')),
                class_stats AS (
                    SELECT label,
                           COUNT(*) FILTER (WHERE predicted_direction=label AND actual_direction=label) tp,
                           COUNT(*) FILTER (WHERE predicted_direction=label) predicted,
                           COUNT(*) FILTER (WHERE actual_direction=label) actual
                    FROM classes CROSS JOIN resolved GROUP BY label
                ), class_metrics AS (
                    SELECT label,
                           COALESCE(tp::numeric / NULLIF(predicted,0),0) class_precision,
                           COALESCE(tp::numeric / NULLIF(actual,0),0) class_recall,
                           COALESCE(2.0 * tp / NULLIF(predicted + actual,0),0) class_f1
                    FROM class_stats
                ), prediction_metrics AS (
                    SELECT COUNT(*) AS total,
                           COUNT(*) FILTER (WHERE resolved_at IS NULL) pending,
                           COUNT(*) FILTER (WHERE resolved_at IS NOT NULL) resolved,
                           COALESCE(AVG(CASE WHEN correct THEN 1.0 ELSE 0.0 END) FILTER (WHERE resolved_at IS NOT NULL),0) accuracy,
                           COALESCE((SELECT AVG(class_recall) FROM class_metrics),0) balanced_accuracy,
                           COALESCE((SELECT AVG(class_f1) FROM class_metrics),0) macro_f1,
                           COALESCE((SELECT class_precision FROM class_metrics WHERE label='LONG'),0) long_precision,
                           COALESCE((SELECT class_precision FROM class_metrics WHERE label='SHORT'),0) short_precision,
                           COALESCE((SELECT class_precision FROM class_metrics WHERE label='WAIT'),0) wait_quality,
                           COALESCE(AVG(CASE WHEN correct THEN 1.0 ELSE 0.0 END)
                               FILTER (WHERE resolved_at IS NOT NULL AND predicted_direction <> 'WAIT'),0) directional_precision,
                           COALESCE(AVG(POWER(short_probability - CASE WHEN outcome_label=-1 THEN 1 ELSE 0 END,2)
                                      + POWER(wait_probability - CASE WHEN outcome_label=0 THEN 1 ELSE 0 END,2)
                                      + POWER(long_probability - CASE WHEN outcome_label=1 THEN 1 ELSE 0 END,2))
                               FILTER (WHERE resolved_at IS NOT NULL),0) brier_score,
                           COALESCE(AVG(-LN(GREATEST(CASE outcome_label WHEN -1 THEN short_probability
                                      WHEN 0 THEN wait_probability WHEN 1 THEN long_probability END,0.000001)))
                               FILTER (WHERE resolved_at IS NOT NULL),0) log_loss,
                           COALESCE(AVG(expected_return),0) average_expected_return,
                           COALESCE(AVG(forward_return) FILTER (WHERE resolved_at IS NOT NULL),0) average_realized_return
                    FROM selected
                ), linked_trades AS (
                    SELECT DISTINCT j.id, j.realized_pnl, j.closed_at
                    FROM selected p JOIN trading.trade_journal j ON j.feature_snapshot_ref=p.id
                    WHERE j.closed_at IS NOT NULL
                ), pnl_path AS (
                    SELECT realized_pnl, closed_at, SUM(realized_pnl) OVER (ORDER BY closed_at) cumulative_pnl
                    FROM linked_trades
                ), drawdowns AS (
                    SELECT cumulative_pnl - MAX(cumulative_pnl) OVER (ORDER BY closed_at) drawdown FROM pnl_path
                ), trade_metrics AS (
                    SELECT COUNT(*) trade_count,
                           COALESCE(AVG(CASE WHEN realized_pnl > 0 THEN 1.0 ELSE 0.0 END),0) trade_win_rate,
                           COALESCE(SUM(realized_pnl) FILTER (WHERE realized_pnl > 0)
                               / NULLIF(ABS(SUM(realized_pnl) FILTER (WHERE realized_pnl < 0)),0),0) profit_factor,
                           COALESCE(AVG(realized_pnl) / NULLIF(STDDEV_SAMP(realized_pnl),0) * SQRT(COUNT(*)),0) sharpe_ratio,
                           COALESCE(AVG(realized_pnl) / NULLIF(STDDEV_SAMP(realized_pnl) FILTER (WHERE realized_pnl < 0),0)
                               * SQRT(COUNT(*)),0) sortino_ratio
                    FROM linked_trades
                )
                SELECT prediction_metrics.*,
                       COALESCE(trade_metrics.trade_count::numeric / NULLIF(prediction_metrics.total,0),0) prediction_to_trade_conversion,
                       trade_metrics.trade_count, trade_metrics.trade_win_rate, trade_metrics.profit_factor,
                       trade_metrics.sharpe_ratio, trade_metrics.sortino_ratio,
                       COALESCE(ABS((SELECT MIN(drawdown) FROM drawdowns)),0) maximum_drawdown
                FROM prediction_metrics CROSS JOIN trade_metrics
                """, symbol, symbol, timeframe, timeframe, regime, regime, strategyId, strategyId,
                modelVersion, modelVersion, safeLimit);
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
