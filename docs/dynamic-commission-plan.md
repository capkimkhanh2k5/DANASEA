# KẾ HOẠCH TRIỂN KHAI HỆ THỐNG HOA HỒNG BIẾN THIÊN (DYNAMIC COMMISSION) — DANASEA

> **Trạng thái:** SẴN SÀNG TRIỂN KHAI 100% (VERIFIED & READY FOR IMPLEMENTATION) — Chờ lệnh của User  
> **Nhánh Git:** `feat/dynamic-commission-configuration`  
> **Chính sách đã phê duyệt:**
> - **Ưu đãi đối tác mới (Onboarding):** **5.0%** trong **3 tháng đầu tiên (90 ngày)** tính từ `COALESCE(vendors.verified_at, vendors.created_at)`.
> - **Biểu phí danh mục (Category-based):** **7.0%** (Cho thuê dụng cụ) | **10.0%** (Thể thao biển phổ thông) | **12.0%** (Tour trải nghiệm chuyên sâu).
> - **Thưởng an toàn & SLA (Safety Rebate):** **2.0%** cộng trực tiếp thành tiền mặt vào kỳ quyết toán (`Settlement`).

---

## 1. TỔNG QUAN & BỐI CẢNH

### 1.1. Hiện trạng
* Hệ thống hiện quản lý tập trung qua biến `PLATFORM_COMMISSION_RATE = 10%` trong bảng `system_configs` (từ migration `V40` và `V43`).
* Mọi đơn hàng con (`SubOrder`) đều snapshot tỷ lệ hoa hồng vào cột `commission_rate` khi tạo đơn.
* Động cơ chiết khấu (`DiscountAllocationEngine`) và động cơ quyết toán (`SettlementCalculationEngine`) đã hoạt động chính xác trên nền tảng snapshot này.

### 1.2. Mục tiêu nâng cấp
* Áp dụng chính sách ưu đãi 5% trong 3 tháng đầu (90 ngày) giúp chuyển đổi số các chủ tàu cano, thợ lặn địa phương.
* Cân bằng biên lợi nhuận giữa dịch vụ cho thuê đơn giản (7%) và các tour lặn biển chuyên sâu (12%).
* Dùng công cụ tài chính hoàn tiền 2% tiền mặt trực tiếp vào bảng quyết toán (`Settlement`) để khuyến khích Vendor tuân thủ tuyệt đối an toàn phao tiêu, không hủy chuyến chủ quan và giữ chất lượng dịch vụ cao.

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
                                  │ Rule ONBOARDING đang Active   │
                                  │ & Vendor duyệt <= 90 ngày?    │
                                  └──────┬─────────────────┬──────┘
                                    CÓ   │                 │  KHÔNG
                  ┌──────────────────────┘                 └──────────────────────┐
                  ▼                                                               ▼
     ┌─────────────────────────┐                                    ┌───────────────────────────┐
     │ ƯU ĐÃI ONBOARDING (5%)  │                                    │ Có Rule CATEGORY Active   │
     │ (Áp dụng 90 ngày đầu)   │                                    │ khớp với category_id?     │
     │ (Priority 100)          │                                    └─────┬───────────────┬─────┘
     └─────────────────────────┘                                      CÓ  │               │ KHÔNG
                                                                ┌─────────┘               └─────────┐
                                                                ▼                                   ▼
                                                  ┌───────────────────────────┐       ┌───────────────────────────┐
                                                  │ TỶ LỆ THEO DANH MỤC       │       │ FALLBACK MẶC ĐỊNH SÀN     │
                                                  │ 7.0% - 10.0% - 12.0%      │       │ (PLATFORM_COMMISSION=10%) │
                                                  │ (Priority 50)             │       │ (Priority 0)              │
                                                  └───────────────────────────┘       └───────────────────────────┘
```

### Quy tắc 1: Ưu đãi Đối tác mới (New Vendor Onboarding)
* **Tỷ lệ hoa hồng:** **$5.0\%$** (`0.0500`).
* **Thời hạn áp dụng:** **3 tháng đầu tiên (90 ngày)** kể từ mốc thời gian tài khoản đối tác trong bảng `vendors` được phê duyệt:
  $$\text{OnboardingAnchor} = \text{COALESCE}(\text{vendors.verified\_at}, \text{vendors.created\_at})$$
  Điều kiện: `vendors.verification_status = 'APPROVED'` và `now <= OnboardingAnchor + INTERVAL '90 days'`.
* **Độ ưu tiên (Priority):** **100** (Cao nhất — ghi đè quy tắc theo danh mục trong suốt 90 ngày đầu).

### Quy tắc 2: Phân tầng theo Danh mục Dịch vụ (Category-based Tiering)
Khi Vendor đã qua 90 ngày đầu (hoặc khi tắt rule Onboarding), hệ thống áp dụng biểu phí theo danh mục dịch vụ (`services.category_id`):
1. **Nhóm 1 — Tiện ích & Cho thuê dụng cụ biển ($7.0\%$ — `0.0700`)**:
   * Danh mục áp dụng: `thue-sup` (Thuê ván SUP), `thue-kayak` (Thuê thuyền Kayak), `thue-van-luot-song` (Thuê ván lướt sóng).
2. **Nhóm 2 — Thể thao biển phổ thông ($10.0\%$ — `0.1000` - Mức chuẩn)**:
   * Danh mục áp dụng: `cano-du-bay` (Cano lướt sóng & Dù bay), `mo-to-nuoc-jetski` (Mô tô nước), `truot-phao-chuoi` (Phao chuối cảm giác mạnh), `cheo-sup-kayak` (Chèo SUP/Kayak có hướng dẫn).
3. **Nhóm 3 — Tour trải nghiệm chuyên sâu & Lặn biển ($12.0\%$ — `0.1200`)**:
   * Danh mục áp dụng: `lan-ngam-san-ho` (Lặn ngắm san hô & Đi bộ dưới biển), `cau-ca-bien` (Câu cá biển cano riêng), `cau-muc-dem` (Câu mực đêm), `hoc-luot-song`, `surf-tour-co-huong-dan`, `du-thuyen-ngam-hoang-hon`.
* **Độ ưu tiên (Priority):** **50**.

### Quy tắc 3: Quỹ thưởng An toàn Biển & Vận hành Xuất sắc (Safety & SLA Rebate — 2.0% Tiền mặt)
* **Mức thưởng:** **$2.0\%$** (`0.0200`) tính trên tổng doanh thu cơ sở hợp lệ (`totalGross` của các đơn không bị loại trừ) trong kỳ quyết toán.
* **Hình thức chi trả:** **Cộng trực tiếp thành tiền mặt** vào số tiền thực nhận (`netPayableAmount`) trong bảng quyết toán định kỳ (`settlements`).
* **Điều kiện xét duyệt tự động trong kỳ `[periodStart, periodEnd]` (Phải thỏa mãn đồng thời):**
  1. **Không vi phạm hủy chuyến chủ quan / lỗi Vendor:** Không phát sinh bản ghi phạt lỗi đối tác nào trong bảng `vendor_fault_compensations` (từ `V49`) hoặc `sub_order_disruptions` do lỗi chủ quan của Vendor trong kỳ.
  2. **Không có tranh chấp đang mở:** Không có đơn hàng nào trong kỳ đang bị vướng khiếu nại (`OPEN` / `UNDER_REVIEW` trong bảng `disputes`).
  3. **Điểm đánh giá chất lượng cao:** Điểm đánh giá trung bình của Vendor (`vendors.rating_avg` hoặc trung bình các đánh giá hiển thị trong bảng `reviews`) đạt $\ge 4.8\star$.
* **Công thức quyết toán tổng hợp (tích hợp với `GenerateSettlementUseCase` và `FinalizeSettlementUseCase`):**
  * **Tại bước khởi tạo bảng quyết toán (`GenerateSettlementUseCase`):**
    $$\text{SafetyRebateAmount} = \text{ round}(\text{TotalGross} \times 2.0\%, 2)$$
    $$\text{NetPayableAmount (Draft)} = (\text{TotalGross} - \text{TotalCommission}) + \text{SafetyRebateAmount}$$
  * **Tại bước phê duyệt chốt sổ (`FinalizeSettlementUseCase`):**
    $$\text{NetPayableAmount (Final)} = \text{NetPayableAmount (Draft)} - \text{VendorFaultCompensationDeductedAmount}$$

---

## 3. THIẾT KẾ KIẾN TRÚC KỸ THUẬT CHI TIẾT

### 3.1. Thiết kế Cơ sở Dữ liệu (Database Migration - `V54`)

> **Lưu ý kỹ thuật quan trọng:** Thư mục `backend/src/main/resources/db/migration/` hiện đã có tới `V53__sync_payment_attempt_state_trigger.sql` (và `V44` đã tồn tại). Do đó, file migration mới bắt buộc đặt tên là **`V54__dynamic_commission_rules.sql`**.

Tập tin migration: `backend/src/main/resources/db/migration/V54__dynamic_commission_rules.sql`

```sql
-- V54__dynamic_commission_rules.sql
-- Triển khai hệ thống quy tắc hoa hồng biến thiên (Onboarding 5%, Category 7%/10%/12%)
-- và Quỹ thưởng An toàn Biển 2% cộng trực tiếp tiền mặt vào bảng quyết toán (settlements).

-- 1. Bảng quản lý quy tắc hoa hồng động
CREATE TABLE IF NOT EXISTS commission_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_code VARCHAR(80) NOT NULL UNIQUE,
    rule_name VARCHAR(150) NOT NULL,
    target_type VARCHAR(50) NOT NULL CHECK (target_type IN ('GLOBAL', 'CATEGORY', 'VENDOR', 'ONBOARDING')),
    category_id UUID REFERENCES categories(id) ON DELETE CASCADE,
    vendor_id UUID REFERENCES vendors(id) ON DELETE CASCADE,
    commission_rate NUMERIC(5, 4) NOT NULL CHECK (commission_rate >= 0.0000 AND commission_rate <= 1.0000),
    duration_days INTEGER CHECK (duration_days IS NULL OR duration_days > 0),
    priority INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    effective_from TIMESTAMP WITH TIME ZONE,
    effective_to TIMESTAMP WITH TIME ZONE,
    description TEXT,
    updated_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_commission_rules_lookup 
ON commission_rules (target_type, is_active, priority DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_commission_rules_category 
ON commission_rules (category_id) WHERE target_type = 'CATEGORY' AND category_id IS NOT NULL;

-- 2. Thêm các cột lưu thưởng An toàn Biển (Safety Rebate 2%) trực tiếp vào bảng settlements
ALTER TABLE settlements
    ADD COLUMN IF NOT EXISTS safety_rebate_rate NUMERIC(5, 4) NOT NULL DEFAULT 0.0000 CHECK (safety_rebate_rate >= 0.0000 AND safety_rebate_rate <= 1.0000),
    ADD COLUMN IF NOT EXISTS safety_rebate_amount NUMERIC(38, 2) NOT NULL DEFAULT 0.00 CHECK (safety_rebate_amount >= 0.00),
    ADD COLUMN IF NOT EXISTS safety_rebate_qualified BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS safety_rebate_note VARCHAR(255);

-- 3. Cấu hình tham số hệ thống cho Quỹ thưởng An toàn Biển trong system_configs
INSERT INTO system_configs (id, "key", "value", description, created_at, updated_at)
SELECT gen_random_uuid(), 'SAFETY_REBATE_RATE', '2.0',
       'Tỷ lệ thưởng an toàn biển và chất lượng dịch vụ cộng tiền mặt khi quyết toán (%)', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE "key" = 'SAFETY_REBATE_RATE');

INSERT INTO system_configs (id, "key", "value", description, created_at, updated_at)
SELECT gen_random_uuid(), 'SAFETY_REBATE_ENABLED', 'true',
       'Bật/tắt chế độ cộng thưởng tiền mặt an toàn biển khi quyết toán', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE "key" = 'SAFETY_REBATE_ENABLED');

-- 4. Seed dữ liệu quy tắc hoa hồng ban đầu
-- 4.1. Rule Onboarding: 5% cho vendor mới trong 90 ngày (3 tháng)
INSERT INTO commission_rules (rule_code, rule_name, target_type, commission_rate, duration_days, priority, is_active, description)
VALUES (
    'ONBOARDING_NEW_VENDOR_90D',
    'Ưu đãi Đối tác mới (3 tháng đầu)',
    'ONBOARDING',
    0.0500,
    90,
    100,
    true,
    'Áp dụng mức hoa hồng ưu đãi 5.0% cho đối tác mới trong 90 ngày đầu kể từ ngày duyệt hồ sơ'
)
ON CONFLICT (rule_code) DO NOTHING;

-- 4.2. Nhóm 1: 7.0% (Tiện ích & Cho thuê dụng cụ biển)
INSERT INTO commission_rules (rule_code, rule_name, target_type, category_id, commission_rate, priority, is_active, description)
SELECT 
    'CATEGORY_' || UPPER(REPLACE(slug, '-', '_')),
    'Biểu phí thuê dụng cụ: ' || name,
    'CATEGORY',
    id,
    0.0700,
    50,
    true,
    'Nhóm tiện ích và cho thuê dụng cụ biển (7.0%)'
FROM categories 
WHERE slug IN ('thue-sup', 'thue-kayak', 'thue-van-luot-song')
ON CONFLICT (rule_code) DO NOTHING;

-- 4.3. Nhóm 2: 10.0% (Thể thao biển phổ thông)
INSERT INTO commission_rules (rule_code, rule_name, target_type, category_id, commission_rate, priority, is_active, description)
SELECT 
    'CATEGORY_' || UPPER(REPLACE(slug, '-', '_')),
    'Biểu phí thể thao biển: ' || name,
    'CATEGORY',
    id,
    0.1000,
    50,
    true,
    'Nhóm hoạt động thể thao biển phổ thông (10.0%)'
FROM categories 
WHERE slug IN ('cano-du-bay', 'mo-to-nuoc-jetski', 'truot-phao-chuoi', 'cheo-sup-kayak')
ON CONFLICT (rule_code) DO NOTHING;

-- 4.4. Nhóm 3: 12.0% (Tour trải nghiệm chuyên sâu & Lặn biển)
INSERT INTO commission_rules (rule_code, rule_name, target_type, category_id, commission_rate, priority, is_active, description)
SELECT 
    'CATEGORY_' || UPPER(REPLACE(slug, '-', '_')),
    'Biểu phí tour chuyên sâu: ' || name,
    'CATEGORY',
    id,
    0.1200,
    50,
    true,
    'Nhóm tour trải nghiệm chuyên sâu và lặn biển (12.0%)'
FROM categories 
WHERE slug IN ('lan-ngam-san-ho', 'cau-ca-bien', 'cau-muc-dem', 'hoc-luot-song', 'surf-tour-co-huong-dan', 'du-thuyen-ngam-hoang-hon')
ON CONFLICT (rule_code) DO NOTHING;
```

---

### 3.2. Cập nhật Backend Core Engine & Bảo vệ Tương thích Ngược

1. **Mở rộng `CommissionPolicyPort`**:
   ```java
   public interface CommissionPolicyPort {
       // Giữ nguyên cho các luồng tra cứu mặc định theo vendor
       BigDecimal getCommissionRate(UUID vendorId);

       // Phương thức mới phân giải đầy đủ theo bối cảnh (Vendor + Service + Category)
       default BigDecimal resolveCommissionRate(UUID vendorId, UUID serviceId, UUID categoryId) {
           return getCommissionRate(vendorId);
       }

       FlashSalePricing.Policy resolveFlashSalePolicy(UUID vendorId);

       default FlashSalePricing.Policy resolveFlashSalePolicy(UUID vendorId, UUID serviceId, UUID categoryId) {
           return resolveFlashSalePolicy(vendorId);
       }
   }
   ```

2. **Chiến lược phân giải trong `CommissionPolicyAdapter` (Đảm bảo 100% tương thích với các bài Test cũ)**:
   * **Thứ tự ưu tiên trong `resolveCommissionRate(vendorId, serviceId, categoryId)`**:
     1. **Kiểm tra Rule `ONBOARDING` (Priority 100)**:
        * Nếu trong bảng `commission_rules` có rule `target_type = 'ONBOARDING'` đang `is_active = true`:
        * Tra cứu bảng `vendors` theo `vendorId`. Nếu `verification_status = 'APPROVED'` và thời gian `COALESCE(verified_at, created_at)` nằm trong vòng `duration_days` (90 ngày) tính đến thời điểm hiện tại $\rightarrow$ Trả về `0.050000` (5%).
     2. **Kiểm tra Rule `CATEGORY` (Priority 50)**:
        * Nếu `categoryId == null` nhưng có `serviceId != null`, tự động tra `categoryId` từ bảng `services`.
        * Tìm rule trong `commission_rules` có `target_type = 'CATEGORY'`, `category_id = categoryId` và `is_active = true` $\rightarrow$ Trả về `commission_rate` tương ứng (`0.070000`, `0.100000` hoặc `0.120000`).
     3. **Fallback về `PLATFORM_COMMISSION_RATE` (Priority 0)**:
        * Nếu không khớp rule nào ở trên (hoặc trong môi trường test đã `TRUNCATE` bảng `commission_rules`), đọc từ `system_configs` với key `PLATFORM_COMMISSION_RATE` (10%).
   * **Bảo vệ `FlashSaleCommissionUnificationIntegrationTest`**:
     * Phương thức `getCommissionRate(UUID vendorId)` (không truyền `serviceId`/`categoryId`) chỉ áp dụng rule động nếu bảng `commission_rules` có dữ liệu được cấu hình; trong các test `FlashSaleCommissionUnificationIntegrationTest`, cần thêm `commission_rules` vào danh sách `TRUNCATE` ở `@BeforeEach` hoặc chỉ kích hoạt Onboarding khi gọi qua `resolveCommissionRate(vendorId, serviceId, categoryId)`. Nhờ đó, toàn bộ 7 test case của `FlashSaleCommissionUnificationIntegrationTest` và 5 unit test của `CommissionPolicyAdapterTest` sẽ **PASS 100%** mà không bị xung đột!

3. **Cập nhật đầy đủ cả 4 vị trí gọi tính hoa hồng trong Codebase**:
   * **Vị trí 1 — [`CreateOrderUseCase.java`](file:///Users/capkimkhanh/Documents/DUT4_1/PBL6/backend/src/main/java/com/danasea/backend/modules/order/application/usecases/CreateOrderUseCase.java)**:
     * Tại dòng 232 (khi chuẩn bị `CandidateItem` cho `DiscountAllocationEngine`) và dòng 292 (khi khởi tạo `SubOrder`), gọi `commissionPolicyPort.resolveCommissionRate(item.vendorId(), item.serviceId(), null)`.
   * **Vị trí 2 — [`OrderPaymentService.java`](file:///Users/capkimkhanh/Documents/DUT4_1/PBL6/backend/src/main/java/com/danasea/backend/modules/order/application/OrderPaymentService.java)**:
     * Tại dòng 322 (khi tạo `SubOrderJpaEntity` từ `BookingItemJpaEntity`), gọi `configuredCommission.resolveCommissionRate(item.getVendorId(), item.getServiceId(), null)`.
   * **Vị trí 3 — [`DiscountPreviewUseCase.java`](file:///Users/capkimkhanh/Documents/DUT4_1/PBL6/backend/src/main/java/com/danasea/backend/modules/order/application/usecases/DiscountPreviewUseCase.java)**:
     * Tại dòng 119 (khi xem trước chiết khấu lúc checkout), gọi `commissionPolicyPort.resolveCommissionRate(item.vendorId(), item.serviceId(), null)` để đảm bảo kết quả Preview khớp tuyệt đối với kết quả Tạo đơn.
   * **Vị trí 4 — [`FlashSaleService.java`](file:///Users/capkimkhanh/Documents/DUT4_1/PBL6/backend/src/main/java/com/danasea/backend/modules/flashsale/application/FlashSaleService.java)**:
     * Hỗ trợ resolve chính sách Flash Sale theo `(vendorId, serviceId)` khi cần kiểm tra trần trợ giá theo danh mục.

4. **Nâng cấp Động cơ Quyết toán (`GenerateSettlementUseCase` & `FinalizeSettlementUseCase`)**:
   * Trong `GenerateSettlementUseCase.java`:
     * Sau khi tính tổng `totalGross`, `totalCommission`, `totalNetPayout` từ các `SettlementLineItem` hợp lệ (không bị `EXCLUDED`):
     * Kiểm tra cấu hình `SAFETY_REBATE_ENABLED` và `SAFETY_REBATE_RATE` (mặc định `2.0%`).
     * Đánh giá điều kiện nhận thưởng An toàn Biển của `vendorId`:
       1. Không có `activeDisputes` (`activeDisputeSubOrderIds.isEmpty()`).
       2. Không có khoản phạt lỗi hủy chuyến chủ quan của Vendor trong kỳ (`vendor_fault_compensations` phát sinh trong kỳ $= 0$).
       3. Điểm đánh giá của Vendor đạt chuẩn ($\ge 4.8\star$, hoặc chưa có đánh giá tiêu cực dưới $4.8\star$).
     * Nếu đủ điều kiện:
       * `safetyRebateRate = 0.0200`
       * `safetyRebateAmount = totalGross.multiply(safetyRebateRate).setScale(2, RoundingMode.HALF_UP)`
       * `totalNetPayout = totalNetPayout.add(safetyRebateAmount)`
     * Lưu `safetyRebateRate`, `safetyRebateAmount`, `safetyRebateQualified = true` vào `SettlementJpaEntity` và trả về trong `SettlementResponse` / `SettlementDetailResponse`.

---

### 3.3. Đặc tả REST API Endpoints & Giao diện Quản trị Admin

1. **REST API Endpoints (Backend — `AdminSystemConfigController`)**:
   * `GET /api/admin/settings/commission-rules`:
     * Trả về danh sách toàn bộ quy tắc trong `commission_rules` (bao gồm Rule Onboarding 90 ngày, 12 Rule theo danh mục kèm tên danh mục tiếng Việt, và cấu hình Quỹ thưởng An toàn 2%).
   * `PUT /api/admin/settings/commission-rules/{id}`:
     * Request body: `{ "commissionRatePercent": "7.0", "durationDays": 90, "isActive": true, "description": "..." }`
     * Validate: Tỷ lệ từ `0%` đến `100%`, ghi nhận `updatedBy = currentAdminId`.
   * Giữ nguyên `GET /api/admin/settings` và `PUT /api/admin/settings/{key}` để tương thích 100% với UI hiện tại.

2. **Giao diện Quản trị (`frontend/src/pages/admin/Settings/Settings.tsx`)**:
   * Mở rộng Khu vực Tài chính trên trang `/admin/settings`:
     * **Thẻ 1 (Hiện có):** Hoa hồng mặc định toàn sàn (`PLATFORM_COMMISSION_RATE` = 10%).
     * **Thẻ 2 (Mới):** **Chính sách Ưu đãi Đối tác mới & Quỹ thưởng An toàn Biển**:
       * Hiển thị mức hoa hồng ưu đãi **5%** trong **90 ngày đầu** (có nút bật/tắt & chỉnh sửa).
       * Hiển thị mức thưởng An toàn Biển **2%** cộng trực tiếp tiền mặt khi quyết toán (có nút bật/tắt & chỉnh sửa).
     * **Bảng Biểu phí theo Danh mục Dịch vụ (Mới):**
       * Hiển thị 3 nhóm danh mục (Nhóm 7% Dụng cụ | Nhóm 10% Thể thao phổ thông | Nhóm 12% Tour chuyên sâu) kèm nút chỉnh sửa nhanh từng danh mục.

---

## 4. LỘ TRÌNH THỰC THI CHI TIẾT (6 PHA)

| Pha | Hạng mục công việc | Tập tin tác động chính | Thời gian dự kiến |
| :--- | :--- | :--- | :--- |
| **Pha 1** | Tạo migration **`V54__dynamic_commission_rules.sql`** (tạo bảng `commission_rules`, thêm cột thưởng 2% vào `settlements`, seed dữ liệu 3 nhóm danh mục & rule 90 ngày). | `V54__dynamic_commission_rules.sql` | 30 phút |
| **Pha 2** | Tạo `CommissionRuleJpaEntity`, `JpaCommissionRuleRepository`; nâng cấp `CommissionPolicyPort` và `CommissionPolicyAdapter`. | `CommissionRuleJpaEntity.java`, `CommissionPolicyPort.java`, `CommissionPolicyAdapter.java` | 1.5 giờ |
| **Pha 3** | Tích hợp phân giải hoa hồng theo dịch vụ/danh mục vào cả 4 luồng: `CreateOrderUseCase`, `OrderPaymentService`, `DiscountPreviewUseCase`, `FlashSaleService`. | `CreateOrderUseCase.java`, `OrderPaymentService.java`, `DiscountPreviewUseCase.java` | 1.0 giờ |
| **Pha 4** | Nâng cấp `SettlementJpaEntity`, `GenerateSettlementUseCase`, `SettlementMapper`, `SettlementResponse` để tính và cộng trực tiếp 2% tiền mặt thưởng An toàn Biển. | `SettlementJpaEntity.java`, `GenerateSettlementUseCase.java`, `SettlementResponse.java` | 1.5 giờ |
| **Pha 5** | Bổ sung API quản lý `commission-rules` trong `AdminSystemConfigController` và nâng cấp giao diện Admin tại `Settings.tsx`. | `AdminSystemConfigController.java`, `Settings.tsx`, `settingsModel.ts` | 1.5 giờ |
| **Pha 6** | Viết Unit Tests & Integration Tests cho toàn bộ quy tắc mới và chạy kiểm tra hồi quy đảm bảo 100% test cũ và mới đều XANH. | `CommissionPolicyAdapterTest.java`, `GenerateSettlementUseCaseTest.java` | 1.0 giờ |

---

## 5. CAM KẾT BẢO TOÀN TÀI CHÍNH & KIỂM TOÁN (AUDIT INVARIANTS)

1. **Bảo toàn bất biến lịch sử (Historical Immutability)**: Mọi đơn hàng cũ đã tạo trước `V54` hoặc trước khi Admin đổi cấu hình đều giữ nguyên `commission_rate` đã snapshot trong `sub_orders`.
2. **Đồng nhất giữa Preview và Checkout**: Cả `DiscountPreviewUseCase`, `CreateOrderUseCase` và `OrderPaymentService` đều gọi chung một hàm phân giải `resolveCommissionRate`, đảm bảo không bao giờ lệch dù chỉ 1 VNĐ.
3. **Không âm quỹ Voucher & Flash Sale**: Thuật toán phân bổ Hare-Niemeyer và trần trợ giá Flash Sale tự động tính toán dựa trên đúng tỷ lệ hoa hồng thực tế của dịch vụ đó.
4. **Minh bạch quyết toán**: Khoản thưởng an toàn 2% được tách bạch rõ ràng trên bảng `settlements` (`safety_rebate_rate`, `safety_rebate_amount`, `safety_rebate_qualified`) trước khi cộng vào `net_payable_amount`.
