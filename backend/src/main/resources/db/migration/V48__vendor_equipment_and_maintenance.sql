CREATE TABLE vendor_equipment (
    id uuid PRIMARY KEY,
    vendor_id uuid NOT NULL REFERENCES vendors(id),
    name varchar(150) NOT NULL,
    compatibility_group varchar(64) NOT NULL,
    capacity integer NOT NULL CHECK (capacity > 0),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_vendor_equipment_vendor ON vendor_equipment(vendor_id);

ALTER TABLE service_slot_units
    ADD COLUMN equipment_id uuid REFERENCES vendor_equipment(id),
    ADD COLUMN equipment_blocked boolean NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT uq_slot_physical_equipment UNIQUE (slot_id, equipment_id),
    ADD CONSTRAINT chk_blocked_unit_has_equipment CHECK (NOT equipment_blocked OR equipment_id IS NOT NULL);
CREATE INDEX idx_slot_units_equipment ON service_slot_units(equipment_id) WHERE equipment_id IS NOT NULL;

CREATE TABLE equipment_maintenance (
    id uuid PRIMARY KEY,
    equipment_id uuid NOT NULL REFERENCES vendor_equipment(id),
    starts_at timestamp NOT NULL,
    ends_at timestamp NOT NULL,
    urgent boolean NOT NULL,
    reason varchar(500) NOT NULL,
    requested_by uuid NOT NULL REFERENCES users(id),
    affected_slots integer NOT NULL DEFAULT 0,
    moved_allocations integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at)
);
CREATE INDEX idx_equipment_maintenance_interval ON equipment_maintenance(equipment_id, starts_at, ends_at);

CREATE TABLE equipment_maintenance_impacts (
    maintenance_id uuid NOT NULL REFERENCES equipment_maintenance(id),
    booking_item_id uuid NOT NULL REFERENCES booking_items(id),
    slot_id uuid NOT NULL REFERENCES service_slots(id),
    impact_type varchar(16) NOT NULL CHECK (impact_type IN ('CONFIRMED', 'ACTIVE_HOLD', 'IN_OPERATION')),
    resolved_at timestamptz,
    PRIMARY KEY (maintenance_id, booking_item_id)
);
CREATE INDEX idx_pending_equipment_maintenance_impacts
    ON equipment_maintenance_impacts(impact_type) WHERE resolved_at IS NULL;

CREATE TABLE equipment_maintenance_moves (
    maintenance_id uuid NOT NULL REFERENCES equipment_maintenance(id),
    allocation_id uuid NOT NULL,
    booking_item_id uuid NOT NULL REFERENCES booking_items(id),
    slot_id uuid NOT NULL REFERENCES service_slots(id),
    source_unit_number integer NOT NULL,
    target_unit_number integer NOT NULL,
    original_seats integer NOT NULL CHECK (original_seats > 0),
    moved_seats integer NOT NULL CHECK (moved_seats > 0),
    PRIMARY KEY (maintenance_id, allocation_id)
);
