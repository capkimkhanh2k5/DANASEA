-- DANASEA — SCRIPT XÓA DỮ LIỆU DEMO SERVICES
-- Mục đích: Dọn sạch 100% dữ liệu tour demo mà không làm ảnh hưởng đến bất kỳ dữ liệu thật nào khác trong Database.
-- Chạy lệnh: docker exec -i pbl6-postgres-1 psql -U danasea_user -d danasea_db < docs/Data/cleanup_demo_services.sql

BEGIN;

-- 1. Xóa các slot units và ca giờ liên quan
DELETE FROM service_slot_units WHERE slot_id IN (
    SELECT id FROM service_slots WHERE service_id::text LIKE 'd0000001%'
);

DELETE FROM service_slots WHERE service_id::text LIKE 'd0000001%';

-- 2. Xóa các gói options liên quan
DELETE FROM service_options WHERE service_id::text LIKE 'd0000001%';

-- 3. Xóa các hình ảnh liên quan
DELETE FROM service_images WHERE service_id::text LIKE 'd0000001%';

-- 4. Xóa dịch vụ demo
DELETE FROM services WHERE id::text LIKE 'd0000001%';

COMMIT;

-- Thông báo hoàn thành
SELECT count(*) AS remaining_demo_services FROM services WHERE id::text LIKE 'd0000001%';
