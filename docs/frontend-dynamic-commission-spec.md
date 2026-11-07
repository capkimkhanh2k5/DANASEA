# Tài Liệu Đặc Tả Kỹ Thuật Frontend — Cấu Hình Hoa Hồng Động & Thưởng An Toàn (Dynamic Commission & SLA Rebate)

> **Dành cho:** Frontend Developer  
> **Phiên bản Backend:** Hoàn tất triển khai tại nhánh `feat/dynamic-commission-configuration`  
> **Mục tiêu:** Cung cấp giao diện quản trị Admin trực quan, minh bạch và an toàn để cấu hình tỷ lệ hoa hồng động (theo danh mục dịch vụ, chính sách ưu đãi đối tác mới gia nhập) và chính sách hoàn tiền mặt Thưởng An Toàn & SLA trên trang **Admin Settings**.

---

## 1. Tổng Quan Nghiệp Vụ & Quy Tắc Tính Phí

Hệ thống hoa hồng sàn DANASEA chuyển dịch từ mức cố định 10% sang cơ chế động nhiều tầng (**Cascading Resolution**) được quản lý tập trung:

1. **Ưu đãi Đối tác mới gia nhập (New Vendor Onboarding)**:
   - Mức hoa hồng: **5.0%** (mặc định).
   - Thời gian ưu đãi: **90 ngày (3 tháng)** tính từ thời điểm đối tác được duyệt (`verified_at`).
   - Sau 90 ngày, hệ thống tự động chuyển sang tính theo Danh mục dịch vụ hoặc mức sàn chuẩn.
2. **Biểu phí Hoa hồng theo Danh mục Dịch vụ (Category Tiering - 12 danh mục)**:
   - **Nhóm 1 — Tiện ích & Cho thuê thiết bị (7.0%)**:
     - Thuê ván SUP (`thue-sup`)
     - Thuê Kayak (`thue-kayak`)
     - Thuê ván lướt sóng (`thue-van-luot-song`)
   - **Nhóm 2 — Thể thao biển phổ thông (10.0%)**:
     - Cano lướt sóng & Dù bay biển (`cano-du-bay`)
     - Mô tô nước Jetski (`mo-to-nuoc-jetski`)
     - Trượt phao chuối cảm giác mạnh (`truot-phao-chuoi`)
     - Chèo SUP & Kayak (`cheo-sup-kayak`)
   - **Nhóm 3 — Tour trải nghiệm chuyên sâu & Lặn biển (12.0%)**:
     - Lặn ngắm san hô & Đi bộ dưới biển (`lan-ngam-san-ho`)
     - Câu cá biển cano riêng (`cau-ca-bien`)
     - Câu mực đêm (`cau-muc-dem`)
     - Học lướt sóng (`hoc-luot-song`)
     - Surf tour có hướng dẫn (`surf-tour-co-huong-dan`)
3. **Mức Sàn Mặc Định (Platform Fallback)**:
   - Cấu hình qua key `PLATFORM_COMMISSION_RATE` (mặc định: **10.0%**).
4. **Chính sách Thưởng An Toàn & SLA Đối tác (Safety Rebate Cash)**:
   - Cấu hình qua key `SAFETY_REBATE_ENABLED` (`true`/`false`) và `SAFETY_REBATE_RATE` (`0.02` = **2.0%**).
   - Cơ chế: Cộng trực tiếp tiền mặt 2.0% vào số tiền thực nhận (`netPayableAmount`) trong chu kỳ đối soát nếu:
     - 0 khiếu nại phát sinh lỗi từ nhà cung cấp (`disputes with vendor fault = 0`).
     - Điểm đánh giá trung bình an toàn & chất lượng trong kỳ $\ge 4.8$ sao (tối thiểu 5 lượt đánh giá).

---

## 2. Hợp Đồng API Backend (API Contracts)

Toàn bộ các endpoint đều yêu cầu xác thực JWT với quyền `ROLE_ADMIN`.

### 2.1. Lấy danh sách quy tắc hoa hồng động
- **Endpoint**: `GET /api/admin/settings/commission-rules` (hoặc `/api/admin/system-configs/commission-rules`)
- **Response**: `200 OK`
```json
[
  {
    "id": "e214d027-e81a-47c1-8406-8c9a35e88001",
    "ruleCode": "ONBOARDING_NEW_VENDOR_90D",
    "ruleName": "Ưu đãi Đối tác mới (3 tháng đầu)",
    "targetType": "ONBOARDING",
    "categoryId": null,
    "categoryName": null,
    "categorySlug": null,
    "vendorId": null,
    "commissionRate": 0.0500,
    "commissionRatePercent": 5.0,
    "durationDays": 90,
    "priority": 100,
    "isActive": true,
    "description": "Áp dụng hoa hồng ưu đãi 5.0% cho đối tác mới trong 90 ngày đầu kể từ ngày duyệt tài khoản",
    "updatedAt": "2026-11-06T04:47:00Z",
    "updatedBy": "4c45b8a5-d8aa-462f-9810-bb46960cb812"
  },
  {
    "id": "e214d027-e81a-47c1-8406-8c9a35e88002",
    "ruleCode": "CATEGORY_THUE_SUP",
    "ruleName": "Thuê ván SUP",
    "targetType": "CATEGORY",
    "categoryId": "129b00cb-a50e-436f-8700-112345678901",
    "categoryName": "Thuê ván SUP",
    "categorySlug": "thue-sup",
    "vendorId": null,
    "commissionRate": 0.0700,
    "commissionRatePercent": 7.0,
    "durationDays": null,
    "priority": 50,
    "isActive": true,
    "description": "Nhóm tiện ích & cho thuê dụng cụ biển (7.0%)",
    "updatedAt": "2026-11-06T04:47:00Z",
    "updatedBy": null
  }
]
```

### 2.2. Cập nhật quy tắc hoa hồng động
- **Endpoint**: `PUT /api/admin/settings/commission-rules/{id}`
- **Request Body**:
```json
{
  "commissionRatePercent": 6.5,
  "durationDays": 90,
  "isActive": true,
  "description": "Cập nhật hoa hồng mới"
}
```
- **Response**: `200 OK` (Trả về đối tượng quy tắc sau khi cập nhật).

### 2.3. Lấy và cập nhật cấu hình hệ thống (Platform Default & Safety Rebate)
- **Lấy toàn bộ config**: `GET /api/admin/settings`
- **Cập nhật config**: `PUT /api/admin/settings/{key}`
  - Các key liên quan:
    - `PLATFORM_COMMISSION_RATE`: Ví dụ `"10.0"`
    - `SAFETY_REBATE_ENABLED`: `"true"` hoặc `"false"`
    - `SAFETY_REBATE_RATE`: `"0.02"` (hoặc `"2.0"`)
- **Request Body**:
```json
{
  "value": "10.0",
  "description": "Tỷ lệ hoa hồng mặc định sàn (%)"
}
```

---

## 3. Kiểu Dữ Liệu TypeScript Đề Xuất (Type Definitions)

File đề xuất: `frontend/src/types/adminSetting.ts`

```typescript
export interface AdminCommissionRuleDto {
  id: string;
  ruleCode: string;
  ruleName: string;
  targetType: 'GLOBAL' | 'CATEGORY' | 'VENDOR' | 'ONBOARDING';
  categoryId?: string;
  categoryName?: string;
  categorySlug?: string;
  vendorId?: string;
  commissionRate?: number;
  commissionRatePercent: number;
  durationDays?: number;
  priority: number;
  isActive: boolean;
  description?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface UpdateAdminCommissionRuleRequest {
  commissionRatePercent?: number;
  durationDays?: number;
  isActive?: boolean;
  description?: string;
}

export interface AdminSystemConfigDto {
  key: string;
  value: string;
  description?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface UpdateAdminConfigRequest {
  value: string;
  description?: string;
}
```

---

## 4. Đặc Tả Dịch Vụ API Client (API Service)

File: `frontend/src/services/adminSettingService.ts`

Bổ sung các phương thức:
```typescript
import { apiClient } from './apiClient';
import type {
  AdminCommissionRuleDto,
  UpdateAdminCommissionRuleRequest,
  AdminSystemConfigDto,
  UpdateAdminConfigRequest,
} from '../types/adminSetting';

export const adminSettingService = {
  // 1. Lấy danh sách quy tắc hoa hồng
  async getCommissionRules(signal?: AbortSignal): Promise<AdminCommissionRuleDto[]> {
    const { data } = await apiClient.get<AdminCommissionRuleDto[]>('/admin/settings/commission-rules', { signal });
    return data;
  },

  // 2. Cập nhật một quy tắc hoa hồng theo ID
  async updateCommissionRule(id: string, payload: UpdateAdminCommissionRuleRequest): Promise<AdminCommissionRuleDto> {
    const { data } = await apiClient.put<AdminCommissionRuleDto>(
      `/admin/settings/commission-rules/${encodeURIComponent(id)}`,
      payload
    );
    return data;
  },

  // 3. Lấy cấu hình hệ thống
  async getSystemConfigs(signal?: AbortSignal): Promise<AdminSystemConfigDto[]> {
    const { data } = await apiClient.get<AdminSystemConfigDto[]>('/admin/settings', { signal });
    return data;
  },

  // 4. Cập nhật cấu hình hệ thống theo Key
  async updateSystemConfig(key: string, payload: UpdateAdminConfigRequest): Promise<AdminSystemConfigDto> {
    const { data } = await apiClient.put<AdminSystemConfigDto>(
      `/admin/settings/${encodeURIComponent(key)}`,
      payload
    );
    return data;
  },
};
```

---

## 5. Đặc Tả Giao Diện & Trải Nghiệm Người Dùng (UI/UX Specification)

File trang: `frontend/src/pages/admin/Settings/Settings.tsx` & `Settings.css`

### 5.1. Bố cục tổng thể (Layout)
Trang Admin Settings cần hiển thị cấu trúc gồm các khu vực:

1. **Thanh Toolbar Quản trị**:
   - Nút **"Khôi phục mặc định"** (hiển thị khi có bất kỳ thông số nào khác giá trị gốc).
   - Bộ chọn xem trước trạng thái (Bình thường / Đang tải / Lỗi kết nối / Dữ liệu trống - dành cho chế độ Demo).
2. **Khu vực A: Thẻ Tài chính & Ưu đãi (Grid 2 cột)**:
   - **Card 1: Hoa hồng Sàn mặc định (Platform Default)**:
     - Giá trị lớn: `10%`
     - Nút "Chỉnh sửa" -> inline input với kiểm tra hợp lệ (0% - 100%), nút Lưu & Hủy.
     - Mô tả: "Tỷ lệ sàn áp dụng khi không có quy tắc danh mục hoặc ưu đãi đối tác mới."
   - **Card 2: Ưu đãi Đối tác mới (New Vendor Onboarding)**:
     - Huy hiệu trạng thái: `Đang áp dụng` (Teal) / `Tạm dừng` (Neutral).
     - Giá trị lớn: `5.0%` trong `90 ngày` (3 tháng đầu).
     - Nút "Chỉnh sửa" -> Mở Dialog cho phép Admin điều chỉnh % và số ngày áp dụng.
3. **Khu vực B: Thưởng An Toàn & Hỗ Trợ Khẩn Cấp (Grid 2 cột)**:
   - **Card 3: Thưởng An toàn & SLA Đối tác (Cash Rebate)**:
     - Huy hiệu: `Đang kích hoạt` (Teal) / `Tạm dừng` (Neutral).
     - Giá trị lớn: `2.0%`
     - Nút "Cấu hình" -> Mở Dialog cho phép bật/tắt chính sách và sửa tỷ lệ %.
     - Mô tả: "Cộng trực tiếp tiền mặt vào số tiền thanh toán đối tác khi đạt: 0 khiếu nại phát sinh lỗi từ NCC và Đánh giá trung bình trong kỳ ≥ 4.8 sao."
   - **Card 4: Liên hệ hỗ trợ khẩn cấp (Hotline BQL Bán đảo Sơn Trà)**:
     - Giá trị: `0236 3920 477` kèm nút sao chép nhanh và chỉnh sửa inline.
4. **Khu vực C: Bảng Biểu Phí Hoa Hồng Theo Danh Mục Dịch Vụ**:
   - Hiển thị bảng 12 danh mục với các cột:
     - **Danh mục dịch vụ**: Tên danh mục + slug code.
     - **Phân khúc tính phí**: Badge phân tầng:
       - *Cho thuê thiết bị (7%)*: Phông nền xanh ngọc nhạt (`#ecfdf5`), chữ xanh đậm (`#065f46`).
       - *Thể thao biển phổ thông (10%)*: Phông nền cyan nhạt (`#eef7f8`), chữ cyan đậm (`#0b5d6f`).
       - *Trải nghiệm chuyên sâu (12%)*: Phông nền vàng hổ phách nhạt (`#fffbeb`), chữ nâu vàng (`#92400e`).
       - *(Lưu ý: Tuân thủ quy định Purple Ban - Tuyệt đối không dùng màu tím/violet).*
     - **Mức hoa hồng (%)**: Hiển thị đậm, ví dụ `7.0%`, `10.0%`, `12.0%`.
     - **Trạng thái**: Badge `Áp dụng` hoặc `Tạm dừng`.
     - **Thao tác**: Nút `Sửa` -> Mở `AdminDialog` để Admin chỉnh sửa tỷ lệ % hoặc trạng thái kích hoạt.
5. **Khu vực D: Ngưỡng Tham Chiếu An Toàn Biển**:
   - Thẻ Chiều cao sóng tham chiếu (`1.5 m`).
   - Thẻ Tốc độ gió giật tham chiếu (`25 km/h`).

### 5.2. Dialogs Cần Có:
1. `AdminDialog` chỉnh sửa Quy tắc Hoa hồng:
   - Tiêu đề: `Chỉnh sửa: [Tên danh mục/quy tắc]`
   - Form: Input số tỷ lệ hoa hồng (%), nếu là onboarding thì thêm input số ngày, checkbox kích hoạt.
2. `AdminDialog` cấu hình Thưởng An toàn & SLA:
   - Form: Input tỷ lệ hoàn tiền mặt (%), checkbox kích hoạt.
3. `AdminDialog` xác nhận khôi phục mặc định:
   - Liệt kê các tham số sẽ hoàn nguyên.

---

## 6. Danh Mục Kiểm Tra Dành Cho FE (Checklist)

- [ ] Định nghĩa interface `AdminCommissionRuleDto` và `UpdateAdminCommissionRuleRequest` trong `types/adminSetting.ts`.
- [ ] Thêm các hàm gọi API trong `services/adminSettingService.ts`.
- [ ] Render 3 thẻ chiến lược hoa hồng (Mặc định 10%, Onboarding 5%, Thưởng an toàn 2%) trên `Settings.tsx`.
- [ ] Render bảng 12 danh mục dịch vụ kèm phân khúc badge màu (7%, 10%, 12%).
- [ ] Tích hợp Dialog chỉnh sửa cho từng quy tắc và xử lý cập nhật state lạc quan / thông báo Toast Notice.
- [ ] Đảm bảo tuân thủ thiết kế: không sử dụng màu tím (Purple Ban), responsive hiển thị tốt trên thiết bị di động, phím tắt Enter/Escape khi chỉnh sửa.
