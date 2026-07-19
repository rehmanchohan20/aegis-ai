CREATE TABLE IF NOT EXISTS intelligence.model_activation_audit (
    id UUID PRIMARY KEY,
    model_id UUID NOT NULL REFERENCES intelligence.model_registry(id),
    model_name VARCHAR(120) NOT NULL,
    version VARCHAR(80) NOT NULL,
    action VARCHAR(30) NOT NULL,
    previous_model_id UUID REFERENCES intelligence.model_registry(id),
    performed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_model_activation_audit_lookup
    ON intelligence.model_activation_audit(model_name, performed_at DESC);
