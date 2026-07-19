package ai.aegis.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ExchangeOrderServiceTest {
    @Test
    void liveExecutionRemainsBlockedByDefault() {
        ExchangeOrderService service = new ExchangeOrderService(mock(JdbcTemplate.class),
                new ObjectMapper(), false, "DISABLED");
        var request = new ExchangeOrderService.OrderRequest("test-1", "binance", "BTCUSDT",
                "LONG", "MARKET", BigDecimal.ONE, null);
        assertEquals("BLOCKED_LIVE_DISABLED", service.submit(request, "DISABLED").status());
    }
}
