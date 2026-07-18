CREATE TABLE IF NOT EXISTS intelligence.prediction_history (
    id UUID PRIMARY KEY,
    symbol VARCHAR(40) NOT NULL,
    interval_name VARCHAR(20) NOT NULL,
    strategy_id VARCHAR(120),
    model_version VARCHAR(120) NOT NULL,
    rules_direction VARCHAR(10) NOT NULL,
    predicted_direction VARCHAR(10) NOT NULL,
    long_probability NUMERIC(12,8) NOT NULL,
    wait_probability NUMERIC(12,8) NOT NULL DEFAULT 0,
    short_probability NUMERIC(12,8) NOT NULL,
    confidence NUMERIC(12,8) NOT NULL,
    feature_snapshot JSONB NOT NULL,
    prediction_time TIMESTAMPTZ NOT NULL,
    outcome_label INTEGER,
    forward_return NUMERIC(24,12),
    correct BOOLEAN,
    resolved_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_prediction_history_pending
    ON intelligence.prediction_history(symbol, interval_name, prediction_time)
    WHERE resolved_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_prediction_history_model
    ON intelligence.prediction_history(model_version, prediction_time DESC);

CREATE TABLE IF NOT EXISTS intelligence.model_drift_snapshot (
    id UUID PRIMARY KEY,
    model_version VARCHAR(120) NOT NULL,
    symbol VARCHAR(40) NOT NULL,
    interval_name VARCHAR(20) NOT NULL,
    sample_size INTEGER NOT NULL,
    accuracy NUMERIC(12,8),
    directional_precision NUMERIC(12,8),
    average_confidence NUMERIC(12,8),
    calibration_error NUMERIC(12,8),
    feature_drift_score NUMERIC(12,8),
    status VARCHAR(30) NOT NULL,
    reasons JSONB NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_model_drift_latest
    ON intelligence.model_drift_snapshot(model_version, calculated_at DESC);