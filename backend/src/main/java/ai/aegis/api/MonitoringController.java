package ai.aegis.api;

import ai.aegis.monitoring.PaperMonitoringResult;
import ai.aegis.monitoring.PaperTradeMonitoringService;
import ai.aegis.monitoring.StrategyFeedback;
import ai.aegis.monitoring.StrategyFeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/monitoring")
public class MonitoringController {
    private final PaperTradeMonitoringService paperMonitoringService;
    private final StrategyFeedbackService feedbackService;

    public MonitoringController(PaperTradeMonitoringService paperMonitoringService,
                                StrategyFeedbackService feedbackService) {
        this.paperMonitoringService = paperMonitoringService;
        this.feedbackService = feedbackService;
    }

    @PostMapping("/paper/mark")
    public ResponseEntity<PaperMonitoringResult> monitor(
            @RequestBody Map<String, BigDecimal> marketPrices) {
        return ResponseEntity.ok(paperMonitoringService.monitor(marketPrices));
    }

    @PostMapping("/strategy-feedback")
    public ResponseEntity<StrategyFeedback> feedback(@RequestBody FeedbackRequest request) {
        return ResponseEntity.ok(feedbackService.evaluate(
                request.backtestMean(), request.backtestVolatility(),
                request.maximumDrawdownPercent(), request.minimumLiveTrades()));
    }

    public record FeedbackRequest(
            double backtestMean,
            double backtestVolatility,
            double maximumDrawdownPercent,
            int minimumLiveTrades
    ) {
        public FeedbackRequest {
            if (backtestVolatility < 0 || maximumDrawdownPercent <= 0 || minimumLiveTrades < 1) {
                throw new IllegalArgumentException("valid feedback thresholds are required");
            }
        }
    }
}
