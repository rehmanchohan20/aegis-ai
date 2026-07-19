CREATE TABLE IF NOT EXISTS intelligence.directional_setup_snapshot (
    id UUID PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL,
    interval_name VARCHAR(10) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(40) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    setup_type VARCHAR(40),
    trade_quality_score NUMERIC(12, 6),
    expected_r_multiple NUMERIC(18, 8),
    target_hit_probability NUMERIC(12, 8),
    stop_hit_probability NUMERIC(12, 8),
    payload JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_directional_setup_latest
    ON intelligence.directional_setup_snapshot(symbol, interval_name, evaluated_at DESC);

CREATE INDEX IF NOT EXISTS idx_directional_setup_actionable
    ON intelligence.directional_setup_snapshot(evaluated_at DESC)
    WHERE status = 'APPROVED_SETUP';

CREATE TABLE IF NOT EXISTS intelligence.directional_setup_backtest (
    id UUID PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL,
    interval_name VARCHAR(10) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    sample_start TIMESTAMPTZ,
    sample_end TIMESTAMPTZ,
    total_setups INTEGER NOT NULL,
    payload JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_directional_backtest_latest
    ON intelligence.directional_setup_backtest(symbol, interval_name, completed_at DESC);
