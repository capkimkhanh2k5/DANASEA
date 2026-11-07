-- ==============================================================================
-- V53__sync_payment_attempt_state_trigger.sql
-- Normalize legacy writes that change payment status without updating attempt fields.
-- ==============================================================================

CREATE OR REPLACE FUNCTION trg_sync_payment_attempt_state()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IN ('SUCCESS', 'REFUNDED', 'FAILED') THEN
        NEW.is_active := false;
        IF NEW.attempt_state IS NULL OR NEW.attempt_state IN ('CREATING', 'PENDING', 'ACTIVE') THEN
            NEW.attempt_state := CASE WHEN NEW.status = 'FAILED' THEN 'FAILED' ELSE 'COMPLETED' END;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_payments_sync_attempt_state ON payments;
CREATE TRIGGER trg_payments_sync_attempt_state
    BEFORE INSERT OR UPDATE ON payments
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_payment_attempt_state();
