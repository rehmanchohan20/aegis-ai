package ai.aegis.market;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MarketDataStateServiceTest {
    @Test
    void producesMicrostructureSnapshotAndRejectsDuplicateTrades() {
        MarketSnapshotStore store = mock(MarketSnapshotStore.class);
        LiveMarketStreamService stream = mock(LiveMarketStreamService.class);
        MarketDataStateService service = new MarketDataStateService(store, stream, "BTCUSDT");
        Instant exchange = Instant.now().minusMillis(20);
        Instant received = Instant.now();
        service.connectionChanged("CONNECTED");
        service.onBookTicker("BTCUSDT", 10, new BigDecimal("100.00"), new BigDecimal("4"),
                new BigDecimal("100.10"), new BigDecimal("2"), exchange, received);
        service.onDepth("BTCUSDT", 20, new BigDecimal("12"), new BigDecimal("8"), exchange, received);
        service.onAggregateTrade("BTCUSDT", 30, new BigDecimal("100.05"), new BigDecimal("3"),
                false, exchange, received);
        service.onAggregateTrade("BTCUSDT", 30, new BigDecimal("100.05"), new BigDecimal("3"),
                false, exchange, received);

        service.persistSnapshots();

        ArgumentCaptor<MarketSnapshot> captured = ArgumentCaptor.forClass(MarketSnapshot.class);
        verify(store).save(captured.capture());
        MarketSnapshot snapshot = captured.getValue();
        assertEquals(1, snapshot.tradeCount());
        assertEquals(new BigDecimal("0.2"), snapshot.orderBookImbalance().stripTrailingZeros());
        assertEquals("GOOD", snapshot.dataQuality());
        assertTrue(snapshot.weightedMidPrice().compareTo(snapshot.bidPrice()) > 0);
        verify(stream).publish(snapshot);
    }
}
