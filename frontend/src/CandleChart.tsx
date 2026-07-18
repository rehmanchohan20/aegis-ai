import { useMemo } from 'react';

type Candle = {
  openTime: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
};

export function CandleChart({ candles }: { candles: Candle[] }) {
  const geometry = useMemo(() => {
    if (!candles.length) return null;
    const width = 900;
    const height = 420;
    const padding = 34;
    const visible = candles.slice(-80);
    const high = Math.max(...visible.map((c) => c.high));
    const low = Math.min(...visible.map((c) => c.low));
    const range = Math.max(high - low, 0.000001);
    const step = (width - padding * 2) / visible.length;
    const y = (price: number) => padding + ((high - price) / range) * (height - padding * 2);
    return { width, height, padding, visible, high, low, step, y };
  }, [candles]);

  if (!geometry) {
    return <div className="chart-empty">Collecting candles for the live chart…</div>;
  }

  const { width, height, padding, visible, high, low, step, y } = geometry;
  return (
    <div className="chart-wrap">
      <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="BTC candlestick chart">
        {[0, 1, 2, 3, 4].map((line) => {
          const lineY = padding + (line * (height - padding * 2)) / 4;
          const price = high - (line * (high - low)) / 4;
          return (
            <g key={line}>
              <line className="grid-line" x1={padding} x2={width - padding} y1={lineY} y2={lineY} />
              <text className="axis-label" x={width - padding + 4} y={lineY + 4}>{price.toFixed(2)}</text>
            </g>
          );
        })}
        {visible.map((candle, index) => {
          const x = padding + index * step + step / 2;
          const bullish = candle.close >= candle.open;
          const bodyTop = y(Math.max(candle.open, candle.close));
          const bodyBottom = y(Math.min(candle.open, candle.close));
          return (
            <g key={`${candle.openTime}-${index}`} className={bullish ? 'candle bullish' : 'candle bearish'}>
              <line x1={x} x2={x} y1={y(candle.high)} y2={y(candle.low)} />
              <rect x={x - Math.max(step * 0.28, 1)} y={bodyTop}
                    width={Math.max(step * 0.56, 2)} height={Math.max(bodyBottom - bodyTop, 1)} />
            </g>
          );
        })}
      </svg>
    </div>
  );
}
