ALTER TABLE agent_trace
    ADD COLUMN safety_events_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN hallucination_risk VARCHAR(16) NOT NULL DEFAULT 'LOW',
    ADD COLUMN risk_factors_json JSONB NOT NULL DEFAULT '[]'::jsonb;
