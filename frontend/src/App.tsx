import { useEffect, useMemo, useState } from 'react';
import { CandleChart } from './CandleChart';

type RiskPlan = {
  entry: number | null;
  stopLoss: number | null;
  takeProfit1: number | null;
  takeProfit2: number | null;
  riskReward: number;
  riskPercent: number;
  status: string;
};

type Analysis = {
  symbol: string;
  interval: string;
  decision: string;
  score: number;
  grade: string;
  confidence: number;
  indicators: Record<string, number>;
  risk: RiskPlan;
  reasons: string[];
};

type Candle = {
  openTime: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
};

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
const SYMBOL = 'BTCUSDT';
const INTERVAL = '1m';

export default function App() {
  const [analysis, setAnalysis] = useState<Analysis | null>(null);
  const [candles, setCandles] = useState<Candle[]>([]);
  const [status, setStatus] = useState('Connecting to market engine…');
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  useEffect(() => {
    const load = async () => {
      try {
        const [analysisResponse, candlesResponse] = await Promise.all([
          fetch(`${API_BASE}/api/v1/analysis/latest?symbol=${SYMBOL}&interval=${INTERVAL}`),
          fetch(`${API_BASE}/api/v1/candles?symbol=${SYMBOL}&interval=${INTERVAL}&limit=200`),
        ]);

        if (!candlesResponse.ok) throw new Error(`Candle API returned ${candlesResponse.status}`);
        const rawCandles = await candlesResponse.json();
        setCandles(rawCandles.map((c: Record<string, unknown>) => ({
          ...c,
          open: Number(c.open), high: Number(c.high), low: Number(c.low),
          close: Number(c.close), volume: Number(c.volume),
        })));

        if (analysisResponse.status === 204) {
          setAnalysis(null);
          setStatus('Collecting at least 35 validated candles…');
        } else {
          if (!analysisResponse.ok) throw new Error(`Analysis API returned ${analysisResponse.status}`);
          setAnalysis(await analysisResponse.json());
          setStatus('Live engine connected');
        }
        setLastUpdated(new Date());
      } catch (error) {
        setStatus(error instanceof Error ? error.message : 'Unable to reach backend');
      }
    };

    load();
    const timer = window.setInterval(load, 5_000);
    return () => window.clearInterval(timer);
  }, []);

  const metrics = useMemo(() => [
    ['Trend', analysis ? (analysis.indicators.price > analysis.indicators.ema20 ? 'Bullish' : 'Bearish') : '—'],
    ['VWAP', analysis?.indicators.vwap?.toFixed(2) ?? '—'],
    ['RSI 14', analysis?.indicators.rsi14?.toFixed(2) ?? '—'],
    ['MACD Hist.', analysis?.indicators.macdHistogram?.toFixed(4) ?? '—'],
    ['ATR %', analysis?.indicators.atrPercent?.toFixed(2) ?? '—'],
    ['Confidence', analysis ? `${analysis.confidence}%` : '—'],
  ], [analysis]);

  const risk = analysis?.risk;
  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">AEGIS AI</p>
          <h1>Trading Intelligence Command Center</h1>
          <p className="subtitle">Multi-factor confirmation, explainable decisions and capital-first risk control.</p>
        </div>
        <div className="connection-block">
          <span className="live">● {status}</span>
          <small>{lastUpdated ? `Updated ${lastUpdated.toLocaleTimeString()}` : 'Waiting for first update'}</small>
        </div>
      </header>

      <section className="hero-grid">
        <article className="panel chart-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">{SYMBOL} · {INTERVAL}</p>
              <h2>Live market structure</h2>
            </div>
            <div className="price-block">
              <strong>{analysis ? `$${analysis.indicators.price.toLocaleString()}` : '$—'}</strong>
              <span>{candles.length} candles loaded</span>
            </div>
          </div>
          <CandleChart candles={candles} />
        </article>

        <aside className="panel intelligence-panel">
          <div className="panel-heading compact">
            <div><p className="eyebrow">Decision Engine v2</p><h2>Market state</h2></div>
            <span className={`grade grade-${analysis?.grade?.replace('+', 'plus') ?? 'none'}`}>{analysis?.grade ?? '—'}</span>
          </div>
          <div className="metric-list">
            {metrics.map(([label, value]) => (
              <div className="metric" key={label}><span>{label}</span><strong>{value}</strong></div>
            ))}
          </div>
          <div className={`signal-card signal-${analysis?.decision?.toLowerCase() ?? 'wait'}`}>
            <span>Current decision · Score {analysis?.score ?? '—'}/100</span>
            <strong>{analysis?.decision ?? 'WAIT'}</strong>
            <p>{analysis?.reasons?.[0] ?? 'No trade is shown until the engine has enough validated market data.'}</p>
          </div>
        </aside>
      </section>

      <section className="lower-grid">
        <article className="panel">
          <p className="eyebrow">Risk Engine</p>
          <h2>Structured trade plan</h2>
          <div className="risk-grid">
            {[
              ['Entry', risk?.entry], ['Stop loss', risk?.stopLoss],
              ['Take profit 1', risk?.takeProfit1], ['Take profit 2', risk?.takeProfit2],
              ['Risk %', risk?.riskPercent], ['R:R', risk?.riskReward],
            ].map(([label, value]) => (
              <div className="risk-item" key={label as string}>
                <span>{label}</span>
                <strong>{value == null ? '—' : Number(value).toFixed(label === 'Risk %' ? 2 : 4)}</strong>
              </div>
            ))}
          </div>
          <div className={`risk-status risk-${risk?.status?.toLowerCase() ?? 'no_trade'}`}>
            {risk?.status ?? 'NO_TRADE'}
          </div>
        </article>

        <article className="panel">
          <p className="eyebrow">Explainability</p>
          <h2>Why the engine decided this</h2>
          <ol className="reason-list">
            {(analysis?.reasons ?? ['Waiting for sufficient live data.']).map((reason) => <li key={reason}>{reason}</li>)}
          </ol>
        </article>
      </section>

      <footer>Research and decision-support software only. No return is guaranteed; risk controls remain mandatory.</footer>
    </main>
  );
}
