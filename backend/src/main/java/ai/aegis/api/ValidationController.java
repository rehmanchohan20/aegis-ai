package ai.aegis.api;

import ai.aegis.health.StrategyHealthDecision;
import ai.aegis.health.StrategyHealthService;
import ai.aegis.portfolio.PortfolioAllocation;
import ai.aegis.portfolio.PortfolioMathService;
import ai.aegis.validation.BootstrapResult;
import ai.aegis.validation.BootstrapValidationService;
import ai.aegis.validation.WalkForwardResult;
import ai.aegis.validation.WalkForwardValidationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/validation")
public class ValidationController {
    private final BootstrapValidationService bootstrap;
    private final WalkForwardValidationService walkForward;
    private final PortfolioMathService portfolio;
    private final StrategyHealthService health;

    public ValidationController(BootstrapValidationService bootstrap, WalkForwardValidationService walkForward,
                                PortfolioMathService portfolio, StrategyHealthService health) {
        this.bootstrap = bootstrap;
        this.walkForward = walkForward;
        this.portfolio = portfolio;
        this.health = health;
    }

    @PostMapping("/bootstrap")
    public BootstrapResult bootstrap(@RequestBody BootstrapRequest request) {
        return bootstrap.analyze(request.tradeReturns(), request.samples(), request.seed());
    }

    @PostMapping("/walk-forward")
    public WalkForwardResult walkForward(@RequestBody WalkForwardRequest request) {
        return walkForward.validate(request.strategyReturns(), request.folds(), request.trainFraction());
    }

    @PostMapping("/risk-parity")
    public PortfolioAllocation riskParity(@RequestBody RiskParityRequest request) {
        return portfolio.riskParity(request.returnsByAsset());
    }

    @PostMapping("/volatility-target")
    public BigDecimal volatilityTarget(@RequestBody VolatilityTargetRequest request) {
        return portfolio.volatilityTargetWeight(request.returns(), request.targetVolatility(), request.maximumWeight());
    }

    @PostMapping("/strategy-health")
    public StrategyHealthDecision strategyHealth(@RequestBody StrategyHealthRequest request) {
        return health.evaluate(request.backtestMean(), request.liveMean(), request.backtestVolatility(),
                request.liveVolatility(), request.liveDrawdown(), request.maximumDrawdown(),
                request.consecutiveLosses(), request.minimumLiveTrades(), request.liveTrades());
    }

    public record BootstrapRequest(List<Double> tradeReturns, int samples, long seed) {}
    public record WalkForwardRequest(List<Double> strategyReturns, int folds, double trainFraction) {}
    public record RiskParityRequest(Map<String, List<Double>> returnsByAsset) {}
    public record VolatilityTargetRequest(List<Double> returns, BigDecimal targetVolatility, BigDecimal maximumWeight) {}
    public record StrategyHealthRequest(double backtestMean, double liveMean, double backtestVolatility,
                                        double liveVolatility, double liveDrawdown, double maximumDrawdown,
                                        int consecutiveLosses, int minimumLiveTrades, int liveTrades) {}
}
