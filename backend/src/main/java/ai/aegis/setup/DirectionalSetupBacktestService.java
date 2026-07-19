package ai.aegis.setup;

import ai.aegis.market.Candle;
import ai.aegis.market.CandleStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DirectionalSetupBacktestService {
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private final CandleStore candles; private final DirectionalBiasService biases;
    private final DirectionalSetupService setups; private final JdbcTemplate jdbc; private final ObjectMapper mapper;

    public DirectionalSetupBacktestService(CandleStore candles, DirectionalBiasService biases,
                                           DirectionalSetupService setups, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.candles=candles; this.biases=biases; this.setups=setups; this.jdbc=jdbc; this.mapper=mapper;
    }

    public DirectionalSetupBacktestResult run(String symbol, String timeframe, int requestedLimit) {
        int limit=Math.max(200,Math.min(requestedLimit,10_000)); Instant started=Instant.now();
        List<Candle> base=candles.latest(symbol,timeframe,limit).stream().filter(Candle::closed).toList();
        Map<String,List<Candle>> higher=Map.of("15m",candles.latest(symbol,"15m",limit),
                "1h",candles.latest(symbol,"1h",limit),"4h",candles.latest(symbol,"4h",limit));
        if(base.size()<160)throw new IllegalArgumentException("setup backtest requires at least 160 closed base candles");
        List<DirectionalSetupBacktestResult.Trade> trades=new ArrayList<>(); int evaluated=0,ambiguous=0;
        for(int index=120;index<base.size()-2;index++){
            Instant decisionTime=base.get(index).closeTime(); List<Candle> history=base.subList(0,index+1);
            Map<String,List<Candle>> available=new LinkedHashMap<>(); higher.forEach((tf,series)->available.put(tf,
                    series.stream().filter(c->c.closed()&&!c.closeTime().isAfter(decisionTime)).toList()));
            DirectionalBias bias=biases.analyze(symbol,available); evaluated++;
            DirectionalSetup setup;
            try{setup=setups.evaluateHistorical(history,bias);}catch(IllegalArgumentException insufficient){continue;}
            if(!setup.actionable()||setup.entryZone()==null||setup.takeProfitLevels().isEmpty())continue;
            Fill fill=findFill(base,index+1,Math.min(base.size(),index+9),setup.entryZone()); if(fill==null)continue;
            BigDecimal stop=setup.stopLoss(),target=setup.takeProfitLevels().getFirst(),risk=fill.price().subtract(stop).abs();
            if(risk.signum()==0)continue;
            Exit exit=resolve(base,fill.index(),Math.min(base.size(),fill.index()+81),setup.direction(),stop,target);
            if("AMBIGUOUS_STOP_ASSUMED".equals(exit.reason()))ambiguous++;
            BigDecimal gross="LONG".equals(setup.direction())?exit.price().subtract(fill.price()):fill.price().subtract(exit.price());
            BigDecimal costs=fill.price().multiply(BigDecimal.valueOf(0.0017),MC); // 7.5 bps per side + 2 bps slippage
            BigDecimal realizedR=gross.subtract(costs,MC).divide(risk,8,RoundingMode.HALF_UP);
            trades.add(new DirectionalSetupBacktestResult.Trade(symbol.toUpperCase(),timeframe,
                    setup.structureState(),
                    setup.direction(),setup.setupType(),decisionTime,base.get(fill.index()).openTime(),
                    base.get(exit.index()).closeTime(),fill.price(),stop,target,realizedR,exit.reason(),
                    setup.tradeQualityScore(),setup.takeProfitHitFirstProbability()));
            index=exit.index();
        }
        DirectionalSetupBacktestResult result=summarize(symbol,timeframe,base,evaluated,ambiguous,trades,started);
        jdbc.update("""
                INSERT INTO intelligence.directional_setup_backtest
                    (id,symbol,interval_name,started_at,completed_at,sample_start,sample_end,total_setups,payload)
                VALUES (?,?,?,?,?,?,?,?,?::jsonb)
                """,result.id(),result.symbol(),result.timeframe(),Timestamp.from(started),Timestamp.from(result.completedAt()),
                Timestamp.from(result.sampleStart()),Timestamp.from(result.sampleEnd()),result.totalSetups(),json(result));
        return result;
    }

    private DirectionalSetupBacktestResult summarize(String symbol,String timeframe,List<Candle> base,int evaluated,
                                                       int ambiguous,List<DirectionalSetupBacktestResult.Trade> trades,Instant started){
        int wins=(int)trades.stream().filter(t->t.realizedR().signum()>0).count(),losses=trades.size()-wins;
        Metrics all=metrics(trades); Map<String,List<DirectionalSetupBacktestResult.Trade>> groups=trades.stream()
                .collect(Collectors.groupingBy(DirectionalSetupBacktestResult.Trade::regime));
        List<DirectionalSetupBacktestResult.Breakdown> breakdown=groups.entrySet().stream().map(entry->{Metrics m=metrics(entry.getValue());
            return new DirectionalSetupBacktestResult.Breakdown(symbol.toUpperCase(),timeframe,entry.getKey(),entry.getValue().size(),m.winRate(),m.averageR(),m.averageR(),m.profitFactor(),m.drawdown());}).toList();
        List<String>warnings=new ArrayList<>();if(trades.size()<30)warnings.add("Fewer than 30 independent setups: results are exploratory, not profitability evidence");
        warnings.add("Signals use only candles closed at decision time; entries begin on subsequent candles");
        warnings.add("Same-candle stop/target collisions are conservatively counted as stops");
        warnings.add("Kline-derived volume profile is an approximation, not tick-level volume-at-price");
        return new DirectionalSetupBacktestResult(UUID.randomUUID(),symbol.toUpperCase(),timeframe,base.getFirst().openTime(),
                base.getLast().closeTime(),evaluated,trades.size(),wins,losses,ambiguous,all.winRate(),all.averageR(),
                all.averageR(),all.profitFactor(),all.drawdown(),breakdown,List.copyOf(trades),warnings,Instant.now());
    }

    private static Metrics metrics(List<DirectionalSetupBacktestResult.Trade> trades){
        if(trades.isEmpty())return new Metrics(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
        BigDecimal sum=BigDecimal.ZERO,gain=BigDecimal.ZERO,loss=BigDecimal.ZERO,equity=BigDecimal.ZERO,peak=BigDecimal.ZERO,dd=BigDecimal.ZERO;
        int wins=0;for(var trade:trades){BigDecimal r=trade.realizedR();sum=sum.add(r);if(r.signum()>0){gain=gain.add(r);wins++;}else loss=loss.add(r.abs());equity=equity.add(r);peak=peak.max(equity);dd=dd.max(peak.subtract(equity));}
        return new Metrics(BigDecimal.valueOf((double)wins/trades.size()),sum.divide(BigDecimal.valueOf(trades.size()),8,RoundingMode.HALF_UP),
                loss.signum()==0?gain:gain.divide(loss,8,RoundingMode.HALF_UP),dd);
    }
    private static Fill findFill(List<Candle> candles,int from,int to,DirectionalSetup.PriceZone zone){for(int i=from;i<to;i++){Candle c=candles.get(i);if(c.low().compareTo(zone.upper())<=0&&c.high().compareTo(zone.lower())>=0)return new Fill(i,zone.lower().add(zone.upper()).divide(BigDecimal.TWO,MC));}return null;}
    private static Exit resolve(List<Candle> candles,int from,int to,String direction,BigDecimal stop,BigDecimal target){for(int i=from;i<to;i++){Candle c=candles.get(i);boolean stopHit="LONG".equals(direction)?c.low().compareTo(stop)<=0:c.high().compareTo(stop)>=0;boolean targetHit="LONG".equals(direction)?c.high().compareTo(target)>=0:c.low().compareTo(target)<=0;if(stopHit&&targetHit)return new Exit(i,stop,"AMBIGUOUS_STOP_ASSUMED");if(stopHit)return new Exit(i,stop,"STOP");if(targetHit)return new Exit(i,target,"TARGET");}Candle last=candles.get(to-1);return new Exit(to-1,last.close(),"TIME_EXIT");}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalArgumentException("Unable to serialize setup backtest",e);}}
    private record Fill(int index,BigDecimal price){} private record Exit(int index,BigDecimal price,String reason){}
    private record Metrics(BigDecimal winRate,BigDecimal averageR,BigDecimal profitFactor,BigDecimal drawdown){}
}
