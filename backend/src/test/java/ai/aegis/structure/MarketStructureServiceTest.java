package ai.aegis.structure;

import ai.aegis.market.Candle;
import ai.aegis.market.CandleStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MarketStructureServiceTest {
    @Test
    void derivesTrendLinesFromConfirmedPivots() {
        MarketStructureService service = new MarketStructureService(mock(CandleStore.class),
                mock(MarketStructureStore.class), "BTCUSDT", "1m");

        MarketStructureSnapshot snapshot = service.analyze(trendingWave());

        assertTrue(snapshot.pivots().size() >= 8);
        assertFalse(snapshot.trendLines().isEmpty());
        assertTrue(snapshot.trendLines().stream().allMatch(line -> line.confirmedTouches() >= 2));
        assertTrue(snapshot.trendLines().stream().allMatch(line -> line.startTime().isBefore(line.endTime())));
    }

    private List<Candle> trendingWave() {
        List<Candle> candles = new ArrayList<>();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int index = 0; index < 120; index++) {
            double center = 100 + index * 0.08 + Math.sin(index * Math.PI / 5.0) * 2.0;
            BigDecimal open = BigDecimal.valueOf(center - .15);
            BigDecimal close = BigDecimal.valueOf(center + .15);
            candles.add(new Candle("BTCUSDT", "1m", start.plus(index, ChronoUnit.MINUTES),
                    start.plus(index + 1L, ChronoUnit.MINUTES), open, BigDecimal.valueOf(center + .55),
                    BigDecimal.valueOf(center - .55), close, BigDecimal.valueOf(1000 + index * 3L), true));
        }
        return List.copyOf(candles);
    }
}
