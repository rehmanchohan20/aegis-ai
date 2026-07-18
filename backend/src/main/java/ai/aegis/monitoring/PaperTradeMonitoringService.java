package ai.aegis.monitoring;

import ai.aegis.attribution.StrategyPnlAttributionService;
import ai.aegis.journal.TradeJournalEntry;
import ai.aegis.journal.TradeJournalStore;
import ai.aegis.paper.PaperTrade;
import ai.aegis.paper.PaperTradingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PaperTradeMonitoringService {
    private final PaperTradingService paperTradingService;
    private final TradeJournalStore journalStore;
    private final StrategyPnlAttributionService attributionService;

    @Autowired
    public PaperTradeMonitoringService(PaperTradingService paperTradingService,
                                       TradeJournalStore journalStore,
                                       StrategyPnlAttributionService attributionService) {
        this.paperTradingService = paperTradingService;
        this.journalStore = journalStore;
        this.attributionService = attributionService;
    }

    public PaperTradeMonitoringService(PaperTradingService paperTradingService,
                                       TradeJournalStore journalStore) {
        this(paperTradingService, journalStore, null);
    }

    public PaperMonitoringResult monitor(Map<String, BigDecimal> marketPrices) {
        if (marketPrices == null || marketPrices.isEmpty()) {
            throw new IllegalArgumentException("marketPrices are required");
        }

        List<PaperTrade> openTrades = paperTradingService.list().stream()
                .filter(t -> "OPEN".equals(t.status()))
                .toList();
        List<PaperTrade> changed = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (PaperTrade trade : openTrades) {
            BigDecimal price = marketPrices.get(trade.symbol());
            if (price == null) price = marketPrices.get(trade.symbol().toLowerCase());
            if (price == null || price.signum() <= 0) {
                warnings.add("No valid market price for " + trade.symbol());
                continue;
            }

            PaperTrade marked = paperTradingService.mark(trade.id(), price);
            if (!"OPEN".equals(marked.status())) {
                persistClosure(marked);
                changed.add(marked);
            }
        }

        return new PaperMonitoringResult(openTrades.size(), changed.size(), changed, warnings, Instant.now());
    }

    private void persistClosure(PaperTrade trade) {
        TradeJournalEntry previous = journalStore.latest(1000).stream()
                .filter(entry -> entry.id().equals(trade.id()))
                .findFirst()
                .orElse(null);

        TradeJournalEntry closed = new TradeJournalEntry(
                trade.id(), trade.symbol(), trade.interval(), trade.side(), trade.status(),
                trade.entryPrice(), trade.stopLoss(), trade.takeProfit(), trade.quantity(), trade.realizedPnl(),
                previous == null ? null : previous.signalScore(),
                previous == null ? null : previous.signalGrade(),
                previous == null ? "Paper trade automatically closed by monitor" : previous.rationale(),
                trade.openedAt(), trade.closedAt()
        );
        journalStore.save(closed);
        if (attributionService != null) attributionService.attribute(closed);
    }
}
