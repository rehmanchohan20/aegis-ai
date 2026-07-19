package ai.aegis.setup;

import ai.aegis.market.Candle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VolumeProfileServiceTest {
    @Test
    void derivesPocValueAreaNodesAndDevelopingPocWithoutFutureData() {
        VolumeProfileService service = new VolumeProfileService();
        List<Candle> candles = new ArrayList<>();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int index = 0; index < 80; index++) {
            BigDecimal center = BigDecimal.valueOf(index < 60 ? 100 : 103 + Math.sin(index) * .4);
            BigDecimal volume = BigDecimal.valueOf(index < 60 ? 1_000 : 100);
            candles.add(new Candle("BTCUSDT", "1m", start.plus(index, ChronoUnit.MINUTES),
                    start.plus(index + 1, ChronoUnit.MINUTES), center, center.add(BigDecimal.ONE),
                    center.subtract(BigDecimal.ONE), center, volume, true));
        }
        VolumeProfile profile = service.fixedRange(candles, 40);
        assertTrue(profile.pointOfControl().doubleValue() > 99 && profile.pointOfControl().doubleValue() < 102);
        assertTrue(profile.valueAreaLow().compareTo(profile.pointOfControl()) <= 0);
        assertTrue(profile.valueAreaHigh().compareTo(profile.pointOfControl()) >= 0);
        assertFalse(profile.highVolumeNodes().isEmpty());
        assertFalse(profile.lowVolumeNodes().isEmpty());
        assertTrue(profile.developingPoc().getFirst().time().isBefore(profile.developingPoc().getLast().time()));
        assertTrue(profile.methodology().contains("not tick-level"));
    }
}
