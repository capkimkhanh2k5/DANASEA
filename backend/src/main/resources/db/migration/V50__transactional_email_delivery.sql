-- V50: Transactional Email Delivery Outbox Schema

CREATE TABLE transactional_email_delivery (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(64) NOT NULL,
    business_key VARCHAR(255) NOT NULL,
    recipient_key VARCHAR(255) NOT NULL,
    family VARCHAR(32) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    payload_version INT NOT NULL DEFAULT 1,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_token VARCHAR(64),
    leased_until TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    last_error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_transactional_email_idempotency UNIQUE (type, business_key, recipient_key, mode)
);

CREATE INDEX idx_trans_email_due_poll
    ON transactional_email_delivery (mode, status, next_attempt_at)
    WHERE status IN ('PENDING', 'RETRYABLE_REJECTION');

CREATE INDEX idx_trans_email_expired_lease
    ON transactional_email_delivery (leased_until)
    WHERE leased_until IS NOT NULL;

CREATE INDEX idx_trans_email_audit
    ON transactional_email_delivery (business_key, recipient_key);
