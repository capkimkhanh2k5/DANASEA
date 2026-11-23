-- 1. Create table service_slot_options
CREATE TABLE IF NOT EXISTS service_slot_options (
    slot_id UUID NOT NULL REFERENCES service_slots(id) ON DELETE CASCADE,
    option_id UUID NOT NULL REFERENCES service_options(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (slot_id, option_id)
);

CREATE INDEX IF NOT EXISTS idx_service_slot_options_slot_id ON service_slot_options(slot_id);
CREATE INDEX IF NOT EXISTS idx_service_slot_options_option_id ON service_slot_options(option_id);

-- 2. Backfill existing bindings from service_slots.option_id (if column exists)
DO '
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = ''service_slots'' AND column_name = ''option_id''
    ) THEN
        EXECUTE ''INSERT INTO service_slot_options (slot_id, option_id)
                 SELECT id, option_id FROM service_slots
                 WHERE option_id IS NOT NULL
                 ON CONFLICT (slot_id, option_id) DO NOTHING'';
    END IF;
END ';

-- 3. Backfill from booking_items for historical slots
INSERT INTO service_slot_options (slot_id, option_id)
SELECT DISTINCT bi.slot_id, bi.option_id
FROM booking_items bi
JOIN service_slots s ON s.id = bi.slot_id
JOIN service_options o ON o.id = bi.option_id
WHERE bi.slot_id IS NOT NULL AND bi.option_id IS NOT NULL
ON CONFLICT (slot_id, option_id) DO NOTHING;
