package ai.aegis.orchestration;

import ai.aegis.admission.TradeAdmissionDecision;
import ai.aegis.admission.TradeAdmissionRequest;
import ai.aegis.admission.TradeAdmissionService;
import ai.aegis.analysis.MarketAnalysis;
import ai.aegis.journal.TradeJournalEntry;
import ai.aegis.journal.TradeJournalStore;
import ai.aegis.market.Candle;
import ai.aegis.ml.MlPrediction;
import ai.aegis.ml.MlPredictionClient;
import ai.aegis.ml.PredictionAuditService;
import ai.aegis.paper.PaperTrade;
import ai.aegis.paper.PaperTradingService;
import ai.aegis.paper.PaperOrder;
import ai.aegis.paper.PaperOrderService;
import ai.aegis.market.MarketDataStateService;
import ai.aegis.market.MarketSnapshot;
import ai.aegis.analysis.MultiTimeframeAnalysisService;
import ai.aegis.analysis.MultiTimeframeDecision;
import ai.aegis.structure.MarketStructureService;
import ai.aegis.structure.MarketStructureSnapshot;
import ai.aegis.ranking.PairRankingService;
import ai.aegis.setup.DirectionalSetup;
import ai.aegis.setup.DirectionalSetupService;
import ai.aegis.risk.RiskEngine;
import ai.aegis.risk.RiskPlan;
import ai.aegis.supervisor.SupervisorDecision;
import ai.aegis.supervisor.SupervisorEngine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GuardedExecutionService {
    private final DecisionCycleService decisionCycleService;
    private final TradeAdmissionService admissionService;
    private final SupervisorEngine supervisorEngine;
    private final RiskEngine riskEngine;
    private final PaperTradingService paperTradingService;
    private final TradeJournalStore journalStore;
    private final MlPredictionClient mlPredictionClient;
    private final PredictionAuditService predictionAuditService;
    private final PaperOrderService paperOrderService;
    private final MarketDataStateService marketData;
    private final MultiTimeframeAnalysisService multiTimeframe;
    private final MarketStructureService marketStructure;
    private final PairRankingService pairRanking;
    private final DirectionalSetupService directionalSetups;

    public GuardedExecutionService(DecisionCycleService decisionCycleService,
                                   TradeAdmissionService admissionService,
                                   SupervisorEngine supervisorEngine,
                                   RiskEngine riskEngine,
                                   PaperTradingService paperTradingService,
                                   TradeJournalStore journalStore,
                                   MlPredictionClient mlPredictionClient,
                                   PredictionAuditService predictionAuditService,
                                   PaperOrderService paperOrderService,
                                   MarketDataStateService marketData,
                                   MultiTimeframeAnalysisService multiTimeframe,
                                   MarketStructureService marketStructure,
                                   PairRankingService pairRanking,
                                   DirectionalSetupService directionalSetups) {
        this.decisionCycleService = decisionCycleService;
        this.admissionService = admissionService;
        this.supervisorEngine = supervisorEngine;
        this.riskEngine = riskEngine;
        this.paperTradingService = paperTradingService;
        this.journalStore = journalStore;
        this.mlPredictionClient = mlPredictionClient;
        this.predictionAuditService = predictionAuditService;
        this.paperOrderService = paperOrderService;
        this.marketData = marketData;
        this.multiTimeframe = multiTimeframe;
        this.marketStructure = marketStructure;
        this.pairRanking = pairRanking;
        this.directionalSetups = directionalSetups;
    }

    public GuardedExecutionResult execute(GuardedExecutionRequest request) {
        DecisionCycleResult cycle = decisionCycleService.run(request.candles());
        List<String> reasons = new ArrayList<>(cycle.reasons());
        Candle latest = request.candles().stream().filter(Candle::closed).reduce((a, b) -> b)
                .orElseThrow(() -> new IllegalArgumentException("at least one closed candle is required"));
        BigDecimal entry = latest.close();
        MarketSnapshot liveMarket = marketData.latest(cycle.symbol());
        boolean marketContextApproved = liveMarket != null && "GOOD".equals(liveMarket.dataQuality());
        if (!marketContextApproved) reasons.add("Real-time market data is missing, stale, or degraded");
        if (liveMarket != null && liveMarket.spreadBps() != null
                && liveMarket.spreadBps().compareTo(BigDecimal.valueOf(20)) > 0) {
            marketContextApproved = false;
            reasons.add("Bid-ask spread exceeds 20 bps execution ceiling");
        }
        MultiTimeframeDecision timeframeContext = multiTimeframe.analyze(cycle.symbol());
        if (!"WAIT".equals(cycle.finalDirection())
                && (!timeframeContext.approved() || !cycle.finalDirection().equals(timeframeContext.decision()))) {
            marketContextApproved = false;
            reasons.addAll(timeframeContext.reasons());
        }
        PairRankingService.CorrelationContext correlation = pairRanking.correlationContext(cycle.symbol());
        if (!correlation.approved()) {
            marketContextApproved = false;
            reasons.add(correlation.reason());
        }
        MarketStructureSnapshot structure = marketStructure.latestOrCalculate(cycle.symbol(), cycle.interval());
        if (structure != null) {
            reasons.add("Market structure " + structure.structureState() + ", breakout " + structure.breakoutState());
            if ("TRANSITION_CHOCH_RISK".equals(structure.structureState())) {
                marketContextApproved = false;
                reasons.add("Change-of-character transition blocks new directional exposure");
            }
        }
        DirectionalSetup directionalSetup = directionalSetups.latest(cycle.symbol(), cycle.interval());
        if (!directionalSetup.actionable() || !cycle.finalDirection().equals(directionalSetup.direction())) {
            marketContextApproved = false;
            reasons.add("Directional setup engine blocks entry: " + directionalSetup.status()
                    + " / " + String.join(", ", directionalSetup.rejections()));
        } else {
            reasons.add("Directional setup approved " + directionalSetup.setupType() + " at "
                    + directionalSetup.expectedRMultiple() + "R with target-first probability "
                    + directionalSetup.takeProfitHitFirstProbability());
        }
        BigDecimal normalizedAtr = cycle.featureSnapshot().usableValue("atrNormalized14").orElse(BigDecimal.ZERO);
        BigDecimal atr = entry.multiply(normalizedAtr);
        RiskPlan riskPlan = riskEngine.build(cycle.finalDirection(), entry, atr);

        MlPrediction mlPrediction = null;
        UUID predictionId = null;
        String strategyId = cycle.candidateSignals().stream().filter(s -> s.eligible())
                .findFirst().map(s -> s.strategyId()).orElse(null);
        boolean mlApproved = true;
        try {
            mlPrediction = mlPredictionClient.predict(cycle.featureSnapshot());
            predictionId = predictionAuditService.record(cycle.symbol(), cycle.interval(), strategyId,
                    cycle.finalDirection(), mlPrediction, cycle.featureSnapshot(), entry,
                    riskPlan, structure, liveMarket);
            if ("HALTED".equals(mlPrediction.driftStatus()) || "DEGRADED".equals(mlPrediction.driftStatus())) {
                mlApproved = false;
                reasons.add("ML approval blocked by model drift status " + mlPrediction.driftStatus());
            } else if ("WAIT".equals(mlPrediction.decision())) {
                reasons.add("ML model is neutral; rules remain primary"
                        + (mlPrediction.uncertaintyStatus() == null ? ""
                        : " (uncertainty " + mlPrediction.uncertaintyStatus() + ")"));
            } else if (!mlPrediction.decision().equals(cycle.finalDirection())) {
                mlApproved = false;
                reasons.add("ML veto: model direction " + mlPrediction.decision()
                        + " conflicts with rules direction " + cycle.finalDirection());
            } else {
                reasons.add("ML confirmation: " + mlPrediction.model() + " agrees with " + cycle.finalDirection()
                        + " at confidence " + mlPrediction.confidence());
            }
        } catch (RuntimeException unavailable) {
            mlApproved = false;
            reasons.add("ML service, active artifact, or prediction audit unavailable; new trade approval fails closed");
        }

        if ("WAIT".equals(cycle.finalDirection()) || !"VALID".equals(riskPlan.status())) {
            reasons.add("Risk plan did not produce a tradable setup");
            SupervisorDecision veto = supervisorEngine.decide(cycle.featureSnapshot(), cycle.candidateSignals(),
                    false, request.strategyHealthy(), request.executionHealthy());
            return new GuardedExecutionResult(cycle, veto, riskPlan, BigDecimal.ZERO, null,
                    "REJECTED", reasons, Instant.now());
        }

        BigDecimal stopDistance = entry.subtract(riskPlan.stopLoss()).abs();
        BigDecimal riskBudget = request.accountBalance().multiply(request.riskPercent())
                .divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP);
        BigDecimal quantity = riskBudget.divide(stopDistance, 8, RoundingMode.DOWN);
        if (quantity.signum() <= 0) {
            reasons.add("Calculated quantity is zero");
            SupervisorDecision veto = supervisorEngine.decide(cycle.featureSnapshot(), cycle.candidateSignals(),
                    false, request.strategyHealthy(), request.executionHealthy());
            return new GuardedExecutionResult(cycle, veto, riskPlan, BigDecimal.ZERO, null,
                    "REJECTED", reasons, Instant.now());
        }

        Map<String, BigDecimal> indicators = new LinkedHashMap<>();
        indicators.put("price", entry);
        indicators.put("atr", atr);
        if (mlPrediction != null) {
            indicators.put("mlLongProbability", mlPrediction.longProbability());
            indicators.put("mlWaitProbability", mlPrediction.waitProbability());
            indicators.put("mlShortProbability", mlPrediction.shortProbability());
            indicators.put("mlConfidence", mlPrediction.confidence());
            if (mlPrediction.confidenceMargin() != null) indicators.put("mlConfidenceMargin", mlPrediction.confidenceMargin());
            if (mlPrediction.featureDriftScore() != null) indicators.put("mlFeatureDriftScore", mlPrediction.featureDriftScore());
            if (mlPrediction.entropy() != null) indicators.put("mlPredictionEntropy", mlPrediction.entropy());
        }
        cycle.featureSnapshot().features().forEach((name, feature) -> {
            if (feature.usable()) indicators.put(name, feature.value());
        });
        MarketAnalysis analysis = new MarketAnalysis(cycle.symbol(), cycle.interval(), cycle.finalDirection(),
                cycle.finalScore(), grade(cycle.finalScore()), BigDecimal.valueOf(cycle.finalScore()),
                Map.copyOf(indicators), riskPlan, List.copyOf(reasons), Instant.now());

        TradeAdmissionDecision admission = admissionService.evaluate(new TradeAdmissionRequest(
                analysis, request.candles(), request.accountBalance(), quantity,
                BigDecimal.valueOf(10), BigDecimal.valueOf(5), 2,
                paperTradingService.list().stream().filter(t -> "OPEN".equals(t.status())).toList().size(),
                BigDecimal.ZERO));
        reasons.addAll(admission.confirmations());
        reasons.addAll(admission.blockers());

        SupervisorDecision supervisor = supervisorEngine.decide(cycle.featureSnapshot(), cycle.candidateSignals(),
                request.riskApproved() && admission.approved() && mlApproved && marketContextApproved,
                request.strategyHealthy(), request.executionHealthy());
        reasons.addAll(supervisor.approvals());
        reasons.addAll(supervisor.vetoes());

        if (!"APPROVED".equals(supervisor.status()) || !request.executePaperTrade()) {
            String status = "APPROVED".equals(supervisor.status()) ? "APPROVED_NOT_EXECUTED" : "REJECTED";
            return new GuardedExecutionResult(cycle, supervisor, riskPlan, quantity, null,
                    status, reasons, Instant.now());
        }

        PaperOrder order = paperOrderService.submit(new PaperOrderService.OrderRequest(null, cycle.symbol(),
                cycle.interval(), supervisor.side(), "MARKET", quantity, null, null,
                Instant.now().plusSeconds(10)));
        if (order.filledQuantity().signum() <= 0 || order.averageFillPrice() == null) {
            reasons.add("Paper order did not fill: " + (order.rejectionReason() == null ? order.status() : order.rejectionReason()));
            return new GuardedExecutionResult(cycle, supervisor, riskPlan, quantity, null,
                    "PAPER_ORDER_NOT_FILLED", reasons, Instant.now());
        }
        BigDecimal fill = order.averageFillPrice();
        BigDecimal targetDistance = riskPlan.takeProfit1().subtract(riskPlan.entry()).abs();
        BigDecimal adjustedStop = "LONG".equals(supervisor.side()) ? fill.subtract(stopDistance) : fill.add(stopDistance);
        BigDecimal adjustedTarget = "LONG".equals(supervisor.side()) ? fill.add(targetDistance) : fill.subtract(targetDistance);
        PaperTrade trade = paperTradingService.open(cycle.symbol(), cycle.interval(), supervisor.side(),
                fill, adjustedStop, adjustedTarget, order.filledQuantity());
        trade = paperTradingService.applyExecutionCosts(trade.id(), fill, order.fee(), order.slippage());
        paperOrderService.linkTrade(order.id(), trade.id());
        Map<String, BigDecimal> probabilities = mlPrediction == null ? Map.of() : Map.of(
                "LONG", mlPrediction.longProbability(), "WAIT", mlPrediction.waitProbability(),
                "SHORT", mlPrediction.shortProbability());
        journalStore.save(new TradeJournalEntry(trade.id(), trade.symbol(), trade.interval(), trade.side(),
                trade.status(), trade.entryPrice(), trade.stopLoss(), trade.takeProfit(), trade.quantity(),
                trade.realizedPnl(), cycle.finalScore(), grade(cycle.finalScore()), String.join(" | ", reasons),
                trade.openedAt(), trade.closedAt(), strategyId, mlPrediction == null ? null : mlPrediction.model(),
                cycle.finalDirection(), mlPrediction == null ? null : mlPrediction.decision(), probabilities,
                predictionId, riskPlan, null, null));
        journalStore.updateExecutionCosts(trade.id(), fill, order.fee(), order.slippage(), order.firstFillAt());
        reasons.add("Paper trade opened from " + order.status() + " depth-aware order " + order.id()
                + " with fees " + order.fee() + " and slippage " + order.slippage());
        return new GuardedExecutionResult(cycle, supervisor, riskPlan, quantity, trade,
                "PAPER_TRADE_OPENED", reasons, Instant.now());
    }

    private String grade(int score) {
        return score >= 90 ? "A+" : score >= 80 ? "A" : score >= 70 ? "B" : score >= 55 ? "C" : "D";
    }
}
