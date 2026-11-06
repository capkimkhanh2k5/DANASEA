# DANASEA — TÀI LIỆU ĐẶC TẢ DỮ LIỆU SEED CHO CÁC TRẢI NGHIỆM BIỂN (SEED SERVICES DATA SPEC)

> **Mục đích:** Đặc tả bộ dữ liệu hạt giống (Seed Data) chuẩn mực cho 6 môn thể thao biển Đà Nẵng, bảo đảm tính toàn vẹn 100% giữa Database Schema, Báo cáo kỹ thuật hàng hải (`docs/RuleOfAct.md`), Đặc tả trang giao diện (`backend/docs/pageRequired.md`) và Giao diện người dùng (`customerHomeData.ts`).

---

## 1. NGUYÊN TẮC AN TOÀN & QUẢN TRỊ DỮ LIỆU
1. **Dải định danh cô lập (Demo ID Range):**
   - Tất cả các bản ghi demo sử dụng tiền tố UUID cố định: `d0000001-0000-0000-0000-00000000000x`.
2. **Khóa ngoại chính thức (Official Foreign Keys):**
   - `vendor_id`: `22222222-2222-2222-2222-222222222221` (*Danasea Water Sports JSC* - đã được duyệt tại `V38`).
   - `category_id`: 6 UUID danh mục chính thức từ `a0000001-...-0001` đến `0006` (từ `V6`).
3. **Cơ chế nạp & xóa 1 chạm độc lập (Zero Backend Pollution):**
   - Nạp dữ liệu: `docs/Data/seed_demo_services.sql`
   - Xóa sạch 100% dữ liệu demo: `docs/Data/cleanup_demo_services.sql`
   - Tuyệt đối không can thiệp vào thư mục Migration Flyway của Backend (`backend/src/main/resources/db/migration`).

---

## 2. MA TRẬN ĐỐI CHIẾU 6 TRẢI NGHIỆM BIỂN ĐÀ NẴNG

| # | Tên Tour (VI / EN) | Danh mục (Category) | Tọa độ GPS | Ngưỡng An toàn (RuleOfAct.md) | Giá niêm yết (VND) | Ca giờ hoạt động (Slots) |
|---|---|---|---|---|---|---|
| **1** | **Chèo SUP Bình Minh Biển Mỹ Khê & Mân Thái**<br>*Sunrise SUP Paddleboarding at My Khe & Man Thai* | `cheo-sup-kayak`<br>`...0001` | `16.0890, 108.2495`<br>(Biển Mân Thái) | • Sóng tối đa: $\le 0.8\text{ m}$<br>• Gió biển: $\ge 5\text{ km/h}$<br>• Áo phao bắt buộc | **280.000**<br>(Gốc: 420.000) | `05:00 - 07:00`<br>`07:30 - 09:30`<br>`16:00 - 18:00` |
| **2** | **Lặn Bình Khí Khám Phá Rạn San Hô Hòn Sụp - Sơn Trà**<br>*Scuba Diving at Hon Sup Coral Reefs - Son Tra* | `lan-ngam-san-ho`<br>`...0002` | `16.1132, 108.2758`<br>(Hòn Sụp - Sơn Trà) | • Sóng tối đa: $\le 0.7\text{ m}$<br>• Tầm nhìn nước $\ge 5\text{ m}$<br>• HLV PADI 1 kèm 4 | **550.000**<br>(Gốc: 780.000) | `08:00 - 11:30`<br>`13:30 - 17:00` |
| **3** | **Bay Dù Lượn Cano Kéo Bãi Biển Mỹ Khê**<br>*Tandem Parasailing Flight at My Khe Beach* | `cano-du-bay`<br>`...0003` | `16.0592, 108.2435`<br>(Biển Mỹ Khê) | • Chuẩn ASTM F3099<br>• Gió: $10 - 25\text{ km/h}$<br>• Sóng: $\le 1.0\text{ m}$ | **500.000**<br>(Gói đôi: 950.000) | `08:30 - 10:30`<br>`14:30 - 17:30` |
| **4** | **Thuê Mô Tô Nước Jetski Cảm Giác Mạnh Vịnh Đà Nẵng**<br>*High-Speed Jetski Wave Riding in Da Nang Bay* | `mo-to-nuoc-jetski`<br>`...0004` | `16.0592, 108.2435`<br>(Biển Mỹ Khê) | • TT 17/2018/TT-BVHTTDL<br>• Cách bờ 150m<br>• Sóng: $\le 1.0\text{ m}$ | **450.000**<br>(30 phút) | `08:00 - 11:00`<br>`14:00 - 17:30` |
| **5** | **Trượt Phao Chuối Tốc Độ Cao Cho Nhóm Bạn**<br>*High-Speed Banana Boat Ride for Group* | `truot-phao-chuoi`<br>`...0005` | `16.0592, 108.2435`<br>(Biển Mỹ Khê) | • Tốc độ $\le 30\text{ km/h}$<br>• Áo phao chuyên dụng<br>• Sóng: $\le 0.8\text{ m}$ | **200.000**<br>(/ người) | `09:00 - 11:30`<br>`15:00 - 17:30` |
| **6** | **Du Thuyền Hạng Sang Ngắm Hoàng Hôn Vịnh Đà Nẵng**<br>*Luxury Sunset Catamaran Yacht Tour* | `du-thuyen-ngam-hoang-hon`<br>`...0006` | `16.0680, 108.2250`<br>(Bến DHC Marina) | • Xuất bến sông Hàn ra vịnh<br>• Sóng vịnh $\le 1.2\text{ m}$<br>• Tiệc trà nhẹ & nhạc sống | **1.200.000**<br>(Gốc: 1.500.000) | `16:30 - 18:30`<br>`19:00 - 21:00` |

---

## 3. CHÍNH SÁCH BẢO HÀNH & AN TOÀN
- **Chính sách hủy/hoàn (`cancellation_policy`):** `STANDARD_24H_V2` (Hoàn tiền 100% khi hủy trước 24 giờ khởi hành).
- **Cam kết an toàn (`waiver_content`):** "Khách hàng xác nhận đủ điều kiện sức khỏe tham gia hoạt động biển, tuân thủ hướng dẫn của huấn luyện viên và quy chuẩn an toàn hàng hải."
- **Trạng thái mở bán (`status`):** `PUBLISHED`, `is_deleted = false` (Bảo đảm hiển thị công khai trên API Discovery).
