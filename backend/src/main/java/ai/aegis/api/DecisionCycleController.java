package ai.aegis.api;

import ai.aegis.market.Candle;
import ai.aegis.orchestration.DecisionCycleResult;
import ai.aegis.orchestration.DecisionCycleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/decision-cycle")
public class DecisionCycleController {
    private final DecisionCycleService service;

    public DecisionCycleController(DecisionCycleService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DecisionCycleResult> run(@RequestBody List<Candle> candles) {
        return ResponseEntity.ok(service.run(candles));
    }
}
