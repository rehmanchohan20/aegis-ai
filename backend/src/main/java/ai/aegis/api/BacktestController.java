package ai.aegis.api;

import ai.aegis.backtest.BacktestRequest;
import ai.aegis.backtest.BacktestResult;
import ai.aegis.backtest.BacktestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/backtests")
public class BacktestController {
    private final BacktestService service;

    public BacktestController(BacktestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BacktestResult> run(@Valid @RequestBody BacktestRequest request) {
        return ResponseEntity.ok(service.run(request));
    }
}
