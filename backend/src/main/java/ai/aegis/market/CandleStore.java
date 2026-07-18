package ai.aegis.market;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class CandleStore {
    private final JdbcTemplate jdbc;

    public CandleStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(Candle candle) {
        jdbc.update("""
                INSERT INTO market.candles
                    (symbol, interval_name, open_time, close_time, open, high, low, close, volume, is_closed)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (symbol, interval_name, open_time)
                DO UPDATE SET close_time = EXCLUDED.close_time, open = EXCLUDED.open,
                    high = EXCLUDED.high, low = EXCLUDED.low, close = EXCLUDED.close,
                    volume = EXCLUDED.volume, is_closed = EXCLUDED.is_closed
                """,
                candle.symbol(), candle.interval(), Timestamp.from(candle.openTime()), Timestamp.from(candle.closeTime()),
                candle.open(), candle.high(), candle.low(), candle.close(), candle.volume(), candle.closed());
    }

    public List<Candle> latest(String symbol, String interval, int limit) {
        return jdbc.query("""
                SELECT symbol, interval_name, open_time, close_time, open, high, low, close, volume, is_closed
                FROM market.candles
                WHERE symbol = ? AND interval_name = ?
                ORDER BY open_time DESC
                LIMIT ?
                """, (rs, rowNum) -> new Candle(
                rs.getString("symbol"), rs.getString("interval_name"),
                rs.getTimestamp("open_time").toInstant(), rs.getTimestamp("close_time").toInstant(),
                rs.getBigDecimal("open"), rs.getBigDecimal("high"), rs.getBigDecimal("low"),
                rs.getBigDecimal("close"), rs.getBigDecimal("volume"), rs.getBoolean("is_closed")
        ), symbol.toUpperCase(), interval, limit).reversed();
    }
}