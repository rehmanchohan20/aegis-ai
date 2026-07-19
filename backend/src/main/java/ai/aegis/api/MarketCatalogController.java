package ai.aegis.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/markets")
public class MarketCatalogController {
    private final List<String> symbols;
    private final List<String> intervals;

    public MarketCatalogController(
            @Value("${aegis.market.symbols}") String symbols,
            @Value("${aegis.market.intervals}") String intervals) {
        this.symbols = values(symbols, true);
        this.intervals = values(intervals, false);
        if (this.symbols.isEmpty() || this.intervals.isEmpty()) {
            throw new IllegalArgumentException("At least one market symbol and interval are required");
        }
    }

    @GetMapping
    public MarketCatalog markets() {
        return new MarketCatalog(symbols, intervals);
    }

    private static List<String> values(String configured, boolean uppercase) {
        if (configured == null) return List.of();
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> uppercase ? value.toUpperCase() : value)
                .distinct()
                .toList();
    }

    public record MarketCatalog(List<String> symbols, List<String> intervals) {
        public MarketCatalog {
            symbols = List.copyOf(symbols);
            intervals = List.copyOf(intervals);
        }
    }
}
