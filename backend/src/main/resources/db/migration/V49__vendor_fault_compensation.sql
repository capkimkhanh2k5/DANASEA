ALTER TABLE discount_codes
    ADD COLUMN compensation_disruption_id UUID REFERENCES sub_order_disruptions(id);

CREATE UNIQUE INDEX uq_discount_compensation_disruption
    ON discount_codes(compensation_disruption_id)
    WHERE compensation_disruption_id IS NOT NULL;

ALTER TABLE discount_codes ADD CONSTRAINT chk_vendor_fault_compensation_voucher
    CHECK (compensation_disruption_id IS NULL OR (
        replacement_sub_order_id IS NULL AND recipient_customer_id IS NOT NULL
        AND max_uses IS NOT NULL AND max_uses = 1
        AND max_uses_per_user IS NOT NULL AND max_uses_per_user = 1
        AND discount_type = 'FIXED' AND scope = 'PLATFORM' AND sponsor_type = 'PLATFORM'
        AND discount_value IS NOT NULL AND min_order_amount IS NOT NULL
        AND min_order_amount > discount_value
    ));

CREATE TABLE vendor_fault_compensations (
    disruption_id UUID PRIMARY KEY REFERENCES sub_order_disruptions(id),
    sub_order_id UUID NOT NULL REFERENCES sub_orders(id),
    original_slot_id UUID NOT NULL REFERENCES service_slots(id),
    vendor_id UUID NOT NULL REFERENCES vendors(id),
    voucher_id UUID NOT NULL UNIQUE REFERENCES discount_codes(id),
    paid_basis NUMERIC(38, 2) NOT NULL CHECK (paid_basis > 0),
    rate_bps INTEGER NOT NULL CHECK (rate_bps BETWEEN 1 AND 10000),
    amount NUMERIC(38, 2) NOT NULL CHECK (amount > 0 AND amount <= paid_basis),
    remaining_amount NUMERIC(38, 2) NOT NULL CHECK (remaining_amount >= 0 AND remaining_amount <= amount),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (sub_order_id, original_slot_id)
);

CREATE INDEX idx_vendor_fault_compensation_outstanding
    ON vendor_fault_compensations(vendor_id, created_at, disruption_id)
    WHERE remaining_amount > 0;

CREATE TABLE vendor_fault_compensation_recoveries (
    disruption_id UUID NOT NULL REFERENCES vendor_fault_compensations(disruption_id),
    settlement_id UUID NOT NULL REFERENCES settlements(id),
    amount NUMERIC(38, 2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (disruption_id, settlement_id)
);

ALTER TABLE settlements ADD COLUMN vendor_fault_compensation_deducted_amount NUMERIC(38, 2)
    NOT NULL DEFAULT 0 CHECK (vendor_fault_compensation_deducted_amount >= 0);
