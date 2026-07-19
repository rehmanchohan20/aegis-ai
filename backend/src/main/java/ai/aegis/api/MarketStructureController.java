package ai.aegis.api;

import ai.aegis.structure.MarketStructureService;
import ai.aegis.structure.MarketStructureSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market-structure")
public class MarketStructureController {
    private final MarketStructureService service;

    public MarketStructureController(MarketStructureService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<MarketStructureSnapshot> latest(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1m") String interval) {
        MarketStructureSnapshot snapshot = service.latestOrCalculate(symbol, interval);
        return snapshot == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(snapshot);
    }
}
