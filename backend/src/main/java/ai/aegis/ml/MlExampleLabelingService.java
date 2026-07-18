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
public class MlExampleLabelingService {
    private final JdbcTemplate jdbc;
    private final Duration horizon;
    private final BigDecimal neutralThreshold;

    public MlExampleLabelingService(JdbcTemplate jdbc,
                                    @Value("${aegis.ml.label-horizon-minutes:15}") long horizonMinutes,
                                    @Value("${aegis.ml.neutral-return-threshold:0.001}") BigDecimal neutralThreshold) {
        this.jdbc = jdbc;
        this.horizon = Duration.ofMinutes(horizonMinutes);
        this.neutralThreshold = neutralThreshold;
    }

    @Scheduled(fixedDelayString = "${aegis.ml.label-delay-ms:60000}")
    @Transactional
    public int labelPending() {
        List<PendingExample> pending = jdbc.query("""
                SELECT id, symbol, interval_name, created_at
                FROM intelligence.ml_examples
                WHERE label IS NULL AND created_at <= ?
                ORDER BY created_at ASC LIMIT 500
                """, (rs, n) -> new PendingExample(UUID.fromString(rs.getString("id")), rs.getString("symbol"),
                rs.getString("interval_name"), rs.getTimestamp("created_at").toInstant()),
                Timestamp.from(Instant.now().minus(horizon)));

        int labelled = 0;
        for (PendingExample example : pending) {
            BigDecimal start = priceAtOrBefore(example.symbol(), example.interval(), example.createdAt());
            BigDecimal future = priceAtOrAfter(example.symbol(), example.interval(), example.createdAt().plus(horizon));
            if (start == null || future == null || start.signum() <= 0) continue;
            BigDecimal forwardReturn = future.subtract(start).divide(start, 12, RoundingMode.HALF_UP);
            int label = forwardReturn.compareTo(neutralThreshold) > 0 ? 1
                    : forwardReturn.compareTo(neutralThreshold.negate()) < 0 ? -1 : 0;
            labelled += jdbc.update("""
                    UPDATE intelligence.ml_examples
                    SET label=?, forward_return=?, labelled_at=?
                    WHERE id=? AND label IS NULL
                    """, label, forwardReturn, Timestamp.from(Instant.now()), example.id());
        }
        return labelled;
    }

    private BigDecimal priceAtOrBefore(String symbol, String interval, Instant at) {
        List<BigDecimal> values = jdbc.query("""
                SELECT close FROM market.candles
                WHERE symbol=? AND interval_name=? AND is_closed=true AND close_time <= ?
                ORDER BY close_time DESC LIMIT 1
                """, (rs, n) -> rs.getBigDecimal("close"), symbol, interval, Timestamp.from(at));
        return values.isEmpty() ? null : values.getFirst();
    }

    private BigDecimal priceAtOrAfter(String symbol, String interval, Instant at) {
        List<BigDecimal> values = jdbc.query("""
                SELECT close FROM market.candles
                WHERE symbol=? AND interval_name=? AND is_closed=true AND close_time >= ?
                ORDER BY close_time ASC LIMIT 1
                """, (rs, n) -> rs.getBigDecimal("close"), symbol, interval, Timestamp.from(at));
        return values.isEmpty() ? null : values.getFirst();
    }

    private record PendingExample(UUID id, String symbol, String interval, Instant createdAt) { }
}
