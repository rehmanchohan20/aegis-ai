package ai.aegis.feature;

import ai.aegis.market.Candle;
import ai.aegis.market.MarketDataStateService;
import ai.aegis.market.MarketSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CandleFeatureServiceTest {
    private final MarketDataStateService marketData = mock(MarketDataStateService.class);
    private final CandleFeatureService service = new CandleFeatureService(marketData);

    @Test
    void buildsUsableFeatureSnapshotFromClosedCandles() {
        List<Candle> candles = new ArrayList<>();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 40; i++) {
            BigDecimal open = BigDecimal.valueOf(100 + i);
            BigDecimal close = BigDecimal.valueOf(100.5 + i);
            candles.add(new Candle("BTCUSDT", "1h", start.plusSeconds(i * 3600L),
                    start.plusSeconds((i + 1) * 3600L), open,
                    close.add(BigDecimal.ONE), open.subtract(BigDecimal.ONE), close,
                    BigDecimal.valueOf(1000 + i * 10L), true));
        }

        Instant observed = candles.getLast().closeTime();
        when(marketData.latest("BTCUSDT")).thenReturn(new MarketSnapshot("BTCUSDT", observed, observed, observed,
                new BigDecimal("140.5"), new BigDecimal("140.4"), new BigDecimal("140.6"),
                new BigDecimal("14.23"), new BigDecimal("140.5"), new BigDecimal("25"),
                new BigDecimal("20"), new BigDecimal("0.1111"), new BigDecimal("4"),
                new BigDecimal("3"), BigDecimal.ONE, 12, new BigDecimal("12"),
                new BigDecimal("0.58"), new BigDecimal("0.002"), new BigDecimal("0.2"),
                15, "CONNECTED", "GOOD", java.util.Map.of(), 1L, 2L));
        FeatureSnapshot snapshot = service.build(candles);

        assertEquals("BTCUSDT", snapshot.symbol());
        assertTrue(snapshot.usableValue("momentum10").isPresent());
        assertTrue(snapshot.usableValue("volatility20").isPresent());
        assertTrue(snapshot.usableValue("atrNormalized14").isPresent());
        assertTrue(snapshot.usableValue("returnSkewness20").isPresent());
        assertTrue(snapshot.usableValue("returnExcessKurtosis20").isPresent());
        assertTrue(snapshot.usableValue("returnAutocorrelation1_20").isPresent());
        assertTrue(snapshot.usableValue("parkinsonVolatility20").isPresent());
        assertTrue(snapshot.usableValue("garmanKlassVolatility20").isPresent());
        assertTrue(snapshot.usableValue("trendTStatistic20").isPresent());
        assertTrue(snapshot.usableValue("amihudIlliquidity20").isPresent());
        assertTrue(snapshot.usableValue("orderBookImbalance").isPresent());
        assertTrue(snapshot.unusableFeatures().isEmpty());
    }

    @Test
    void rejectsInsufficientHistory() {
        assertThrows(IllegalArgumentException.class, () -> service.build(List.of()));
    }
}
