package ai.aegis.api;

import ai.aegis.ml.DeploymentApprovalService;
import ai.aegis.ml.ModelRegistryService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/models")
public class ModelAdminController {
    private final ModelRegistryService registry;
    private final DeploymentApprovalService approvals;

    public ModelAdminController(ModelRegistryService registry, DeploymentApprovalService approvals) {
        this.registry = registry;
        this.approvals = approvals;
    }

    @PostMapping
    public ModelRegistryService.ModelVersion register(@RequestBody RegisterRequest request) {
        return registry.register(request.modelName(), request.version(), request.metrics(), request.artifactUri(), request.featureNames());
    }

    @PostMapping("/{id}/approval")
    public DeploymentApprovalService.Approval approve(@PathVariable UUID id, @RequestBody ApprovalRequest request) {
        return approvals.evaluate(id, request.walkForwardEfficiency(), request.outOfSampleSharpe(), request.maximumDrawdown());
    }

    @PostMapping("/{id}/activate")
    public ModelRegistryService.ModelVersion activate(@PathVariable UUID id) { return registry.activate(id); }

    @PostMapping("/rollback")
    public ModelRegistryService.ModelVersion rollback(@RequestBody RollbackRequest request) {
        return registry.rollback(request.modelName(), request.version());
    }

    @GetMapping
    public List<ModelRegistryService.ModelVersion> list(@RequestParam String modelName) { return registry.list(modelName); }

    public record RegisterRequest(String modelName, String version, Map<String, Object> metrics,
                                  String artifactUri, List<String> featureNames) { }
    public record ApprovalRequest(BigDecimal walkForwardEfficiency, BigDecimal outOfSampleSharpe,
                                  BigDecimal maximumDrawdown) { }
    public record RollbackRequest(String modelName, String version) { }
}
