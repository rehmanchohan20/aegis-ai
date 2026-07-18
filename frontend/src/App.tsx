const metrics = [
  ['Trend', 'Bullish'],
  ['Momentum', 'Strong'],
  ['Liquidity', 'Watching'],
  ['Confidence', '72%'],
];

export default function App() {
  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">AEGIS AI</p>
          <h1>Trading Intelligence Dashboard</h1>
        </div>
        <span className="live">● LIVE FOUNDATION</span>
      </header>

      <section className="hero-grid">
        <article className="panel chart-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">BTCUSDT · 1m</p>
              <h2>Market workspace</h2>
            </div>
            <strong>$—</strong>
          </div>
          <div className="chart-placeholder">
            Live Binance chart and WebSocket ingestion land next in Sprint 1.
          </div>
        </article>

        <aside className="panel intelligence-panel">
          <p className="eyebrow">Decision Engine v0</p>
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
            <span>Current signal</span>
            <strong>WAIT</strong>
            <p>No fake recommendation is emitted until enough validated market data exists.</p>
          </div>
        </aside>
      </section>
    </main>
  );
}
