ALTER TABLE service_slots DROP CONSTRAINT IF EXISTS service_slots_status_check;
ALTER TABLE service_slots ADD CONSTRAINT service_slots_status_check
    CHECK (status IN ('OPEN', 'CLOSED', 'BLOCKED', 'CANCELLED'));

CREATE TABLE vendor_departure_cancellations (
    slot_id UUID PRIMARY KEY REFERENCES service_slots(id),
    vendor_id UUID NOT NULL REFERENCES vendors(id),
    requested_by UUID NOT NULL REFERENCES users(id),
    reason VARCHAR(500) NOT NULL,
    cancelled_at TIMESTAMPTZ NOT NULL
);
