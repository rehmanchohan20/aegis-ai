import { useEffect, useRef } from 'react';
import {
  CandlestickSeries,
  ColorType,
  createChart,
  createSeriesMarkers,
  HistogramSeries,
  LineSeries,
  type IChartApi,
  type UTCTimestamp,
} from 'lightweight-charts';

export type Candle = {
  openTime: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
};

export type TrendLine = {
  type: string;
  startTime: string;
  startPrice: number;
  endTime: string;
  endPrice: number;
  breakStatus: string;
  confirmedTouches: number;
  confidenceScore: number;
};

export type MarketStructure = {
  structureState: string;
  regime: string;
  supportPrice: number | null;
  resistancePrice: number | null;
  atrUpperBand: number | null;
  atrLowerBand: number | null;
  trendLines: TrendLine[];
};

export type DirectionalSetup = {
  status: string;
  direction: string;
  setupType: string;
  probabilitySource: string;
  expectedRMultiple: number;
  volumeState: string;
  confirmations: string[];
  rejections: string[];
  tradeQualityScore: number;
  takeProfitHitFirstProbability: number | null;
  fixedRangeProfile: {
    pointOfControl: number;
    valueAreaHigh: number;
    valueAreaLow: number;
    bins: Array<{ lower: number; upper: number; volumeFraction: number; nodeType: string }>;
    developingPoc: Array<{ time: string; price: number }>;
  };
  entryZone: { lower: number; upper: number; basis: string } | null;
  stopLoss: number | null;
  takeProfitLevels: number[];
  markers: Array<{ time: string; price: number; type: string; direction: string; label: string }>;
};

function time(value: string): UTCTimestamp {
  return Math.floor(new Date(value).getTime() / 1000) as UTCTimestamp;
}

function ema(candles: Candle[], period: number): Array<{ time: UTCTimestamp; value: number }> {
  const alpha = 2 / (period + 1);
  let current = candles[0]?.close ?? 0;
  return candles.map((candle) => {
    current = alpha * candle.close + (1 - alpha) * current;
    return { time: time(candle.openTime), value: current };
  });
}

function vwap(candles: Candle[]): Array<{ time: UTCTimestamp; value: number }> {
  let valueVolume = 0;
  let totalVolume = 0;
  return candles.map((candle) => {
    valueVolume += ((candle.high + candle.low + candle.close) / 3) * candle.volume;
    totalVolume += candle.volume;
    return { time: time(candle.openTime), value: totalVolume > 0 ? valueVolume / totalVolume : candle.close };
  });
}

export function CandleChart({ candles, symbol, structure, setup }: { candles: Candle[]; symbol: string; structure: MarketStructure | null; setup: DirectionalSetup | null }) {
  const container = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!container.current || candles.length === 0) return;
    const chart: IChartApi = createChart(container.current, {
      autoSize: true,
      height: 500,
      layout: { background: { type: ColorType.Solid, color: '#07111f' }, textColor: '#8298b8' },
      grid: { vertLines: { color: 'rgba(154,180,215,.07)' }, horzLines: { color: 'rgba(154,180,215,.1)' } },
      rightPriceScale: { borderColor: 'rgba(154,180,215,.18)' },
      timeScale: { borderColor: 'rgba(154,180,215,.18)', timeVisible: true, secondsVisible: false },
      crosshair: { vertLine: { color: '#466381' }, horzLine: { color: '#466381' } },
    });
    const price = chart.addSeries(CandlestickSeries, {
      upColor: '#55e6c1', downColor: '#ff6b7a', wickUpColor: '#55e6c1', wickDownColor: '#ff6b7a', borderVisible: false,
    });
    price.setData(candles.map((candle) => ({ time: time(candle.openTime), open: candle.open, high: candle.high, low: candle.low, close: candle.close })));
    const volume = chart.addSeries(HistogramSeries, { priceFormat: { type: 'volume' }, priceScaleId: 'volume' });
    volume.priceScale().applyOptions({ scaleMargins: { top: .82, bottom: 0 } });
    volume.setData(candles.map((candle) => ({ time: time(candle.openTime), value: candle.volume, color: candle.close >= candle.open ? 'rgba(85,230,193,.25)' : 'rgba(255,107,122,.25)' })));
    const addLine = (data: Array<{ time: UTCTimestamp; value: number }>, color: string, width: 1 | 2 = 1, style = 0) => {
      const series = chart.addSeries(LineSeries, { color, lineWidth: width, lineStyle: style, priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false });
      series.setData(data);
      return series;
    };
    addLine(ema(candles, 20), '#55a7ff', 2);
    addLine(ema(candles, 50), '#bb86fc', 2);
    addLine(vwap(candles), '#ffbe5c', 1);
    const first = time(candles[0].openTime);
    const last = time(candles[candles.length - 1].openTime);
    const horizontal = (value: number | null, color: string, style: number) => {
      if (value != null && Number.isFinite(value)) addLine([{ time: first, value }, { time: last, value }], color, 1, style);
    };
    horizontal(structure?.supportPrice ?? null, '#55e6c1', 2);
    horizontal(structure?.resistancePrice ?? null, '#ff6b7a', 2);
    horizontal(structure?.atrUpperBand ?? null, '#ffbe5c', 3);
    horizontal(structure?.atrLowerBand ?? null, '#ffbe5c', 3);
    horizontal(setup?.fixedRangeProfile.pointOfControl ?? null, '#f6c85f', 0);
    horizontal(setup?.fixedRangeProfile.valueAreaHigh ?? null, '#7d8ea6', 2);
    horizontal(setup?.fixedRangeProfile.valueAreaLow ?? null, '#7d8ea6', 2);
    horizontal(setup?.entryZone?.lower ?? null, '#3dd9b4', 3);
    horizontal(setup?.entryZone?.upper ?? null, '#3dd9b4', 3);
    horizontal(setup?.stopLoss ?? null, '#ff4567', 0);
    setup?.takeProfitLevels.forEach((target) => horizontal(target, '#25d07f', 3));
    if (setup?.fixedRangeProfile.developingPoc.length) addLine(setup.fixedRangeProfile.developingPoc.map((point) => ({ time: time(point.time), value: point.price })), '#f6c85f', 2);
    structure?.trendLines.slice(0, 8).forEach((line) => {
      const broken = line.breakStatus !== 'ACTIVE';
      addLine([
        { time: time(line.startTime), value: line.startPrice },
        { time: time(line.endTime), value: line.endPrice },
      ], broken ? '#60738e' : line.type === 'SUPPORT' ? '#55e6c1' : '#ff6b7a', line.confidenceScore >= .7 ? 2 : 1, broken ? 2 : 0);
    });
    if (setup?.markers.length) createSeriesMarkers(price, setup.markers.map((marker) => ({
      time: time(marker.time), position: marker.direction === 'SHORT' ? 'aboveBar' as const : 'belowBar' as const,
      color: marker.type.includes('RETEST') ? '#f6c85f' : marker.direction === 'SHORT' ? '#ff6b7a' : '#55e6c1',
      shape: marker.type.includes('BREAK')
        ? marker.direction === 'SHORT' ? 'arrowDown' as const : 'arrowUp' as const
        : 'circle' as const,
      text: marker.label,
    })));
    chart.timeScale().fitContent();
    const resize = new ResizeObserver(() => chart.applyOptions({ width: container.current?.clientWidth ?? 800 }));
    resize.observe(container.current);
    return () => { resize.disconnect(); chart.remove(); };
  }, [candles, structure, setup]);

  if (candles.length === 0) return <div className="chart-empty">Collecting validated candles for {symbol}...</div>;
  const maximumProfile = Math.max(...(setup?.fixedRangeProfile.bins.map((bin) => bin.volumeFraction) ?? [1]));
  return <div className="chart-wrap"><div className="chart-canvas" ref={container} aria-label={`${symbol} chart with directional setup and volume profile`} /><div className="volume-profile" aria-label="Fixed-range volume profile">{setup?.fixedRangeProfile.bins.map((bin) => <span key={`${bin.lower}-${bin.upper}`} className={`profile-bin ${bin.nodeType.toLowerCase()}`} style={{ width: `${Math.max(2, bin.volumeFraction / maximumProfile * 100)}%` }} title={`${bin.nodeType} ${bin.lower.toFixed(4)}-${bin.upper.toFixed(4)}`} />)}</div></div>;
}
