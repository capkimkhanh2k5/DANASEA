-- ==============================================================================
-- V52__sync_payment_attempt_state_trigger.sql
-- Mục đích: Trigger tự động đồng bộ attempt_state cho các thao tác insert/update
--          legacy khi không truyền attempt_state (bảo vệ uq_payments_in_flight).
-- ==============================================================================

CREATE OR REPLACE FUNCTION trg_sync_payment_attempt_state()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'FAILED' AND (NEW.attempt_state IS NULL OR NEW.attempt_state = 'PENDING') THEN
        NEW.attempt_state := 'FAILED';
    ELSIF NEW.status = 'SUCCESS' AND (NEW.attempt_state IS NULL OR NEW.attempt_state = 'PENDING') THEN
        NEW.attempt_state := 'COMPLETED';
    ELSIF NEW.status = 'CANCELLED' AND (NEW.attempt_state IS NULL OR NEW.attempt_state = 'PENDING') THEN
        NEW.attempt_state := 'TERMINATED';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_payments_sync_attempt_state ON payments;
CREATE TRIGGER trg_payments_sync_attempt_state
    BEFORE INSERT OR UPDATE ON payments
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_payment_attempt_state();
