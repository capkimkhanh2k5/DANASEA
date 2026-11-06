# KẾ HOẠCH TRIỂN KHAI HỆ THỐNG HOA HỒNG BIẾN THIÊN (DYNAMIC COMMISSION) — DANASEA

> **Trạng thái:** SẴN SÀNG TRIỂN KHAI (READY FOR IMPLEMENTATION) — Chờ lệnh của User  
> **Nhánh Git:** `feat/dynamic-commission-configuration`  
> **Chính sách đã phê duyệt:**
> - Ưu đãi đối tác mới: **5.0%** trong **3 tháng đầu tiên** (90 ngày).
> - Biểu phí danh mục: **7.0%** (Dụng cụ) | **10.0%** (Thể thao phổ thông) | **12.0%** (Tour chuyên sâu).
> - Thưởng an toàn & SLA: **2.0%** cộng trực tiếp tiền mặt vào kỳ quyết toán (`Settlement`).

---

## 1. TỔNG QUAN & BỐI CẢNH

### 1.1. Hiện trạng
* Hệ thống quản lý tập trung qua biến `PLATFORM_COMMISSION_RATE = 10%` trong bảng `system_configs`.
* Mọi đơn hàng con (`SubOrder`) đều snapshot mức 10% khi tạo đơn.
* Động cơ chiết khấu (`DiscountAllocationEngine`) và động cơ quyết toán (`SettlementCalculationEngine`) đã hoạt động chính xác trên nền tảng snapshot này.

### 1.2. Mục tiêu nâng cấp
* Áp dụng chính sách ưu đãi 5% trong 3 tháng đầu giúp chuyển đổi số các chủ tàu cano, thợ lặn địa phương.
* Cân bằng biên lợi nhuận giữa dịch vụ cho thuê đơn giản (7%) và các tour lặn biển chuyên sâu (12%).
* Dùng công cụ tài chính hoàn tiền 2% tiền mặt để khuyến khích Vendor tuân thủ tuyệt đối an toàn phao tiêu và cảnh báo thời tiết biển.

---

## 2. ĐẶC TẢ CHI TIẾT CÁC QUY TẮC NGHIỆP VỤ ĐÃ CHỐT

```
                                  ┌───────────────────────────────┐
                                  │      YÊU CẦU ĐẶT CHỖ MỚI      │
                                  │ (vendorId, service, category) │
                                  └──────────────┬────────────────┘
                                                 │
                                                 ▼
                                  ┌───────────────────────────────┐
                                  │   Vendor mới gia nhập < 90d?  │
                                  └──────┬─────────────────┬──────┘
                                    CÓ   │                 │  KHÔNG
                  ┌──────────────────────┘                 └──────────────────────┐
                  ▼                                                               ▼
     ┌─────────────────────────┐                                    ┌───────────────────────────┐
     │ ƯU ĐÃI ONBOARDING (5%)  │                                    │  Có Rule theo Category?   │
     │ (Áp dụng 3 tháng đầu)   │                                    └─────┬───────────────┬─────┘
     │ (Priority 100)          │                                      CÓ  │               │ KHÔNG
     └─────────────────────────┘                                ┌─────────┘               └─────────┐
                                                                ▼                                   ▼
                                                  ┌───────────────────────────┐       ┌───────────────────────────┐
                                                  │ TỶ LỆ THEO DANH MỤC       │       │ FALLBACK MẶC ĐỊNH SÀN     │
                                                  │ 7% - 10% - 12%            │       │ (PLATFORM_COMMISSION=10%) │
                                                  │ (Priority 50)             │       │ (Priority 0)              │
                                                  └───────────────────────────┘       └───────────────────────────┘
```

### Quy tắc 1: Ưu đãi Đối tác mới (New Vendor Onboarding)
* **Tỷ lệ hoa hồng:** **$5.0\%$** (giảm 50% so với phí sàn chuẩn).
* **Thời hạn áp dụng:** **3 tháng đầu tiên (90 ngày)** kể từ ngày tài khoản Vendor được phê duyệt (`approved_at` hoặc `created_at` của Vendor).
* **Độ ưu tiên (Priority):** **100** (Ưu tiên cao nhất, ghi đè toàn bộ quy tắc theo danh mục trong thời gian ưu đãi).

### Quy tắc 2: Phân tầng theo Danh mục Dịch vụ (Category-based Tiering)
Khi Vendor đã qua 3 tháng đầu, hệ thống áp dụng biểu phí theo danh mục dịch vụ:
1. **Nhóm 1 — Tiện ích & Cho thuê dụng cụ biển (7.0%)**:
   * Áp dụng cho: `thue-sup` (Thuê ván SUP), `thue-kayak` (Thuê thuyền Kayak), `thue-van-luot-song` (Thuê ván lướt sóng).
   * Cơ sở: Dịch vụ này biên lợi nhuận mỏng, giá trị thấp ($100k - $200k$), áp 7% giúp hút toàn bộ hộ kinh doanh dọc bãi biển Đà Nẵng lên app.
2. **Nhóm 2 — Thể thao biển phổ thông (10.0% - Mức chuẩn)**:
   * Áp dụng cho: `cano-du-bay` (Cano lướt sóng & Dù bay), `mo-to-nuoc-jetski` (Mô tô nước), `truot-phao-chuoi` (Phao chuối cảm giác mạnh), `cheo-sup-kayak` (Chèo SUP/Kayak có tour guide).
   * Cơ sở: Dịch vụ có nhu cầu đều đặn, mức 10% là chuẩn mực cân bằng.
3. **Nhóm 3 — Tour trải nghiệm chuyên sâu & Lặn biển (12.0%)**:
   * Áp dụng cho: `lan-ngam-san-ho` (Lặn ngắm san hô & Seawalker), `cau-ca-bien` (Câu cá cano riêng), `cau-muc-dem` (Câu mực đêm có ăn tối), `hoc-luot-song`, `surf-tour-co-huong-dan`.
   * Cơ sở: Giá trị đơn cao ($450k - $2.4M$), dịch vụ phức tạp cần sàn hỗ trợ CSKH và bảo hiểm biển sâu sát.
* **Độ ưu tiên (Priority):** **50**.

### Quy tắc 3: Quỹ thưởng An toàn Biển & Vận hành Xuất sắc (Safety & SLA Rebate)
* **Mức thưởng:** **$2.0\%$** tổng doanh thu cơ sở (`commissionBasisAmount`) của kỳ quyết toán.
* **Hình thức chi trả:** **Cộng trực tiếp thành tiền mặt** vào số tiền thực nhận (`netPayableAmount`) trong bảng quyết toán định kỳ (`Settlement`) của Vendor.
* **Điều kiện nhận thưởng trong kỳ (Phải thỏa mãn đồng thời 4 tiêu chí):**
  1. *An toàn thời tiết:* Tuyệt đối không xuất bến trong các khung giờ có cảnh báo dông sét (`severe_weather_code`) hoặc sóng/gió vượt ngưỡng an toàn phao tiêu.
  2. *Tranh chấp:* Không có khiếu nại chưa giải quyết trong kỳ (`open_disputes = 0`).
  3. *Tỷ lệ hủy chuyến:* Tỷ lệ hủy chuyến chủ quan từ phía Vendor $< 1\%$.
  4. *Đánh giá khách hàng:* Điểm đánh giá dịch vụ trung bình trong kỳ $\ge 4.8\star$ (tối thiểu 5 lượt đánh giá).
* **Công thức quyết toán mới:**
  $$\text{NetPayableAmount} = \text{NetSales} - \text{CommissionAmount} - \text{Deductions} + \mathbf{SafetyRebateAmount}$$
  *(Trong đó: $\text{SafetyRebateAmount} = \text{TotalCommissionBasis} \times 2.0\%$)*

---

## 3. THIẾT KẾ KIẾN TRÚC KỸ THUẬT CHI TIẾT

### 3.1. Thiết kế Cơ sở Dữ liệu (Database Migration - V44)

Tập tin migration: `backend/src/main/resources/db/migration/V44__dynamic_commission_rules.sql`

```sql
-- 1. Bảng quản lý quy tắc hoa hồng động
CREATE TABLE IF NOT EXISTS commission_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_name VARCHAR(150) NOT NULL,
    target_type VARCHAR(50) NOT NULL CHECK (target_type IN ('GLOBAL', 'CATEGORY', 'VENDOR', 'ONBOARDING')),
    category_id UUID REFERENCES categories(id) ON DELETE CASCADE,
    vendor_id UUID REFERENCES users(id) ON DELETE CASCADE,
    commission_rate NUMERIC(5, 4) NOT NULL CHECK (commission_rate >= 0.0000 AND commission_rate <= 1.0000),
    priority INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    effective_from TIMESTAMP WITH TIME ZONE,
    effective_to TIMESTAMP WITH TIME ZONE,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_commission_rules_lookup 
ON commission_rules (target_type, is_active, priority DESC);

-- 2. Thêm trường lưu rebate tiền mặt trong bảng settlements
ALTER TABLE settlements
    ADD COLUMN IF NOT EXISTS rebate_rate NUMERIC(5, 4) DEFAULT 0.0000,
    ADD COLUMN IF NOT EXISTS rebate_amount NUMERIC(15, 2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS rebate_reason VARCHAR(255);

-- 3. Seed dữ liệu ban đầu cho V44
-- Rule Onboarding: 5% cho vendor mới (3 tháng)
INSERT INTO commission_rules (rule_name, target_type, commission_rate, priority, is_active, description)
VALUES ('Ưu đãi Đối tác mới (3 tháng đầu)', 'ONBOARDING', 0.0500, 100, true, 'Áp dụng 5% cho vendor trong 90 ngày đầu kể từ khi kích hoạt');

-- Rules theo danh mục:
-- Nhóm 7% (Cho thuê dụng cụ)
INSERT INTO commission_rules (rule_name, target_type, category_id, commission_rate, priority, is_active)
SELECT 'Phí ưu đãi thuê dụng cụ: ' || name, 'CATEGORY', id, 0.0700, 50, true
FROM categories WHERE slug IN ('thue-sup', 'thue-kayak', 'thue-van-luot-song');

-- Nhóm 10% (Thể thao biển phổ thông)
INSERT INTO commission_rules (rule_name, target_type, category_id, commission_rate, priority, is_active)
SELECT 'Phí chuẩn thể thao biển: ' || name, 'CATEGORY', id, 0.1000, 50, true
FROM categories WHERE slug IN ('cano-du-bay', 'mo-to-nuoc-jetski', 'truot-phao-chuoi', 'cheo-sup-kayak');

-- Nhóm 12% (Tour trải nghiệm chuyên sâu)
INSERT INTO commission_rules (rule_name, target_type, category_id, commission_rate, priority, is_active)
SELECT 'Phí tour chuyên sâu: ' || name, 'CATEGORY', id, 0.1200, 50, true
FROM categories WHERE slug IN ('lan-ngam-san-ho', 'cau-ca-bien', 'cau-muc-dem', 'hoc-luot-song', 'surf-tour-co-huong-dan');
```

### 3.2. Cập nhật Backend Core Engine

1. **Mở rộng `CommissionPolicyPort`**:
   ```java
   public interface CommissionPolicyPort {
       BigDecimal getCommissionRate(UUID vendorId);
       
       // Phương thức phân giải đa tầng mới
       BigDecimal resolveCommissionRate(UUID vendorId, UUID categoryId, UUID serviceId);
       
       FlashSalePricing.Policy resolveFlashSalePolicy(UUID vendorId);
   }
   ```

2. **Cập nhật `CommissionPolicyAdapter`**:
   * Triển khai thuật toán Cascade Resolution:
     * **Bước 1**: Kiểm tra tài khoản Vendor có thời gian duyệt $\le 90$ ngày $\rightarrow$ Trả về `0.0500` (5%).
     * **Bước 2**: Tìm rule active trong bảng `commission_rules` khớp với `categoryId` $\rightarrow$ Trả về `0.0700`, `0.1000` hoặc `0.1200`.
     * **Bước 3**: Fallback về `PLATFORM_COMMISSION_RATE` trong `system_configs` (10%).

3. **Cập nhật luồng tạo đơn (`CreateOrderUseCase`)**:
   * Khi duyệt các `BookingItem`, trích xuất `categoryId` từ `Service` tương ứng.
   * Gọi `commissionPolicyPort.resolveCommissionRate(vendorId, categoryId, serviceId)`.
   * Ghi nhận tỷ lệ này vào `subOrder.setCommissionRate(rate)`.
   * Vẫn giữ nguyên tính **snapshot bất biến** vào bảng `sub_orders`.

4. **Nâng cấp Động cơ Quyết toán (`SettlementCalculationEngine` & `GenerateSettlementUseCase`)**:
   * Kiểm tra điều kiện thưởng 2%:
     * Truy vấn số lượng tranh chấp (`disputes`) của vendor trong kỳ.
     * Kiểm tra nhật ký an toàn thời tiết biển (`weather_sensitive` & `safety_rule_evaluations`).
     * Tính điểm đánh giá trung bình từ bảng `reviews`.
   * Nếu đạt điều kiện:
     * `rebateRate = 0.0200` (2%).
     * `rebateAmount = totalCommissionBasis * 0.0200`.
     * `netPayableAmount = netPayableAmount + rebateAmount`.
     * Ghi nhận trực tiếp vào bảng `settlements`.

### 3.3. Nâng cấp Giao diện Quản trị Admin (`/admin/settings`)
* Giữ nguyên thẻ **Tài chính: Hoa hồng mặc định toàn sàn** (10%).
* Bổ sung bảng **Biểu phí Danh mục Dịch vụ**:
  * Hiển thị danh sách 12 danh mục biển kèm mức % hiện tại.
  * Admin có nút chỉnh sửa nhanh từng danh mục (có validation từ 0% - 100%).
* Bổ sung khu vực **Cấu hình Ưu đãi & Thưởng An toàn**:
  * Switch bật/tắt & ô nhập số tháng ưu đãi Vendor mới (Mặc định: Bật, 5%, 3 tháng).
  * Switch bật/tắt & ô nhập % thưởng an toàn hàng hải (Mặc định: Bật, 2%).

---

## 4. LỘ TRÌNH THỰC THI (KHI USER RA LỆNH CODE)

| Giai đoạn | Chi tiết công việc | Thời gian ước tính |
| :--- | :--- | :--- |
| **Pha 1: Database Migration** | Tạo file migration `V44__dynamic_commission_rules.sql` và seed dữ liệu. | 30 phút |
| **Pha 2: Backend Entities & Port** | Tạo `CommissionRuleJpaEntity`, repository, nâng cấp `CommissionPolicyPort` và `CommissionPolicyAdapter`. | 1.5 giờ |
| **Pha 3: Order Flow Integration** | Nối `categoryId` từ Service vào `CreateOrderUseCase` và `DiscountAllocationEngine`. | 1.0 giờ |
| **Pha 4: Settlement Rebate 2%** | Nâng cấp `GenerateSettlementUseCase` để cộng tiền mặt 2% an toàn biển. | 1.5 giờ |
| **Pha 5: Admin UI Settings** | Cập nhật `frontend/src/pages/admin/Settings/` hiển thị và chỉnh sửa biểu phí danh mục + toggle ưu đãi. | 2.0 giờ |
| **Pha 6: Verification & Test Suite** | Chạy toàn bộ Unit Tests + Integration Tests kiểm toán tài chính cân bằng $100\%$. | 1.0 giờ |

---

## 5. BẢO BẢNG TÀI CHÍNH & KIỂM TOÁN (AUDIT GUARANTEE)

1. **Bảo toàn bất biến (Snapshotting)**: Mọi đơn hàng cũ đã tạo trước migration V44 vẫn giữ nguyên 100% dữ liệu gốc trong `sub_orders`.
2. **Không âm quỹ voucher**: Thuật toán phân bổ Hare-Niemeyer vẫn giữ nguyên, voucher sàn không bị vượt ngân sách dù hoa hồng danh mục là 7% hay 12%.
3. **Minh bạch sao kê quyết toán**: Dòng tiền hoàn thưởng 2% được ghi nhận rõ ràng thành trường riêng (`rebate_amount`, `rebate_rate`) trong bảng quyết toán để Vendor dễ dàng đối soát.
