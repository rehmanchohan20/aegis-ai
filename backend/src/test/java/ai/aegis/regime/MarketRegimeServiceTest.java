package ai.aegis.regime;

import ai.aegis.market.Candle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketRegimeServiceTest {
    private final MarketRegimeService service = new MarketRegimeService();

    @Test
    void shouldDetectBullTrend() {
        MarketRegime regime = service.detect(candles(true));
        assertThat(regime.trend()).isEqualTo("BULL_TREND");
        assertThat(regime.support()).isLessThan(regime.resistance());
    }

    @Test
    void shouldDetectRange() {
        MarketRegime regime = service.detect(candles(false));
        assertThat(regime.trend()).isEqualTo("RANGE");
        assertThat(regime.structure()).isEqualTo("MEAN_REVERTING");
    }

    private static List<Candle> candles(boolean trending) {
        List<Candle> result = new ArrayList<>();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 60; i++) {
            double value = trending ? 100 + (i * 0.5) : 100 + Math.sin(i / 2.0);
            BigDecimal close = BigDecimal.valueOf(value);
            result.add(new Candle("BTCUSDT", "1m",
                    start.plus(i, ChronoUnit.MINUTES), start.plus(i + 1, ChronoUnit.MINUTES),
                    close.subtract(BigDecimal.valueOf(0.2)), close.add(BigDecimal.valueOf(0.4)),
                    close.subtract(BigDecimal.valueOf(0.4)), close,
                    BigDecimal.valueOf(1000 + i), true));
        }
        return result;
    }
}
