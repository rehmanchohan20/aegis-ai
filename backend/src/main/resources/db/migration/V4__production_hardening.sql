CREATE SCHEMA IF NOT EXISTS execution;

CREATE TABLE IF NOT EXISTS intelligence.model_registry (
    id UUID PRIMARY KEY,
    model_name VARCHAR(120) NOT NULL,
    version VARCHAR(80) NOT NULL,
    status VARCHAR(30) NOT NULL,
    metrics JSONB NOT NULL,
    artifact_uri TEXT NOT NULL,
    feature_names JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    UNIQUE(model_name, version)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_model_per_name
    ON intelligence.model_registry(model_name)
    WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS intelligence.deployment_approval (
    id UUID PRIMARY KEY,
    model_id UUID NOT NULL REFERENCES intelligence.model_registry(id),
    walk_forward_efficiency NUMERIC(12,6) NOT NULL,
    out_of_sample_sharpe NUMERIC(12,6) NOT NULL,
    maximum_drawdown NUMERIC(12,6) NOT NULL,
    approved BOOLEAN NOT NULL,
    reasons JSONB NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS intelligence.strategy_pnl_attribution (
    trade_id UUID PRIMARY KEY,
    strategy_id VARCHAR(120) NOT NULL,
    model_version VARCHAR(80),
    symbol VARCHAR(40) NOT NULL,
    interval_name VARCHAR(20) NOT NULL,
    realized_pnl NUMERIC(24,8) NOT NULL,
    closed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS execution.order_audit (
    id UUID PRIMARY KEY,
    client_order_id VARCHAR(120) NOT NULL UNIQUE,
    venue VARCHAR(40) NOT NULL,
    symbol VARCHAR(40) NOT NULL,
    side VARCHAR(10) NOT NULL,
    order_type VARCHAR(20) NOT NULL,
    quantity NUMERIC(24,8) NOT NULL,
    requested_price NUMERIC(24,8),
    status VARCHAR(30) NOT NULL,
    external_order_id VARCHAR(160),
    response_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
