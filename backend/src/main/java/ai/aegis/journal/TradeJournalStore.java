package ai.aegis.journal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class TradeJournalStore {
    private final JdbcTemplate jdbc;

    public TradeJournalStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(TradeJournalEntry trade) {
        jdbc.update("""
                INSERT INTO trading.trade_journal
                    (id, symbol, interval_name, side, status, entry_price, stop_loss, take_profit,
                     quantity, realized_pnl, signal_score, signal_grade, rationale, opened_at, closed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status,
                    realized_pnl = EXCLUDED.realized_pnl, closed_at = EXCLUDED.closed_at,
                    rationale = EXCLUDED.rationale
                """, trade.id(), trade.symbol(), trade.interval(), trade.side(), trade.status(),
                trade.entryPrice(), trade.stopLoss(), trade.takeProfit(), trade.quantity(), trade.realizedPnl(),
                trade.signalScore(), trade.signalGrade(), trade.rationale(), Timestamp.from(trade.openedAt()),
                trade.closedAt() == null ? null : Timestamp.from(trade.closedAt()));
    }

    public List<TradeJournalEntry> latest(int limit) {
        return jdbc.query("""
                SELECT * FROM trading.trade_journal ORDER BY opened_at DESC LIMIT ?
                """, (rs, rowNum) -> new TradeJournalEntry(
                UUID.fromString(rs.getString("id")), rs.getString("symbol"), rs.getString("interval_name"),
                rs.getString("side"), rs.getString("status"), rs.getBigDecimal("entry_price"),
                rs.getBigDecimal("stop_loss"), rs.getBigDecimal("take_profit"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("realized_pnl"), (Integer) rs.getObject("signal_score"),
                rs.getString("signal_grade"), rs.getString("rationale"),
                rs.getTimestamp("opened_at").toInstant(),
                rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toInstant()
        ), limit);
    }
}
