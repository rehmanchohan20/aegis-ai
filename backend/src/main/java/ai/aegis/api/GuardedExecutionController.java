package ai.aegis.api;

import ai.aegis.orchestration.GuardedExecutionRequest;
import ai.aegis.orchestration.GuardedExecutionResult;
import ai.aegis.orchestration.GuardedExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guarded-execution")
public class GuardedExecutionController {
    private final GuardedExecutionService service;

    public GuardedExecutionController(GuardedExecutionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<GuardedExecutionResult> execute(@RequestBody GuardedExecutionRequest request) {
        return ResponseEntity.ok(service.execute(request));
    }
}
