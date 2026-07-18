package ai.aegis.orchestration;

import ai.aegis.admission.TradeAdmissionDecision;
import ai.aegis.admission.TradeAdmissionRequest;
import ai.aegis.admission.TradeAdmissionService;
import ai.aegis.analysis.MarketAnalysis;
import ai.aegis.journal.TradeJournalEntry;
import ai.aegis.journal.TradeJournalStore;
import ai.aegis.market.Candle;
import ai.aegis.paper.PaperTrade;
import ai.aegis.paper.PaperTradingService;
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

@Service
public class GuardedExecutionService {
    private final DecisionCycleService decisionCycleService;
    private final TradeAdmissionService admissionService;
    private final SupervisorEngine supervisorEngine;
    private final RiskEngine riskEngine;
    private final PaperTradingService paperTradingService;
    private final TradeJournalStore journalStore;

    public GuardedExecutionService(DecisionCycleService decisionCycleService,
                                   TradeAdmissionService admissionService,
                                   SupervisorEngine supervisorEngine,
                                   RiskEngine riskEngine,
                                   PaperTradingService paperTradingService,
                                   TradeJournalStore journalStore) {
        this.decisionCycleService = decisionCycleService;
        this.admissionService = admissionService;
        this.supervisorEngine = supervisorEngine;
        this.riskEngine = riskEngine;
        this.paperTradingService = paperTradingService;
        this.journalStore = journalStore;
    }

    public GuardedExecutionResult execute(GuardedExecutionRequest request) {
        DecisionCycleResult cycle = decisionCycleService.run(request.candles());
        List<String> reasons = new ArrayList<>(cycle.reasons());
        Candle latest = request.candles().stream().filter(Candle::closed).reduce((a, b) -> b)
                .orElseThrow(() -> new IllegalArgumentException("at least one closed candle is required"));
        BigDecimal entry = latest.close();
        BigDecimal normalizedAtr = cycle.featureSnapshot().usableValue("atrNormalized14").orElse(BigDecimal.ZERO);
        BigDecimal atr = entry.multiply(normalizedAtr);
        RiskPlan riskPlan = riskEngine.build(cycle.finalDirection(), entry, atr);

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
        cycle.featureSnapshot().features().forEach((name, feature) -> {
            if (feature.usable()) indicators.put(name, feature.value());
        });
        MarketAnalysis analysis = new MarketAnalysis(cycle.symbol(), cycle.interval(), cycle.finalDirection(),
                cycle.finalScore(), grade(cycle.finalScore()), BigDecimal.valueOf(cycle.finalScore()),
                Map.copyOf(indicators), riskPlan, cycle.reasons(), Instant.now());

        TradeAdmissionDecision admission = admissionService.evaluate(new TradeAdmissionRequest(
                analysis, request.candles(), request.accountBalance(), quantity,
                BigDecimal.valueOf(10), BigDecimal.valueOf(5), 2,
                paperTradingService.list().stream().filter(t -> "OPEN".equals(t.status())).toList().size(),
                BigDecimal.ZERO));
        reasons.addAll(admission.confirmations());
        reasons.addAll(admission.blockers());

        SupervisorDecision supervisor = supervisorEngine.decide(cycle.featureSnapshot(), cycle.candidateSignals(),
                request.riskApproved() && admission.approved(), request.strategyHealthy(), request.executionHealthy());
        reasons.addAll(supervisor.approvals());
        reasons.addAll(supervisor.vetoes());

        if (!"APPROVED".equals(supervisor.status()) || !request.executePaperTrade()) {
            String status = "APPROVED".equals(supervisor.status()) ? "APPROVED_NOT_EXECUTED" : "REJECTED";
            return new GuardedExecutionResult(cycle, supervisor, riskPlan, quantity, null,
                    status, reasons, Instant.now());
        }

        PaperTrade trade = paperTradingService.open(cycle.symbol(), cycle.interval(), supervisor.side(),
                riskPlan.entry(), riskPlan.stopLoss(), riskPlan.takeProfit1(), quantity);
        journalStore.save(new TradeJournalEntry(trade.id(), trade.symbol(), trade.interval(), trade.side(),
                trade.status(), trade.entryPrice(), trade.stopLoss(), trade.takeProfit(), trade.quantity(),
                trade.realizedPnl(), cycle.finalScore(), grade(cycle.finalScore()), String.join(" | ", reasons),
                trade.openedAt(), trade.closedAt()));
        reasons.add("Paper trade opened and journaled: " + trade.id());
        return new GuardedExecutionResult(cycle, supervisor, riskPlan, quantity, trade,
                "PAPER_TRADE_OPENED", reasons, Instant.now());
    }

    private String grade(int score) {
        return score >= 90 ? "A+" : score >= 80 ? "A" : score >= 70 ? "B" : score >= 55 ? "C" : "D";
    }
}
