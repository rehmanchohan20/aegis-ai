package ai.aegis.api;

import ai.aegis.market.Candle;
import ai.aegis.quant.KellySizingResult;
import ai.aegis.quant.KellySizingService;
import ai.aegis.quant.MonteCarloResult;
import ai.aegis.quant.MonteCarloService;
import ai.aegis.quant.QuantMetrics;
import ai.aegis.quant.QuantitativeAnalysisService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/quant")
public class QuantController {
    private final QuantitativeAnalysisService quantitativeAnalysisService;
    private final KellySizingService kellySizingService;
    private final MonteCarloService monteCarloService;

    public QuantController(QuantitativeAnalysisService quantitativeAnalysisService,
                           KellySizingService kellySizingService,
                           MonteCarloService monteCarloService) {
        this.quantitativeAnalysisService = quantitativeAnalysisService;
        this.kellySizingService = kellySizingService;
        this.monteCarloService = monteCarloService;
    }

    @PostMapping("/metrics")
    public QuantMetrics metrics(@RequestBody List<Candle> candles) {
        return quantitativeAnalysisService.analyze(candles);
    }

    @PostMapping("/kelly")
    public KellySizingResult kelly(@RequestBody KellyRequest request) {
        return kellySizingService.calculate(request.accountBalance(), request.winRate(), request.averageWin(), request.averageLoss());
    }

    @PostMapping("/monte-carlo")
    public MonteCarloResult monteCarlo(@RequestBody MonteCarloRequest request) {
        return monteCarloService.simulate(request.initialBalance(), request.historicalReturns(),
                request.tradesPerSimulation(), request.simulations(), request.seed());
    }

    public record KellyRequest(BigDecimal accountBalance, BigDecimal winRate,
                               BigDecimal averageWin, BigDecimal averageLoss) {}

    public record MonteCarloRequest(BigDecimal initialBalance, List<BigDecimal> historicalReturns,
                                    int tradesPerSimulation, int simulations, long seed) {}
}
