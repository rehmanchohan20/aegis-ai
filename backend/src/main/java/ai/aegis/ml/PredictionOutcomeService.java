package ai.aegis.ml;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PredictionOutcomeService {
    private final JdbcTemplate jdbc;
    private final Duration horizon;
    private final BigDecimal neutralThreshold;

    public PredictionOutcomeService(JdbcTemplate jdbc,
                                    @Value("${aegis.ml.label-horizon-minutes:15}") long horizonMinutes,
                                    @Value("${aegis.ml.neutral-return-threshold:0.001}") BigDecimal neutralThreshold) {
        if (horizonMinutes <= 0) throw new IllegalArgumentException("prediction horizon must be positive");
        if (neutralThreshold == null || neutralThreshold.signum() < 0) {
            throw new IllegalArgumentException("neutral return threshold cannot be negative");
        }
        this.jdbc = jdbc;
        this.horizon = Duration.ofMinutes(horizonMinutes);
        this.neutralThreshold = neutralThreshold;
    }

    @Scheduled(fixedDelayString = "${aegis.ml.prediction-resolution-delay-ms:60000}")
    @Transactional
    public int reconcileDuePredictions() {
        List<PendingPrediction> pending = jdbc.query("""
                SELECT id, symbol, interval_name, predicted_direction, starting_price, prediction_time,
                       COALESCE(evaluation_horizon_seconds, ?) evaluation_horizon_seconds,
                       stop_loss_price, take_profit_price
                FROM intelligence.prediction_history
                WHERE resolved_at IS NULL AND starting_price IS NOT NULL
                  AND prediction_time + COALESCE(evaluation_horizon_seconds, ?) * INTERVAL '1 second' <= CURRENT_TIMESTAMP
                ORDER BY prediction_time LIMIT 500
                """, (rs, row) -> new PendingPrediction(
                UUID.fromString(rs.getString("id")), rs.getString("symbol"), rs.getString("interval_name"),
                rs.getString("predicted_direction"), rs.getBigDecimal("starting_price"),
                rs.getTimestamp("prediction_time").toInstant(), rs.getInt("evaluation_horizon_seconds"),
                rs.getBigDecimal("stop_loss_price"), rs.getBigDecimal("take_profit_price")),
                horizon.toSeconds(), horizon.toSeconds());

        int resolved = 0;
        for (PendingPrediction prediction : pending) {
            Instant dueAt = prediction.predictionTime().plusSeconds(prediction.horizonSeconds());
            List<ClosingPrice> prices = jdbc.query("""
                    SELECT close, high, low, close_time FROM market.candles
                    WHERE symbol = ? AND interval_name = ? AND is_closed = TRUE AND close_time >= ?
                    ORDER BY close_time LIMIT 1
                    """, (rs, row) -> new ClosingPrice(rs.getBigDecimal("close"), rs.getBigDecimal("high"),
                    rs.getBigDecimal("low"), rs.getTimestamp("close_time").toInstant()), prediction.symbol().toUpperCase(),
                    prediction.interval(), Timestamp.from(dueAt));
            if (prices.isEmpty()) continue;
            ClosingPrice ending = prices.getFirst();
            List<ClosingPrice> path = jdbc.query("""
                    SELECT close, high, low, close_time FROM market.candles
                    WHERE symbol = ? AND interval_name = ? AND is_closed = TRUE
                      AND close_time > ? AND close_time <= ? ORDER BY close_time
                    """, (rs, row) -> new ClosingPrice(rs.getBigDecimal("close"), rs.getBigDecimal("high"),
                    rs.getBigDecimal("low"), rs.getTimestamp("close_time").toInstant()),
                    prediction.symbol().toUpperCase(), prediction.interval(),
                    Timestamp.from(prediction.predictionTime()), Timestamp.from(ending.closeTime()));
            Resolution outcome = resolve(prediction.startingPrice(), prices.getFirst().price(),
                    prediction.predictedDirection(), neutralThreshold);
            PathOutcome pathOutcome = resolvePath(path, prediction.stopLoss(), prediction.takeProfit());
            resolved += jdbc.update("""
                    UPDATE intelligence.prediction_history SET outcome_label = ?, forward_return = ?, correct = ?,
                        take_profit_hit_first = ?, stop_loss_hit_first = ?, path_outcome = ?, resolved_at = ?
                    WHERE id = ? AND resolved_at IS NULL
                    """, outcome.outcomeLabel(), outcome.forwardReturn(), outcome.correct(),
                    pathOutcome.takeProfitFirst(), pathOutcome.stopLossFirst(), pathOutcome.status(),
                    Timestamp.from(Instant.now()), prediction.id());
        }
        return resolved;
    }

    static Resolution resolve(BigDecimal startingPrice, BigDecimal endingPrice,
                              String predictedDirection, BigDecimal threshold) {
        if (startingPrice == null || endingPrice == null || startingPrice.signum() <= 0 || endingPrice.signum() <= 0) {
            throw new IllegalArgumentException("positive starting and ending prices are required");
        }
        BigDecimal forwardReturn = endingPrice.subtract(startingPrice)
                .divide(startingPrice, 12, RoundingMode.HALF_UP);
        int label = forwardReturn.compareTo(threshold) > 0 ? 1
                : forwardReturn.compareTo(threshold.negate()) < 0 ? -1 : 0;
        String actualDirection = label > 0 ? "LONG" : label < 0 ? "SHORT" : "WAIT";
        return new Resolution(label, forwardReturn, actualDirection.equalsIgnoreCase(predictedDirection));
    }

    static PathOutcome resolvePath(List<ClosingPrice> candles, BigDecimal stopLoss, BigDecimal takeProfit) {
        if (stopLoss == null || takeProfit == null) return new PathOutcome(null, null, "LEVELS_UNAVAILABLE");
        for (ClosingPrice candle : candles) {
            boolean stopHit = candle.low().compareTo(stopLoss) <= 0 && candle.high().compareTo(stopLoss) >= 0;
            boolean targetHit = candle.low().compareTo(takeProfit) <= 0 && candle.high().compareTo(takeProfit) >= 0;
            if (stopHit && targetHit) return new PathOutcome(null, null, "AMBIGUOUS_SAME_CANDLE");
            if (stopHit) return new PathOutcome(false, true, "STOP_LOSS_FIRST");
            if (targetHit) return new PathOutcome(true, false, "TAKE_PROFIT_FIRST");
        }
        return new PathOutcome(false, false, "NEITHER_WITHIN_HORIZON");
    }

    private record PendingPrediction(UUID id, String symbol, String interval, String predictedDirection,
                                     BigDecimal startingPrice, Instant predictionTime, int horizonSeconds,
                                     BigDecimal stopLoss, BigDecimal takeProfit) { }
    record ClosingPrice(BigDecimal price, BigDecimal high, BigDecimal low, Instant closeTime) { }
    record PathOutcome(Boolean takeProfitFirst, Boolean stopLossFirst, String status) { }
    record Resolution(int outcomeLabel, BigDecimal forwardReturn, boolean correct) { }
}
