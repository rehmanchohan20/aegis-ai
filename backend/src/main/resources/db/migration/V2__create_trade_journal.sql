CREATE SCHEMA IF NOT EXISTS trading;

CREATE TABLE IF NOT EXISTS trading.trade_journal (
    id UUID PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    interval_name VARCHAR(16) NOT NULL,
    side VARCHAR(8) NOT NULL,
    status VARCHAR(24) NOT NULL,
    entry_price NUMERIC(24, 10) NOT NULL,
    stop_loss NUMERIC(24, 10) NOT NULL,
    take_profit NUMERIC(24, 10) NOT NULL,
    quantity NUMERIC(24, 10) NOT NULL,
    realized_pnl NUMERIC(24, 10) NOT NULL DEFAULT 0,
    signal_score INTEGER,
    signal_grade VARCHAR(8),
    rationale TEXT,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_trade_journal_symbol_opened
    ON trading.trade_journal(symbol, opened_at DESC);
CREATE INDEX IF NOT EXISTS idx_trade_journal_status
    ON trading.trade_journal(status);
