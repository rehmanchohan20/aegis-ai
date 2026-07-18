package ai.aegis.monitoring;

import ai.aegis.journal.TradeJournalStore;
import ai.aegis.paper.PaperTrade;
import ai.aegis.paper.PaperTradingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaperTradeMonitoringServiceTest {

    @Test
    void closesTargetHitAndPersistsJournal() {
        PaperTradingService paper = new PaperTradingService();
        TradeJournalStore journal = mock(TradeJournalStore.class);
        PaperTrade trade = paper.open("BTCUSDT", "1m", "LONG",
                new BigDecimal("100"), new BigDecimal("95"),
                new BigDecimal("110"), BigDecimal.ONE);

        PaperTradeMonitoringService service = new PaperTradeMonitoringService(paper, journal);
        PaperMonitoringResult result = service.monitor(Map.of("BTCUSDT", new BigDecimal("111")));

        assertEquals(1, result.inspected());
        assertEquals(1, result.closed());
        assertEquals("TARGET_HIT", result.updatedTrades().getFirst().status());
        assertEquals(trade.id(), result.updatedTrades().getFirst().id());
        verify(journal).save(any());
    }
}
