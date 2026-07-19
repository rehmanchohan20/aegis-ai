import { useEffect, useState } from 'react';
import { CandleChart, type Candle, type DirectionalSetup, type MarketStructure } from './CandleChart';

type RiskPlan = { entry: number | null; stopLoss: number | null; takeProfit1: number | null; takeProfit2: number | null; riskReward: number; riskPercent: number; status: string };
type Analysis = { symbol: string; interval: string; decision: string; score: number; grade: string; confidence: number; indicators: Record<string, number>; risk: RiskPlan; reasons: string[] };
type PaperTrade = { id: string; symbol: string; interval: string; side: string; entryPrice: number; averageFillPrice?: number; status: string; realizedPnl: number; unrealizedPnl?: number; fees?: number; openedAt: string; closedAt: string | null };
type ModelVersion = { modelName: string; version: string; status: string; metrics: Record<string, unknown>; createdAt: string; activatedAt: string | null };
type Performance = { strategyId?: string; modelVersion?: string; trades: number; netPnl: number; averagePnl: number; winRate: number };
type Insights = { activeModel: ModelVersion | null; strategies: Performance[]; models: Performance[]; modelHealth: Record<string, unknown>; labelledExamples: number; unlabelledExamples: number };
type Prediction = { id: string; model_version: string; rules_direction: string; predicted_direction: string; long_probability: number; wait_probability: number; short_probability: number; confidence: number; confidence_margin: number | null; feature_drift_score: number | null; drift_status: string | null; prediction_entropy: number | null; uncertainty_status: string | null; prediction_set: string[] | null; expected_return?: number | null; expected_volatility?: number | null; trade_quality_score?: number | null; outcome_label: number | null; correct: boolean | null; prediction_time: string };
type PredictionSummary = { total: number; pending: number; resolved: number; correct: number; average_confidence: number; accuracy: number; directional_precision: number; calibration_error: number };
type Dashboard = { decision: { finalDirection: string; finalScore: number; reasons: string[] } | null; paperTrades: PaperTrade[]; openTrades: number; predictionSummary: PredictionSummary; recentPredictions: Prediction[]; insights: Insights; safety: { liveExecutionEnabled: boolean; killSwitchEngaged: boolean; executionMode: string }; generatedAt: string };
type MarketCatalog = { symbols: string[]; intervals: string[] };
type LiveMarket = { symbol: string; snapshotTime: string; exchangeTime: string; receivedAt: string; lastPrice: number; bidPrice: number; askPrice: number; spreadBps: number; weightedMidPrice: number; bidDepth: number; askDepth: number; orderBookImbalance: number; aggressiveBuyVolume: number; aggressiveSellVolume: number; cumulativeVolumeDelta: number; tradeCount: number; tradeIntensity: number; averageTradeSize: number; realizedVolatility: number; volumeAcceleration: number; ingestionLatencyMs: number; connectionStatus: string; dataQuality: string; featureStatus: Record<string, string> };
type PairScore = { symbol: string; tradeQualityScore: number; shortTermReturn: number; btcRelativeReturn: number; btcCorrelation: number; spreadBps: number | null; orderBookImbalance: number | null; dataQuality: string; warnings: string[] };
type Ranking = { pairs: PairScore[]; broadMarketRegime: string; btcReturn: number; calculatedAt: string };

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '';
const API_KEY = import.meta.env.VITE_AEGIS_API_KEY ?? 'local-viewer-change-me';
const DEFAULT_SYMBOLS = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT', 'XRPUSDT', 'ADAUSDT', 'DOGEUSDT', 'LINKUSDT'];

async function fetchJson<T>(path: string, signal?: AbortSignal): Promise<T | null> {
  const response = await fetch(`${API_BASE}${path}`, { signal, headers: { 'X-AEGIS-API-KEY': API_KEY, Accept: 'application/json' } });
  if (response.status === 204) return null;
  const body = await response.text();
  if (!response.ok) throw new Error(`${path} returned ${response.status}${body ? `: ${body.slice(0, 180)}` : ''}`);
  if (!body.trim()) return null;
  try { return JSON.parse(body) as T; } catch { throw new Error(`${path} returned invalid JSON`); }
}

async function streamSnapshots(symbol: string, signal: AbortSignal, onSnapshot: (value: LiveMarket) => void): Promise<void> {
  const response = await fetch(`${API_BASE}/api/v1/market-data/stream?symbol=${encodeURIComponent(symbol)}`, {
    signal, headers: { 'X-AEGIS-API-KEY': API_KEY, Accept: 'text/event-stream' },
  });
  if (!response.ok || !response.body) throw new Error(`Live stream returned ${response.status}`);
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (!signal.aborted) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() ?? '';
    for (const block of blocks) {
      const data = block.split(/\r?\n/).filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trim()).join('');
      if (data) onSnapshot(JSON.parse(data) as LiveMarket);
    }
  }
}

const percentage = (value: number | null | undefined): string => `${(Number(value ?? 0) * 100).toFixed(1)}%`;
const money = (value: number | null | undefined): string => Number(value ?? 0).toLocaleString(undefined, { maximumFractionDigits: 4 });
const pnl = (value: number | null | undefined): string => `${Number(value ?? 0) >= 0 ? '+' : ''}${Number(value ?? 0).toFixed(2)}`;

export default function App() {
  const [analysis, setAnalysis] = useState<Analysis | null>(null);
  const [candles, setCandles] = useState<Candle[]>([]);
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [structure, setStructure] = useState<MarketStructure | null>(null);
  const [directionalSetup, setDirectionalSetup] = useState<DirectionalSetup | null>(null);
  const [ranking, setRanking] = useState<Ranking | null>(null);
  const [liveMarket, setLiveMarket] = useState<LiveMarket | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [catalog, setCatalog] = useState<MarketCatalog>({ symbols: DEFAULT_SYMBOLS, intervals: ['1m', '3m', '5m', '15m', '1h', '4h'] });
  const [symbol, setSymbol] = useState('BTCUSDT');
  const [interval, setInterval] = useState('1m');

  useEffect(() => { void fetchJson<MarketCatalog>('/api/v1/markets').then((value) => value && setCatalog(value)).catch(() => undefined); }, []);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const load = async () => {
      try {
        const [nextAnalysis, rawCandles, nextDashboard, nextStructure, nextRanking, snapshot, nextSetup] = await Promise.all([
          fetchJson<Analysis>(`/api/v1/analysis/latest?symbol=${encodeURIComponent(symbol)}&interval=${encodeURIComponent(interval)}`, controller.signal),
          fetchJson<Array<Record<string, unknown>>>(`/api/v1/candles?symbol=${encodeURIComponent(symbol)}&interval=${encodeURIComponent(interval)}&limit=500`, controller.signal),
          fetchJson<Dashboard>(`/api/v1/dashboard?symbol=${encodeURIComponent(symbol)}&interval=${encodeURIComponent(interval)}`, controller.signal),
          fetchJson<MarketStructure>(`/api/v1/market-structure?symbol=${encodeURIComponent(symbol)}&interval=${encodeURIComponent(interval)}`, controller.signal),
          fetchJson<Ranking>('/api/v1/pair-ranking', controller.signal),
          fetchJson<LiveMarket>(`/api/v1/market-data/latest?symbol=${encodeURIComponent(symbol)}`, controller.signal),
          fetchJson<DirectionalSetup>(`/api/v1/directional-setups?symbol=${encodeURIComponent(symbol)}&timeframe=${encodeURIComponent(interval)}`, controller.signal),
        ]);
        if (!active) return;
        setAnalysis(nextAnalysis);
        setCandles((rawCandles ?? []).map((item) => ({ openTime: String(item.openTime), open: Number(item.open), high: Number(item.high), low: Number(item.low), close: Number(item.close), volume: Number(item.volume) })));
        setDashboard(nextDashboard); setStructure(nextStructure); setRanking(nextRanking); setLiveMarket(snapshot); setDirectionalSetup(nextSetup);
        setError(null); setLastUpdated(new Date());
      } catch (caught) { if (active && !(caught instanceof DOMException && caught.name === 'AbortError')) setError(caught instanceof Error ? caught.message : 'Unable to reach the AEGIS API'); }
      finally { if (active) setLoading(false); }
    };
    void load();
    const timer = window.setInterval(() => void load(), 15_000);
    void streamSnapshots(symbol, controller.signal, (snapshot) => {
      if (!active) return;
      setLiveMarket(snapshot); setLastUpdated(new Date());
      setCandles((current) => current.map((candle, index) => index === current.length - 1 ? { ...candle, close: snapshot.lastPrice, high: Math.max(candle.high, snapshot.lastPrice), low: Math.min(candle.low, snapshot.lastPrice) } : candle));
    }).catch((caught: unknown) => { if (active && !(caught instanceof DOMException && caught.name === 'AbortError')) setError(caught instanceof Error ? caught.message : 'Live stream disconnected'); });
    return () => { active = false; controller.abort(); window.clearInterval(timer); };
  }, [symbol, interval]);

  const prediction = dashboard?.recentPredictions[0] ?? null;
  const summary = dashboard?.predictionSummary;
  const health = String(dashboard?.insights.modelHealth.status ?? prediction?.drift_status ?? 'WATCH');
  const trades = dashboard?.paperTrades ?? [];
  const quality = ranking?.pairs.find((pair) => pair.symbol === symbol);
  const openTrades = trades.filter((trade) => trade.status === 'OPEN');
  const closedTrades = trades.filter((trade) => trade.status !== 'OPEN');
  const dataAge = liveMarket && lastUpdated
    ? Math.max(0, (lastUpdated.getTime() - new Date(liveMarket.receivedAt).getTime()) / 1000) : null;
  return <main className="shell">
    <header className="topbar"><div><p className="eyebrow">AEGIS AI · Quant command center</p><h1>Real-time guarded paper trading</h1><p className="subtitle">Exchange streams, multi-timeframe structure, calibrated ML, realistic fills, and evidence-based model health.</p></div><div className="connection-block"><span className={`live ${error ? 'state-error' : ''}`}>● {loading ? 'Loading' : error ? 'Stream degraded' : liveMarket?.connectionStatus ?? 'Connected'}</span><small>{lastUpdated ? `Updated ${lastUpdated.toLocaleTimeString()} · ${dataAge?.toFixed(1) ?? '—'}s old` : 'Waiting for first snapshot'}</small></div></header>
    {error && <section className="error-banner" role="alert"><strong>Data warning</strong><span>{error}</span></section>}
    <section className="market-toolbar"><div><label htmlFor="market-symbol">Market</label><select id="market-symbol" value={symbol} onChange={(event) => setSymbol(event.target.value)}>{catalog.symbols.map((value) => <option key={value}>{value}</option>)}</select></div><div><label htmlFor="market-interval">Timeframe</label><select id="market-interval" value={interval} onChange={(event) => setInterval(event.target.value)}>{catalog.intervals.map((value) => <option key={value}>{value}</option>)}</select></div><p><strong>{catalog.symbols.length} live markets · {ranking?.broadMarketRegime ?? 'MIXED'}</strong><span>Binance WebSocket · one-second snapshots · paper execution only</span></p></section>
    <section className="status-strip"><Status label="Live price" value={`$${money(liveMarket?.lastPrice)}`} /><Status label="Spread" value={`${Number(liveMarket?.spreadBps ?? 0).toFixed(2)} bps`} /><Status label="Data quality" value={liveMarket?.dataQuality ?? 'MISSING'} className={liveMarket?.dataQuality === 'GOOD' ? 'safe' : 'health-watch'} /><Status label="Model health" value={health} className={`health-${health.toLowerCase()}`} /><Status label="Execution" value={dashboard?.safety.executionMode ?? 'PAPER_ONLY'} /><Status label="Kill switch" value={(dashboard?.safety.killSwitchEngaged ?? true) ? 'ENGAGED' : 'RELEASED'} className="safe" /></section>
    <section className="hero-grid"><article className="panel chart-panel"><div className="panel-heading"><div><p className="eyebrow">{symbol} · {interval} · {structure?.structureState ?? 'BUILDING'}</p><h2>Directional setup & volume profile</h2></div><div className="price-block"><strong>${money(liveMarket?.lastPrice ?? analysis?.indicators.price)}</strong><span>{structure?.regime ?? ranking?.broadMarketRegime ?? 'UNKNOWN'} · {candles.length} candles</span></div></div><CandleChart candles={candles} symbol={symbol} structure={structure} setup={directionalSetup} /><div className="chart-legend"><span className="ema20">EMA 20</span><span className="ema50">EMA 50</span><span className="vwap">VWAP</span><span>POC / VAH / VAL</span><span>Entry / stop / targets</span></div></article><aside className="panel intelligence-panel"><div className="panel-heading compact"><div><p className="eyebrow">Selective setup engine</p><h2>{directionalSetup?.setupType?.replaceAll('_', ' ') ?? 'Searching for setup'}</h2></div><span className={`grade grade-${(directionalSetup?.direction ?? 'WAIT').toLowerCase()}`}>{directionalSetup?.direction ?? 'WAIT'}</span></div><div className="metric-list"><Metric label="Setup status" value={directionalSetup?.status ?? 'SEARCHING'} /><Metric label="Higher-timeframe bias" value={directionalSetup?.direction ?? 'WAIT'} /><Metric label="Trade quality" value={`${Number(directionalSetup?.tradeQualityScore ?? 0).toFixed(1)} / 100`} /><Metric label="Expected reward" value={`${Number(directionalSetup?.expectedRMultiple ?? 0).toFixed(2)}R`} /><Metric label="TP before SL" value={percentage(directionalSetup?.takeProfitHitFirstProbability)} /><Metric label="Probability source" value={directionalSetup?.probabilitySource ?? 'UNAVAILABLE'} /></div><p className="decision-reason">{directionalSetup?.confirmations?.[0] ?? directionalSetup?.rejections?.[0] ?? 'Waiting for higher-timeframe alignment and a high-quality pullback or retest.'}</p></aside></section>
    <section className="setup-strip"><Metric label="POC" value={`$${money(directionalSetup?.fixedRangeProfile.pointOfControl)}`} /><Metric label="VAH" value={`$${money(directionalSetup?.fixedRangeProfile.valueAreaHigh)}`} /><Metric label="VAL" value={`$${money(directionalSetup?.fixedRangeProfile.valueAreaLow)}`} /><Metric label="Volume state" value={directionalSetup?.volumeState?.replaceAll('_', ' ') ?? '—'} /><Metric label="Entry zone" value={directionalSetup?.entryZone ? `$${money(directionalSetup.entryZone.lower)} – $${money(directionalSetup.entryZone.upper)}` : '—'} /><Metric label="Stop / TP1" value={directionalSetup?.stopLoss == null ? '—' : directionalSetup.takeProfitLevels.length > 0 ? `$${money(directionalSetup.stopLoss)} / $${money(directionalSetup.takeProfitLevels[0])}` : `$${money(directionalSetup.stopLoss)} / no 2.5R target`} /></section>
    <section className="micro-grid"><Metric label="Order-book imbalance" value={Number(liveMarket?.orderBookImbalance ?? 0).toFixed(3)} /><Metric label="Weighted mid" value={`$${money(liveMarket?.weightedMidPrice)}`} /><Metric label="Buy / sell flow" value={`${money(liveMarket?.aggressiveBuyVolume)} / ${money(liveMarket?.aggressiveSellVolume)}`} /><Metric label="Cumulative delta" value={money(liveMarket?.cumulativeVolumeDelta)} /><Metric label="Trade intensity" value={`${Number(liveMarket?.tradeIntensity ?? 0).toFixed(2)}/s`} /><Metric label="Ingestion latency" value={`${liveMarket?.ingestionLatencyMs ?? 0} ms`} /></section>
    <section className="lower-grid"><article className="panel"><p className="eyebrow">Calibrated prediction</p><h2>LONG / WAIT / SHORT</h2><div className="probability-bars">{([['LONG', prediction?.long_probability], ['WAIT', prediction?.wait_probability], ['SHORT', prediction?.short_probability]] as const).map(([label, value]) => <div key={label}><span>{label}<b>{percentage(value)}</b></span><progress max="1" value={Number(value ?? 0)} /></div>)}</div><div className="metric-list compact-list"><Metric label="Expected return" value={percentage(prediction?.expected_return)} /><Metric label="Expected volatility" value={percentage(prediction?.expected_volatility)} /><Metric label="Confidence margin" value={percentage(prediction?.confidence_margin)} /><Metric label="Feature drift" value={Number(prediction?.feature_drift_score ?? 0).toFixed(3)} /><Metric label="Calibration error" value={percentage(summary?.calibration_error)} /></div></article><article className="panel"><p className="eyebrow">Structure context</p><h2>Price geometry</h2><div className="metric-list"><Metric label="Support" value={`$${money(structure?.supportPrice)}`} /><Metric label="Resistance" value={`$${money(structure?.resistancePrice)}`} /><Metric label="Validated trend lines" value={String(structure?.trendLines?.length ?? 0)} /><Metric label="Order-flow quality" value={quality?.dataQuality ?? 'MISSING'} /><Metric label="BTC correlation" value={Number(quality?.btcCorrelation ?? 0).toFixed(3)} /></div></article></section>
    <PairTable ranking={ranking} selected={symbol} onSelect={setSymbol} />
    <section className="lower-grid"><Attribution title="Strategy P&L attribution" rows={dashboard?.insights.strategies ?? []} label={(row) => row.strategyId ?? 'unknown'} /><Attribution title="Model-version P&L attribution" rows={dashboard?.insights.models ?? []} label={(row) => row.modelVersion ?? 'rules-only'} /></section>
    <section className="lower-grid"><TradeList title="Open paper positions" trades={openTrades} /><TradeList title="Closed paper trades" trades={closedTrades} /></section>
    <footer>Quantitative research and guarded paper trading only. Public exchange data is consumed, but real-money order placement remains disabled and fail-closed.</footer>
  </main>;
}

function Status({ label, value, className = '' }: { label: string; value: string; className?: string }) { return <div><span>{label}</span><strong className={className}>{value}</strong></div>; }
function Metric({ label, value }: { label: string; value: string }) { return <div className="metric"><span>{label}</span><strong>{value}</strong></div>; }
function PairTable({ ranking, selected, onSelect }: { ranking: Ranking | null; selected: string; onSelect: (symbol: string) => void }) { return <article className="panel pair-panel"><div className="panel-heading"><div><p className="eyebrow">Cross-market scanner</p><h2>Pair ranking & correlation context</h2></div><span>{ranking?.broadMarketRegime ?? 'MIXED'}</span></div><div className="pair-table"><div className="pair-row pair-header"><span>Pair</span><span>Quality</span><span>1h return</span><span>BTC relative</span><span>Spread</span><span>Book pressure</span></div>{ranking?.pairs.map((pair) => <button className={`pair-row ${selected === pair.symbol ? 'selected' : ''}`} key={pair.symbol} onClick={() => onSelect(pair.symbol)}><b>{pair.symbol}</b><span>{Number(pair.tradeQualityScore).toFixed(1)}</span><span className={pair.shortTermReturn >= 0 ? 'positive' : 'negative'}>{percentage(pair.shortTermReturn)}</span><span>{percentage(pair.btcRelativeReturn)}</span><span>{pair.spreadBps == null ? '—' : `${Number(pair.spreadBps).toFixed(2)} bps`}</span><span>{pair.orderBookImbalance == null ? '—' : Number(pair.orderBookImbalance).toFixed(3)}</span></button>)}</div></article>; }
function Attribution({ title, rows, label }: { title: string; rows: Performance[]; label: (row: Performance) => string }) { return <article className="panel"><p className="eyebrow">Attribution</p><h2>{title}</h2><div className="metric-list">{rows.slice(0, 8).map((row) => <div className="metric" key={label(row)}><span>{label(row)} · {row.trades} trades · {Number(row.winRate).toFixed(1)}% win</span><strong className={Number(row.netPnl) >= 0 ? 'positive' : 'negative'}>{pnl(row.netPnl)}</strong></div>)}{rows.length === 0 && <div className="empty-state">No closed attributed trades yet.</div>}</div></article>; }
function TradeList({ title, trades }: { title: string; trades: PaperTrade[] }) { return <article className="panel"><p className="eyebrow">Execution ledger</p><h2>{title}</h2><div className="trade-list">{trades.slice(0, 8).map((trade) => <div key={trade.id}><span><b>{trade.symbol}</b> {trade.side} · {trade.status}<small>fill ${money(trade.averageFillPrice ?? trade.entryPrice)} · fees {money(trade.fees)}</small></span><strong className={Number(trade.status === 'OPEN' ? trade.unrealizedPnl : trade.realizedPnl) >= 0 ? 'positive' : 'negative'}>{pnl(trade.status === 'OPEN' ? trade.unrealizedPnl : trade.realizedPnl)}</strong></div>)}{trades.length === 0 && <div className="empty-state">No trades in this state.</div>}</div></article>; }
