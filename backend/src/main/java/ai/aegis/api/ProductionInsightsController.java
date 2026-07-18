package ai.aegis.api;

import ai.aegis.dashboard.ProductionInsightsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/insights")
public class ProductionInsightsController {
    private final ProductionInsightsService service;

    public ProductionInsightsController(ProductionInsightsService service) { this.service = service; }

    @GetMapping
    public ProductionInsightsService.Insights get(@RequestParam(defaultValue = "aegis-direction") String modelName) {
        return service.snapshot(modelName);
    }
}
