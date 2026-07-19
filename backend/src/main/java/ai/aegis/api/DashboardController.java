package ai.aegis.api;

import ai.aegis.dashboard.DashboardService;
import ai.aegis.dashboard.DashboardSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardSnapshot dashboard(@RequestParam(defaultValue = "BTCUSDT") String symbol,
                                       @RequestParam(defaultValue = "1m") String interval) {
        return dashboardService.snapshot(symbol, interval);
    }
}
