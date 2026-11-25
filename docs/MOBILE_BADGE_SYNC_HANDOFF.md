# Tài liệu Bàn giao cho Team Mobile — Đồng bộ Hệ thống Huy hiệu Đối tác (`BadgeTier`)

> **Phạm vi:** Ứng dụng Khách hàng (`mobile/`) và Ứng dụng Đối tác (`vendor_mobile/`)  
> **Nhánh tham chiếu:** `research_badge_system` *(nhánh này **không chỉnh sửa code trong `mobile/` và `vendor_mobile/`** để tránh conflict với nhánh của team Mobile)*  
> **Trạng thái tương thích:** Backend hoàn toàn **tương thích ngược (backward-compatible)** — app Mobile hiện tại vẫn chạy bình thường, không crash. Team Mobile cần thực hiện các hạng mục bên dưới để sửa **2 lỗi dữ liệu thật**, đồng bộ **nhãn/parser 3 hạng chuẩn**, và nâng cấp UI khớp 100% với Web & Backend.

---

## 1. Quy ước Huy hiệu Chuẩn Toàn Hệ thống (Single Source of Truth)

Backend và Database (`vendors.badge_tier`) chỉ dùng **đúng 3 hạng duy nhất** (loại bỏ hoàn toàn `STANDARD`, `BRONZE`, `SILVER`, `GOLD`, `PLATINUM`):

| Enum API | Nhãn tiếng Việt chuẩn | Nhãn tiếng Anh chuẩn | Điều kiện tự động đạt được | Hiển thị phía Khách hàng |
| :--- | :--- | :--- | :--- | :--- |
| `NONE` | **Chưa xếp hạng** | Unranked | `< 1` đánh giá hiển thị **hoặc** `ratingAvg < 3.50` | **Ẩn badge** |
| `VERIFIED` | **Đã xác minh** | Verified | `≥ 1` đánh giá hiển thị **và** `ratingAvg >= 3.50` | Hiển thị (màu xanh ngọc / `emerald`) |
| `TOP_RATED` | **Đối tác hàng đầu** | Top Partner | `≥ 5` đánh giá hiển thị **và** `ratingAvg >= 4.50` | Hiển thị nổi bật (màu vàng hổ phách / `amber`) |

### Quyền lợi gắn với `TOP_RATED` (để hiển thị/giải thích cho Vendor)
1. **Ưu tiên xếp hạng mặc định (`recommended`)** trên trang Tìm kiếm & gợi ý AI.
2. **Có bộ lọc riêng `"Đối tác hàng đầu"`** (`?badgeTier=TOP_RATED`).
3. **Nhận diện ưu tiên** trong hàng chờ duyệt rút tiền (`AdminPayouts`).
4. **Điều kiện tiên quyết mở khóa Quỹ thưởng An toàn Biển `+2%` tiền mặt** khi quyết toán *(khi đồng thời đạt `ratingAvg >= 4.80`, `0` khiếu nại đang mở và `0` lỗi phạt hủy chuyến trong kỳ)*.

---

## 2. Thay đổi API Backend liên quan tới Mobile

### 2.1. `GET /api/services` (Tìm kiếm & Danh sách dịch vụ)
- **Thứ tự mặc định (Server-driven):** Khi không truyền `sortBy` (hoặc truyền `sortBy=recommended`), Backend tự động sắp xếp ưu tiên theo `TOP_RATED → VERIFIED → NONE`, sau đó theo `avgRating DESC`, `ratingCount DESC`, `createdAt DESC`.
- **Tham số Query mới:**
  - `badgeTier` (optional): `NONE` | `VERIFIED` | `TOP_RATED` (dùng cho chip lọc **"Đối tác hàng đầu"**: `?badgeTier=TOP_RATED`).
  - `sortBy` (optional): hỗ trợ thêm `recommended` (mặc định) và `newest` (mới nhất), bên cạnh `rating_desc`, `price_asc`, `price_desc`, `views_desc`, `bookings_desc`.
- **Field mới trong mỗi item của `content[]`:**
  ```json
  {
    "id": "uuid",
    "vendorId": "uuid",
    "vendorBadgeTier": "TOP_RATED",
    "name": "Lặn ngắm san hô Sơn Trà",
    "averageRating": 4.9,
    "reviewCount": 32
  }
  ```

### 2.2. `GET /api/vendor/profile` (Hồ sơ đối tác)
- Trả về `badgeTier`: `"NONE" | "VERIFIED" | "TOP_RATED"`, cùng `ratingAvg`, `ratingCount`.

### 2.3. `GET /api/vendor/settlements` & `GET /api/vendor/settlements/{id}` (Quyết toán)
- Khi không đủ điều kiện nhận thưởng `+2%` do chưa đạt `TOP_RATED`, trường `safetyRebateNote` sẽ ghi rõ:  
  `"Không đủ điều kiện thưởng An toàn: Chưa đạt huy hiệu Đối tác hàng đầu (TOP_RATED), ..."`

---

## 3. Việc cần làm cho Team Mobile (Ưu tiên theo mức độ)

### Mức P0 — Đồng bộ chuẩn Enum/Parser/Nhãn & Sửa 2 lỗi dữ liệu thật (BẮT BUỘC)

#### 3.1. Đồng bộ Parser, Enum & Nhãn tiếng Việt trên 5 file cốt lõi

1. **`mobile/lib/core/data/catalog_repository.dart`** (dòng `160–164`):
   - `GET /api/services` trả `vendorBadgeTier`, còn `GET /api/services/{id}` trả `badgeTier`. Cập nhật fallback để đọc được cả 2:
   ```dart
   vendorBadge: switch (json['vendorBadgeTier'] ?? json['badgeTier']) {
     'VERIFIED' => VendorBadgeTier.verified,
     'TOP_RATED' => VendorBadgeTier.topRated,
     _ => VendorBadgeTier.none,
   },
   ```

2. **`mobile/lib/features/vendor/presentation/screens/vendor_profile_screen.dart`** (dòng `83–90`):
   - Đổi nhãn hiển thị `TOP_RATED` từ `'Được đánh giá cao'` sang `'Đối tác hàng đầu'` (giữ nguyên cơ chế ẩn `NONE`):
   ```dart
   if (vendor.badgeTier == 'VERIFIED' || vendor.badgeTier == 'TOP_RATED')
     LocalizedText(
       vendor.badgeTier == 'TOP_RATED' ? 'Đối tác hàng đầu' : 'Đã xác minh',
     ),
   ```

3. **`vendor_mobile/lib/core/models/vendor_models.dart`** (dòng `113–128`):
   - Đồng bộ `labelVi` về đúng bộ nhãn chuẩn (`Chưa xếp hạng` / `Đã xác minh` / `Đối tác hàng đầu`) thay cho nhãn cũ (`Đối tác mới` / `Đối tác xác thực`), đồng thời bổ sung hàm `VendorBadgeTier.fromApi(...)`:
   ```dart
   enum VendorBadgeTier {
     none,
     verified,
     topRated;

     static VendorBadgeTier fromApi(String? value) {
       switch (value?.trim().toUpperCase()) {
         case 'VERIFIED':
           return VendorBadgeTier.verified;
         case 'TOP_RATED':
           return VendorBadgeTier.topRated;
         default:
           return VendorBadgeTier.none;
       }
     }

     String get labelVi {
       switch (this) {
         case VendorBadgeTier.none:
           return 'Chưa xếp hạng';
         case VendorBadgeTier.verified:
           return 'Đã xác minh';
         case VendorBadgeTier.topRated:
           return 'Đối tác hàng đầu';
       }
     }
   }
   ```

4. **`vendor_mobile/lib/features/ai/presentation/widgets/copilot_artifact_card.dart`** (dòng `45` & `90`):
   - Bỏ fallback `'STANDARD'` (không tồn tại ở Backend) và hiển thị nhãn tiếng Việt thay vì in thô enum:
   ```dart
   // Import thêm: import '../../../../core/models/vendor_models.dart';
   final VendorBadgeTier badgeTier = VendorBadgeTier.fromApi(data['badgeTier']?.toString());
   // ...
   'Hạng: ${badgeTier.labelVi}',
   ```

5. **`vendor_mobile/lib/core/l10n/translations.dart`** (gần dòng `1541`):
   - Bổ sung bản dịch tiếng Anh cho nhãn mới `"Chưa xếp hạng"`:
   ```dart
   "Chưa xếp hạng": "Unranked",
   ```
   *(Lưu ý: `"Đã xác minh": "Verified"` và `"Đối tác hàng đầu": "Top partner"` đã có sẵn trong file.)*

---

#### 3.2. Sửa 2 lỗi hiển thị sai dữ liệu thật

#### Bug 1 (`vendor_mobile`): Trang Hồ sơ doanh nghiệp luôn hiện cứng `"Đã xác minh"`
- **File:** `vendor_mobile/lib/core/data/vendor_repositories.dart` (dòng `110–144`, hàm `getVendorProfile()`)
- **Nguyên nhân:** Hàm `getVendorProfile()` gọi `GET /api/vendor/profile` nhưng **quên không truyền `badgeTier`** vào constructor `VendorModel(...)`, khiến `VendorModel` luôn dùng giá trị mặc định `VendorBadgeTier.verified`. Kết quả là mọi đối tác (`NONE`, `VERIFIED`, `TOP_RATED`) đều bị hiện là `"Đã xác minh"`.
- **Cách sửa đề xuất:**
  ```dart
  final vendor = VendorModel(
    // ... các field hiện có ...
    badgeTier: VendorBadgeTier.fromApi(json['badgeTier']?.toString()),
    ratingAvg: (json['ratingAvg'] as num?)?.toDouble() ?? _db.currentVendor.ratingAvg,
    ratingCount: (json['ratingCount'] as num?)?.toInt() ?? _db.currentVendor.ratingCount,
  );
  ```

#### Bug 2 (`mobile`): Trang Home đang hardcode `isVerified: true` cho mọi dịch vụ
- **File:**
  - `mobile/lib/features/home/domain/models/home_models.dart` (dòng `12–24`)
  - `mobile/lib/features/home/presentation/screens/home_screen.dart` (dòng `79–94`)
  - `mobile/lib/features/home/presentation/widgets/experience_card.dart` (dòng `72–100`)
- **Nguyên nhân:** `home_screen.dart` đang gán cứng `isVerified: true` cho tất cả `ExperienceItem`, không đọc `s.vendorBadge`. Do đó `ExperienceCard` luôn hiển thị nhãn `"Nhà cung cấp đã xác minh"` cho cả vendor `NONE`, và không hiển thị `"Đối tác hàng đầu"` cho vendor `TOP_RATED`.
- **Cách sửa đề xuất:**
  1. Thêm `final VendorBadgeTier vendorBadge;` (mặc định `VendorBadgeTier.none`) vào `ExperienceItem`.
  2. Trong `home_screen.dart`, truyền `vendorBadge: s.vendorBadge` (và cân nhắc bỏ `sortBy: 'popularity'` hoặc để mặc định `recommended` cho section Đề xuất).
  3. Trong `experience_card.dart`:
     - Nếu `item.vendorBadge == VendorBadgeTier.none` → **ẩn badge hoàn toàn**.
     - Nếu `item.vendorBadge == VendorBadgeTier.verified` → hiện nhãn **"Đã xác minh"** (màu xanh ngọc).
     - Nếu `item.vendorBadge == VendorBadgeTier.topRated` → hiện nhãn **"Đối tác hàng đầu"** (màu vàng hổ phách / amber, icon `Icons.workspace_premium` hoặc `Icons.verified`).

---

### Mức P1 — Đồng bộ UI/UX với Web (KHUYẾN NGHỊ)

#### 1. Customer Mobile (`mobile/`): Lọc & hiển thị Huy hiệu ở màn Search và Chi tiết dịch vụ
- **`mobile/lib/core/data/catalog_repository.dart`** (hàm `search(...)`, dòng `54–74`):
  - Thêm tham số `String? badgeTier` và gắn vào `query` khi khác null/empty:
    ```dart
    if (badgeTier != null && badgeTier.trim().isNotEmpty) 'badgeTier': badgeTier.trim(),
    ```
- **`mobile/lib/features/search/presentation/screens/search_screen.dart`**:
  - Thêm state `bool _onlyTopRatedVendors = false;` và nút filter chip **"Đối tác hàng đầu"** (truyền `badgeTier: _onlyTopRatedVendors ? 'TOP_RATED' : null` vào `_repository.search(...)`).
  - Render pill huy hiệu từ `service.vendorBadge` trên mỗi card kết quả tìm kiếm (ẩn khi `VendorBadgeTier.none`).
  - *(Tùy chọn)* Thêm thanh chọn sắp xếp: **Đề xuất** *(mặc định, không cần gửi `sortBy`)*, **Mới nhất** (`newest`), **Đánh giá cao** (`rating_desc`), **Giá tăng/giảm** (`price_asc` / `price_desc`).
- **`mobile/lib/features/experience_detail/presentation/screens/experience_detail_screen.dart`** & **`catalog_detail_screen.dart`**:
  - Hiển thị huy hiệu `service.vendorBadge` cạnh tên đơn vị tổ chức (ẩn khi `none`, phân biệt màu giữa `verified` và `topRated`).

#### 2. Vendor Mobile (`vendor_mobile/`): Minh bạch lộ trình & quyền lợi cho Đối tác
- **`vendor_mobile/lib/features/profile/presentation/screens/business_profile_screen.dart`** (dòng `156–173`):
  - Đổi màu sắc/icon của badge theo đúng 3 hạng:
    - `none`: xám trung tính (`Chưa xếp hạng`)
    - `verified`: xanh ngọc (`Đã xác minh`)
    - `topRated`: vàng hổ phách (`Đối tác hàng đầu`)
  - Thêm dòng chú thích ngắn dưới badge giải thích cơ chế tự động tính (`≥ 1 đánh giá & ≥ 3.5★` → Đã xác minh; `≥ 5 đánh giá & ≥ 4.5★` → Đối tác hàng đầu).
- **`vendor_mobile/lib/features/settlements/presentation/screens/settlement_revenue_screen.dart`** & **`payout_detail_screen.dart`**:
  - Đảm bảo hiển thị đầy đủ `safetyRebateQualified`, `safetyRebateAmount`, và `safetyRebateNote` trả về từ API quyết toán để đối tác thấy rõ điều kiện mở khóa thưởng `+2%`.

---

## 4. Checklist QA cho Team Mobile

- [ ] **Vendor Mobile — Profile:** Đăng nhập 3 tài khoản vendor thuộc 3 hạng `NONE`, `VERIFIED`, `TOP_RATED` → kiểm tra màn Hồ sơ hiển thị đúng 3 nhãn tương ứng, không bị kẹt ở `"Đã xác minh"`.
- [ ] **Vendor Mobile — AI Copilot:** Hỏi Copilot *"Chỉ số kinh doanh của tôi"* → kiểm tra thẻ KPI hiển thị `Hạng: Chưa xếp hạng` / `Đã xác minh` / `Đối tác hàng đầu` (không hiện chữ tiếng Anh hay `STANDARD`).
- [ ] **Customer Mobile — Home & Search:** Kiểm tra dịch vụ của vendor `NONE` **không** hiện badge; dịch vụ của vendor `VERIFIED` hiện `"Đã xác minh"`; dịch vụ của vendor `TOP_RATED` hiện `"Đối tác hàng đầu"` và ưu tiên đứng đầu danh sách mặc định.
- [ ] **Customer Mobile — Filter:** Bật chip `"Đối tác hàng đầu"` → kiểm tra request gọi `GET /api/services?...&badgeTier=TOP_RATED` và chỉ trả dịch vụ thuộc vendor `TOP_RATED`.
- [ ] **Song ngữ (VI/EN):** Chuyển ngôn ngữ sang tiếng Anh → kiểm tra nhãn hiển thị tương ứng `Unranked` / `Verified` / `Top Partner`.
