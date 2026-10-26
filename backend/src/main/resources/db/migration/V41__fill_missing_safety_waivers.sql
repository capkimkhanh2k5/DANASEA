-- V41__fill_missing_safety_waivers.sql
-- Ensure services requiring safety waiver have standard waiver content populated

-- 1. Populate default Vietnamese & English waiver content for services with waiver_required = TRUE but null content
UPDATE services
SET 
    waiver_content = CASE 
        WHEN LOWER(name) LIKE '%dù bay%' OR LOWER(name) LIKE '%parasail%' THEN
            'Du khách cam kết đảm bảo điều kiện sức khỏe, không có tiền sử bệnh tim mạch hoặc sợ độ cao. Bắt buộc mặc áo phao chuyên dụng và cài đai an toàn theo hướng dẫn của kỹ thuật viên.'
        WHEN LOWER(name) LIKE '%lặn%' OR LOWER(name) LIKE '%dive%' OR LOWER(name) LIKE '%snorkeling%' THEN
            'Khách hàng xác nhận không có tiền sử bệnh tim mạch, huyết áp hoặc hen suyễn; bắt buộc mặc áo phao hoặc thiết bị lặn chuyên dụng và tuân thủ quy chuẩn an toàn biển.'
        WHEN LOWER(name) LIKE '%mô tô%' OR LOWER(name) LIKE '%jetski%' THEN
            'Người tham gia bắt buộc mặc áo phao có còi cứu sinh, chỉ điều khiển trong vùng phao tiêu an toàn quy định và tuân thủ hướng dẫn viên.'
        WHEN LOWER(name) LIKE '%lướt sóng%' OR LOWER(name) LIKE '%surf%' THEN
            'Học viên cam kết biết bơi cơ bản, luôn gắn dây buộc chân an toàn vào ván và tuân thủ khu vực thực hành theo chỉ dẫn của huấn luyện viên.'
        ELSE
            'Du khách cam kết đủ sức khỏe tham gia, bắt buộc mặc áo phao trong suốt hành trình và tuân thủ nghiêm ngặt chỉ dẫn của hướng dẫn viên cứu hộ.'
    END,
    waiver_content_en = CASE 
        WHEN LOWER(name) LIKE '%dù bay%' OR LOWER(name) LIKE '%parasail%' THEN
            'Participants confirm they are in good health with no history of cardiovascular issues or severe acrophobia. Wearing a certified life jacket and harness as instructed by safety staff is mandatory.'
        WHEN LOWER(name) LIKE '%lặn%' OR LOWER(name) LIKE '%dive%' OR LOWER(name) LIKE '%snorkeling%' THEN
            'Participants confirm no history of cardiovascular, blood pressure, or asthma conditions. Wearing life vests or specialized diving gear and adhering to marine safety protocols is mandatory.'
        WHEN LOWER(name) LIKE '%mô tô%' OR LOWER(name) LIKE '%jetski%' THEN
            'Participants must wear certified life jackets with whistle, operate only within designated safety buoy zones, and strictly obey instructor guidance.'
        WHEN LOWER(name) LIKE '%lướt sóng%' OR LOWER(name) LIKE '%surf%' THEN
            'Students confirm basic swimming ability, must keep the safety leash attached to the surfboard at all times, and practice only within designated safe zones.'
        ELSE
            'Participants confirm good health condition, must wear life jackets throughout the trip, and strictly follow rescue guide instructions.'
    END
WHERE waiver_required = TRUE AND (waiver_content IS NULL OR TRIM(waiver_content) = '');

-- 2. Backfill pending sub_orders under PENDING_PAYMENT master orders
UPDATE sub_orders so
SET 
    waiver_content = s.waiver_content,
    waiver_content_en = s.waiver_content_en
FROM services s, master_orders mo
WHERE so.service_id = s.id
  AND so.master_order_id = mo.id
  AND so.waiver_required = TRUE
  AND (so.waiver_content IS NULL OR TRIM(so.waiver_content) = '')
  AND mo.status = 'PENDING_PAYMENT'
  AND so.status = 'PENDING';
