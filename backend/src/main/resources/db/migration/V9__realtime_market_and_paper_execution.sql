CREATE TABLE IF NOT EXISTS market.market_snapshot (
    symbol VARCHAR(30) NOT NULL,
    snapshot_time TIMESTAMPTZ NOT NULL,
    exchange_time TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    last_price NUMERIC(30, 12) NOT NULL,
    bid_price NUMERIC(30, 12),
    ask_price NUMERIC(30, 12),
    spread_bps NUMERIC(18, 8),
    weighted_mid_price NUMERIC(30, 12),
    bid_depth NUMERIC(38, 12),
    ask_depth NUMERIC(38, 12),
    order_book_imbalance NUMERIC(18, 8),
    aggressive_buy_volume NUMERIC(38, 12) NOT NULL DEFAULT 0,
    aggressive_sell_volume NUMERIC(38, 12) NOT NULL DEFAULT 0,
    cumulative_volume_delta NUMERIC(38, 12) NOT NULL DEFAULT 0,
    trade_count INTEGER NOT NULL DEFAULT 0,
    trade_intensity NUMERIC(18, 8) NOT NULL DEFAULT 0,
    average_trade_size NUMERIC(30, 12) NOT NULL DEFAULT 0,
    realized_volatility NUMERIC(18, 10) NOT NULL DEFAULT 0,
    volume_acceleration NUMERIC(18, 8) NOT NULL DEFAULT 0,
    ingestion_latency_ms BIGINT NOT NULL,
    connection_status VARCHAR(24) NOT NULL,
    data_quality VARCHAR(24) NOT NULL,
    feature_status JSONB NOT NULL,
    last_trade_id BIGINT,
    last_depth_update_id BIGINT,
    PRIMARY KEY (symbol, snapshot_time)
);

SELECT create_hypertable('market.market_snapshot', 'snapshot_time', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_market_snapshot_latest
    ON market.market_snapshot(symbol, snapshot_time DESC);

CREATE TABLE IF NOT EXISTS market.market_structure_snapshot (
    id UUID PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL,
    interval_name VARCHAR(10) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    structure_state VARCHAR(40) NOT NULL,
    regime VARCHAR(40) NOT NULL,
    support_price NUMERIC(30, 12),
    resistance_price NUMERIC(30, 12),
    atr NUMERIC(30, 12),
    atr_upper_band NUMERIC(30, 12),
    atr_lower_band NUMERIC(30, 12),
    consolidation BOOLEAN NOT NULL,
    breakout_state VARCHAR(40) NOT NULL,
    pivots JSONB NOT NULL,
    trend_lines JSONB NOT NULL,
    zones JSONB NOT NULL,
    markers JSONB NOT NULL,
    warnings JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_market_structure_latest
    ON market.market_structure_snapshot(symbol, interval_name, calculated_at DESC);

CREATE TABLE IF NOT EXISTS trading.paper_order (
    id UUID PRIMARY KEY,
    client_order_id VARCHAR(120) NOT NULL UNIQUE,
    trade_id UUID,
    symbol VARCHAR(30) NOT NULL,
    interval_name VARCHAR(10) NOT NULL,
    side VARCHAR(10) NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    requested_quantity NUMERIC(30, 12) NOT NULL,
    filled_quantity NUMERIC(30, 12) NOT NULL DEFAULT 0,
    limit_price NUMERIC(30, 12),
    stop_price NUMERIC(30, 12),
    average_fill_price NUMERIC(30, 12),
    slippage NUMERIC(30, 12) NOT NULL DEFAULT 0,
    fee NUMERIC(30, 12) NOT NULL DEFAULT 0,
    rejection_reason VARCHAR(160),
    submitted_at TIMESTAMPTZ NOT NULL,
    first_fill_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_paper_order_working
    ON trading.paper_order(symbol, submitted_at)
    WHERE status IN ('NEW', 'PARTIALLY_FILLED', 'TRIGGERED');

CREATE TABLE IF NOT EXISTS trading.paper_fill (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES trading.paper_order(id),
    quantity NUMERIC(30, 12) NOT NULL,
    price NUMERIC(30, 12) NOT NULL,
    fee NUMERIC(30, 12) NOT NULL,
    liquidity VARCHAR(12) NOT NULL,
    market_snapshot_time TIMESTAMPTZ,
    filled_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_paper_fill_order
    ON trading.paper_fill(order_id, filled_at);

ALTER TABLE trading.trade_journal
    ADD COLUMN IF NOT EXISTS entry_signal_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS order_submitted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS fill_time TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS average_fill_price NUMERIC(30, 12),
    ADD COLUMN IF NOT EXISTS exit_price NUMERIC(30, 12),
    ADD COLUMN IF NOT EXISTS fees NUMERIC(30, 12) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS slippage NUMERIC(30, 12) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS unrealized_pnl NUMERIC(30, 12) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS maximum_favorable_excursion NUMERIC(30, 12) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS maximum_adverse_excursion NUMERIC(30, 12) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS prediction_snapshot JSONB,
    ADD COLUMN IF NOT EXISTS trend_context JSONB,
    ADD COLUMN IF NOT EXISTS order_flow_context JSONB;

ALTER TABLE intelligence.prediction_history
    ADD COLUMN IF NOT EXISTS final_approved_direction VARCHAR(10),
    ADD COLUMN IF NOT EXISTS expected_return NUMERIC(24, 12),
    ADD COLUMN IF NOT EXISTS expected_volatility NUMERIC(24, 12),
    ADD COLUMN IF NOT EXISTS trade_quality_score NUMERIC(12, 8),
    ADD COLUMN IF NOT EXISTS stop_hit_probability NUMERIC(12, 8),
    ADD COLUMN IF NOT EXISTS target_hit_probability NUMERIC(12, 8),
    ADD COLUMN IF NOT EXISTS evaluation_horizon_seconds INTEGER,
    ADD COLUMN IF NOT EXISTS take_profit_hit_first BOOLEAN,
    ADD COLUMN IF NOT EXISTS stop_loss_hit_first BOOLEAN,
    ADD COLUMN IF NOT EXISTS market_regime VARCHAR(40),
    ADD COLUMN IF NOT EXISTS trend_context JSONB,
    ADD COLUMN IF NOT EXISTS order_flow_context JSONB;

CREATE INDEX IF NOT EXISTS idx_prediction_history_dimensions
    ON intelligence.prediction_history(symbol, interval_name, prediction_time DESC, model_version);
