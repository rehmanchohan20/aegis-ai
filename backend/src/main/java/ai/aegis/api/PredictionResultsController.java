package ai.aegis.api;

import ai.aegis.ml.PredictionAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/predictions")
public class PredictionResultsController {
    private final PredictionAuditService auditService;

    public PredictionResultsController(PredictionAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<Map<String, Object>> latest(@RequestParam(defaultValue = "100") int limit) {
        return auditService.latest(limit);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(required = false) String modelVersion) {
        return auditService.summary(modelVersion);
    }

    @GetMapping("/rolling")
    public List<Map<String, Object>> rolling(@RequestParam(required = false) String modelVersion,
                                              @RequestParam(defaultValue = "30") int days) {
        return auditService.rolling(modelVersion, days);
    }
}
