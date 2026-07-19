package ai.aegis.api;

import ai.aegis.paper.PaperOrder;
import ai.aegis.paper.PaperOrderService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/paper-orders")
public class PaperOrderController {
    private final PaperOrderService service;

    public PaperOrderController(PaperOrderService service) { this.service = service; }

    @PostMapping
    public PaperOrder submit(@RequestBody PaperOrderService.OrderRequest request) { return service.submit(request); }

    @DeleteMapping("/{id}")
    public PaperOrder cancel(@PathVariable UUID id) { return service.cancel(id); }

    @GetMapping
    public List<PaperOrder> list(@RequestParam(defaultValue = "100") int limit) { return service.list(limit); }
}
