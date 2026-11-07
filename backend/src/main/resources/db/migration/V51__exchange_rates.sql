CREATE TABLE IF NOT EXISTS exchange_rates (
    id UUID PRIMARY KEY,
    base_currency VARCHAR(3) NOT NULL,
    target_currency VARCHAR(3) NOT NULL,
    rate NUMERIC(15, 6) NOT NULL,
    effective_date DATE NOT NULL,
    source VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_exchange_rate_daily UNIQUE (base_currency, target_currency, effective_date),
    CONSTRAINT chk_exchange_rate_positive CHECK (rate > 0)
);

CREATE INDEX IF NOT EXISTS idx_exchange_rates_lookup
    ON exchange_rates (base_currency, target_currency, effective_date DESC);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS exchange_rate NUMERIC(15, 6),
    ADD COLUMN IF NOT EXISTS exchange_rate_date DATE,
    ADD COLUMN IF NOT EXISTS exchange_rate_source VARCHAR(50);
