-- Create domain_events table for event persistence and debugging
CREATE TABLE IF NOT EXISTS domain_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Create index for querying by type and date
CREATE INDEX IF NOT EXISTS idx_domain_events_type ON domain_events(type);
CREATE INDEX IF NOT EXISTS idx_domain_events_created_at ON domain_events(created_at DESC);

