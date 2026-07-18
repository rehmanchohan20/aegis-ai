CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE IF NOT EXISTS candles (
    symbol VARCHAR(30) NOT NULL,
    interval_code VARCHAR(10) NOT NULL,
    open_time TIMESTAMPTZ NOT NULL,
    close_time TIMESTAMPTZ NOT NULL,
    open_price NUMERIC(30, 12) NOT NULL,
    high_price NUMERIC(30, 12) NOT NULL,
    low_price NUMERIC(30, 12) NOT NULL,
    close_price NUMERIC(30, 12) NOT NULL,
    volume NUMERIC(38, 12) NOT NULL,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (symbol, interval_code, open_time)
);

SELECT create_hypertable('candles', 'open_time', if_not_exists => TRUE);
CREATE INDEX IF NOT EXISTS idx_candles_symbol_interval_time
    ON candles (symbol, interval_code, open_time DESC);
