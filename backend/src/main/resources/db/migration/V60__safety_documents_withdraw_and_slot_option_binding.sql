-- 1. Update check constraint on service_safety_documents for WITHDRAWN and SUPERSEDED
ALTER TABLE service_safety_documents DROP CONSTRAINT IF EXISTS service_safety_documents_status_check;
ALTER TABLE service_safety_documents ADD CONSTRAINT service_safety_documents_status_check
    CHECK (status::text = ANY (ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'WITHDRAWN'::character varying, 'SUPERSEDED'::character varying]::text[]));

-- 2. Add option_id to service_slots
ALTER TABLE service_slots ADD COLUMN IF NOT EXISTS option_id uuid;
ALTER TABLE service_slots DROP CONSTRAINT IF EXISTS fk_service_slots_option_id;
ALTER TABLE service_slots ADD CONSTRAINT fk_service_slots_option_id FOREIGN KEY (option_id) REFERENCES service_options(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_service_slots_option_id ON service_slots(option_id);

-- 3. Backfill option_id only for historical slots that have bookings exclusively for a single option
WITH single_option_slots AS (
    SELECT slot_id, MIN(option_id::text)::uuid AS opt_id
    FROM booking_items
    WHERE slot_id IS NOT NULL AND option_id IS NOT NULL
    GROUP BY slot_id
    HAVING COUNT(DISTINCT option_id) = 1
)
UPDATE service_slots s
SET option_id = sos.opt_id
FROM single_option_slots sos
WHERE s.id = sos.slot_id AND s.option_id IS NULL;
