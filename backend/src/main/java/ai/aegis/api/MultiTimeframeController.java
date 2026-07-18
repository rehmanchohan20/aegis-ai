package ai.aegis.api;

import ai.aegis.analysis.MultiTimeframeAnalysisService;
import ai.aegis.analysis.MultiTimeframeDecision;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analysis/multi-timeframe")
public class MultiTimeframeController {
    private final MultiTimeframeAnalysisService service;

    public MultiTimeframeController(MultiTimeframeAnalysisService service) {
        this.service = service;
    }

    @GetMapping
    public MultiTimeframeDecision analyze(@RequestParam(defaultValue = "BTCUSDT") String symbol) {
        return service.analyze(symbol);
    }
}
