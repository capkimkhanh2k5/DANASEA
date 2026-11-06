"""
Simulation Enrichment Script for DANASEA
Seeds all simulation matrix fixtures for TC-01 through TC-08 and AD-01 through AD-06:
- Platform Discount Codes (DANASEA10, DANASEA50K)
- Active Flash Sale for Jet Ski Kajin Vina (starts today, ends tomorrow night)
- Wishlists for Customer 1 and Customer 2
- Real-time In-app Conversations & Messages
- 12 Verified Reviews (including 1 vendor reply and 1 flagged review for moderation)
- Active Dispute with Attachment for Customer 1
- Payout Request #10 set to REQUESTED for Admin Approval
- Vendor 9 (Surf Shack Da Nang) set to PENDING for Onboarding Approval
- Early Morning Departure Slots (2026-11-06 today and 2026-11-07 tomorrow 07:30 - 08:00 AM)
"""

import subprocess
import sys


def main():
    sql = """
BEGIN;

-- 1. Discount Codes
INSERT INTO discount_codes (
    id, code, discount_type, discount_value, scope, sponsor_type,
    min_order_amount, max_discount_amount, max_uses, max_uses_per_user,
    used_count, is_active, valid_from, valid_to, created_at, updated_at
) VALUES 
('11111111-1111-1111-1111-111111111111', 'DANASEA10', 'PERCENTAGE', 10.00, 'PLATFORM', 'PLATFORM',
 500000.00, 100000.00, 100, 5, 0, true, '2026-10-01 00:00:00+07', '2026-12-31 23:59:59+07', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('22222222-2222-2222-2222-222222222222', 'DANASEA50K', 'FIXED', 50000.00, 'PLATFORM', 'PLATFORM',
 300000.00, 50000.00, 50, 1, 0, true, '2026-10-01 00:00:00+07', '2026-12-31 23:59:59+07', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET is_active = true, valid_to = '2026-12-31 23:59:59+07';

-- 2. Flash Sale (Enabled for mo-to-nuoc-kajin-vina)
INSERT INTO flash_sales (
    id, service_id, option_id, status, unit_subsidy, starts_at, ends_at,
    quota, budget, original_unit_price, created_by, created_at, funding_source
) VALUES (
    '33333333-3333-3333-3333-333333333333',
    '2cdfabcf-13a2-550a-8f9e-283aad7053c9',
    '2942f332-cac2-5e68-be94-a3d013ffb760',
    'ENABLED',
    150000.00,
    CURRENT_TIMESTAMP - INTERVAL '1 hour',
    CURRENT_TIMESTAMP + INTERVAL '24 hours',
    20,
    3000000.00,
    650000.00,
    '3e3005a7-001a-5d9f-b4ee-993b52a01d61',
    CURRENT_TIMESTAMP,
    'PLATFORM'
) ON CONFLICT (id) DO NOTHING;

-- 3. Wishlists for Customer 1 and Customer 2
INSERT INTO wishlists (id, user_id, service_id, created_at, updated_at) VALUES
('44444444-4444-4444-4444-444444444441', '46c8f2ea-2be5-5365-be80-31fd57d9e260', '2cdfabcf-13a2-550a-8f9e-283aad7053c9', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('44444444-4444-4444-4444-444444444442', '46c8f2ea-2be5-5365-be80-31fd57d9e260', '4190046d-112a-5b93-beb2-bcc8b1cd16d4', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('44444444-4444-4444-4444-444444444443', '1a5e9f3e-cbae-5840-8c20-1f7d8abeb2b0', '088d874c-a61e-5b58-a7a9-497f32a56901', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 4. Conversations and Messages between Customer 1 and Kajin Vina
INSERT INTO conversations (id, customer_id, vendor_id, master_order_id, last_message_sequence, created_at, updated_at) VALUES
('55555555-5555-5555-5555-555555555551', '46c8f2ea-2be5-5365-be80-31fd57d9e260', 'baea9605-392b-5003-93f1-60f2c0582f65', '00010000-0000-0000-0000-000000000003', 2, CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '1 hour')
ON CONFLICT (id) DO UPDATE SET last_message_sequence = 2;

INSERT INTO messages (id, conversation_id, sender_id, content, message_sequence, is_read, created_at, updated_at) VALUES
('55555555-5555-5555-5555-555555555552', '55555555-5555-5555-5555-555555555551', '46c8f2ea-2be5-5365-be80-31fd57d9e260', 'Chào shop, sáng mai em đi mô tô nước lúc 8h30 thì có cần chuẩn bị đồ bảo hộ riêng không ạ?', 1, true, CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours'),
('55555555-5555-5555-5555-555555555553', '55555555-5555-5555-5555-555555555551', '322173c1-c330-54d9-9d1b-b69c7e480920', 'Chào bạn Long, bên mình đã trang bị sẵn áo phao chuẩn SOLAS và kính bơi cho khách rồi nhé. Bạn chỉ cần mặc đồ thoải mái và có mặt trước 15 phút tại bến.', 2, false, CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP - INTERVAL '1 hour')
ON CONFLICT (id) DO NOTHING;

-- 5. Reviews
INSERT INTO reviews (
    id, customer_id, vendor_id, service_id, sub_order_id, rating, comment,
    is_visible, is_flagged, flag_reason, moderation_note, vendor_reply, vendor_replied_at, created_at, updated_at
) VALUES
('66666666-6666-6666-6666-666666666601', '46c8f2ea-2be5-5365-be80-31fd57d9e260', '27afde91-58fd-5daa-b9fa-7f93f00135f0', '088d874c-a61e-5b58-a7a9-497f32a56901', '00020000-0000-0000-0000-000000000001', 5, 'Mô tô nước chạy rất đầm, nhân viên bến Danabeach hướng dẫn nhiệt tình và an toàn.', true, false, null, null, null, null, CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '3 days'),
('66666666-6666-6666-6666-666666666602', '1a5e9f3e-cbae-5840-8c20-1f7d8abeb2b0', '27afde91-58fd-5daa-b9fa-7f93f00135f0', '088d874c-a61e-5b58-a7a9-497f32a56901', '00020000-0000-0000-0000-000000000002', 5, 'Trải nghiệm rất tuyệt vời tại bãi biển Mỹ Khê!', true, false, null, null, null, null, CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '3 days'),
('66666666-6666-6666-6666-666666666603', '46c8f2ea-2be5-5365-be80-31fd57d9e260', 'baea9605-392b-5003-93f1-60f2c0582f65', '2cdfabcf-13a2-550a-8f9e-283aad7053c9', '00020000-0000-0000-0000-000000000003', 5, 'Cano và mô tô nước của Kajin Vina rất mới, áo phao chuẩn xịn.', true, false, null, null, 'Cảm ơn quý khách đã tin tưởng trải nghiệm dịch vụ của Kajin Vina! Hẹn gặp lại bạn vào chuyến đi sau.', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days'),
('66666666-6666-6666-6666-666666666604', '1a5e9f3e-cbae-5840-8c20-1f7d8abeb2b0', 'baea9605-392b-5003-93f1-60f2c0582f65', '2cdfabcf-13a2-550a-8f9e-283aad7053c9', '00020000-0000-0000-0000-000000000004', 4, 'Dịch vụ tốt, bến bãi sạch sẽ.', true, false, null, null, null, null, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days'),
('66666666-6666-6666-6666-666666666605', '46c8f2ea-2be5-5365-be80-31fd57d9e260', '631db4cf-1ec4-5b06-8f98-56abcf3406d0', '1dd26db5-f863-5b03-b562-ac46f43df9d2', '00020000-0000-0000-0000-000000000005', 5, 'Huấn luyện viên kèm 1:1 tận tâm, đứng nước được ngay buổi đầu.', true, false, null, null, null, null, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days'),
('66666666-6666-6666-6666-666666666606', '1a5e9f3e-cbae-5840-8c20-1f7d8abeb2b0', '631db4cf-1ec4-5b06-8f98-56abcf3406d0', '1dd26db5-f863-5b03-b562-ac46f43df9d2', '00020000-0000-0000-0000-000000000006', 5, 'Lớp học bài bản, có cano cứu hộ túc trực nên rất yên tâm.', true, false, null, null, null, null, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day'),
('66666666-6666-6666-6666-666666666607', '46c8f2ea-2be5-5365-be80-31fd57d9e260', '10f8ffc5-e987-592f-9d38-426ad7f97066', '57c22a43-22fd-57da-894c-257ade02450d', '00020000-0000-0000-0000-000000000007', 4, 'San hô Bãi Nam rất đẹp, nước trong.', true, false, null, null, null, null, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day'),
('66666666-6666-6666-6666-666666666608', '1a5e9f3e-cbae-5840-8c20-1f7d8abeb2b0', '10f8ffc5-e987-592f-9d38-426ad7f97066', '57c22a43-22fd-57da-894c-257ade02450d', '00020000-0000-0000-0000-000000000008', 5, 'Dịch vụ chu đáo, cano đưa đón đúng giờ.', true, false, null, null, null, null, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day'),
('66666666-6666-6666-6666-666666666609', '46c8f2ea-2be5-5365-be80-31fd57d9e260', '128672c5-94e7-5560-896c-158794913b8c', '88e06f4d-fbb7-5700-a45d-0f6ad1cb0d08', '00020000-0000-0000-0000-000000000009', 5, 'Cano riêng thoáng mát, câu được nhiều cá mú.', true, false, null, null, null, null, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day'),
('66666666-6666-6666-6666-666666666610', '46c8f2ea-2be5-5365-be80-31fd57d9e260', '2680f19f-8b5a-5aa8-8474-cb13e2ff1c22', '9b3444ce-b7c7-5f40-9f14-2c1ca4258dea', '00020000-0000-0000-0000-000000000011', 4, 'Đi tàu câu mực đêm không khí trong lành.', true, false, null, null, null, null, CURRENT_TIMESTAMP - INTERVAL '12 hours', CURRENT_TIMESTAMP - INTERVAL '12 hours'),
('66666666-6666-6666-6666-666666666611', '1a5e9f3e-cbae-5840-8c20-1f7d8abeb2b0', '2680f19f-8b5a-5aa8-8474-cb13e2ff1c22', '9b3444ce-b7c7-5f40-9f14-2c1ca4258dea', '00020000-0000-0000-0000-000000000012', 4, 'Trải nghiệm thú vị về đêm tại vịnh Đà Nẵng.', true, false, null, null, null, null, CURRENT_TIMESTAMP - INTERVAL '10 hours', CURRENT_TIMESTAMP - INTERVAL '10 hours'),
('66666666-6666-6666-6666-666666666612', '46c8f2ea-2be5-5365-be80-31fd57d9e260', '5fb18fee-16fd-5b9f-bcbd-759b47768d9b', '5536f743-3b6d-518e-af2b-05634a218195', '00020000-0000-0000-0000-000000000013', 1, 'Cano xuất phát trễ hơn nửa tiếng, làm lỡ kế hoạch cả buổi sáng của gia đình tôi!', true, true, 'Ngôn từ phản ánh tiêu cực về thời gian vận hành bến', 'Chờ đối soát hành trình GPS cano với bến cảng', null, null, CURRENT_TIMESTAMP - INTERVAL '5 hours', CURRENT_TIMESTAMP - INTERVAL '5 hours')
ON CONFLICT (id) DO NOTHING;

-- 6. Dispute and Dispute Attachment (for AD-04 Admin Dispute Resolution)
INSERT INTO disputes (
    id, order_id, sub_order_id, customer_id, raised_by, category, reason,
    description, status, refund_percentage, created_at, updated_at
) VALUES (
    '77777777-7777-7777-7777-777777777771',
    '00010000-0000-0000-0000-000000000005',
    '00020000-0000-0000-0000-000000000005',
    '46c8f2ea-2be5-5365-be80-31fd57d9e260',
    '46c8f2ea-2be5-5365-be80-31fd57d9e260',
    'SERVICE_QUALITY',
    'SERVICE_NOT_AS_DESCRIBED',
    'Tôi đã có mặt lúc 7h00 sáng tại bãi biển Mỹ Khê nhưng bến bãi dời giờ đến 7h45 mới tập trung HLV. Đề nghị hoàn lại 50% tiền vé.',
    'OPEN',
    50.00,
    CURRENT_TIMESTAMP - INTERVAL '4 hours',
    CURRENT_TIMESTAMP - INTERVAL '4 hours'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO dispute_attachments (id, dispute_id, file_url, created_at, updated_at) VALUES
('77777777-7777-7777-7777-777777777772', '77777777-7777-7777-7777-777777777771', 'https://res.cloudinary.com/xmxguqpi/image/upload/v1793219663/danasea_demo_data_package/evidence/delay_receipt.jpg', CURRENT_TIMESTAMP - INTERVAL '4 hours', CURRENT_TIMESTAMP - INTERVAL '4 hours')
ON CONFLICT (id) DO NOTHING;

-- 7. Payout Request #10 set to REQUESTED (for AD-03 Admin Payout Approval)
UPDATE payout_requests
SET status = 'REQUESTED', processed_by = NULL, processed_at = NULL, updated_at = CURRENT_TIMESTAMP
WHERE id = '00090000-0000-0000-0000-000000000010';

-- 8. Vendor 9 (Surf Shack Da Nang) set to PENDING (for AD-01 Admin Vendor Onboarding Approval)
UPDATE vendors
SET verification_status = 'PENDING', verified_by = NULL, verified_at = NULL, updated_at = CURRENT_TIMESTAMP
WHERE id = '5a53c8e5-3a76-5383-b104-17b22d4af50f';

UPDATE vendor_documents
SET status = 'PENDING', reviewed_by = NULL, reviewed_at = NULL, updated_at = CURRENT_TIMESTAMP
WHERE vendor_id = '5a53c8e5-3a76-5383-b104-17b22d4af50f';

-- 9. Slots for Today (2026-11-06) and Tomorrow (2026-11-07 07:30 - 08:00)
INSERT INTO service_slots (
    id, service_id, date, start_time, end_time, capacity, booked_count, status, inventory_type, created_at, updated_at
) VALUES
('88888888-8888-8888-8888-888888888801', '2cdfabcf-13a2-550a-8f9e-283aad7053c9', CURRENT_DATE, '08:30:00', '11:00:00', 6, 2, 'OPEN', 'SHARED_CAPACITY_UNITS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('88888888-8888-8888-8888-888888888802', '4190046d-112a-5b93-beb2-bcc8b1cd16d4', CURRENT_DATE, '14:30:00', '16:30:00', 8, 0, 'OPEN', 'PERSON_LIMIT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('88888888-8888-8888-8888-888888888803', '2cdfabcf-13a2-550a-8f9e-283aad7053c9', CURRENT_DATE + INTERVAL '1 day', '07:30:00', '09:30:00', 6, 0, 'OPEN', 'SHARED_CAPACITY_UNITS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('88888888-8888-8888-8888-888888888804', '3758c297-54ff-5887-89bd-62220227570b', CURRENT_DATE + INTERVAL '1 day', '08:00:00', '10:00:00', 8, 0, 'OPEN', 'PERSON_LIMIT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

COMMIT;
"""
    cmd = ['docker', 'exec', '-i', 'pbl6-postgres-1', 'psql', '-U', 'danasea_user', '-d', 'danasea_db']
    res = subprocess.run(cmd, input=sql.encode('utf-8'), capture_output=True)
    print("STDOUT:\n", res.stdout.decode('utf-8', errors='replace').encode('ascii', errors='replace').decode())
    if res.stderr:
        print("STDERR:\n", res.stderr.decode('utf-8', errors='replace').encode('ascii', errors='replace').decode())
    if res.returncode != 0:
        sys.exit(1)

if __name__ == '__main__':
    main()
