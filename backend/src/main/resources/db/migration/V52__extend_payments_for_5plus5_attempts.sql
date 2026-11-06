-- ==============================================================================
-- V51__extend_payments_for_5plus5_attempts.sql
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

-- 4. Pre-check & Backfill An toàn
-- 100% payments lịch sử đặt is_active = false
UPDATE payments SET is_active = false;

-- Chuẩn hóa attempt_state cho các giao dịch lịch sử theo status thực tế
UPDATE payments SET attempt_state = CASE 
    WHEN status = 'SUCCESS' THEN 'COMPLETED'
    WHEN status = 'FAILED' THEN 'FAILED'
    WHEN status = 'CANCELLED' THEN 'TERMINATED'
    ELSE 'TERMINATED'
END
WHERE attempt_state = 'PENDING';

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
    ORDER BY p.master_order_id, p.paid_at ASC NULLS LAST, p.created_at ASC
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

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_in_flight 
    ON payments (master_order_id) WHERE attempt_state IN ('CREATING', 'PENDING');

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_payos_order_code 
    ON payments (payos_order_code) WHERE payos_order_code IS NOT NULL;

