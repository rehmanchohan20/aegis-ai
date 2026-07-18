package ai.aegis.api;

import ai.aegis.admission.TradeAdmissionDecision;
import ai.aegis.admission.TradeAdmissionRequest;
import ai.aegis.admission.TradeAdmissionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trade-admission")
public class TradeAdmissionController {
    private final TradeAdmissionService service;

    public TradeAdmissionController(TradeAdmissionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TradeAdmissionDecision> evaluate(@Valid @RequestBody TradeAdmissionRequest request) {
        return ResponseEntity.ok(service.evaluate(request));
    }
}
