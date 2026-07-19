package ai.aegis.paper;

import ai.aegis.market.MarketDataStateService;
import ai.aegis.market.MarketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaperOrderService {
    private static final Logger log = LoggerFactory.getLogger(PaperOrderService.class);
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

    private final JdbcTemplate jdbc;
    private final MarketDataStateService marketData;
    private final BigDecimal feeBps;
    private final BigDecimal baseSlippageBps;
    private final BigDecimal impactBps;
    private final BigDecimal maximumSpreadBps;
    private final BigDecimal maximumOrderNotional;
    private final int maximumWorkingOrders;
    private final BigDecimal depthParticipation;

    public PaperOrderService(JdbcTemplate jdbc, MarketDataStateService marketData,
                             @Value("${aegis.paper.fee-bps:7.5}") BigDecimal feeBps,
                             @Value("${aegis.paper.base-slippage-bps:1.0}") BigDecimal baseSlippageBps,
                             @Value("${aegis.paper.impact-bps:12.0}") BigDecimal impactBps,
                             @Value("${aegis.paper.maximum-spread-bps:20}") BigDecimal maximumSpreadBps,
                             @Value("${aegis.paper.maximum-order-notional:25000}") BigDecimal maximumOrderNotional,
                             @Value("${aegis.paper.maximum-working-orders:12}") int maximumWorkingOrders,
                             @Value("${aegis.paper.depth-participation:0.10}") BigDecimal depthParticipation) {
        this.jdbc = jdbc;
        this.marketData = marketData;
        this.feeBps = feeBps;
        this.baseSlippageBps = baseSlippageBps;
        this.impactBps = impactBps;
        this.maximumSpreadBps = maximumSpreadBps;
        this.maximumOrderNotional = maximumOrderNotional;
        this.maximumWorkingOrders = maximumWorkingOrders;
        this.depthParticipation = depthParticipation;
    }

    @Transactional
    public PaperOrder submit(OrderRequest request) {
        validate(request);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String clientOrderId = request.clientOrderId() == null || request.clientOrderId().isBlank()
                ? "PAPER-" + id : request.clientOrderId();
        MarketSnapshot market = marketData.latest(request.symbol());
        String rejection = admissionRejection(request, market);
        String status = rejection == null ? "NEW" : "REJECTED";
        jdbc.update("""
                INSERT INTO trading.paper_order
                (id, client_order_id, symbol, interval_name, side, order_type, status,
                 requested_quantity, filled_quantity, limit_price, stop_price, slippage, fee,
                 rejection_reason, submitted_at, completed_at, expires_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 0, 0, ?, ?, ?, ?, ?)
                """, id, clientOrderId, request.symbol().toUpperCase(), request.interval(),
                request.side().toUpperCase(), request.orderType().toUpperCase(), status, request.quantity(),
                request.limitPrice(), request.stopPrice(), rejection, Timestamp.from(now),
                rejection == null ? null : Timestamp.from(now),
                request.expiresAt() == null ? null : Timestamp.from(request.expiresAt()), Timestamp.from(now));
        PaperOrder order = require(id);
        return rejection == null ? attemptFill(order, market, now) : order;
    }

    @Scheduled(fixedRateString = "${aegis.paper.matching-interval-ms:1000}")
    public void matchWorkingOrders() {
        for (PaperOrder order : working()) {
            try { attemptFill(order, marketData.latest(order.symbol()), Instant.now()); }
            catch (RuntimeException exception) { log.error("Paper matching failed for order {}", order.id(), exception); }
        }
    }

    @Transactional
    public PaperOrder cancel(UUID id) {
        PaperOrder order = require(id);
        if (!List.of("NEW", "PARTIALLY_FILLED", "TRIGGERED").contains(order.status())) return order;
        Instant now = Instant.now();
        jdbc.update("UPDATE trading.paper_order SET status='CANCELLED', completed_at=?, updated_at=? WHERE id=?",
                Timestamp.from(now), Timestamp.from(now), id);
        return require(id);
    }

    public List<PaperOrder> list(int limit) {
        return query("SELECT * FROM trading.paper_order ORDER BY submitted_at DESC LIMIT ?", Math.max(1, Math.min(limit, 500)));
    }

    public void linkTrade(UUID orderId, UUID tradeId) {
        jdbc.update("UPDATE trading.paper_order SET trade_id=?, updated_at=? WHERE id=?",
                tradeId, Timestamp.from(Instant.now()), orderId);
    }

    private List<PaperOrder> working() {
        return query("SELECT * FROM trading.paper_order WHERE status IN ('NEW','PARTIALLY_FILLED','TRIGGERED') ORDER BY submitted_at", new Object[0]);
    }

    @Transactional
    PaperOrder attemptFill(PaperOrder order, MarketSnapshot market, Instant now) {
        if (!List.of("NEW", "PARTIALLY_FILLED", "TRIGGERED").contains(order.status())) return order;
        if (order.expiresAt() != null && !order.expiresAt().isAfter(now)) {
            jdbc.update("UPDATE trading.paper_order SET status='EXPIRED', completed_at=?, updated_at=? WHERE id=?",
                    Timestamp.from(now), Timestamp.from(now), order.id());
            return require(order.id());
        }
        if (market == null || !"GOOD".equals(market.dataQuality())) return order;
        boolean buy = "LONG".equals(order.side());
        BigDecimal touch = buy ? market.askPrice() : market.bidPrice();
        BigDecimal depth = buy ? market.askDepth() : market.bidDepth();
        if (touch == null || depth == null || depth.signum() <= 0) return order;
        if (!triggered(order, market.lastPrice())) return order;
        if (!marketable(order, touch)) return order;
        BigDecimal remaining = order.requestedQuantity().subtract(order.filledQuantity(), MC);
        BigDecimal available = depth.multiply(depthParticipation, MC).max(BigDecimal.ZERO);
        BigDecimal fillQuantity = remaining.min(available).setScale(12, RoundingMode.DOWN);
        if (fillQuantity.signum() <= 0) return order;
        BigDecimal participation = fillQuantity.divide(depth.max(BigDecimal.valueOf(.00000001)), MC);
        BigDecimal slippageBps = baseSlippageBps.add(impactBps.multiply(participation, MC), MC);
        BigDecimal multiplier = slippageBps.divide(BigDecimal.valueOf(10_000), MC);
        BigDecimal fillPrice = touch.multiply(buy ? BigDecimal.ONE.add(multiplier, MC)
                : BigDecimal.ONE.subtract(multiplier, MC), MC);
        if ("LIMIT".equals(order.orderType()) || "STOP_LIMIT".equals(order.orderType())) {
            fillPrice = buy ? fillPrice.min(order.limitPrice()) : fillPrice.max(order.limitPrice());
        }
        BigDecimal notional = fillPrice.multiply(fillQuantity, MC);
        BigDecimal fee = notional.multiply(feeBps.divide(BigDecimal.valueOf(10_000), MC), MC);
        BigDecimal slippage = fillPrice.subtract(touch, MC).abs().multiply(fillQuantity, MC);
        UUID fillId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO trading.paper_fill
                (id, order_id, quantity, price, fee, liquidity, market_snapshot_time, filled_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, fillId, order.id(), fillQuantity, fillPrice, fee,
                ("MARKET".equals(order.orderType()) || "STOP".equals(order.orderType())) ? "TAKER" : "MAKER",
                Timestamp.from(market.snapshotTime()), Timestamp.from(now));
        BigDecimal newFilled = order.filledQuantity().add(fillQuantity, MC);
        BigDecimal previousNotional = order.averageFillPrice() == null ? BigDecimal.ZERO
                : order.averageFillPrice().multiply(order.filledQuantity(), MC);
        BigDecimal average = previousNotional.add(notional, MC).divide(newFilled, MC);
        boolean complete = newFilled.compareTo(order.requestedQuantity()) >= 0;
        jdbc.update("""
                UPDATE trading.paper_order SET status=?, filled_quantity=?, average_fill_price=?,
                    slippage=slippage+?, fee=fee+?, first_fill_at=COALESCE(first_fill_at, ?),
                    completed_at=?, updated_at=? WHERE id=?
                """, complete ? "FILLED" : "PARTIALLY_FILLED", newFilled, average, slippage, fee,
                Timestamp.from(now), complete ? Timestamp.from(now) : null, Timestamp.from(now), order.id());
        return require(order.id());
    }

    private String admissionRejection(OrderRequest request, MarketSnapshot market) {
        Integer working = jdbc.queryForObject("SELECT COUNT(*) FROM trading.paper_order WHERE status IN ('NEW','PARTIALLY_FILLED','TRIGGERED')", Integer.class);
        if (working != null && working >= maximumWorkingOrders) return "maximum working-order limit reached";
        if (market == null || !"GOOD".equals(market.dataQuality())
                || Duration.between(market.receivedAt(), Instant.now()).abs().compareTo(Duration.ofSeconds(5)) > 0) {
            return "market data is missing or stale";
        }
        if (market.spreadBps() == null || market.spreadBps().compareTo(maximumSpreadBps) > 0) return "spread exceeds paper-execution limit";
        if (request.quantity().multiply(market.lastPrice(), MC).compareTo(maximumOrderNotional) > 0) return "maximum order notional exceeded";
        return null;
    }

    private static boolean triggered(PaperOrder order, BigDecimal marketPrice) {
        if (!List.of("STOP", "STOP_LIMIT").contains(order.orderType())) return true;
        return "LONG".equals(order.side()) ? marketPrice.compareTo(order.stopPrice()) >= 0
                : marketPrice.compareTo(order.stopPrice()) <= 0;
    }

    private static boolean marketable(PaperOrder order, BigDecimal touch) {
        if (!List.of("LIMIT", "STOP_LIMIT").contains(order.orderType())) return true;
        return "LONG".equals(order.side()) ? touch.compareTo(order.limitPrice()) <= 0
                : touch.compareTo(order.limitPrice()) >= 0;
    }

    private void validate(OrderRequest request) {
        if (request == null || request.symbol() == null || request.symbol().isBlank()
                || request.interval() == null || request.interval().isBlank()) throw new IllegalArgumentException("symbol and interval are required");
        if (!List.of("LONG", "SHORT").contains(request.side() == null ? "" : request.side().toUpperCase())) throw new IllegalArgumentException("side must be LONG or SHORT");
        String type = request.orderType() == null ? "" : request.orderType().toUpperCase();
        if (!List.of("MARKET", "LIMIT", "STOP", "STOP_LIMIT").contains(type)) throw new IllegalArgumentException("unsupported paper order type");
        if (request.quantity() == null || request.quantity().signum() <= 0) throw new IllegalArgumentException("positive quantity is required");
        if (List.of("LIMIT", "STOP_LIMIT").contains(type) && (request.limitPrice() == null || request.limitPrice().signum() <= 0)) throw new IllegalArgumentException("limit price is required");
        if (List.of("STOP", "STOP_LIMIT").contains(type) && (request.stopPrice() == null || request.stopPrice().signum() <= 0)) throw new IllegalArgumentException("stop price is required");
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) throw new IllegalArgumentException("expiration must be in the future");
    }

    private PaperOrder require(UUID id) {
        List<PaperOrder> rows = query("SELECT * FROM trading.paper_order WHERE id=?", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Paper order not found");
        return rows.getFirst();
    }

    private List<PaperOrder> query(String sql, Object... arguments) {
        return jdbc.query(sql, (rs, row) -> new PaperOrder(UUID.fromString(rs.getString("id")),
                rs.getString("client_order_id"), rs.getString("trade_id") == null ? null : UUID.fromString(rs.getString("trade_id")),
                rs.getString("symbol"), rs.getString("interval_name"), rs.getString("side"),
                rs.getString("order_type"), rs.getString("status"), rs.getBigDecimal("requested_quantity"),
                rs.getBigDecimal("filled_quantity"), rs.getBigDecimal("limit_price"), rs.getBigDecimal("stop_price"),
                rs.getBigDecimal("average_fill_price"), rs.getBigDecimal("slippage"), rs.getBigDecimal("fee"),
                rs.getString("rejection_reason"), rs.getTimestamp("submitted_at").toInstant(),
                instant(rs.getTimestamp("first_fill_at")), instant(rs.getTimestamp("completed_at")),
                instant(rs.getTimestamp("expires_at")), rs.getTimestamp("updated_at").toInstant()), arguments);
    }

    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }

    public record OrderRequest(String clientOrderId, String symbol, String interval, String side,
                               String orderType, BigDecimal quantity, BigDecimal limitPrice,
                               BigDecimal stopPrice, Instant expiresAt) { }
}
