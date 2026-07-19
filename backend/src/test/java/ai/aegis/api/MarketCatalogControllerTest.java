package ai.aegis.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketCatalogControllerTest {
    @Test
    void exposesDistinctNormalizedConfiguredMarkets() {
        MarketCatalogController controller = new MarketCatalogController(
                "btcusdt, ETHUSDT,btcusdt, SOLUSDT", "1m, 5m,1m");

        MarketCatalogController.MarketCatalog catalog = controller.markets();

        assertEquals(List.of("BTCUSDT", "ETHUSDT", "SOLUSDT"), catalog.symbols());
        assertEquals(List.of("1m", "5m"), catalog.intervals());
    }
}
