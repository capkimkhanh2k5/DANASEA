# 🌊 DANASEA — TÀI LIỆU KỸ THUẬT & LUỒNG NGHIỆP VỤ HỆ THỐNG AI (WORKFLOWS AI)

---

## 1. KIẾN TRÚC TỔNG THỂ HỆ THỐNG AI LAI (HYBRID AI ARCHITECTURE)

Hệ thống AI của DANASEA áp dụng mô hình **Dual-Brain Hybrid Architecture** kết hợp giữa Điện toán Đám mây (Cloud LLM) và Máy chủ Suy luận Cục bộ (Local Fast-Inference Worker):

```
Client (Web React / Mobile Flutter)
       │
       ▼
Security Gateway (JWT, LocaleContext, AiExecutionBudgetFilter 8s, RedisRateLimiter)
       │
       ▼
Spring Boot Core Backend (Clean Architecture)
       │
       ├──► CLI Proxy (OAuth) ────────► Hội thoại, sinh ngôn ngữ tự nhiên, trích xuất dẫn chứng review
       │
       ├──► Quyet Small Worker (Fast Inference) ───► Phân loại ý định, chấm điểm gợi ý, kiểm duyệt text, rủi ro giao dịch
       │
       ├──► PostgreSQL Database (Flyway V21 - V25) ─► ai_itineraries, ai_assessment_cases, ai_chat_requests,
       │                                             ai_itinerary_lifecycle, ai_customer_preferences, ai_confirmation_outcomes
       │
       └──► Redis In-Memory Cache ─────────────────► Token Bucket, Draft Cards (15p), Distributed Locks (30s), Key Pool
```

### Nguyên tắc Bất biến Cốt lõi (Invariants):
1. **Tuyệt đối không tự động can thiệp tài chính / tồn kho:** Cả CLI Proxy LLM và Quyet Worker đều không bao giờ được phép trực tiếp sửa đổi tiền, tự ý hủy booking, hoàn tiền hay tạo hóa đơn trong DB.
2. **Backend Code luôn nắm quyền quyết định cuối cùng:** Khi mô hình AI trả về kết quả mâu thuẫn với quy tắc hệ thống, Backend Rule Engine luôn ghi đè kết quả của AI.
3. **Truy vấn AI không gây ô nhiễm dữ liệu vận hành:** Các yêu cầu đọc dữ liệu qua AI không được làm tăng `view_count` của dịch vụ và không ghi lịch sử xem gần đây.
4. **Bảo vệ tài nguyên & Deadline thực thi (Budget Enforced):** Bộ lọc `AiExecutionBudgetFilter` giới hạn tối đa 8 request AI đồng thời trên mỗi JVM và thực thi deadline nghiêm ngặt (tối đa 8000ms), tránh nghẽn thread.
5. **Dữ liệu hội thoại không tự thành sự thật nguồn:** Toàn bộ phản hồi văn bản của trợ lý ảo luôn được gắn cờ `generatedTextVerified = false`; các thẻ tương tác (`AssistantArtifact`) chứa dữ kiện từ CSDL được bóc tách riêng biệt.

---

## 2. BỘ CÔNG CỤ TOÀN DIỆN CỦA TRỢ LÝ ẢO (AI TOOL REGISTRY — 15 TOOLS)

Trợ lý ảo (`POST /api/assistant/chat`) được trang bị **15 công cụ kỹ thuật** (6 công cụ cơ bản + 9 công cụ khách hàng mở rộng):

### A. 6 Công cụ cơ bản (Legacy Core Tools)
1. **`search_services`** *(READ - PostgreSQL)*: Tìm kiếm dịch vụ theo từ khóa, tầm giá và danh mục bằng Full-Text Search.
2. **`get_service_detail`** *(READ - PostgreSQL)*: Lấy thông tin chi tiết dịch vụ, biểu giá các tùy chọn đặt và các khung giờ khả dụng.
3. **`get_policy`** *(READ - PostgreSQL/Redis)*: Đọc chính xác văn bản chính sách hệ thống (Hủy vé, hoàn tiền, hủy do thời tiết, quy chuẩn an toàn).
4. **`get_weather_forecast`** *(READ - Weather API)*: Dự báo thời tiết và tình trạng sóng biển tại các vùng biển Đà Nẵng (Sơn Trà, Mỹ Khê, Mân Thái).
5. **`get_safety_alert`** *(READ - Maritime Safety)*: Cảnh báo an toàn hàng hải, ngưỡng rủi ro sóng to gió lớn cho từng loại hình hoạt động biển.
6. **`request_booking_confirmation`** *(WRITE - Redis)*: Tạo phiếu xác nhận đặt chỗ nháp (`ConfirmationCard`) dạng Native Option với `option_id`, `slot_id`, `unitPrice`, `participants` lưu vào Redis với TTL 15 phút.

### B. 9 Công cụ mở rộng khách hàng (CustomerFeatureTool)
7. **`ai_smart_search`** *(READ)*: Tìm kiếm dịch vụ bằng ngôn ngữ tự nhiên, tự động trích xuất ngân sách nhóm, số người, ngày đi và ràng buộc giờ.
8. **`ai_recommend_services`** *(READ)*: Đề xuất dịch vụ dựa trên sở thích, điểm đánh giá và điểm tương quan `RELEVANCE` từ Quyet Worker.
9. **`ai_nearby_services`** *(READ)*: Tìm kiếm trải nghiệm biển xung quanh tọa độ GPS người dùng bằng công thức bán kính Haversine.
10. **`ai_compare_services`** *(READ)*: So sánh từ 2 đến 4 dịch vụ về tổng chi phí cho cả đoàn, quyền lợi gói, thời lượng và chính sách hoàn tiền chung.
11. **`ai_plan_itinerary`** *(WRITE - PostgreSQL V21/V23)*: Tự động lập lịch trình du lịch biển tối ưu ngân sách, kiểm tra thời gian di chuyển giữa các điểm và tính an toàn thời tiết.
12. **`ai_replan_itinerary`** *(WRITE - PostgreSQL V23)*: Tái lập lịch trình khi có slot bị hủy hoặc thời tiết thay đổi, sinh Proposal diff và kiểm soát đồng thời bằng Optimistic Locking (`@Version`).
13. **`ai_review_summary`** *(READ)*: Đọc tóm tắt đánh giá trích xuất từ tối đa 50 bình luận thực tế; Quyet phân tích khía cạnh và CLI Proxy trích dẫn câu có ID nguồn kiểm chứng.
14. **`ai_weather_slot`** *(READ)*: Kiểm tra an toàn thời tiết cho một ca dịch vụ cụ thể; nếu thiếu dữ liệu sẽ trả về `UNKNOWN` chứ không tự ý cho phép đặt chỗ.
15. **`ai_customer_support`** *(READ)*: Tra cứu trạng thái đơn hàng của chính khách hàng đăng nhập và xem trước chính sách hủy (`CancellationPreviewResult`).
16. **`ai_marine_briefing`** *(READ - PostgreSQL/Weather)*: Cẩm nang an toàn biển và checklist đồ cần mang cá nhân hóa theo từng hoạt động đặt chỗ của khách.

### C. Bộ công cụ dành cho Đối tác (VendorFeatureTool — 5 Tools)
17. **`vendor_dashboard_summary`**: Báo cáo tổng hợp số liệu kinh doanh, tỷ lệ lấp đầy slot và đánh giá chất lượng.
18. **`vendor_revenue_and_bookings`**: Thống kê doanh thu thực tế, tiền quyết toán và lịch đặt tour theo chuỗi thời gian.
19. **`vendor_review_coaching`**: Phân tích đánh giá theo 4 trụ cột (`GUIDE`, `SAFETY`, `PUNCTUALITY`, `VALUE`), trích dẫn dẫn chứng và tư vấn nâng hạng huy hiệu.
20. **`vendor_slot_advisor`**: Dự báo công suất lấp đầy slot và cảnh báo thời tiết biển 7-14 ngày để tư vấn mở thêm/giảm ca.
21. **`vendor_optimize_listing`**: Chấm điểm chất lượng bài đăng tour (0-100), phát hiện thiếu sót an toàn và tối ưu nội dung chuẩn SEO.

### D. Bộ công cụ dành cho Quản trị viên (AdminFeatureTool — 6 Tools)
22. **`admin_platform_dashboard`**: Bức tranh kinh doanh toàn sàn 1 chạm (GMV, Payout, Commission, số lượng booking và cảnh báo rủi ro).
23. **`admin_revenue_timeseries`**: Biểu đồ chuỗi thời gian GMV, tiền thu, tiền hoàn và hoa hồng theo ngày/tuần/tháng.
24. **`admin_booking_cancellation_breakdown`**: Phân loại lý do hủy tour toàn sàn (thời tiết biển, lỗi đối tác, khách hủy, can thiệp admin).
25. **`admin_vendor_diagnostics`**: Chẩn đoán sức khỏe vận hành đối tác (tỷ lệ hủy, tỷ lệ lấp đầy, điểm số và vi phạm).
26. **`admin_dispute_summarize`**: Tóm tắt lượt quét QR, trạng thái ca và lời trình bày của hai bên.
27. **`admin_risk_and_support_queue_overview`**: Giám sát hàng đợi rủi ro thanh toán và ticket hỗ trợ khách hàng.

### E. Ánh xạ Thẻ Giao Diện Cấu Trúc (AssistantArtifactMapper)
Các kết quả gọi tool được chuyển hóa thành các thẻ tương tác (`AssistantArtifact`) đồng nhất:
- `BOOKING_CONFIRMATION_CARD`: Thẻ xác nhận đặt vé nháp.
- `ITINERARY_PREVIEW`: Thẻ phương án lịch trình kèm thông tin di chuyển.
- `DISCOVERY_SUGGESTION`: Thẻ danh sách dịch vụ gợi ý kèm điểm tương quan.
- `SERVICE_COMPARISON`: Thẻ ma trận so sánh chi tiết giữa 2-4 dịch vụ.
- `REVIEW_HIGHLIGHT`: Thẻ trích dẫn đánh giá thực tế có ID nguồn.
- `CANONICAL_POLICY`: Thẻ chính sách hoàn hủy quy chuẩn từ hệ thống.
- `SUPPORT_ACTION`: Thẻ hành động hỗ trợ đơn hàng.

---

## 3. DANH SÁCH TOÀN BỘ API BACKEND MODULE AI

### A. Nhóm Chat Assistant (`/api/assistant/**`)
- **`POST /api/assistant/chat`** *(READ + WRITE)*: Tiếp nhận câu hỏi, kiểm tra Rate Limit, nạp lịch sử từ DB, gọi CLI Proxy LLM trong vòng lặp Tool Calling (tối đa 5 lượt) và trả lời. Hỗ trợ header `Idempotency-Key` (chống xử lý trùng) và `X-Execution-Budget-Ms`.
- **`GET /api/assistant/conversations/{id}`** *(READ)*: Lấy thông tin hội thoại kèm kiểm tra quyền sở hữu (403 Forbidden nếu truy cập chéo).
- **`GET /api/assistant/conversations/{id}/history`** *(READ)*: Lấy lịch sử tin nhắn gần nhất.
- **`POST /api/assistant/conversations/{id}/confirm`** *(READ + WRITE)*: Xác nhận đặt chỗ từ Thẻ nháp. Thiết lập khóa phân tán Redis (30s), kiểm tra Outcome bền vững (V25), thẩm định lại giá & slot thời gian thực, kích hoạt `CreateBookingHoldUseCase` giữ chỗ 15 phút kèm cơ chế bù trừ tự động.

### B. Nhóm Tính Năng Khám Phá Khách Hàng AI (`/api/ai/**` — Yêu cầu Đăng nhập)
- **`POST /api/ai/search`**: Khám phá thông minh bằng ngôn ngữ tự nhiên. Quét tới 10.000 items catalog, hỗ trợ phân trang `offset`/`nextOffset`, trả về candidates và intent.
- **`POST /api/ai/recommendations`**: Gợi ý dịch vụ theo ngữ cảnh và sở thích kết hợp Quyet Relevance Scoring (top 5), lưu `recommendationId` (V24).
- **`POST /api/ai/nearby`**: Tìm dịch vụ quanh tọa độ GPS trong bán kính `radiusKm`.
- **`POST /api/ai/services/compare`**: So sánh 2–4 dịch vụ theo cấu trúc giá nhóm và tiện ích.
- **`POST /api/ai/weather`**: Đánh giá điều kiện thời tiết cho một cặp Service-Option-Slot cụ thể.
- **`GET /api/ai/review-summaries/{serviceId}`**: Tóm tắt đánh giá khách quan dựa trên review thật chưa bị gắn cờ.
- **`POST /api/ai/support`**: Hỗ trợ khách hàng tra cứu đơn của chính mình và xem trước điều kiện hoàn hủy (`CancellationPreviewResult`).

### C. Nhóm Vòng Đời Lịch Trình (`/api/ai/itineraries/**`)
- **`POST /api/ai/itineraries/preview`**: Tạo tối đa 3 phương án xem trước, lưu vào bảng `ai_itinerary_previews` (TTL 15 phút).
- **`POST /api/ai/itineraries/previews/{previewId}/save`**: Lưu phương án đã chọn vào bảng `ai_itineraries` (`status = DRAFT`, `version = 0`) kèm `Idempotency-Key`.
- **`POST /api/ai/itineraries/{id}/accept`**: Tái kiểm tra giá/slot/thời tiết và chuyển trạng thái lịch trình thành `ACCEPTED` (`version = 1`).
- **`POST /api/ai/itineraries/{id}/archive`**: Lưu trữ lịch trình (`status = ARCHIVED`).
- **`GET /api/ai/itineraries/page`**: Lấy danh sách lịch trình phân trang (tối đa 20 lịch/trang).
- **`GET /api/ai/itineraries/{id}/revisions`**: Xem toàn bộ lịch sử các phiên bản sửa đổi trong `ai_itinerary_revisions`.
- **`POST /api/ai/itineraries/{id}/replan`**: Tạo đề xuất Re-plan `PENDING` trong `ai_itinerary_proposals` kèm diff, không đè lịch đang chạy.
- **`GET /api/ai/itineraries/{id}/proposals`**: Xem danh sách đề xuất Re-plan.
- **`POST /api/ai/itineraries/{id}/proposals/{proposalId}/accept`**: Chấp thuận đề xuất, lưu revision và tăng version.
- **`POST /api/ai/itineraries/{id}/proposals/{proposalId}/reject`**: Từ chối đề xuất.

### D. Nhóm Sở Thích & Phản Hồi Gợi Ý (`/api/ai/preferences` & `/api/ai/recommendations/**`)
- **`GET /api/ai/preferences`**: Xem cấu hình sở thích cá nhân và trạng thái consent.
- **`PUT /api/ai/preferences`**: Bật/tắt consent, cập nhật interests và exclusions với khóa lạc quan `expectedVersion`.
- **`POST /api/ai/recommendations/{recommendationId}/feedback`**: Gửi phản hồi tương tác (`SHOWN`, `CLICK`, `POSITIVE`, `NEGATIVE`) kèm `Idempotency-Key`.

### E. Nhóm Hỗ Trợ Đơn Hàng & Hàng Đợi Admin (`/api/ai/support/requests/**` & `/api/admin/support/requests/**`)
- **`POST /api/ai/support/requests/preview`**: Xem trước thông tin yêu cầu đổi lịch/hỗ trợ.
- **`POST /api/ai/support/requests`**: Tạo ticket hỗ trợ lưu vào `ai_customer_support_requests` (`status = WAITING_REVIEW`) có `Idempotency-Key`.
- **`GET /api/admin/support/requests`** *(ADMIN ONLY)*: Truy vấn danh sách ticket chờ duyệt.
- **`POST /api/admin/support/requests/{id}/handle`** *(ADMIN ONLY)*: Admin tiếp nhận và xử lý ticket (`RESOLVED` hoặc `DECLINED`) với `expectedVersion` và ghi chú giải trình.

### F. Nhóm Kiểm Duyệt & Đánh Giá Rủi Ro (`/api/ai/**` & `/api/admin/ai/**`)
- **`POST /api/ai/content-assessments`**: Quét văn bản (1–2500 ký tự) bằng Regex (SĐT/link) + Quyet Moderation $\rightarrow$ lưu `ai_assessment_cases`.
- **`POST /api/ai/classifications/service`**: Dự đoán phân loại danh mục dịch vụ (SUP, KAYAK, DIVING...).
- **`POST /api/admin/ai/risk-cases`** *(ADMIN ONLY)*: Phân tích rủi ro giao dịch dựa trên vận tốc tạo đơn, thanh toán lỗi và hoàn tiền $\rightarrow$ tạo case thẩm định.
- **`GET /api/admin/ai/assessment-cases`** *(ADMIN ONLY)*: Lấy danh sách hồ sơ rủi ro/kiểm duyệt theo trạng thái (`NEEDS_REVIEW`).
- **`POST /api/admin/ai/assessment-cases/{id}/resolve`** *(ADMIN ONLY)*: Admin giải quyết hồ sơ (`RESOLVED`/`DISMISSED`) kèm ghi chú giải trình.

---

## 4. QUY TẮC NGHIỆP VỤ & THUẬT TOÁN ĐẶC TẢ

### 1. Thuật toán Lập Lịch Trình (Bounded Chronological Greedy with Estimated Transfers)
- **Đầu vào:** `TravelContext` (ngày đi, khung giờ `dayStart` - `dayEnd`, số người `partySize`, ngân sách tổng `effectiveBudget`, số lượng hoạt động tối đa `maxActivities`).
- **Lấy mẫu:** Quét ứng viên từ bộ lọc Recommendation, phẳng hóa tối đa 5 slot/option, sắp xếp tăng dần theo: `slot.date + slot.start` $\rightarrow$ `partyTotal` $\rightarrow$ `service.id`.
- **Duyệt tham lam (Greedy Loop):**
  1. Kiểm tra giới hạn: Dừng lại nếu đã đủ `maxActivities`.
  2. Không trùng lặp: Mỗi dịch vụ chỉ xuất hiện tối đa 1 lần trong cả hành trình.
  3. Ngân sách: Tổng chi phí không vượt quá `effectiveBudget`.
  4. **Ước lượng chuyển tiếp (Estimated Transfers):**
     $$\text{transitMinutes} = 15 \text{ (phút đệm)} + \left\lceil \frac{\text{distanceKm}}{25} \times 60 \right\rceil \text{ phút}$$
     Nếu $\text{start}_{\text{sau}} < \text{end}_{\text{trước}} + \text{transitMinutes} \rightarrow$ Bỏ qua slot do không kịp di chuyển giữa 2 bãi biển.
  5. Thời tiết: Phải đạt chuẩn `acceptable` từ `TravelWeatherPort`.
- **Kết quả:** Sinh tối đa 3 phương án lưu vào `ai_itinerary_previews`. Khi lưu draft ghi vào `ai_itineraries` với `version = 0`.

### 2. Thuật toán Tái Lập Lịch Trình (Re-planning with Proposal Diff & Monitoring Job)
- **Bảo vệ quyền sở hữu:** `itinerary.ownerId == currentUserId` (chặn 404/403).
- **Kiểm soát phiên bản:** Đối chiếu `request.expectedVersion == savedItinerary.version`. Nếu lệch $\rightarrow$ HTTP 409 Conflict.
- **Tạo Proposal:** Chạy lại thuật toán loại bỏ các slot/dịch vụ bị hủy, lưu bản ghi vào `ai_itinerary_proposals` với trạng thái `PENDING` và diff: `removedSlotIds`, `addedSlotIds`, `changedItems`.
- **Giám sát tự động (`ItinerarySourceMonitoringJob`):** Quét định kỳ mỗi 60s các ca bị hủy hoặc thời tiết xấu của lịch `ACCEPTED`, tự động sinh Proposal PENDING và gửi In-App Notification (dedup) cho khách hàng.

### 3. Quy tắc Tính Giá Khách Đoàn & Phân Nhóm Gói Riêng (Party Pricing Formula)
- **Hình thức theo người (`PER_PERSON`):**
  $$\text{quantity} = \text{partySize}, \quad \text{partyTotal} = \text{unitPrice} \times \text{quantity}$$
- **Hình thức theo gói riêng (`PER_PACKAGE`):**
  $$\text{quantity} = \left\lceil \frac{\text{partySize}}{\text{maxPaxPerPackage}} \right\rceil, \quad \text{partyTotal} = \text{unitPrice} \times \text{quantity}$$
  - Phân bổ số người vào từng gói qua `ParticipantGrouping.propose(...)` (Ví dụ: nhóm 3 người, gói cano tối đa 2 người/gói $\rightarrow$ cần 2 gói với danh sách phân bổ `[2, 1]`).

### 4. Cơ chế Xác Nhận Đặt Chỗ Bền Vững (Durable Re-validation & Compensation)
- **Outcome Bền Vững (Flyway V25):** Kiểm tra bảng `ai_confirmation_outcomes` trước khi xử lý. Nếu đã hoàn tất $\rightarrow$ Replay ngay kết quả cũ (Idempotent).
- **Khóa phân tán:** Thiết lập `tryAcquireProcessingLock` (TTL 30s) trên Redis.
- **Tái thẩm định:** Đọc lại giá và slot thời gian thực từ CSDL. Nếu có biến động $\rightarrow$ Quản lý số lần thử lại (`retryCount < 2` tạo alternative card; `retryCount >= 2` báo lỗi).
- **Bù trừ tự động (Compensation Rollback):** Nếu tạo Booking Hold thành công nhưng quá trình lưu Outcome vào PostgreSQL gặp lỗi hệ thống, tự động gọi `CancelBookingHoldUseCase` giải phóng tồn kho ngay lập tức.

### 5. Bộ Quy Tắc Phát Hiện Rủi Ro Giao Dịch (Velocity Rules 3/5/3)
Backend tính toán các chỉ số hành vi thực tế trong CSDL:
- **Số lần thanh toán thất bại:** $\ge 3$ lần trong vòng 24 giờ (`THREE_FAILED_PAYMENTS_IN_24H`).
- **Tần suất tạo đơn hàng:** $\ge 5$ đơn trong vòng 1 giờ (`FIVE_ORDERS_IN_1H`).
- **Tần suất yêu cầu hoàn tiền:** $\ge 3$ yêu cầu trong vòng 7 ngày (`THREE_REFUND_REQUESTS_IN_7D`).
- Kết hợp với xác suất rủi ro từ Quyet Worker task `RISK` (ngưỡng $\ge 0.5$). Nếu thỏa mãn bất kỳ điều kiện nào $\rightarrow$ Đưa vào hàng đợi `NEEDS_REVIEW`.

---

## 5. CÁC KỊCH BẢN THỰC TẾ ĐIỂN HÌNH

### Kịch bản 1: Khám Phá Thông Minh ➔ Lập Lịch Trình 3 Bước ➔ Giám Sát Tự Động
1. **Tìm kiếm:** Khách gọi `POST /api/ai/search` gửi `"Tìm tour lặn Cù Lao Chàm ngày mai cho 3 người dưới 400k/người"`.
   - Backend parse query: keyword `"lặn Cù Lao Chàm"`, `partySize = 3`, `effectiveBudget = 1.200.000đ`.
   - `AiCatalogReader` quét catalog thực tế, lọc option và slot khả dụng, trả danh sách kết quả kèm intent.
2. **Lập lịch xem trước (Preview):** Khách gọi `POST /api/ai/itineraries/preview`.
   - Hệ thống chạy thuật toán Greedy, tính thời gian di chuyển giữa các bãi biển (đệm 15m, tốc độ 25km/h), kiểm tra an toàn sóng gió.
   - Sinh 3 phương án xem trước lưu vào `ai_itinerary_previews` (TTL 15m).
3. **Lưu nháp & Chấp thuận (Save Draft & Accept):**
   - Khách chọn phương án 1 $\rightarrow$ gọi `/previews/{id}/save` kèm `Idempotency-Key` $\rightarrow$ lưu `ai_itineraries` (`status: DRAFT`).
   - Khách bấm chốt lịch $\rightarrow$ gọi `/{id}/accept` $\rightarrow$ Backend kiểm tra lại giá và slot thời gian thực $\rightarrow$ chuyển sang `ACCEPTED` (`version: 1`).
4. **Giám sát tự động khi biển động:**
   - 3 tiếng sau, trạm khí tượng phát cảnh báo sóng lớn tại Cù Lao Chàm.
   - `ItinerarySourceMonitoringJob` phát hiện thời tiết xấu, tự động sinh Proposal `PENDING` và đẩy thông báo In-App Notification cho khách hàng đề xuất chuyển sang trải nghiệm ven bờ.

### Kịch bản 2: Trợ Lý Đa Năng ➔ Chốt Vé Native Option ➔ Khóa Giữ Chỗ Re-validation Bền Vững
1. **Tư vấn:** Khách chat: *"Mai tôi muốn đi cano riêng ngắm bán đảo Sơn Trà nhóm 3 người, có gói nào phù hợp không?"*
   - CLI Proxy LLM kích hoạt công cụ `ai_smart_search` và `get_service_detail`. Backend trả về gói cano riêng (tối đa 2 người/gói, giá 1.500.000đ/gói).
2. **Chốt phiếu đặt vé nháp:** Khách đồng ý đặt $\rightarrow$ LLM gọi `request_booking_confirmation(service_id, option_id, slot_id, date, participants: 3)`.
   - Backend tính toán: Cần 2 gói cano, phân bổ `[2, 1]`, sinh `ConfirmationCard` lưu vào Redis với TTL 15 phút.
   - Khách nhìn thấy Thẻ Xác Nhận trên giao diện chat kèm nút **[Xác Nhận Đặt Chỗ]**.
3. **Thẩm định & Khóa giữ chỗ bền vững:** Khách bấm xác nhận $\rightarrow$ gọi `POST /api/assistant/conversations/{id}/confirm`.
   - Backend kiểm tra bảng `ai_confirmation_outcomes` (chống xử lý lặp), thiết lập khóa phân tán Redis 30s, kiểm tra quyền sở hữu hội thoại.
   - Đối chiếu lại giá và chỗ trống thực tế trong PostgreSQL.
   - Kích hoạt `CreateBookingHoldUseCase`, chạy script Redis Lua khóa nguyên tử và tạo bản ghi Booking trạng thái `HOLD` (15 phút) trong CSDL.
   - Lưu outcome vào `ai_confirmation_outcomes` (V25), giải phóng lock và chuyển khách hàng sang màn hình thanh toán đơn hàng an toàn.


### Kịch bản 3: Đối Tác Huấn Luyện Đánh Giá 4 Trụ Cột ➔ Tối Ưu Lịch & Bài Đăng
1. **Huấn luyện chất lượng:** Đối tác gọi `POST /api/vendor/ai/review-coaching`.
   - Backend quét các bình luận thực tế, trích xuất điểm mạnh/yếu theo 4 trụ cột (`GUIDE`, `SAFETY`, `PUNCTUALITY`, `VALUE`) kèm dẫn chứng bằng lời của khách.
   - Đề xuất lộ trình từ `VERIFIED` lên `TOP_RATED` và lưu trữ phân tích vào `ai_vendor_insights` (V55).
2. **Soạn câu trả lời thông minh:** Khi nhận một đánh giá 3 sao phản ánh việc chờ cano lâu, đối tác bấm `POST /api/vendor/ai/reviews/{id}/reply-draft` (tone: `EMPATHETIC`, customPoints: `"thời gian chờ cano"`).
   - AI sinh câu trả lời thấu cảm, cam kết nâng cấp quy trình và đính kèm lời xin lỗi chân thành giữ chân khách hàng.
3. **Tối ưu hóa bài đăng & Cố vấn slot:**
   - Đối tác khởi tạo bài đăng mới qua `/api/vendor/ai/listings/optimize` $\rightarrow$ AI chấm điểm 45/100, chỉ ra thiếu thông tin áo phao và tạo bản nháp từ dữ liệu đối tác đã cung cấp.
   - Đối tác kiểm tra lịch slot qua `/api/vendor/ai/slots/advisor` $\rightarrow$ AI cảnh báo ca sáng thứ Bảy đã kín 95%, đề xuất mở thêm ca 05:30 đón bình minh.

### Kịch bản 4: Tóm Tắt Tranh Chấp & Cẩm Nang Biển 24 Giờ
1. **Khách hàng khiếu nại không có cano đón:** Khách yêu cầu hoàn tiền 100% qua đơn tranh chấp `DISPUTE-101`.
2. **Admin hòa giải bằng AI:** Quản trị viên bấm `POST /api/admin/ai/disputes/{id}/summarize`.
   - Bản tóm tắt hiển thị lượt quét QR, trạng thái ca và lời trình bày của hai bên.
   - Kết quả `MANUAL_REVIEW_REQUIRED` chuyển quản trị viên xác minh tình huống và chính sách hoàn tiền trước khi quyết định.
3. **Tự động kích hoạt Cẩm nang biển trước 24 giờ:**
   - `TripReminderJob` quét các chuyến đi khởi hành trong ngày mai.
   - Tự động gọi `CustomerMarineBriefingUseCase.preGenerateForTripReminder` và gửi thông báo `MARINE_BRIEFING_READY` đến điện thoại khách hàng kèm checklist đồ cần mang và hướng dẫn an toàn biển.
