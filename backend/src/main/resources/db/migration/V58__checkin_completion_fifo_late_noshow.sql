-- ==============================================================================
-- V58__checkin_completion_fifo_late_noshow.sql
-- Description: Phase 2 Database Migration for DANASEA
--              - Bổ sung trạng thái NO_SHOW cho sub_orders
--              - Bổ sung actual_slot_id cho điều phối FIFO ca sớm
--              - Bổ sung cờ is_late và late_checkin_reason
--              - Bổ sung operation_type (FIXED_DEPARTURE, DURATION_BASED) cho services
--              - Hỗ trợ đánh dấu hủy do sự cố (SC14) trên vendor_departure_cancellations
-- ==============================================================================

-- 1. CẬP NHẬT CHECK CONSTRAINT TRÊN BẢNG sub_orders CHO NO_SHOW
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'sub_orders'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%status%'
    ) LOOP
        EXECUTE 'ALTER TABLE sub_orders DROP CONSTRAINT IF EXISTS ' || quote_ident(r.conname);
    END LOOP;
END $$;

ALTER TABLE sub_orders DROP CONSTRAINT IF EXISTS sub_orders_status_check;
ALTER TABLE sub_orders DROP CONSTRAINT IF EXISTS chk_sub_orders_status;

ALTER TABLE sub_orders ADD CONSTRAINT chk_sub_orders_status CHECK (
    status IN (
        'PENDING',
        'CONFIRMED',
        'REJECTED',
        'COMPLETED',
        'CANCELLED',
        'REFUNDED',
        'PARTIALLY_REFUNDED',
        'CHECKED_IN',
        'IN_PROGRESS',
        'NO_SHOW'
    )
);

-- 2. BỔ SUNG CỘT CHO sub_orders (FIFO & LATE CHECKIN)
ALTER TABLE sub_orders ADD COLUMN IF NOT EXISTS actual_slot_id UUID REFERENCES service_slots(id);
ALTER TABLE sub_orders ADD COLUMN IF NOT EXISTS is_late BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sub_orders ADD COLUMN IF NOT EXISTS late_checkin_reason VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_sub_orders_actual_slot_id ON sub_orders(actual_slot_id);

-- 3. BỔ SUNG operation_type CHO services
ALTER TABLE services ADD COLUMN IF NOT EXISTS operation_type VARCHAR(50) NOT NULL DEFAULT 'FIXED_DEPARTURE';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_services_operation_type'
    ) THEN
        ALTER TABLE services ADD CONSTRAINT chk_services_operation_type
            CHECK (operation_type IN ('FIXED_DEPARTURE', 'DURATION_BASED'));
    END IF;
END $$;

-- Backfill thông minh cho dữ liệu dịch vụ cũ:
-- Phân loại chính xác các dịch vụ thể thao biển theo lượt / thời lượng (thuê SUP, Kayak, Jetski, Phao chuối, Bay dù)
-- thành DURATION_BASED dựa trên đặc thù vận hành của từng dịch vụ (không mặc định toàn bộ danh mục).
-- Các tour có hướng dẫn viên, chuyến tàu/du thuyền theo lịch trình cố định luôn là FIXED_DEPARTURE.
UPDATE services s
SET operation_type = 'DURATION_BASED'
FROM categories c
WHERE s.category_id = c.id
  AND (
      s.slug ILIKE '%thue%'
      OR s.slug ILIKE '%cho-thue%'
      OR s.slug ILIKE '%jetski%'
      OR s.slug ILIKE '%phao-chuoi%'
      OR s.slug ILIKE '%du-bay%'
      OR s.name ILIKE '%thuê%'
      OR s.name ILIKE '%tự chèo%'
      OR s.name ILIKE '%mô tô nước%'
      OR (c.slug IN ('cheo-sup-kayak', 'mo-to-nuoc-jetski', 'truot-phao-chuoi')
          AND s.slug NOT ILIKE '%tour%' AND s.name NOT ILIKE '%tour%')
  )
  AND s.slug NOT ILIKE '%tour%'
  AND s.name NOT ILIKE '%tour%'
  AND s.slug NOT ILIKE '%chuyen%'
  AND s.name NOT ILIKE '%chuyến%'
  AND s.slug NOT ILIKE '%du-thuyen%'
  AND s.slug NOT ILIKE '%tau-cao-toc%'
  AND s.slug NOT ILIKE '%tour-dao%';

-- Đảm bảo toàn bộ các dịch vụ tour, du thuyền, chuyến đi theo hải trình cố định giữ vững FIXED_DEPARTURE
UPDATE services
SET operation_type = 'FIXED_DEPARTURE'
WHERE slug ILIKE '%tour%'
   OR name ILIKE '%tour%'
   OR slug ILIKE '%chuyen%'
   OR name ILIKE '%chuyến%'
   OR slug ILIKE '%du-thuyen%'
   OR slug ILIKE '%tau-cao-toc%'
   OR slug ILIKE '%tour-dao%'
   OR slug ILIKE '%ngam-hoang-hon%';

-- 4. BỔ SUNG HỖ TRỢ SC14 CHO vendor_departure_cancellations
ALTER TABLE vendor_departure_cancellations ADD COLUMN IF NOT EXISTS is_incident BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE vendor_departure_cancellations ADD COLUMN IF NOT EXISTS incident_type VARCHAR(50);

-- 5. BỔ SUNG CỘT BẢO LƯU THÔNG TIN DUYỆT/CHI TRẢ CHO payout_requests
ALTER TABLE payout_requests ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500);
ALTER TABLE payout_requests ADD COLUMN IF NOT EXISTS bank_reference VARCHAR(100);
ALTER TABLE payout_requests ADD COLUMN IF NOT EXISTS admin_note VARCHAR(1000);
ALTER TABLE payout_requests ADD COLUMN IF NOT EXISTS vendor_note VARCHAR(1000);
