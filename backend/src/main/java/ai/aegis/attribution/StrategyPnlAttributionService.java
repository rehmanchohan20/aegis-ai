package ai.aegis.attribution;

import ai.aegis.journal.TradeJournalEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

@Service
public class StrategyPnlAttributionService {
    private final JdbcTemplate jdbc;

    public StrategyPnlAttributionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void attribute(TradeJournalEntry trade) {
        if (trade == null || trade.closedAt() == null || trade.realizedPnl() == null) return;
        String strategyId = trade.strategyId() == null ? "unknown" : trade.strategyId();
        String modelVersion = trade.modelVersion();
        jdbc.update("""
                INSERT INTO intelligence.strategy_pnl_attribution
                (trade_id, strategy_id, model_version, symbol, interval_name, realized_pnl, closed_at, side, market_regime)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (trade_id) DO UPDATE SET strategy_id=EXCLUDED.strategy_id,
                    model_version=EXCLUDED.model_version, realized_pnl=EXCLUDED.realized_pnl,
                    closed_at=EXCLUDED.closed_at
                """, trade.id(), strategyId, modelVersion, trade.symbol(), trade.interval(),
                trade.realizedPnl(), Timestamp.from(trade.closedAt()), trade.side(), trade.marketRegime());
    }
}
