package ai.aegis.attribution;

import ai.aegis.journal.TradeJournalEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StrategyPnlAttributionService {
    private static final Pattern STRATEGY = Pattern.compile("Selected strategy: ([A-Za-z0-9_.-]+)");
    private static final Pattern MODEL = Pattern.compile("ML confirmation: ([A-Za-z0-9_.-]+)");
    private final JdbcTemplate jdbc;

    public StrategyPnlAttributionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void attribute(TradeJournalEntry trade) {
        if (trade == null || trade.closedAt() == null || trade.realizedPnl() == null) return;
        String rationale = trade.rationale() == null ? "" : trade.rationale();
        String strategyId = extract(STRATEGY, rationale, "unknown");
        String modelVersion = extract(MODEL, rationale, null);
        jdbc.update("""
                INSERT INTO intelligence.strategy_pnl_attribution
                (trade_id, strategy_id, model_version, symbol, interval_name, realized_pnl, closed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (trade_id) DO UPDATE SET strategy_id=EXCLUDED.strategy_id,
                    model_version=EXCLUDED.model_version, realized_pnl=EXCLUDED.realized_pnl,
                    closed_at=EXCLUDED.closed_at
                """, trade.id(), strategyId, modelVersion, trade.symbol(), trade.interval(),
                trade.realizedPnl(), Timestamp.from(trade.closedAt()));
    }

    private String extract(Pattern pattern, String value, String fallback) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : fallback;
    }
}
