ALTER TABLE intelligence.model_drift_snapshot
    ADD COLUMN IF NOT EXISTS feature_population_stability_index NUMERIC(12, 8),
    ADD COLUMN IF NOT EXISTS balanced_accuracy_lower_bound NUMERIC(12, 8),
    ADD COLUMN IF NOT EXISTS macro_f1_lower_bound NUMERIC(12, 8);

ALTER TABLE intelligence.prediction_history
    ADD COLUMN IF NOT EXISTS prediction_entropy NUMERIC(12, 8),
    ADD COLUMN IF NOT EXISTS uncertainty_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS prediction_set JSONB;

ALTER TABLE intelligence.ml_examples
    ADD COLUMN IF NOT EXISTS label_threshold NUMERIC(24, 12);

CREATE INDEX IF NOT EXISTS idx_prediction_history_health_window
    ON intelligence.prediction_history(model_version, symbol, interval_name, prediction_time DESC)
    INCLUDE (predicted_direction, outcome_label, correct, confidence, feature_drift_score);
