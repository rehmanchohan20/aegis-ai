import { useEffect, useState } from 'react';

type Analysis = {
  symbol: string;
  interval: string;
  decision: string;
  score: number;
  grade: string;
  confidence: number;
  indicators: Record<string, number>;
  reasons: string[];
};

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export default function App() {
  const [analysis, setAnalysis] = useState<Analysis | null>(null);
  const [status, setStatus] = useState('Waiting for at least 21 live candles…');

  useEffect(() => {
    const load = async () => {
      try {
        const response = await fetch(`${API_BASE}/api/v1/analysis/latest?symbol=BTCUSDT&interval=1m`);
        if (response.status === 204) {
          setStatus('Collecting live Binance candles…');
          return;
        }
        if (!response.ok) throw new Error(`API returned ${response.status}`);
        setAnalysis(await response.json());
        setStatus('Live analysis connected');
      } catch (error) {
        setStatus(error instanceof Error ? error.message : 'Unable to reach backend');
      }
    };

    load();
    const timer = window.setInterval(load, 10_000);
    return () => window.clearInterval(timer);
  }, []);

  const metrics = [
    ['Trend', analysis ? (analysis.indicators.price > analysis.indicators.ema20 ? 'Bullish' : 'Bearish') : '—'],
    ['RSI 14', analysis?.indicators.rsi14?.toFixed(2) ?? '—'],
    ['ATR %', analysis?.indicators.atrPercent?.toFixed(2) ?? '—'],
    ['Confidence', analysis ? `${analysis.confidence}%` : '—'],
  ];

  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">AEGIS AI</p>
          <h1>Trading Intelligence Dashboard</h1>
        </div>
        <span className="live">● {status}</span>
      </header>

      <section className="hero-grid">
        <article className="panel chart-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">BTCUSDT · 1m</p>
              <h2>Market workspace</h2>
            </div>
            <strong>{analysis ? `$${analysis.indicators.price.toLocaleString()}` : '$—'}</strong>
          </div>
          <div className="chart-placeholder">
            Live Binance ingestion is active. Interactive candlestick rendering is the next frontend block.
          </div>
        </article>

        <aside className="panel intelligence-panel">
          <p className="eyebrow">Decision Engine v1</p>
          <h2>Market state</h2>
          <div className="metric-list">
            {metrics.map(([label, value]) => (
              <div className="metric" key={label}>
                <span>{label}</span>
                <strong>{value}</strong>
              </div>
            ))}
          </div>
          <div className="signal-card">
            <span>Current signal · Grade {analysis?.grade ?? '—'}</span>
            <strong>{analysis?.decision ?? 'WAIT'}</strong>
            <p>{analysis?.reasons?.[0] ?? 'No recommendation is emitted until enough validated market data exists.'}</p>
          </div>
        </aside>
      </section>
    </main>
  );
}