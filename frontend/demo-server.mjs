import { createServer } from 'node:http';

const port = 8090;
const now = Date.now();
const candles = Array.from({ length: 180 }, (_, index) => {
  const trend = index * 3.1;
  const cycle = Math.sin(index / 8) * 135 + Math.sin(index / 23) * 70;
  const open = 63150 + trend + cycle;
  const close = open + Math.sin(index * 1.73) * 42;
  return {
    openTime: new Date(now - (180 - index) * 60_000).toISOString(),
    open: Number(open.toFixed(2)),
    high: Number((Math.max(open, close) + 32 + (index % 7) * 3).toFixed(2)),
    low: Number((Math.min(open, close) - 29 - (index % 5) * 4).toFixed(2)),
    close: Number(close.toFixed(2)),
    volume: Number((38 + Math.abs(Math.sin(index / 5)) * 52).toFixed(3)),
  };
});

const latestPrice = candles.at(-1).close;
const predictions = Array.from({ length: 12 }, (_, index) => ({
  id: `demo-prediction-${index + 1}`,
  model_version: 'aegis-direction:demo-20260719',
  rules_direction: index % 4 === 0 ? 'WAIT' : 'LONG',
  predicted_direction: index % 5 === 4 ? 'WAIT' : 'LONG',
  long_probability: index % 5 === 4 ? 0.41 : 0.69 + (index % 3) * 0.05,
  wait_probability: index % 5 === 4 ? 0.38 : 0.18,
  short_probability: index % 5 === 4 ? 0.21 : 0.13 - (index % 3) * 0.05,
  confidence: index % 5 === 4 ? 0.41 : 0.69 + (index % 3) * 0.05,
  confidence_margin: index % 5 === 4 ? 0.03 : 0.51 + (index % 3) * 0.05,
  feature_drift_score: 0.63,
  drift_status: 'HEALTHY',
  prediction_entropy: index % 5 === 4 ? 1.05 : 0.71,
  uncertainty_status: index % 5 === 4 ? 'HIGH' : 'LOW',
  prediction_set: index % 5 === 4 ? ['LONG', 'WAIT'] : ['LONG'],
  outcome_label: index < 3 ? null : index % 4 === 0 ? 0 : 1,
  correct: index < 3 ? null : index % 4 !== 0,
  prediction_time: new Date(now - index * 15 * 60_000).toISOString(),
}));

const analysis = {
  symbol: 'BTCUSDT', interval: '1m', decision: 'LONG', score: 0.73, grade: 'A-', confidence: 0.78,
  indicators: {
    price: latestPrice, realizedVolatility20: 0.0132, downsideDeviation20: 0.0074,
    returnSkewness20: 0.18, returnExcessKurtosis20: 0.64, trendTStatistic20: 2.91,
    parkinsonVolatility20: 0.0118, garmanKlassVolatility20: 0.0121, amihudIlliquidity20: 0.0000042,
  },
  risk: {
    entry: latestPrice, stopLoss: latestPrice * 0.989, takeProfit1: latestPrice * 1.016,
    takeProfit2: latestPrice * 1.028, riskReward: 2.35, riskPercent: 0.5, status: 'PAPER_READY',
  },
  reasons: ['Trend strength is statistically significant and the low-uncertainty ML result confirms the rules signal.'],
};

const dashboard = {
  decision: { finalDirection: 'LONG', finalScore: 0.73, reasons: analysis.reasons },
  paperTrades: [
    { id: 'demo-open-1', symbol: 'BTCUSDT', interval: '1m', side: 'LONG', entryPrice: latestPrice - 82, status: 'OPEN', realizedPnl: 0, openedAt: new Date(now - 42 * 60_000).toISOString(), closedAt: null },
    { id: 'demo-closed-1', symbol: 'BTCUSDT', interval: '5m', side: 'LONG', entryPrice: latestPrice - 410, status: 'TARGET_1', realizedPnl: 38.72, openedAt: new Date(now - 8 * 3_600_000).toISOString(), closedAt: new Date(now - 6 * 3_600_000).toISOString() },
    { id: 'demo-closed-2', symbol: 'ETHUSDT', interval: '15m', side: 'SHORT', entryPrice: 3512.4, status: 'STOPPED', realizedPnl: -11.48, openedAt: new Date(now - 30 * 3_600_000).toISOString(), closedAt: new Date(now - 27 * 3_600_000).toISOString() },
  ],
  openTrades: 1,
  predictionSummary: { total: 124, pending: 3, resolved: 121, correct: 78, average_confidence: 0.718, accuracy: 0.645, directional_precision: 0.681, calibration_error: 0.074 },
  recentPredictions: predictions,
  insights: {
    activeModel: { modelName: 'aegis-direction', version: 'demo-20260719', status: 'ACTIVE', metrics: { balancedAccuracy: 0.638, macroF1: 0.621 }, createdAt: new Date(now - 7 * 86_400_000).toISOString(), activatedAt: new Date(now - 2 * 86_400_000).toISOString() },
    strategies: [
      { strategyId: 'trend-confirmation-v2', trades: 31, netPnl: 184.42, averagePnl: 5.95, winRate: 64.5 },
      { strategyId: 'mean-reversion-v1', trades: 22, netPnl: 47.18, averagePnl: 2.14, winRate: 54.5 },
    ],
    models: [
      { modelVersion: 'aegis-direction:demo-20260719', trades: 38, netPnl: 201.63, averagePnl: 5.31, winRate: 63.2 },
      { modelVersion: 'rules-only', trades: 15, netPnl: 29.97, averagePnl: 2.0, winRate: 53.3 },
    ],
    modelHealth: { status: 'HEALTHY', sample_size: 121, accuracy: 0.645, directional_precision: 0.681, calibration_error: 0.074, feature_drift_score: 0.63, unresolved_prediction_count: 3, class_distribution_shift: 0.083, feature_population_stability_index: 0.091 },
    labelledExamples: 1840, unlabelledExamples: 46,
  },
  safety: { liveExecutionEnabled: false, killSwitchEngaged: true, executionMode: 'PAPER_ONLY · DEMO DATA' },
  generatedAt: new Date(now).toISOString(),
};

function send(response, status, body) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  response.end(JSON.stringify(body));
}

createServer((request, response) => {
  const url = new URL(request.url ?? '/', `http://${request.headers.host}`);
  if (url.pathname === '/health') return send(response, 200, { status: 'UP', mode: 'SAFE_DEMO' });
  if (url.pathname === '/api/v1/analysis/latest') return send(response, 200, analysis);
  if (url.pathname === '/api/v1/candles') return send(response, 200, candles);
  if (url.pathname === '/api/v1/dashboard') return send(response, 200, dashboard);
  if (url.pathname === '/api/v1/predictions') return send(response, 200, predictions);
  if (url.pathname === '/api/v1/predictions/summary') return send(response, 200, dashboard.predictionSummary);
  return send(response, 404, { error: 'Demo route not found' });
}).listen(port, '127.0.0.1', () => {
  console.log(`AEGIS safe demo API listening on http://127.0.0.1:${port}`);
});
