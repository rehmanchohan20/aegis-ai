ALTER TABLE intelligence.prediction_history
    ADD COLUMN IF NOT EXISTS stop_loss_price NUMERIC(30, 12),
    ADD COLUMN IF NOT EXISTS take_profit_price NUMERIC(30, 12),
    ADD COLUMN IF NOT EXISTS path_outcome VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_prediction_due_resolution
    ON intelligence.prediction_history(prediction_time, evaluation_horizon_seconds)
    WHERE resolved_at IS NULL AND starting_price IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ml_examples_market_observation
    ON intelligence.ml_examples(symbol, interval_name, created_at);

