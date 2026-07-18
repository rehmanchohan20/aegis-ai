package ai.aegis.api;

import ai.aegis.execution.ExchangeOrderService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/execution/orders")
public class ExchangeExecutionController {
    private final ExchangeOrderService service;

    public ExchangeExecutionController(ExchangeOrderService service) { this.service = service; }

    @PostMapping
    public ExchangeOrderService.OrderResult submit(
            @RequestBody ExchangeOrderService.OrderRequest request,
            @RequestHeader(value = "X-AEGIS-EXECUTION-CONFIRMATION", required = false) String confirmation) {
        return service.submit(request, confirmation);
    }
}
