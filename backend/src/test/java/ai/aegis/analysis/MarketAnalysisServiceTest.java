package ai.aegis.analysis;

import ai.aegis.indicator.AtrIndicator;
import ai.aegis.indicator.EmaIndicator;
import ai.aegis.indicator.MacdIndicator;
import ai.aegis.indicator.RsiIndicator;
import ai.aegis.indicator.VwapIndicator;
import ai.aegis.market.Candle;
import ai.aegis.risk.RiskEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketAnalysisServiceTest {
    private final MarketAnalysisService service = new MarketAnalysisService(
            new EmaIndicator(), new RsiIndicator(), new AtrIndicator(),
            new MacdIndicator(), new VwapIndicator(), new RiskEngine());

    @Test
    void shouldProduceLongDecisionForControlledUptrend() {
        MarketAnalysis analysis = service.analyze(candles(true));

        assertThat(analysis.decision()).isEqualTo("LONG");
        assertThat(analysis.score()).isGreaterThanOrEqualTo(68);
        assertThat(analysis.indicators()).containsKeys(
                "ema20", "vwap", "rsi14", "atr14", "atrPercent", "macd", "macdSignal", "macdHistogram");
        assertThat(analysis.risk().status()).isEqualTo("VALID");
        assertThat(analysis.risk().stopLoss()).isLessThan(analysis.risk().entry());
        assertThat(analysis.reasons()).isNotEmpty();
    }

    @Test
    void shouldProduceShortDecisionForControlledDowntrend() {
        MarketAnalysis analysis = service.analyze(candles(false));

        assertThat(analysis.decision()).isEqualTo("SHORT");
        assertThat(analysis.score()).isLessThanOrEqualTo(32);
        assertThat(analysis.risk().stopLoss()).isGreaterThan(analysis.risk().entry());
    }

    private static List<Candle> candles(boolean rising) {
        List<Candle> candles = new ArrayList<>();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 60; i++) {
            BigDecimal base = rising
                    ? BigDecimal.valueOf(100 + i * 0.8)
                    : BigDecimal.valueOf(150 - i * 0.8);
            candles.add(new Candle(
                    "BTCUSDT",
                    "1m",
                    start.plus(i, ChronoUnit.MINUTES),
                    start.plus(i + 1, ChronoUnit.MINUTES),
                    base.subtract(BigDecimal.valueOf(0.2)),
                    base.add(BigDecimal.valueOf(0.5)),
                    base.subtract(BigDecimal.valueOf(0.5)),
                    base,
                    BigDecimal.valueOf(1000 + i * 15L),
                    true
            ));
        }
        return candles;
    }
}
