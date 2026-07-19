package ai.aegis.journal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.math.BigDecimal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import ai.aegis.risk.RiskPlan;

@Repository
public class TradeJournalStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public TradeJournalStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void save(TradeJournalEntry trade) {
        jdbc.update("""
                INSERT INTO trading.trade_journal
                    (id, symbol, interval_name, side, status, entry_price, stop_loss, take_profit,
                     quantity, realized_pnl, signal_score, signal_grade, rationale, opened_at, closed_at,
                     strategy_id, model_version, rules_direction, ml_direction, ml_probabilities,
                     feature_snapshot_ref, risk_plan, closure_reason, market_regime)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?)
                ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status,
                    realized_pnl = EXCLUDED.realized_pnl, closed_at = EXCLUDED.closed_at,
                    rationale = EXCLUDED.rationale
                """, trade.id(), trade.symbol(), trade.interval(), trade.side(), trade.status(),
                trade.entryPrice(), trade.stopLoss(), trade.takeProfit(), trade.quantity(), trade.realizedPnl(),
                trade.signalScore(), trade.signalGrade(), trade.rationale(), Timestamp.from(trade.openedAt()),
                trade.closedAt() == null ? null : Timestamp.from(trade.closedAt()), trade.strategyId(),
                trade.modelVersion(), trade.rulesDirection(), trade.mlDirection(), json(trade.mlProbabilities()),
                trade.featureSnapshotRef(), json(trade.riskPlan()), trade.closureReason(), trade.marketRegime());
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
                rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toInstant(),
                rs.getString("strategy_id"), rs.getString("model_version"), rs.getString("rules_direction"),
                rs.getString("ml_direction"), readMap(rs.getString("ml_probabilities")),
                rs.getString("feature_snapshot_ref") == null ? null : UUID.fromString(rs.getString("feature_snapshot_ref")),
                readRiskPlan(rs.getString("risk_plan")), rs.getString("closure_reason"), rs.getString("market_regime")
        ), limit);
    }

    public void updateExecutionCosts(UUID tradeId, BigDecimal averageFillPrice, BigDecimal fees,
                                     BigDecimal slippage, java.time.Instant fillTime) {
        jdbc.update("""
                UPDATE trading.trade_journal SET average_fill_price=?, fees=?, slippage=?, fill_time=?,
                    order_submitted_at=COALESCE(order_submitted_at, opened_at),
                    entry_signal_at=COALESCE(entry_signal_at, opened_at)
                WHERE id=?
                """, averageFillPrice, fees, slippage,
                fillTime == null ? null : Timestamp.from(fillTime), tradeId);
    }

    private String json(Object value) {
        if (value == null) return null;
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Unable to serialize trade metadata", e); }
    }

    private Map<String, BigDecimal> readMap(String value) {
        if (value == null) return Map.of();
        try { return mapper.readValue(value, new TypeReference<Map<String, BigDecimal>>() { }); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored ML probabilities", e); }
    }

    private RiskPlan readRiskPlan(String value) {
        if (value == null) return null;
        try { return mapper.readValue(value, RiskPlan.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored risk plan", e); }
    }
}
