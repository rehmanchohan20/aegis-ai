package ai.aegis.paper;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaperTradingService {
    private final Map<UUID, PaperTrade> trades = new ConcurrentHashMap<>();

    public PaperTrade open(String symbol, String interval, String side, BigDecimal entryPrice,
                           BigDecimal stopLoss, BigDecimal takeProfit, BigDecimal quantity) {
        validate(side, entryPrice, stopLoss, takeProfit, quantity);
        PaperTrade trade = new PaperTrade(UUID.randomUUID(), symbol.toUpperCase(), interval, side.toUpperCase(),
                entryPrice, stopLoss, takeProfit, quantity, "OPEN", BigDecimal.ZERO, Instant.now(), null);
        trades.put(trade.id(), trade);
        return trade;
    }

    public PaperTrade mark(UUID id, BigDecimal marketPrice) {
        PaperTrade trade = require(id);
        if (!"OPEN".equals(trade.status())) return trade;

        boolean longTrade = "LONG".equals(trade.side());
        boolean stopHit = longTrade ? marketPrice.compareTo(trade.stopLoss()) <= 0
                : marketPrice.compareTo(trade.stopLoss()) >= 0;
        boolean targetHit = longTrade ? marketPrice.compareTo(trade.takeProfit()) >= 0
                : marketPrice.compareTo(trade.takeProfit()) <= 0;

        if (!stopHit && !targetHit) return trade;
        return close(id, stopHit ? trade.stopLoss() : trade.takeProfit(), stopHit ? "STOPPED" : "TARGET_HIT");
    }

    public PaperTrade close(UUID id, BigDecimal exitPrice, String status) {
        PaperTrade trade = require(id);
        if (!"OPEN".equals(trade.status())) return trade;
        BigDecimal movement = "LONG".equals(trade.side())
                ? exitPrice.subtract(trade.entryPrice())
                : trade.entryPrice().subtract(exitPrice);
        BigDecimal pnl = movement.multiply(trade.quantity()).setScale(2, RoundingMode.HALF_UP);
        PaperTrade closed = new PaperTrade(trade.id(), trade.symbol(), trade.interval(), trade.side(),
                trade.entryPrice(), trade.stopLoss(), trade.takeProfit(), trade.quantity(), status,
                pnl, trade.openedAt(), Instant.now());
        trades.put(id, closed);
        return closed;
    }

    public List<PaperTrade> list() {
        List<PaperTrade> result = new ArrayList<>(trades.values());
        result.sort(Comparator.comparing(PaperTrade::openedAt).reversed());
        return List.copyOf(result);
    }

    private PaperTrade require(UUID id) {
        PaperTrade trade = trades.get(id);
        if (trade == null) throw new IllegalArgumentException("Paper trade not found");
        return trade;
    }

    private void validate(String side, BigDecimal entry, BigDecimal stop, BigDecimal target, BigDecimal quantity) {
        if (!"LONG".equalsIgnoreCase(side) && !"SHORT".equalsIgnoreCase(side)) {
            throw new IllegalArgumentException("side must be LONG or SHORT");
        }
        if (entry == null || stop == null || target == null || quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("valid prices and positive quantity are required");
        }
        if ("LONG".equalsIgnoreCase(side) && !(stop.compareTo(entry) < 0 && target.compareTo(entry) > 0)) {
            throw new IllegalArgumentException("LONG requires stop below and target above entry");
        }
        if ("SHORT".equalsIgnoreCase(side) && !(stop.compareTo(entry) > 0 && target.compareTo(entry) < 0)) {
            throw new IllegalArgumentException("SHORT requires stop above and target below entry");
        }
    }
}
