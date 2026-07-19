CREATE SCHEMA IF NOT EXISTS market;

CREATE TABLE IF NOT EXISTS market.candles (
    symbol VARCHAR(30) NOT NULL,
    interval_name VARCHAR(10) NOT NULL,
    open_time TIMESTAMPTZ NOT NULL,
    close_time TIMESTAMPTZ NOT NULL,
    open NUMERIC(30, 12) NOT NULL,
    high NUMERIC(30, 12) NOT NULL,
    low NUMERIC(30, 12) NOT NULL,
    close NUMERIC(30, 12) NOT NULL,
    volume NUMERIC(38, 12) NOT NULL,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (symbol, interval_name, open_time)
);

SELECT create_hypertable('market.candles', 'open_time', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_market_candles_closed_lookup
    ON market.candles(symbol, interval_name, close_time)
    WHERE is_closed = TRUE;

ALTER TABLE intelligence.prediction_history
    ADD COLUMN IF NOT EXISTS starting_price NUMERIC(30, 12),
    ADD COLUMN IF NOT EXISTS confidence_margin NUMERIC(12, 8),
    ADD COLUMN IF NOT EXISTS feature_drift_score NUMERIC(12, 8),
    ADD COLUMN IF NOT EXISTS drift_status VARCHAR(30);

-- V5 clients expressed confidence as margin percentage; normalize legacy rows to [0,1].
UPDATE intelligence.prediction_history
SET confidence = confidence / 100.0
WHERE confidence > 1.0;

CREATE INDEX IF NOT EXISTS idx_prediction_history_resolution_due
    ON intelligence.prediction_history(prediction_time, symbol, interval_name)
    WHERE resolved_at IS NULL;

ALTER TABLE intelligence.model_drift_snapshot
    ADD COLUMN IF NOT EXISTS class_distribution_shift NUMERIC(12, 8),
    ADD COLUMN IF NOT EXISTS unresolved_prediction_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE trading.trade_journal
    ADD COLUMN IF NOT EXISTS strategy_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS model_version VARCHAR(120),
    ADD COLUMN IF NOT EXISTS rules_direction VARCHAR(10),
    ADD COLUMN IF NOT EXISTS ml_direction VARCHAR(10),
    ADD COLUMN IF NOT EXISTS ml_probabilities JSONB,
    ADD COLUMN IF NOT EXISTS feature_snapshot_ref UUID,
    ADD COLUMN IF NOT EXISTS risk_plan JSONB,
    ADD COLUMN IF NOT EXISTS closure_reason VARCHAR(80),
    ADD COLUMN IF NOT EXISTS market_regime VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_trade_journal_attribution
    ON trading.trade_journal(strategy_id, model_version, symbol, interval_name, opened_at DESC);

ALTER TABLE intelligence.strategy_pnl_attribution
    ADD COLUMN IF NOT EXISTS side VARCHAR(10),
    ADD COLUMN IF NOT EXISTS market_regime VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_strategy_pnl_model_window
    ON intelligence.strategy_pnl_attribution(model_version, closed_at DESC);
