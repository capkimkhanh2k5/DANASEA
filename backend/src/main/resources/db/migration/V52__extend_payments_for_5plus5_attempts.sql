-- ==============================================================================
-- V52__extend_payments_for_5plus5_attempts.sql
-- Mục đích: Hỗ trợ multi-attempt tracking, deadline 5+5, bảo toàn MOMO/SEPAY/PAYPAL,
--          bổ sung PAYOS/ZALOPAY, Partial Unique Index cho in-flight, active và canonical.
-- ==============================================================================

-- 1. Sequence an toàn cho payOS orderCode
CREATE SEQUENCE IF NOT EXISTS payos_order_code_seq
    START WITH 100001 INCREMENT BY 1 MINVALUE 100000 MAXVALUE 9007199254740991 CACHE 1;

-- 2. Bổ sung các cột phục vụ multi-attempt vào bảng payments
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS attempt_number INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS attempt_state VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS danasea_deadline_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS provider_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS canceled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancel_status VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS is_canonical BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS payos_order_code BIGINT;

-- 3. Cập nhật Check Constraint cho Provider (BẢO TOÀN MOMO, SEPAY, VNPAY, PAYPAL + THÊM PAYOS, ZALOPAY)
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_provider_check;
ALTER TABLE payments ADD CONSTRAINT payments_provider_check 
    CHECK (provider IN ('VNPAY', 'MOMO', 'SEPAY', 'PAYPAL', 'PAYOS', 'ZALOPAY'));

ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payments_attempt_state;
ALTER TABLE payments ADD CONSTRAINT chk_payments_attempt_state 
    CHECK (attempt_state IN ('CREATING', 'PENDING', 'ACTIVE', 'COMPLETED', 'FAILED', 'TERMINATED'));

ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payments_cancel_status;
ALTER TABLE payments ADD CONSTRAINT chk_payments_cancel_status 
    CHECK (cancel_status IN ('NONE', 'CANCEL_REQUESTED', 'CANCELED', 'CANCEL_UNKNOWN', 'ALREADY_CAPTURED'));

-- Preserve existing order deadlines. Legacy orders could store only a local intent expiry.
-- The earliest known expiry keeps a migrated order from opening a fresh payment window.
WITH legacy_expiries AS (
    SELECT master_order_id, min(expires_at) AS first_expiry
    FROM payments
    WHERE status = 'PENDING' AND expires_at IS NOT NULL
    GROUP BY master_order_id
)
UPDATE master_orders o
SET payment_deadline = e.first_expiry
FROM legacy_expiries e
WHERE o.id = e.master_order_id AND o.status = 'PENDING_PAYMENT' AND o.payment_deadline IS NULL;

UPDATE payments SET attempt_state = CASE
    WHEN status IN ('SUCCESS', 'REFUNDED') THEN 'COMPLETED'
    WHEN status = 'FAILED' THEN 'FAILED'
    ELSE 'TERMINATED'
END
WHERE status IS DISTINCT FROM 'PENDING';

-- Keep every unresolved attempt pending for reconciliation, even with an expired URL
-- or no saved gateway reference. Only the newest attempt of a payable order is active.
WITH pending_attempts AS (
    SELECT p.id, o.payment_deadline, o.status AS order_status,
           row_number() OVER (PARTITION BY o.id ORDER BY p.created_at DESC, p.id DESC) AS position
    FROM payments p
    JOIN master_orders o ON o.id = p.master_order_id
    WHERE p.status = 'PENDING'
)
UPDATE payments p
SET danasea_deadline_at = a.payment_deadline,
    is_active = a.position = 1 AND a.order_status IS NOT DISTINCT FROM 'PENDING_PAYMENT'
FROM pending_attempts a
WHERE p.id = a.id;

-- Chỉ gán is_canonical = true cho đơn thực sự thành công toàn vẹn
WITH valid_canonical AS (
    SELECT DISTINCT ON (p.master_order_id) p.id
    FROM payments p
    JOIN master_orders mo ON p.master_order_id = mo.id
    JOIN bookings b ON mo.booking_id = b.id
    WHERE p.status = 'SUCCESS'
      AND mo.status IN ('PAID', 'COMPLETED', 'PARTIALLY_COMPLETED')
      AND b.status = 'CONFIRMED'
      AND p.amount = mo.total_amount
    ORDER BY p.master_order_id, p.paid_at ASC NULLS LAST, p.created_at ASC, p.id ASC
)
UPDATE payments p
SET is_canonical = true
FROM valid_canonical vc
WHERE p.id = vc.id;

-- 5. Ràng buộc bảo vệ cấp DB Engine
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_canonical_order 
    ON payments (master_order_id) WHERE is_canonical = true;

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_active_attempt 
    ON payments (master_order_id) WHERE is_active = true;

-- Legacy orders can have several unresolved rows. The application also checks for
-- inactive pending attempts before opening another gateway link.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_in_flight
    ON payments (master_order_id) WHERE attempt_state = 'CREATING' OR (attempt_state = 'PENDING' AND is_active);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_payos_order_code 
    ON payments (payos_order_code) WHERE payos_order_code IS NOT NULL;

