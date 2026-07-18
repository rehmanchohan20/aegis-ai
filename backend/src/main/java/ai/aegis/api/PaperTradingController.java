package ai.aegis.api;

import ai.aegis.paper.PaperTrade;
import ai.aegis.paper.PaperTradingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/paper-trades")
public class PaperTradingController {
    private final PaperTradingService service;

    public PaperTradingController(PaperTradingService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PaperTrade> open(@RequestBody OpenPaperTradeRequest request) {
        return ResponseEntity.ok(service.open(request.symbol(), request.interval(), request.side(),
                request.entryPrice(), request.stopLoss(), request.takeProfit(), request.quantity()));
    }

    @PostMapping("/{id}/mark")
    public ResponseEntity<PaperTrade> mark(@PathVariable UUID id, @RequestBody MarkPriceRequest request) {
        return ResponseEntity.ok(service.mark(id, request.marketPrice()));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<PaperTrade> close(@PathVariable UUID id, @RequestBody MarkPriceRequest request) {
        return ResponseEntity.ok(service.close(id, request.marketPrice(), "MANUALLY_CLOSED"));
    }

    @GetMapping
    public ResponseEntity<List<PaperTrade>> list() {
        return ResponseEntity.ok(service.list());
    }

    public record OpenPaperTradeRequest(String symbol, String interval, String side, BigDecimal entryPrice,
                                        BigDecimal stopLoss, BigDecimal takeProfit, BigDecimal quantity) {}
    public record MarkPriceRequest(BigDecimal marketPrice) {}
}
