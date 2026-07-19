import { useEffect, useMemo, useState } from 'react';
import { CandleChart } from './CandleChart';

type RiskPlan = { entry: number | null; stopLoss: number | null; takeProfit1: number | null; takeProfit2: number | null; riskReward: number; riskPercent: number; status: string };
type Analysis = { symbol: string; interval: string; decision: string; score: number; grade: string; confidence: number; indicators: Record<string, number>; risk: RiskPlan; reasons: string[] };
type Candle = { openTime: string; open: number; high: number; low: number; close: number; volume: number };
type PaperTrade = { id: string; symbol: string; interval: string; side: string; entryPrice: number; status: string; realizedPnl: number; openedAt: string; closedAt: string | null };
type ModelVersion = { modelName: string; version: string; status: string; metrics: Record<string, unknown>; createdAt: string; activatedAt: string | null };
type Performance = { strategyId?: string; modelVersion?: string; trades: number; netPnl: number; averagePnl: number; winRate: number };
type Insights = { activeModel: ModelVersion | null; strategies: Performance[]; models: Performance[]; modelHealth: Record<string, unknown>; labelledExamples: number; unlabelledExamples: number };
type Prediction = { id: string; model_version: string; rules_direction: string; predicted_direction: string; long_probability: number; wait_probability: number; short_probability: number; confidence: number; confidence_margin: number | null; feature_drift_score: number | null; drift_status: string | null; prediction_entropy: number | null; uncertainty_status: string | null; prediction_set: string[] | null; outcome_label: number | null; correct: boolean | null; prediction_time: string };
type PredictionSummary = { total: number; pending: number; resolved: number; correct: number; average_confidence: number; accuracy: number; directional_precision: number; calibration_error: number };
type Dashboard = { decision: { finalDirection: string; finalScore: number; reasons: string[] } | null; paperTrades: PaperTrade[]; openTrades: number; predictionSummary: PredictionSummary; recentPredictions: Prediction[]; insights: Insights; safety: { liveExecutionEnabled: boolean; killSwitchEngaged: boolean; executionMode: string }; generatedAt: string };

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';
const API_KEY = import.meta.env.VITE_AEGIS_API_KEY ?? 'local-viewer-change-me';
const SYMBOL = 'BTCUSDT';
const INTERVAL = '1m';

async function fetchJson<T>(path: string): Promise<T | null> {
  const response = await fetch(`${API_BASE}${path}`, { headers: { 'X-AEGIS-API-KEY': API_KEY, Accept: 'application/json' } });
  if (response.status === 204) return null;
  const body = await response.text();
  if (!response.ok) throw new Error(`${path} returned ${response.status}${body ? `: ${body.slice(0, 180)}` : ''}`);
  if (!body.trim()) return null;
  try { return JSON.parse(body) as T; }
  catch { throw new Error(`${path} returned invalid JSON`); }
}

function percentage(value: number | null | undefined): string { return `${(Number(value ?? 0) * 100).toFixed(1)}%`; }
function pnl(value: number | null | undefined): string { const amount = Number(value ?? 0); return `${amount >= 0 ? '+' : ''}${amount.toFixed(2)}`; }

export default function App() {
  const [analysis, setAnalysis] = useState<Analysis | null>(null);
  const [candles, setCandles] = useState<Candle[]>([]);
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const [nextAnalysis, rawCandles, nextDashboard] = await Promise.all([
          fetchJson<Analysis>(`/api/v1/analysis/latest?symbol=${SYMBOL}&interval=${INTERVAL}`),
          fetchJson<Array<Record<string, unknown>>>(`/api/v1/candles?symbol=${SYMBOL}&interval=${INTERVAL}&limit=200`),
          fetchJson<Dashboard>('/api/v1/dashboard'),
        ]);
        if (!active) return;
        setAnalysis(nextAnalysis);
        setCandles((rawCandles ?? []).map((item) => ({ openTime: String(item.openTime), open: Number(item.open), high: Number(item.high), low: Number(item.low), close: Number(item.close), volume: Number(item.volume) })));
        setDashboard(nextDashboard); setError(null); setLastUpdated(new Date());
      } catch (caught) { if (active) setError(caught instanceof Error ? caught.message : 'Unable to reach the AEGIS API'); }
      finally { if (active) setLoading(false); }
    };
    void load();
    const timer = window.setInterval(() => void load(), 10_000);
    return () => { active = false; window.clearInterval(timer); };
  }, []);

  const latestPrediction = dashboard?.recentPredictions[0] ?? null;
  const summary = dashboard?.predictionSummary;
  const healthStatus = String(dashboard?.insights.modelHealth.status ?? latestPrediction?.drift_status ?? 'WATCH');
  const classDistributionShift = Number(dashboard?.insights.modelHealth.class_distribution_shift ?? 0);
  const populationStabilityIndex = Number(dashboard?.insights.modelHealth.feature_population_stability_index ?? 0);
  const trades = dashboard?.paperTrades ?? [];
  const openTrades = trades.filter((trade) => trade.status === 'OPEN');
  const closedTrades = trades.filter((trade) => trade.status !== 'OPEN');
  const metrics = useMemo(() => [
    ['Rules decision', dashboard?.decision?.finalDirection ?? analysis?.decision ?? 'WAIT'],
    ['Signal score', String(dashboard?.decision?.finalScore ?? analysis?.score ?? '—')],
    ['Active model', dashboard?.insights.activeModel?.version ?? 'No active model'],
    ['Recent accuracy', percentage(summary?.accuracy)],
    ['Directional precision', percentage(summary?.directional_precision)],
    ['Pending outcomes', String(summary?.pending ?? 0)],
  ], [analysis, dashboard, summary]);

  return <main className="shell">
    <header className="topbar"><div><p className="eyebrow">AEGIS AI</p><h1>Guarded Research & Paper Trading</h1><p className="subtitle">Explainable rules, drift-aware ML confirmation, auditable outcomes, and fail-closed execution.</p></div><div className="connection-block"><span className={`live ${error ? 'state-error' : ''}`}>● {loading ? 'Loading' : error ? 'API degraded' : 'Local stack connected'}</span><small>{lastUpdated ? `Updated ${lastUpdated.toLocaleTimeString()}` : 'Waiting for first update'}</small></div></header>
    {error && <section className="error-banner" role="alert"><strong>API error</strong><span>{error}</span></section>}
    <section className="status-strip"><div><span>Execution mode</span><strong>{dashboard?.safety.executionMode ?? 'PAPER_ONLY'}</strong></div><div><span>Kill switch</span><strong className="safe">{(dashboard?.safety.killSwitchEngaged ?? true) ? 'ENGAGED' : 'RELEASED'}</strong></div><div><span>Model health</span><strong className={`health-${healthStatus.toLowerCase()}`}>{healthStatus}</strong></div><div><span>Open positions</span><strong>{dashboard?.openTrades ?? 0}</strong></div></section>
    <section className="hero-grid"><article className="panel chart-panel"><div className="panel-heading"><div><p className="eyebrow">{SYMBOL} · {INTERVAL}</p><h2>Validated candle structure</h2></div><div className="price-block"><strong>{analysis ? `$${Number(analysis.indicators.price).toLocaleString()}` : '$—'}</strong><span>{candles.length} candles</span></div></div><CandleChart candles={candles} /></article><aside className="panel intelligence-panel"><div className="panel-heading compact"><div><p className="eyebrow">Unified decision</p><h2>Rules + ML</h2></div><span className={`grade grade-${(dashboard?.decision?.finalDirection ?? 'WAIT').toLowerCase()}`}>{dashboard?.decision?.finalDirection ?? 'WAIT'}</span></div><div className="metric-list">{metrics.map(([label, value]) => <div className="metric" key={label}><span>{label}</span><strong>{value}</strong></div>)}</div><p className="decision-reason">{dashboard?.decision?.reasons?.[0] ?? analysis?.reasons?.[0] ?? 'Waiting for enough validated market data.'}</p></aside></section>
    <section className="lower-grid"><article className="panel"><p className="eyebrow">Latest prediction</p><h2>LONG / WAIT / SHORT</h2><div className="probability-bars">{([['LONG', latestPrediction?.long_probability], ['WAIT', latestPrediction?.wait_probability], ['SHORT', latestPrediction?.short_probability]] as const).map(([label, value]) => <div key={label}><span>{label}<b>{percentage(value)}</b></span><progress max="1" value={Number(value ?? 0)} /></div>)}</div><div className="metric-list compact-list"><div className="metric"><span>ML decision</span><strong>{latestPrediction?.predicted_direction ?? 'WAIT'}</strong></div><div className="metric"><span>Confidence margin</span><strong>{percentage(latestPrediction?.confidence_margin)}</strong></div><div className="metric"><span>Prediction entropy</span><strong>{Number(latestPrediction?.prediction_entropy ?? 0).toFixed(3)}</strong></div><div className="metric"><span>Conformal set</span><strong>{latestPrediction?.prediction_set?.join(' / ') ?? '—'}</strong></div><div className="metric"><span>Uncertainty</span><strong>{latestPrediction?.uncertainty_status ?? '—'}</strong></div><div className="metric"><span>Feature drift score</span><strong>{Number(latestPrediction?.feature_drift_score ?? 0).toFixed(3)}</strong></div><div className="metric"><span>Calibration error</span><strong>{percentage(summary?.calibration_error)}</strong></div></div></article><article className="panel"><p className="eyebrow">Risk plan</p><h2>Capital-first controls</h2><div className="risk-grid">{[['Entry', analysis?.risk.entry], ['Stop', analysis?.risk.stopLoss], ['Target 1', analysis?.risk.takeProfit1], ['Target 2', analysis?.risk.takeProfit2], ['Risk %', analysis?.risk.riskPercent], ['R:R', analysis?.risk.riskReward]].map(([label, value]) => <div className="risk-item" key={label as string}><span>{label}</span><strong>{value == null ? '—' : Number(value).toFixed(3)}</strong></div>)}</div><div className="safety-note">Live orders remain blocked unless every guarded execution prerequisite is explicitly satisfied.</div></article></section>
    <section className="lower-grid"><article className="panel"><p className="eyebrow">Statistical stability</p><h2>Distribution monitoring</h2><div className="metric-list"><div className="metric"><span>Class distribution shift</span><strong>{classDistributionShift.toFixed(3)}</strong></div><div className="metric"><span>Population stability index</span><strong>{populationStabilityIndex.toFixed(3)}</strong></div><div className="metric"><span>Recent sample size</span><strong>{String(dashboard?.insights.modelHealth.sample_size ?? 0)}</strong></div></div></article><article className="panel"><p className="eyebrow">Outcome maintenance</p><h2>Prediction evidence</h2><div className="metric-list"><div className="metric"><span>Resolved outcomes</span><strong>{summary?.resolved ?? 0}</strong></div><div className="metric"><span>Pending outcomes</span><strong>{summary?.pending ?? 0}</strong></div><div className="metric"><span>Average confidence</span><strong>{percentage(summary?.average_confidence)}</strong></div></div></article></section>
    <section className="lower-grid"><Attribution title="Strategy P&L attribution" rows={dashboard?.insights.strategies ?? []} label={(row) => row.strategyId ?? 'unknown'} /><Attribution title="Model-version P&L attribution" rows={dashboard?.insights.models ?? []} label={(row) => row.modelVersion ?? 'rules-only'} /></section>
    <section className="lower-grid"><TradeList title="Open paper positions" trades={openTrades} /><TradeList title="Closed paper trades" trades={closedTrades} /></section>
    <footer>Research and paper-trading software only. Real-money execution is disabled by default and remains fail-closed.</footer>
  </main>;
}

function Attribution({ title, rows, label }: { title: string; rows: Performance[]; label: (row: Performance) => string }) {
  return <article className="panel"><p className="eyebrow">Attribution</p><h2>{title}</h2><div className="metric-list">{rows.slice(0, 8).map((row) => <div className="metric" key={label(row)}><span>{label(row)} · {row.trades} trades · {Number(row.winRate).toFixed(1)}% win</span><strong className={Number(row.netPnl) >= 0 ? 'positive' : 'negative'}>{pnl(row.netPnl)}</strong></div>)}{rows.length === 0 && <div className="empty-state">No closed attributed trades yet.</div>}</div></article>;
}

function TradeList({ title, trades }: { title: string; trades: PaperTrade[] }) {
  return <article className="panel"><p className="eyebrow">Paper ledger</p><h2>{title}</h2><div className="trade-list">{trades.slice(0, 8).map((trade) => <div key={trade.id}><span><b>{trade.symbol}</b> {trade.side} · {trade.status}</span><strong className={Number(trade.realizedPnl) >= 0 ? 'positive' : 'negative'}>{pnl(trade.realizedPnl)}</strong></div>)}{trades.length === 0 && <div className="empty-state">No trades in this state.</div>}</div></article>;
}
