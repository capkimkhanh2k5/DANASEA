CREATE TABLE service_operating_schedules (
    service_id uuid PRIMARY KEY REFERENCES services (id) ON DELETE CASCADE,
    starts_on date NOT NULL,
    ends_on date,
    active boolean NOT NULL,
    weekly_rules jsonb NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (ends_on IS NULL OR ends_on >= starts_on),
    CHECK (jsonb_typeof(weekly_rules) = 'array')
);

CREATE TABLE service_operating_schedule_slots (
    slot_id uuid PRIMARY KEY REFERENCES service_slots (id) ON DELETE CASCADE
);

CREATE TABLE service_operating_schedule_exceptions (
    id uuid PRIMARY KEY,
    service_id uuid NOT NULL REFERENCES services (id) ON DELETE CASCADE,
    date_from date NOT NULL,
    date_to date NOT NULL,
    start_time time(0),
    reason varchar(500) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (date_to >= date_from)
);

CREATE INDEX idx_operating_exceptions_service_dates
    ON service_operating_schedule_exceptions (service_id, date_to, date_from);
