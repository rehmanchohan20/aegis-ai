package ai.aegis.setup;

import ai.aegis.market.Candle;
import ai.aegis.market.CandleStore;
import ai.aegis.structure.MarketStructureService;
import ai.aegis.structure.MarketStructureSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DirectionalBiasServiceTest {
    @Test
    void requiresAlignedOneHourAndFourHourEvidence() {
        MarketStructureService structures = mock(MarketStructureService.class);
        when(structures.analyze(anyList())).thenReturn(structure("BULL_HH_HL"));
        DirectionalBiasService service = new DirectionalBiasService(mock(CandleStore.class), structures);
        List<Candle> trend = trend("1h", 100, .25);
        DirectionalBias result = service.analyze("BTCUSDT", Map.of("15m", trend, "1h", trend, "4h", trend));
        assertEquals("LONG", result.direction());
        assertTrue(result.higherTimeframesAligned());
    }

    private static List<Candle> trend(String timeframe, int count, double step) {
        Instant start=Instant.parse("2025-01-01T00:00:00Z");
        return java.util.stream.IntStream.range(0,count).mapToObj(i->{BigDecimal p=BigDecimal.valueOf(100+i*step);
            return new Candle("BTCUSDT",timeframe,start.plus(i,ChronoUnit.HOURS),start.plus(i+1,ChronoUnit.HOURS),
                    p,p.add(BigDecimal.ONE),p.subtract(BigDecimal.ONE),p,BigDecimal.valueOf(1000),true);}).toList();
    }
    private static MarketStructureSnapshot structure(String state){return new MarketStructureSnapshot(UUID.randomUUID(),"BTCUSDT","1h",Instant.now(),state,"BULL_TREND",
            BigDecimal.valueOf(100),BigDecimal.valueOf(130),BigDecimal.ONE,BigDecimal.valueOf(132),BigDecimal.valueOf(98),false,"NONE",List.of(),List.of(),List.of(),List.of(),List.of());}
}
