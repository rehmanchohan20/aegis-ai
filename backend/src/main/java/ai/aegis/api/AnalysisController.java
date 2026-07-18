package ai.aegis.api;

import ai.aegis.analysis.MarketAnalysis;
import ai.aegis.analysis.MarketAnalysisService;
import ai.aegis.market.Candle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {
    private final MarketAnalysisService service;

    public AnalysisController(MarketAnalysisService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MarketAnalysis> analyze(
            @Valid @Size(min = 21, message = "At least 21 candles are required") @RequestBody List<Candle> candles) {
        return ResponseEntity.ok(service.analyze(candles));
    }
}
