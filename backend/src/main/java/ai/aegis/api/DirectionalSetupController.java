package ai.aegis.api;

import ai.aegis.setup.DirectionalBias;
import ai.aegis.setup.DirectionalBiasService;
import ai.aegis.setup.DirectionalSetup;
import ai.aegis.setup.DirectionalSetupBacktestResult;
import ai.aegis.setup.DirectionalSetupBacktestService;
import ai.aegis.setup.DirectionalSetupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/directional-setups")
public class DirectionalSetupController {
    private final DirectionalSetupService setups; private final DirectionalBiasService biases;
    private final DirectionalSetupBacktestService backtests;
    public DirectionalSetupController(DirectionalSetupService setups,DirectionalBiasService biases,
                                      DirectionalSetupBacktestService backtests){this.setups=setups;this.biases=biases;this.backtests=backtests;}
    @GetMapping public DirectionalSetup latest(@RequestParam(defaultValue="BTCUSDT")String symbol,
                                               @RequestParam(defaultValue="1m")String timeframe){return setups.latest(symbol,timeframe);}
    @GetMapping("/bias") public DirectionalBias bias(@RequestParam(defaultValue="BTCUSDT")String symbol){return biases.analyze(symbol);}
    @PostMapping("/backtest") public DirectionalSetupBacktestResult backtest(@RequestParam(defaultValue="BTCUSDT")String symbol,
             @RequestParam(defaultValue="1m")String timeframe,@RequestParam(defaultValue="2000")int limit){return backtests.run(symbol,timeframe,limit);}
}
