CREATE SCHEMA IF NOT EXISTS intelligence;

CREATE TABLE IF NOT EXISTS intelligence.strategy_attribution (
    id UUID PRIMARY KEY,
    strategy_id VARCHAR(120) NOT NULL,
    symbol VARCHAR(40) NOT NULL,
    interval_name VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,
    score INTEGER NOT NULL,
    eligible BOOLEAN NOT NULL,
    feature_snapshot JSONB NOT NULL,
    confirmations JSONB NOT NULL,
    rejections JSONB NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_strategy_attribution_lookup
    ON intelligence.strategy_attribution(strategy_id, symbol, interval_name, observed_at DESC);

CREATE TABLE IF NOT EXISTS intelligence.ml_examples (
    id UUID PRIMARY KEY,
    symbol VARCHAR(40) NOT NULL,
    interval_name VARCHAR(20) NOT NULL,
    strategy_id VARCHAR(120) NOT NULL,
    features JSONB NOT NULL,
    label INTEGER,
    forward_return NUMERIC(24, 12),
    created_at TIMESTAMPTZ NOT NULL,
    labelled_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ml_examples_unlabelled
    ON intelligence.ml_examples(symbol, interval_name, created_at)
    WHERE label IS NULL;
