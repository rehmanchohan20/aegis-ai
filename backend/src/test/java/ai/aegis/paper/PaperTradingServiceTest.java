package ai.aegis.paper;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaperTradingServiceTest {
    private final PaperTradingService service = new PaperTradingService();

    @Test
    void shouldCloseLongAtTarget() {
        PaperTrade trade = service.open("BTCUSDT", "1m", "LONG",
                BigDecimal.valueOf(100), BigDecimal.valueOf(95), BigDecimal.valueOf(110), BigDecimal.valueOf(2));

        PaperTrade closed = service.mark(trade.id(), BigDecimal.valueOf(111));

        assertThat(closed.status()).isEqualTo("TARGET_HIT");
        assertThat(closed.realizedPnl()).isEqualByComparingTo("20.00");
    }

    @Test
    void shouldCloseShortAtStop() {
        PaperTrade trade = service.open("BTCUSDT", "1m", "SHORT",
                BigDecimal.valueOf(100), BigDecimal.valueOf(105), BigDecimal.valueOf(90), BigDecimal.ONE);

        PaperTrade closed = service.mark(trade.id(), BigDecimal.valueOf(106));

        assertThat(closed.status()).isEqualTo("STOPPED");
        assertThat(closed.realizedPnl()).isEqualByComparingTo("-5.00");
    }
}
