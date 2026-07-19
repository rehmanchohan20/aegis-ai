package ai.aegis.setup;

import ai.aegis.market.Candle;
import ai.aegis.market.CandleStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DirectionalSetupBacktestServiceTest {
    @Test
    void entersNoEarlierThanSignalCloseAndUsesConservativeSameBarOrdering() {
        CandleStore candles=mock(CandleStore.class); List<Candle> history=history();
        when(candles.latest(anyString(),anyString(),anyInt())).thenReturn(history);
        DirectionalBiasService biases=mock(DirectionalBiasService.class);
        DirectionalBias bias=new DirectionalBias("BTCUSDT","LONG",BigDecimal.valueOf(.8),Map.of(),true,List.of(),Instant.now());
        when(biases.analyze(anyString(),anyMap())).thenReturn(bias);
        DirectionalSetupService setups=mock(DirectionalSetupService.class);
        when(setups.evaluateHistorical(anyList(),any())).thenReturn(actionable(bias));
        JdbcTemplate jdbc=mock(JdbcTemplate.class); when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        var service=new DirectionalSetupBacktestService(candles,biases,setups,jdbc,new ObjectMapper().findAndRegisterModules());
        DirectionalSetupBacktestResult result=service.run("BTCUSDT","1m",500);
        assertFalse(result.trades().isEmpty());
        assertTrue(result.trades().stream().noneMatch(trade->trade.entryTime().isBefore(trade.signalTime())));
        assertTrue(result.warnings().stream().anyMatch(warning->warning.contains("Same-candle")));
    }
    private static DirectionalSetup actionable(DirectionalBias bias){
        return new DirectionalSetup(UUID.randomUUID(),"BTCUSDT","1m",Instant.now(),"APPROVED_SETUP","LONG",bias,null,null,
                "BULL_HH_HL","BREAKOUT_RETEST","ACCEPTANCE_IN_VALUE",true,true,true,true,false,
                new DirectionalSetup.PriceZone(BigDecimal.valueOf(100),BigDecimal.valueOf(101),"RETEST"),BigDecimal.valueOf(98),
                List.of(BigDecimal.valueOf(105)),BigDecimal.valueOf(3),BigDecimal.valueOf(.35),BigDecimal.valueOf(.65),
                BigDecimal.valueOf(80),"TEST",List.of(),List.of(),List.of(),List.of());
    }
    private static List<Candle> history(){Instant start=Instant.parse("2025-01-01T00:00:00Z");return java.util.stream.IntStream.range(0,240).mapToObj(i->{
        BigDecimal close=BigDecimal.valueOf(101+(i%4)*.2);return new Candle("BTCUSDT","1m",start.plus(i,ChronoUnit.MINUTES),
                start.plus(i+1,ChronoUnit.MINUTES),BigDecimal.valueOf(100.5),BigDecimal.valueOf(106),BigDecimal.valueOf(99),close,BigDecimal.valueOf(1000),true);}).toList();}
}
