package ai.aegis.structure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class MarketStructureStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public MarketStructureStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void save(MarketStructureSnapshot snapshot) {
        jdbc.update("""
                INSERT INTO market.market_structure_snapshot
                (id, symbol, interval_name, calculated_at, structure_state, regime, support_price,
                 resistance_price, atr, atr_upper_band, atr_lower_band, consolidation, breakout_state,
                 pivots, trend_lines, zones, markers, warnings)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb)
                """, snapshot.id(), snapshot.symbol(), snapshot.interval(), Timestamp.from(snapshot.calculatedAt()),
                snapshot.structureState(), snapshot.regime(), snapshot.supportPrice(), snapshot.resistancePrice(),
                snapshot.atr(), snapshot.atrUpperBand(), snapshot.atrLowerBand(), snapshot.consolidation(),
                snapshot.breakoutState(), json(snapshot.pivots()), json(snapshot.trendLines()), json(snapshot.zones()),
                json(snapshot.markers()), json(snapshot.warnings()));
    }

    public MarketStructureSnapshot latest(String symbol, String interval) {
        List<MarketStructureSnapshot> rows = jdbc.query("""
                SELECT * FROM market.market_structure_snapshot
                WHERE symbol=? AND interval_name=? ORDER BY calculated_at DESC LIMIT 1
                """, (rs, row) -> new MarketStructureSnapshot(UUID.fromString(rs.getString("id")),
                rs.getString("symbol"), rs.getString("interval_name"),
                rs.getTimestamp("calculated_at").toInstant(), rs.getString("structure_state"),
                rs.getString("regime"), rs.getBigDecimal("support_price"),
                rs.getBigDecimal("resistance_price"), rs.getBigDecimal("atr"),
                rs.getBigDecimal("atr_upper_band"), rs.getBigDecimal("atr_lower_band"),
                rs.getBoolean("consolidation"), rs.getString("breakout_state"),
                read(rs.getString("pivots"), new TypeReference<>() { }),
                read(rs.getString("trend_lines"), new TypeReference<>() { }),
                read(rs.getString("zones"), new TypeReference<>() { }),
                read(rs.getString("markers"), new TypeReference<>() { }),
                read(rs.getString("warnings"), new TypeReference<>() { })),
                symbol.toUpperCase(), interval);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize market structure", exception); }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid stored market structure", exception); }
    }
}
