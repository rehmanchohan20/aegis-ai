package ai.aegis.api;

import ai.aegis.ranking.PairRankingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pair-ranking")
public class PairRankingController {
    private final PairRankingService service;

    public PairRankingController(PairRankingService service) { this.service = service; }

    @GetMapping
    public PairRankingService.RankingSnapshot rank() { return service.rank(); }

    @GetMapping("/correlation")
    public PairRankingService.CorrelationContext correlation(
            @RequestParam(defaultValue = "BTCUSDT") String symbol) { return service.correlationContext(symbol); }
}
