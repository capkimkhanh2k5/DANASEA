-- DANASEA — SCRIPT SEED DỮ LIỆU DEMO SERVICES (18 TRẢI NGHIỆM BIỂN CHUẨN ĐÀ NẴNG)
-- Mục đích: Nạp dữ liệu mẫu phong phú cho 6 nhóm bộ môn thể thao biển Đà Nẵng
-- Cách chạy: scripts/seed_demo_services.bat HOẶC docker exec -i pbl6-postgres-1 psql -U danasea_user -d danasea_db < docs/Data/seed_demo_services.sql

BEGIN;
SET client_encoding = 'UTF8';

-- =========================================================================
-- 1. BẢNG SERVICES (18 Trải nghiệm phân bổ 6 danh mục, 100% PUBLISHED)
-- =========================================================================
INSERT INTO services (
    id, vendor_id, category_id, name, name_en, slug,
    description, description_en, price, duration_minutes, capacity_per_slot,
    location_name, address, latitude, longitude,
    weather_sensitive, min_wind_kmh, max_wavem,
    avg_rating, rating_count, view_count, status,
    waiver_content, waiver_content_en, waiver_required, waiver_version,
    created_at, updated_at
) VALUES 
-- ==========================================
-- DANH MỤC 1: Chèo SUP & Kayak (cheo-sup-kayak)
-- ==========================================
(
    'd0000001-0000-0000-0000-000000000001',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000001',
    'Chèo SUP Đón Bình Minh Biển Mỹ Khê & Mân Thái',
    'Sunrise SUP Paddleboarding at My Khe & Man Thai',
    'cheo-sup-don-binh-minh-my-khe-man-thai',
    'Trải nghiệm chèo ván đứng SUP ngắm bình minh trên mặt vịnh Mân Thái tĩnh lặng, kèm huấn luyện viên và chụp ảnh kỷ niệm.',
    'Experience sunrise stand-up paddleboarding in serene Man Thai waters with professional guide and photoshoot included.',
    280000.00, 120, 15,
    'Bãi biển Mân Thái, Sơn Trà', 'Đường Hoàng Sa, Phường Mân Thái, Quận Sơn Trà, TP. Đà Nẵng',
    16.0890, 108.2495,
    true, 5.0, 0.8,
    4.98, 342, 1850, 'PUBLISHED',
    'Khách hàng cam kết đủ sức khỏe tham gia, bắt buộc mặc áo phao và tuân thủ hướng dẫn của huấn luyện viên.',
    'Participants must confirm good health, wear life vests at all times, and follow instructor safety guidance.',
    true, 1,
    NOW(), NOW()
),
(
    'd0000001-0000-0000-0000-000000000011',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000001',
    'Tour Chèo Kayak Khám Phá Hang Bà Đính - Sơn Trà',
    'Kayak Expedition to Ba Dinh Sea Cave - Son Tra',
    'tour-cheo-kayak-kham-pha-hang-ba-dinh-son-tra',
    'Hành trình chèo thuyền Kayak đôi men theo vách đá hùng vĩ của Bán đảo Sơn Trà, luồn qua hang đá biển hoang sơ tuyệt đẹp.',
    'Double-kayak expedition along the rugged cliffs of Son Tra Peninsula exploring secluded ocean caves.',
    320000.00, 150, 12,
    'Vịnh Bãi Bụt, Bán đảo Sơn Trà', 'Khu du lịch Bãi Bụt, Quận Sơn Trà, TP. Đà Nẵng',
    16.1050, 108.2650,
    true, 5.0, 0.9,
    4.94, 182, 1240, 'PUBLISHED',
    'Yêu cầu trang bị áo phao suốt hành trình, tuân thủ hướng dẫn giữ cự ly an toàn với vách đá của đội cứu hộ.',
    'Life jackets mandatory throughout tour; strictly adhere to guide safety distance from rock faces.',
    true, 1,
    NOW(), NOW()
),
(
    'd0000001-0000-0000-0000-000000000012',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000001',
    'Khóa Học SUP Cấp Tốc & Chụp Ảnh Flycam Bình Minh',
    'Crash SUP Clinic with Sunrise 4K Drone Media',
    'khoa-hoc-sup-cap-toc-chup-anh-flycam-binh-minh',
    'Học kỹ năng chèo SUP, giữ thăng bằng và cứu hộ nước trong 30 phút, sau đó tự do trải nghiệm và nhận video flycam 4K kỷ niệm.',
    'Learn paddle techniques, balance control and rescue drills in 30 mins, then paddle freely with 4K drone video included.',
    350000.00, 120, 10,
    'Bãi tắm Phạm Văn Đồng, Mỹ Khê', 'Ngã ba Phạm Văn Đồng & Võ Nguyên Giáp, Sơn Trà, TP. Đà Nẵng',
    16.0712, 108.2460,
    true, 5.0, 0.7,
    4.96, 129, 920, 'PUBLISHED',
    'Khách hàng đồng ý cho phép ghi hình và cam kết tuân theo hiệu lệnh điều phối của huấn luyện viên.',
    'Participants consent to aerial filming and agree to comply with coach directions at all times.',
    true, 1,
    NOW(), NOW()
),

-- ==========================================
-- DANH MỤC 2: Lặn Biển San Hô (lan-ngam-san-ho)
-- ==========================================
(
    'd0000001-0000-0000-0000-000000000002',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000002',
    'Lặn Bình Khí Khám Phá Rạn San Hô Hòn Sụp Bán Đảo Sơn Trà',
    'Scuba Diving at Hon Sup Coral Reefs - Son Tra Peninsula',
    'lan-binh-khi-san-ho-hon-sup-son-tra',
    'Khám phá hệ sinh thái san hô tự nhiên tại Hòn Sụp cùng huấn luyện viên PADI chuyên nghiệp, trang bị bình dưỡng khí cao cấp.',
    'Explore pristine natural coral reefs at Hon Sup with certified PADI instructors and professional diving gear.',
    550000.00, 210, 10,
    'Hòn Sụp, Bán đảo Sơn Trà', 'Khu vực biển Hòn Sụp, Bán đảo Sơn Trà, TP. Đà Nẵng',
    16.1132, 108.2758,
    true, 0.0, 0.7,
    4.96, 188, 1420, 'PUBLISHED',
    'Khách hàng xác nhận không có tiền sử bệnh tim mạch, huyết áp hoặc hen suyễn; tuân thủ quy chuẩn an toàn lặn biển PADI.',
    'Participants certify no history of cardiac, hypertension or asthma conditions and comply with PADI diving safety rules.',
    true, 1,
    NOW(), NOW()
),
(
    'd0000001-0000-0000-0000-000000000021',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000002',
    'Đi Bộ Dưới Đáy Biển Seawalker Chiêm Ngưỡng San Hô Bãi Rạng',
    'Seawalker Ocean Trek at Bai Rang Coral Reserve',
    'di-bo-duoi-bien-seawalker-san-ho-bai-rang',
    'Trải nghiệm bước đi nhẹ nhàng trên đáy biển với mũ dưỡng khí tiêu chuẩn Nhật Bản, không cần biết bơi vẫn thở bình thường như trên cạn.',
    'Trek on the ocean floor wearing Japanese dive helmets, breathe naturally underwater without needing swimming skills.',
    750000.00, 120, 16,
    'Bãi Rạng, Sơn Trà', 'Khu vực bãi đá tự nhiên Bãi Rạng, Sơn Trà, TP. Đà Nẵng',
    16.1200, 108.2800,
    true, 0.0, 0.8,
    4.97, 245, 1780, 'PUBLISHED',
    'Khách hàng cam kết không chạm tay, giẫm đạp làm hư hại rạn san hô; tuân theo người dẫn đường 100% thời gian dưới nước.',
    'Strictly avoid touching or stepping on coral structures; follow underwater guide instructions at all times.',
    true, 1,
    NOW(), NOW()
),
(
    'd0000001-0000-0000-0000-000000000022',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000002',
    'Lặn Snorkeling Nước Nông Kèm Cano Vận Chuyển Mũi Nghê',
    'Coastal Snorkeling Tour by Speedboat to Mui Nghe',
    'lan-snorkeling-nuoc-nong-cano-mui-nghe',
    'Cano cao tốc đưa khách ra bãi lặn Mũi Nghê nước trong vắt nhìn thấu đáy; trang bị kính lặn ống thở và áo phao chống chìm.',
    'Speedboat transport to crystalline waters of Mui Nghe for shallow snorkeling with full snorkel mask and safety vests.',
    390000.00, 150, 18,
    'Mũi Nghê, Đông Nam Sơn Trà', 'Bờ đông nam Bán đảo Sơn Trà, TP. Đà Nẵng',
    16.1280, 108.3100,
    true, 0.0, 0.9,
    4.91, 156, 1100, 'PUBLISHED',
    'Người tham gia luôn mặc áo phao chuyên dụng, không bơi tách đoàn ra khỏi phạm vi giám sát của cano bảo trợ.',
    'Wear safety buoyancy vests continuously and remain within designated snorkeling boundaries monitored by speedboat.',
    true, 1,
    NOW(), NOW()
),

-- ==========================================
-- DANH MỤC 3: Cano & Dù Bay Biển (cano-du-bay)
-- ==========================================
(
    'd0000001-0000-0000-0000-000000000003',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000003',
    'Bay Dù Lượn Cano Kéo Bãi Biển Mỹ Khê',
    'Tandem Parasailing Flight at My Khe Beach',
    'bay-du-luon-cano-keo-bien-my-khe',
    'Bay lượn trên độ cao 70m ngắm toàn cảnh bãi biển Mỹ Khê và tượng Phật Bà Sơn Trà, cano cao tốc đời mới kéo dù an toàn.',
    'Fly 70m above stunning My Khe Beach with panoramic views of Son Tra coastline towed by high-speed speedboat.',
    500000.00, 60, 20,
    'Bãi biển Mỹ Khê', 'Bãi tắm số 1 Võ Nguyên Giáp, Quận Sơn Trà, TP. Đà Nẵng',
    16.0592, 108.2435,
    true, 10.0, 1.0,
    4.95, 210, 2100, 'PUBLISHED',
    'Tuân thủ quy chuẩn an toàn ASTM F3099, tải trọng tối đa 160kg, không mang vật sắc nhọn hoặc thiết bị rơi rớt khi bay.',
    'Complies with ASTM F3099 safety standards, maximum weight 160kg, no loose items permitted during flight.',
    true, 1,
    NOW(), NOW()
),
(
    'd0000001-0000-0000-0000-000000000031',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000003',
    'Cano Cao Tốc Du Ngoạn Toàn Cảnh Vịnh Sơn Trà',
    'Express Speedboat Cruise Around Son Tra Peninsula',
    'cano-cao-toc-du-ngoan-toan-canh-vinh-son-tra',
    'Lướt sóng tốc độ cao quanh bán đảo, chiêm ngưỡng Bãi Bụt, Bãi Nam, Bãi Bắc và ngọn hải đăng Sơn Trà cổ kính từ góc nhìn biển.',
    'High-speed cruise circling the peninsula to admire Bai But, Bai Nam, Bai Bac and the historic Son Tra Lighthouse.',
    400000.00, 90, 24,
    'Bến thủy nội địa Sơn Trà', 'Cảng Tiên Sa, Đường Yết Kiêu, Quận Sơn Trà, TP. Đà Nẵng',
    16.1250, 108.2180,
    true, 5.0, 1.1,
    4.92, 178, 1310, 'PUBLISHED',
    'Cài quai áo phao trong suốt thời gian cano di chuyển, phụ nữ mang thai và người có bệnh cột sống không nên tham gia.',
    'Keep life vests fastened during transit; pregnant women and individuals with spine conditions are advised not to join.',
    true, 1,
    NOW(), NOW()
),
(
    'd0000001-0000-0000-0000-000000000032',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000003',
    'Bay Dù Đôi Hoàng Hôn Kèm Quay Phim Flycam 4K Mỹ Khê',
    'Sunset Couple Parasailing with 4K Drone Footage',
    'bay-du-doi-hoang-hon-quay-flycam-my-khe',
    'Trải nghiệm bay dù đôi lãng mạn trong ánh hoàng hôn buông xuống vịnh Đà Nẵng, flycam ghi lại toàn bộ khoảnh khắc từ trên cao.',
    'Romantic couple parasailing during golden sunset hours over Da Nang Bay with cinematic 4K drone videography.',
    950000.00, 60, 12,
    'Bãi tắm Sao Biển, Mỹ Khê', 'Đường Võ Nguyên Giáp, Phường Mỹ An, Quận Ngũ Hành Sơn, TP. Đà Nẵng',
    16.0450, 108.2470,
    true, 8.0, 0.9,
    4.99, 114, 1620, 'PUBLISHED',
    'Tổng cân nặng cặp đôi từ 80kg đến 150kg, tuân thủ hiệu lệnh của kỹ thuật viên cất và hạ dù trên sàn cano.',
    'Total combined weight 80-150kg; strictly follow technicians launch and winch recovery commands.',
    true, 1,
    NOW(), NOW()
),

-- ==========================================
-- DANH MỤC 4: Mô Tô Nước Jetski (mo-to-nuoc-jetski)
-- ==========================================
(
    'd0000001-0000-0000-0000-000000000004',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000004',
    'Thuê Mô Tô Nước Jetski Cảm Giác Mạnh Vịnh Đà Nẵng',
    'High-Speed Jetski Wave Riding in Da Nang Bay',
    'thue-mo-to-nuoc-jetski-cam-giac-manh',
    'Tự do lái mô tô nước Yamaha công suất lớn rẽ sóng tốc độ cao trong vùng biển tiêu chuẩn an toàn ngoài vùng tắm Mỹ Khê.',
    'Experience high-speed Yamaha watercraft wave riding within designated marine zones outside swimmer boundaries.',
    450000.00, 30, 8,
    'Biển Mỹ Khê - Khu thể thao', 'Bãi biển Võ Nguyên Giáp, Phường Phước Mỹ, Quận Sơn Trà, TP. Đà Nẵng',
    16.0592, 108.2435,
    true, 0.0, 1.0,
    4.92, 165, 1340, 'PUBLISHED',
    'Người lái phải đủ 18 tuổi, mặc áo phao có còi cứu sinh, chỉ chạy trong vùng phao tiêu quy định theo Thông tư 17/2018.',
    'Drivers must be 18+, wear life vests with whistle, and operate strictly inside buoy-marked sport zones.',
    true, 1,
    NOW(), NOW()
),
(
    'd0000001-0000-0000-0000-000000000041',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000004',
    'Trải Nghiệm Lái Jetski Đôi Cùng Huấn Luyện Viên 45 Phút',
    'Tandem Jetski Masterclass with Pro Instructor 45 Mins',
    'trai-nghiem-jetski-doi-huan-luyen-vien-45-phut',
    'Khóa trải nghiệm mô tô nước chuyên nghiệp dành cho người mới: HLV hướng dẫn kỹ thuật bứt tốc, drift trên sóng và xử lý an toàn.',
    'Professional watercraft masterclass for novices: learn acceleration thrills, ocean drifts and safety handling.',
    650000.00, 45, 6,
    'Bãi Bụt, Sơn Trà', 'Khu thể thao biển Bãi Bụt, Sơn Trà, TP. Đà Nẵng',
    16.1040, 108.2630,
    true, 0.0, 1.0,
    4.95, 98, 880, 'PUBLISHED',
    'Gắn dây khóa ngắt động cơ khẩn cấp (kill-switch lanyard) vào cổ tay trước khi khởi động máy.',
    'Attach engine emergency kill-switch lanyard to wrist before starting the ignition.',
    true, 1,
    NOW(), NOW()
),
(
    'd0000001-0000-0000-0000-000000000042',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000004',
    'Tour Mô Tô Nước Khám Phá Tuyến Hải Trình Mân Thái - Bãi Bụt',
    'Guided Sea Safari Jetski Tour: Man Thai to Bai But',
    'tour-mo-to-nuoc-hai-trinh-man-thai-bai-but',
    'Hải trình đoàn Jetski có đội cứu hộ dẫn đường lướt sóng từ bờ biển Mân Thái đến vách đá Bãi Bụt ngắm Chùa Linh Ứng từ biển.',
    'Guided sea-safari convoy from Man Thai Beach to Bai But cove offering unique seaward panoramas of Linh Ung Pagoda.',
    850000.00, 60, 8,
    'Bãi biển Mân Thái', 'Đường Hoàng Sa, Quận Sơn Trà, TP. Đà Nẵng',
    16.0890, 108.2495,
    true, 0.0, 1.1,
    4.96, 84, 1050, 'PUBLISHED',
    'Tuyệt đối tuân theo tốc độ dẫn đoàn và giữ khoảng cách tối thiểu 30m giữa hai phương tiện mô tô nước.',
    'Maintain strictly guided convoy speed and keep a minimum 30-meter safe distance between craft.',
    true, 1,
    NOW(), NOW()
),

-- ==========================================
-- DANH MỤC 5: Trượt Phao Chuối (truot-phao-chuoi)
-- ==========================================
(
    'd0000001-0000-0000-0000-000000000005',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000005',
    'Trượt Phao Chuối Tốc Độ Cao Cho Nhóm Bạn',
    'High-Speed Banana Boat Ride for Group',
    'truot-phao-chuoi-toc-do-cao-nhom-ban',
    'Hoạt động gắn kết nhóm cực kỳ phấn khích: cano kéo phao chuối lướt trên đầu sóng tạo những pha rẽ cua ngoạn mục.',
    'Exciting group water activity: high-speed banana boat rides skimming ocean waves with thrilling spray turns.',
    200000.00, 45, 24,
    'Bãi biển Mỹ Khê', 'Bãi tắm Phạm Văn Đồng, Quận Sơn Trà, TP. Đà Nẵng',
    16.0592, 108.2435,
    true, 0.0, 0.8,
    4.90, 142, 980, 'PUBLISHED',
    'Người tham gia bắt buộc mặc áo phao cài khóa an toàn, giữ chặt tay nắm phao và biết bơi cơ bản.',
    'All riders must fasten life vests, hold secure grip handles firmly, and possess basic water survival skills.',
    true, 1,
    NOW(), NOW()
),
(
    'd0000001-0000-0000-0000-000000000051',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000005',
    'Phao Bay Cảm Giác Mạnh Flying Fish Lướt Sóng Mỹ Khê',
    'Flying Fish Inflatable Ocean Glide Ride',
    'phao-bay-cam-giac-manh-flying-fish-my-khe',
    'Cảm giác phấn khích tột độ khi phao chuyên dụng nâng bổng lên khỏi mặt nước 2-3 mét nhờ sức nâng khí động học và cano tốc độ cao.',
    'Extreme adrenaline rush as aerodynamic inflatable raft lifts 2-3m off water surface towed by express powerboat.',
    250000.00, 30, 12,
    'Bãi tắm số 2 Mỹ Khê', 'Đường Võ Nguyên Giáp, Quận Sơn Trà, TP. Đà Nẵng',
    16.0620, 108.2450,
    true, 5.0, 0.8,
    4.93, 118, 860, 'PUBLISHED',
    'Không buông tay khỏi quai giữ lực khi phao cất cánh; tuân thủ trọng lượng đồng đều hai bên cánh phao.',
    'Hold handles firmly during lift-off; balance passenger weights evenly on both sides of the raft.',
    true, 1,
    NOW(), NOW()
),
(
    'd0000001-0000-0000-0000-000000000052',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000005',
    'Combo Phao Chuối & Phao Tròn Cảm Giác Mạnh Nhóm 6 Người',
    'Action Water Pack: Banana Boat & Donut Tube for 6',
    'combo-phao-chuoi-phao-tron-nhom-6-nguoi',
    'Gói trải nghiệm liên hoàn 2 bộ môn phao kéo hot nhất mùa hè dành riêng cho nhóm bạn bè, công ty hoặc gia đình trẻ.',
    'Back-to-back package featuring banana boat sprints and donut tube spinning action tailored for group fun.',
    990000.00, 60, 18,
    'Khu giải trí biển Mân Thái', 'Bãi biển Mân Thái, Quận Sơn Trà, TP. Đà Nẵng',
    16.0880, 108.2490,
    true, 0.0, 0.8,
    4.94, 92, 750, 'PUBLISHED',
    'Áo phao thể thao chuyên dụng được phát cho từng khách, có thuyền cứu hộ túc trực song song 100% thời gian.',
    'Dedicated life vests provided for all guests; rescue boat on active standby throughout session.',
    true, 1,
    NOW(), NOW()
),

-- ==========================================
-- DANH MỤC 6: Du Thuyền Hoàng Hôn (du-thuyen-ngam-hoang-hon)
-- ==========================================
(
    'd0000001-0000-0000-0000-000000000006',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000006',
    'Du Thuyền Hạng Sang Ngắm Hoàng Hôn Vịnh Đà Nẵng',
    'Luxury Sunset Catamaran Yacht Tour',
    'du-thuyen-hang-sang-ngam-hoang-hon-vinh-da-nang',
    'Du ngoạn ngắm hoàng hôn rực rỡ từ bến DHC Marina xuôi dòng sông Hàn ra vịnh, thưởng thức trà chiều, rượu vang và nhạc sống.',
    'Sail into breathtaking sunset waters from DHC Marina out to Da Nang Bay with afternoon tea, wine, and live acoustics.',
    1200000.00, 120, 30,
    'Bến du thuyền DHC Marina', 'Đường Trần Hưng Đạo, Quận Sơn Trà, TP. Đà Nẵng',
    16.0680, 108.2250,
    true, 0.0, 1.2,
    5.00, 96, 2400, 'PUBLISHED',
    'Tuân thủ quy tắc an toàn đường thủy nội địa và hàng hải, có mặt tại bến trước 20 phút giờ khởi hành.',
    'Comply with inland waterway and maritime safety guidelines, arrive at marina dock 20 minutes before departure.',
    true, 1,
    NOW(), NOW()
),
(
    'd0000001-0000-0000-0000-000000000061',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000006',
    'Tiệc Du Thuyền Riêng Tư Ngắm Cầu Rồng Phun Lửa & Pháo Hoa',
    'Private VIP Yacht Charter: Dragon Bridge Fire & Lights',
    'tiec-du-thuyen-rieng-tu-ngam-cau-rong-phun-lua',
    'Trải nghiệm đẳng cấp thượng lưu: bao trọn du thuyền 2 thân phục vụ tiệc BBQ hải sản, champagne và ngắm Cầu Rồng phun lửa.',
    'Exclusive private catamaran charter featuring seafood BBQ buffet, premium champagne and prime Dragon Bridge fire show.',
    3500000.00, 180, 15,
    'Bến du thuyền Sông Hàn', 'Đường Bạch Đằng, Quận Hải Châu, TP. Đà Nẵng',
    16.0720, 108.2240,
    false, 0.0, 1.0,
    5.00, 68, 3100, 'PUBLISHED',
    'Hành khách tuân thủ quy định không hút thuốc trong khoang kín và không di chuyển ra mạn tàu khi chưa có hướng dẫn.',
    'No smoking permitted inside closed cabins; avoid moving onto outer railings without crew clearance.',
    true, 1,
    NOW(), NOW()
),
(
    'd0000001-0000-0000-0000-000000000062',
    '22222222-2222-2222-2222-222222222221',
    'a0000001-0000-0000-0000-000000000006',
    'Du Thuyền Buồm Catamaran Khám Phá Rạn Biển Nam Bán Đảo',
    'Catamaran Sailing Cruise: South Peninsula Ocean Escapes',
    'du-thuyen-buom-catamaran-kham-pha-ran-bien-nam',
    'Hành trình giương buồm đón gió biển đông, ngắm cảnh bãi rạn hoang sơ, câu cá giải trí và tắm biển giữa làn nước ngọc bích.',
    'Set sail across East Sea breeze to explore secluded southern reefs, enjoy recreational sea angling and crystal swims.',
    1800000.00, 240, 20,
    'Bến du thuyền Quốc Tế', 'Đường Như Nguyệt, Phường Thuận Phước, Quận Hải Châu, TP. Đà Nẵng',
    16.0840, 108.2190,
    true, 0.0, 1.2,
    4.98, 85, 1450, 'PUBLISHED',
    'Bắt buộc mang giày đế mềm chống trượt khi di chuyển trên boong tàu buồm, mặc áo phao khi xuống lưới thư giãn.',
    'Soft non-marking deck shoes mandatory; life vests required when lounging on ocean trampoline nets.',
    true, 1,
    NOW(), NOW()
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    price = EXCLUDED.price,
    avg_rating = EXCLUDED.avg_rating,
    rating_count = EXCLUDED.rating_count,
    view_count = EXCLUDED.view_count,
    status = 'PUBLISHED',
    updated_at = NOW();

-- =========================================================================
-- 2. BẢNG SERVICE_IMAGES (Hình ảnh đại dương Unsplash sắc nét)
-- =========================================================================
INSERT INTO service_images (id, service_id, url, sort_order, created_at, updated_at) VALUES
-- SUP Images
('d0000002-0000-0000-0000-000000000001', 'd0000001-0000-0000-0000-000000000001', 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),
('d0000002-0000-0000-0000-000000000002', 'd0000001-0000-0000-0000-000000000001', 'https://images.unsplash.com/photo-1544551763-8dd44758c2dd?auto=format&fit=crop&w=800&q=80', 2, NOW(), NOW()),
('d0000002-0000-0000-0000-000000000011', 'd0000001-0000-0000-0000-000000000011', 'https://images.unsplash.com/photo-1472745433479-4556f22e32c2?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),
('d0000002-0000-0000-0000-000000000012', 'd0000001-0000-0000-0000-000000000012', 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),

-- Diving Images
('d0000002-0000-0000-0000-000000000003', 'd0000001-0000-0000-0000-000000000002', 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),
('d0000002-0000-0000-0000-000000000004', 'd0000001-0000-0000-0000-000000000002', 'https://images.unsplash.com/photo-1682687220063-4742bd7fd538?auto=format&fit=crop&w=800&q=80', 2, NOW(), NOW()),
('d0000002-0000-0000-0000-000000000021', 'd0000001-0000-0000-0000-000000000021', 'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),
('d0000002-0000-0000-0000-000000000022', 'd0000001-0000-0000-0000-000000000022', 'https://images.unsplash.com/photo-1544551763-77ef2d0cfc6c?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),

-- Parasailing & Cano Images
('d0000002-0000-0000-0000-000000000005', 'd0000001-0000-0000-0000-000000000003', 'https://images.unsplash.com/photo-1605559424843-9e4c228bf1c2?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),
('d0000002-0000-0000-0000-000000000031', 'd0000001-0000-0000-0000-000000000031', 'https://images.unsplash.com/photo-1559136555-9303baea8ebd?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),
('d0000002-0000-0000-0000-000000000032', 'd0000001-0000-0000-0000-000000000032', 'https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),

-- Jetski Images
('d0000002-0000-0000-0000-000000000006', 'd0000001-0000-0000-0000-000000000004', 'https://images.unsplash.com/photo-1569263979104-865ab7cd8d13?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),
('d0000002-0000-0000-0000-000000000041', 'd0000001-0000-0000-0000-000000000041', 'https://images.unsplash.com/photo-1544551763-77ef2d0cfc6c?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),
('d0000002-0000-0000-0000-000000000042', 'd0000001-0000-0000-0000-000000000042', 'https://images.unsplash.com/photo-1569263979104-865ab7cd8d13?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),

-- Banana Boat Images
('d0000002-0000-0000-0000-000000000007', 'd0000001-0000-0000-0000-000000000005', 'https://images.unsplash.com/photo-1519046904884-53103b34b206?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),
('d0000002-0000-0000-0000-000000000051', 'd0000001-0000-0000-0000-000000000051', 'https://images.unsplash.com/photo-1502680390469-be75c86b636f?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),
('d0000002-0000-0000-0000-000000000052', 'd0000001-0000-0000-0000-000000000052', 'https://images.unsplash.com/photo-1519046904884-53103b34b206?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),

-- Yacht Images
('d0000002-0000-0000-0000-000000000008', 'd0000001-0000-0000-0000-000000000006', 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),
('d0000002-0000-0000-0000-000000000061', 'd0000001-0000-0000-0000-000000000061', 'https://images.unsplash.com/photo-1544551763-8dd44758c2dd?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW()),
('d0000002-0000-0000-0000-000000000062', 'd0000001-0000-0000-0000-000000000062', 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80', 1, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- =========================================================================
-- 3. BẢNG SERVICE_OPTIONS (Gói dịch vụ chi tiết)
-- =========================================================================
INSERT INTO service_options (
    id, service_id, name, option_type, pricing_unit, price, max_pax_per_package, benefits, status, created_at, updated_at
) VALUES
('d0000003-0000-0000-0000-000000000001', 'd0000001-0000-0000-0000-000000000001', 'Vé Tiêu Chuẩn (Kèm Hướng Dẫn & Ảnh)', 'SHARED', 'PER_PERSON', 280000.00, 1, 'Ván SUP, áo phao, mái chèo carbon, hướng dẫn viên 15 phút, bộ 10 ảnh kỷ niệm kỹ thuật số.', 'ACTIVE', NOW(), NOW()),
('d0000003-0000-0000-0000-000000000011', 'd0000001-0000-0000-0000-000000000011', 'Kayak Đôi (Giá Cho 2 Người)', 'SHARED', 'PER_PERSON', 320000.00, 2, 'Kayak đôi cao cấp, túi chống nước điện thoại, hướng dẫn viên dẫn đường vào hang.', 'ACTIVE', NOW(), NOW()),
('d0000003-0000-0000-0000-000000000012', 'd0000001-0000-0000-0000-000000000012', 'Vé SUP + Video Flycam 4K', 'SHARED', 'PER_PERSON', 350000.00, 1, 'Trọn gói thiết bị, huấn luyện viên 1 kèm 3, video flycam 4K biên tập hoàn thiện.', 'ACTIVE', NOW(), NOW()),

('d0000003-0000-0000-0000-000000000002', 'd0000001-0000-0000-0000-000000000002', 'Lặn Biển Cơ Bản (HLV 1 kèm 2)', 'SHARED', 'PER_PERSON', 550000.00, 1, 'Bộ đồ lặn cao cấp, bình khí, kính lặn, chân vịt, HLV theo sát 100% thời gian.', 'ACTIVE', NOW(), NOW()),
('d0000003-0000-0000-0000-000000000021', 'd0000001-0000-0000-0000-000000000021', 'Vé Đi Bộ Dưới Biển Seawalker', 'SHARED', 'PER_PERSON', 750000.00, 1, 'Mũ dưỡng khí seawalker, lặn bộ dưới rạn san hô 20 phút, thợ lặn chuyên nghiệp bảo trợ.', 'ACTIVE', NOW(), NOW()),
('d0000003-0000-0000-0000-000000000022', 'd0000001-0000-0000-0000-000000000022', 'Vé Snorkeling Bán Đảo Kèm Cano', 'SHARED', 'PER_PERSON', 390000.00, 1, 'Cano khứ hồi, kính lặn, ống thở silicone y tế, áo phao, nước suối.', 'ACTIVE', NOW(), NOW()),

('d0000003-0000-0000-0000-000000000003', 'd0000001-0000-0000-0000-000000000003', 'Bay Đơn Tiêu Chuẩn (1 người)', 'SHARED', 'PER_PERSON', 500000.00, 1, 'Bay 10 phút trên không, cano kéo tốc độ cao, áo phao đạt chuẩn ASTM.', 'ACTIVE', NOW(), NOW()),
('d0000003-0000-0000-0000-000000000031', 'd0000001-0000-0000-0000-000000000031', 'Tour Cano Cao Tốc Tham Quan Vịnh', 'SHARED', 'PER_PERSON', 400000.00, 1, 'Tour 90 phút quanh bán đảo Sơn Trà, áo phao cao cấp, hướng dẫn viên nhiệt tình.', 'ACTIVE', NOW(), NOW()),
('d0000003-0000-0000-0000-000000000032', 'd0000001-0000-0000-0000-000000000032', 'Gói Bay Dù Đôi Hoàng Hôn VIP', 'SHARED', 'PER_PERSON', 950000.00, 2, 'Bay đôi 15 phút thời khắc hoàng hôn vàng, cano cao tốc kéo, video flycam sắc nét.', 'ACTIVE', NOW(), NOW()),

('d0000003-0000-0000-0000-000000000004', 'd0000001-0000-0000-0000-000000000004', 'Thuê Jetski 30 Phút Tự Lái', 'SHARED', 'PER_PERSON', 450000.00, 1, 'Mô tô nước Yamaha FX Cruiser, xăng đầy bình, hướng dẫn an toàn 5 phút.', 'ACTIVE', NOW(), NOW()),
('d0000003-0000-0000-0000-000000000041', 'd0000001-0000-0000-0000-000000000041', 'Khóa Jetski Masterclass 45 Phút', 'SHARED', 'PER_PERSON', 650000.00, 1, 'HLV kèm 1-1, học kỹ năng lướt sóng lớn và drift ôm cua tốc độ cao.', 'ACTIVE', NOW(), NOW()),
('d0000003-0000-0000-0000-000000000042', 'd0000001-0000-0000-0000-000000000042', 'Vé Tour Jetski Safari Bãi Bụt', 'SHARED', 'PER_PERSON', 850000.00, 1, 'Đoàn Jetski lướt qua các bãi biển đẹp nhất Sơn Trà kèm đội cano cứu hộ bảo trợ.', 'ACTIVE', NOW(), NOW()),

('d0000003-0000-0000-0000-000000000005', 'd0000001-0000-0000-0000-000000000005', 'Vé Trượt Phao Chuối Cá Nhân', 'SHARED', 'PER_PERSON', 200000.00, 1, 'Lướt sóng 20 phút cùng nhóm, áo phao chuyên dụng chống va đập.', 'ACTIVE', NOW(), NOW()),
('d0000003-0000-0000-0000-000000000051', 'd0000001-0000-0000-0000-000000000051', 'Vé Phao Bay Flying Fish', 'SHARED', 'PER_PERSON', 250000.00, 1, 'Bay lượn trên ngọn sóng 15 phút, trải nghiệm cảm giác cất cánh khỏi mặt nước.', 'ACTIVE', NOW(), NOW()),
('d0000003-0000-0000-0000-000000000052', 'd0000001-0000-0000-0000-000000000052', 'Vé Combo Nhóm 6 Người', 'SHARED', 'PER_PERSON', 990000.00, 6, 'Trọn gói trượt phao chuối và phao tròn 45 phút cho nhóm tối đa 6 khách.', 'ACTIVE', NOW(), NOW()),

('d0000003-0000-0000-0000-000000000006', 'd0000001-0000-0000-0000-000000000006', 'Vé Du Thuyền Hoàng Hôn VIP', 'SHARED', 'PER_PERSON', 1200000.00, 1, 'Du ngoạn 2 tiếng trên sông Hàn & vịnh Đà Nẵng, tiệc trà, rượu vang, canapé nhẹ.', 'ACTIVE', NOW(), NOW()),
('d0000003-0000-0000-0000-000000000061', 'd0000001-0000-0000-0000-000000000061', 'Bao Trọn Du Thuyền Tiệc Cầu Rồng', 'SHARED', 'PER_PERSON', 3500000.00, 10, 'Bao trọn tàu cho đoàn gia đình/công ty 10 khách, tiệc BBQ hải sản, champagne Pháp.', 'ACTIVE', NOW(), NOW()),
('d0000003-0000-0000-0000-000000000062', 'd0000001-0000-0000-0000-000000000062', 'Vé Du Thuyền Buồm Khám Phá Rạn San Hô', 'SHARED', 'PER_PERSON', 1800000.00, 1, 'Hành trình 4 tiếng trên du thuyền buồm Catamaran, câu cá giải trí, bơi lội giữa vịnh.', 'ACTIVE', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- =========================================================================
-- 4. BẢNG SERVICE_SLOTS (Khung giờ hoạt động thực tế: Hôm nay, Ngày mai)
-- =========================================================================
INSERT INTO service_slots (id, service_id, date, start_time, end_time, capacity, booked_count, status, inventory_type, created_at, updated_at) VALUES
-- SUP Slots
('d0000004-0000-0000-0000-000000000001', 'd0000001-0000-0000-0000-000000000001', CURRENT_DATE, '05:00:00', '07:00:00', 15, 3, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000002', 'd0000001-0000-0000-0000-000000000001', CURRENT_DATE, '07:30:00', '09:30:00', 15, 1, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000003', 'd0000001-0000-0000-0000-000000000001', CURRENT_DATE + 1, '05:00:00', '07:00:00', 15, 0, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000011', 'd0000001-0000-0000-0000-000000000011', CURRENT_DATE, '06:00:00', '08:30:00', 12, 2, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000012', 'd0000001-0000-0000-0000-000000000012', CURRENT_DATE, '05:30:00', '07:30:00', 10, 4, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),

-- Diving Slots
('d0000004-0000-0000-0000-000000000004', 'd0000001-0000-0000-0000-000000000002', CURRENT_DATE, '08:00:00', '11:30:00', 10, 2, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000005', 'd0000001-0000-0000-0000-000000000002', CURRENT_DATE + 1, '08:00:00', '11:30:00', 10, 0, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000021', 'd0000001-0000-0000-0000-000000000021', CURRENT_DATE, '09:00:00', '11:00:00', 16, 5, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000022', 'd0000001-0000-0000-0000-000000000022', CURRENT_DATE, '08:30:00', '11:00:00', 18, 3, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),

-- Parasailing Slots
('d0000004-0000-0000-0000-000000000006', 'd0000001-0000-0000-0000-000000000003', CURRENT_DATE, '08:30:00', '10:30:00', 20, 4, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000007', 'd0000001-0000-0000-0000-000000000003', CURRENT_DATE, '15:00:00', '17:00:00', 20, 2, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000031', 'd0000001-0000-0000-0000-000000000031', CURRENT_DATE, '09:00:00', '10:30:00', 24, 6, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000032', 'd0000001-0000-0000-0000-000000000032', CURRENT_DATE, '16:00:00', '17:00:00', 12, 2, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),

-- Jetski Slots
('d0000004-0000-0000-0000-000000000008', 'd0000001-0000-0000-0000-000000000004', CURRENT_DATE, '09:00:00', '11:00:00', 8, 1, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000041', 'd0000001-0000-0000-0000-000000000041', CURRENT_DATE, '10:00:00', '11:30:00', 6, 1, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000042', 'd0000001-0000-0000-0000-000000000042', CURRENT_DATE, '14:30:00', '16:00:00', 8, 0, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),

-- Banana Boat Slots
('d0000004-0000-0000-0000-000000000009', 'd0000001-0000-0000-0000-000000000005', CURRENT_DATE, '10:00:00', '11:30:00', 24, 6, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000051', 'd0000001-0000-0000-0000-000000000051', CURRENT_DATE, '14:00:00', '15:30:00', 12, 3, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000052', 'd0000001-0000-0000-0000-000000000052', CURRENT_DATE, '15:30:00', '17:00:00', 18, 6, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),

-- Yacht Slots
('d0000004-0000-0000-0000-000000000010', 'd0000001-0000-0000-0000-000000000006', CURRENT_DATE, '16:30:00', '18:30:00', 30, 8, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000061', 'd0000001-0000-0000-0000-000000000061', CURRENT_DATE, '19:30:00', '21:30:00', 15, 5, 'OPEN', 'PERSON_LIMIT', NOW(), NOW()),
('d0000004-0000-0000-0000-000000000062', 'd0000001-0000-0000-0000-000000000062', CURRENT_DATE, '14:00:00', '18:00:00', 20, 4, 'OPEN', 'PERSON_LIMIT', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

COMMIT;

-- Kiểm tra tổng số dịch vụ demo trong DB
SELECT count(*) AS total_demo_services FROM services WHERE id::text LIKE 'd0000001%';
