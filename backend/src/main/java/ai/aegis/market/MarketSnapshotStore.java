package ai.aegis.market;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Repository
public class MarketSnapshotStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public MarketSnapshotStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void save(MarketSnapshot snapshot) {
        jdbc.update("""
                INSERT INTO market.market_snapshot
                (symbol, snapshot_time, exchange_time, received_at, last_price, bid_price, ask_price,
                 spread_bps, weighted_mid_price, bid_depth, ask_depth, order_book_imbalance,
                 aggressive_buy_volume, aggressive_sell_volume, cumulative_volume_delta,
                 trade_count, trade_intensity, average_trade_size, realized_volatility,
                 volume_acceleration, ingestion_latency_ms, connection_status, data_quality,
                 feature_status, last_trade_id, last_depth_update_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                ON CONFLICT (symbol, snapshot_time) DO UPDATE SET
                    exchange_time=EXCLUDED.exchange_time, received_at=EXCLUDED.received_at,
                    last_price=EXCLUDED.last_price, bid_price=EXCLUDED.bid_price,
                    ask_price=EXCLUDED.ask_price, spread_bps=EXCLUDED.spread_bps,
                    weighted_mid_price=EXCLUDED.weighted_mid_price, bid_depth=EXCLUDED.bid_depth,
                    ask_depth=EXCLUDED.ask_depth, order_book_imbalance=EXCLUDED.order_book_imbalance,
                    aggressive_buy_volume=EXCLUDED.aggressive_buy_volume,
                    aggressive_sell_volume=EXCLUDED.aggressive_sell_volume,
                    cumulative_volume_delta=EXCLUDED.cumulative_volume_delta,
                    trade_count=EXCLUDED.trade_count, trade_intensity=EXCLUDED.trade_intensity,
                    average_trade_size=EXCLUDED.average_trade_size,
                    realized_volatility=EXCLUDED.realized_volatility,
                    volume_acceleration=EXCLUDED.volume_acceleration,
                    ingestion_latency_ms=EXCLUDED.ingestion_latency_ms,
                    connection_status=EXCLUDED.connection_status, data_quality=EXCLUDED.data_quality,
                    feature_status=EXCLUDED.feature_status, last_trade_id=EXCLUDED.last_trade_id,
                    last_depth_update_id=EXCLUDED.last_depth_update_id
                """, snapshot.symbol(), Timestamp.from(snapshot.snapshotTime()),
                Timestamp.from(snapshot.exchangeTime()), Timestamp.from(snapshot.receivedAt()),
                snapshot.lastPrice(), snapshot.bidPrice(), snapshot.askPrice(), snapshot.spreadBps(),
                snapshot.weightedMidPrice(), snapshot.bidDepth(), snapshot.askDepth(),
                snapshot.orderBookImbalance(), snapshot.aggressiveBuyVolume(), snapshot.aggressiveSellVolume(),
                snapshot.cumulativeVolumeDelta(), snapshot.tradeCount(), snapshot.tradeIntensity(),
                snapshot.averageTradeSize(), snapshot.realizedVolatility(), snapshot.volumeAcceleration(),
                snapshot.ingestionLatencyMs(), snapshot.connectionStatus(), snapshot.dataQuality(),
                json(snapshot.featureStatus()), snapshot.lastTradeId(), snapshot.lastDepthUpdateId());
    }

    public List<MarketSnapshot> latest(String symbol, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 3600));
        return jdbc.query("""
                SELECT * FROM market.market_snapshot WHERE symbol=?
                ORDER BY snapshot_time DESC LIMIT ?
                """, (rs, row) -> new MarketSnapshot(rs.getString("symbol"),
                rs.getTimestamp("snapshot_time").toInstant(), rs.getTimestamp("exchange_time").toInstant(),
                rs.getTimestamp("received_at").toInstant(), rs.getBigDecimal("last_price"),
                rs.getBigDecimal("bid_price"), rs.getBigDecimal("ask_price"), rs.getBigDecimal("spread_bps"),
                rs.getBigDecimal("weighted_mid_price"), rs.getBigDecimal("bid_depth"),
                rs.getBigDecimal("ask_depth"), rs.getBigDecimal("order_book_imbalance"),
                rs.getBigDecimal("aggressive_buy_volume"), rs.getBigDecimal("aggressive_sell_volume"),
                rs.getBigDecimal("cumulative_volume_delta"), rs.getInt("trade_count"),
                rs.getBigDecimal("trade_intensity"), rs.getBigDecimal("average_trade_size"),
                rs.getBigDecimal("realized_volatility"), rs.getBigDecimal("volume_acceleration"),
                rs.getLong("ingestion_latency_ms"), rs.getString("connection_status"),
                rs.getString("data_quality"), readMap(rs.getString("feature_status")),
                (Long) rs.getObject("last_trade_id"), (Long) rs.getObject("last_depth_update_id")),
                symbol.toUpperCase(), safeLimit).reversed();
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize market snapshot", exception); }
    }

    private Map<String, String> readMap(String value) {
        try { return mapper.readValue(value, new TypeReference<>() { }); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid market feature status", exception); }
    }
}
