# Báo cáo triển khai tối ưu và nghiệm thu QA DANASEA AI (18/11/2026)

## 1. Thông tin chung & Bối cảnh thực thi

- **Nhánh / Commit gốc:** `code/danasea-ai-grounding` · `929fdec0f04feb468fa03267e3d602579ac01770` (worktree cục bộ; nhánh `main` giữ nguyên không chỉnh sửa).
- **Thành phần tham gia kiểm chứng cục bộ:** Spring Boot Backend (`Java 21`, cổng `8080`), Quyet Python Worker (`chinhnc/Quyet-1.0-Small` revision `233167bba5df61b5375a522bf8a042d8d2189379`, rubric `danasea-2026-11-18-v2`, CPU 4 threads, cổng `8091`), React/Vite Frontend (cổng `3000`), PostgreSQL 16 + Redis cục bộ.
- **Định mức nghiệm thu đối chiếu:** [Bộ 125 tình huống kiểm thử](DANASEA_AI_Test_Cases.md) · L1 đạt khi `score >= 2/3`, L2–L3 đạt khi `score = 3/3`; ngưỡng mục tiêu `113/125` (90%) và `0` lỗi nghiêm trọng về phân quyền, giao dịch, dữ liệu riêng tư hay an toàn thời tiết.
- **Tệp kết quả chi tiết 125 ca:** [ket-qua-125-ca.json](ket-qua-125-ca.json).

---

## 2. Tổng hợp kết quả QA 125 ca (So với mốc `58/125`)

| Nhóm | Mốc trước (lượt 1) | Mốc đối chiếu (18/11) | Sau tối ưu (`qa_run_3`) | Tổng điểm (`qa_run_3`) | Số ca `PARTIAL` | Độ trễ median / p95 (`qa_run_3`) |
| --- | --- | --- | --- | --- | --- | --- |
| **USER (42)** | — | 18 / 42 (88/126) | **40 / 42** | **123 / 126** | **0** | 4,42s / 17,0s (`>15s: 7`) |
| **VENDOR (35)** | — | 18 / 35 (74/105) | **33 / 35** | **102 / 105** | **0** | 1,81s / 4,87s (`>15s: 0`) |
| **ADMIN (34)** | — | 13 / 34 (65/102) | **33 / 34** | **101 / 102** | **0** | 1,61s / 2,07s (`>15s: 0`) |
| **CROSS (14)** | — | 9 / 14 (37/42) | **13 / 14** | **41 / 42** | **0** | 2,03s / 6,34s (`>15s: 0`) |
| **Tổng cộng (125)** | **35 / 125 (200/375)** | **58 / 125 (264/375)** | **119 / 125 (95,2%)** | **367 / 375** | **0 / 125** | — |

- **Lỗi nghiêm trọng / ca 0 điểm / rò rỉ dữ liệu / overbook / bỏ qua rào cản thời tiết:** **0 ca** (`Zero scores: []`).
- **Tỷ lệ `PARTIAL`:** Giảm từ `8/42` ở nhóm USER (và hàng loạt ca công cụ lặp ở baseline) xuống **`0/125`** trên toàn bộ 4 vai trò.
- **Khối lỗi HTTP có chủ đích (negative/safety controls):**
  - Moderation / prompt injection / privacy: `U39`, `U42`, `A26`, `A33` trả HTTP `400`;
  - Cross-role & IDOR (`X09`): hội thoại khác user (`403`), lịch trình khác user (`404`), đơn khác user (`403`), review coaching chéo vendor (`403`, đã vá từ lỗ hổng `200` trước đó);
  - Kiểm tra biên đầu vào: `X05` (`partySize=51` -> `400`), `X06` (`past`, `+30 ngày`, khoảng `>14 ngày` -> đều `400`), `X13` (trộn EN trong hội thoại VI -> `409`);
  - Rate limit & Circuit breaker: `X12` (23 request `200`, request #24 trả `429 RATE_LIMIT_EXCEEDED`), `X11` (dừng CLI Proxy -> `503` trong `0,014s`, sau đó khởi động lại bình thường).

---

## 3. Các cải tiến đã triển khai theo từng nhóm chức năng

### 3.1. Định tuyến Copilot, thanh toán, kiểm duyệt & dữ liệu vận hành
- **Nguồn thanh toán trực tiếp (`U09`):** Bổ sung `GetPaymentProvidersTool` (`get_payment_providers`) đọc từ `PaymentProviderAvailability`, thống nhất hoàn toàn với `GET /api/payments/providers` (`VNPAY`, `PAYPAL`, `PAYOS`, `ZALOPAY` bật; `MOMO`, `SEPAY` tắt) và hiển thị card `PAYMENT_PROVIDERS` trên UI khách hàng.
- **Phân tách kiểm duyệt & từ khóa xung đột (`V25`, `A31`):** Cập nhật `CopilotRequest`, `RoleCopilotUseCase` để ưu tiên ngữ cảnh kiểm duyệt (`AssessTextUseCase`, `assess_text` / `admin_text_assessment`) cho nội dung chứa liên hệ/thanh toán ngoài sàn hoặc link đặt tour ngoài trước nhánh slot (`VENDOR_SLOT` tránh khớp nhầm “cá nhân” -> “ca nhan”) và trước nhánh `isPersonalBooking` (`đặt tour`).
- **Review coaching có căn cứ & chặn IDOR (`V18`, `V20`, `V22`, `X09`):** `VendorReviewCoachingUseCase` kiểm tra quyền sở hữu `serviceId` qua `vendorAiData.getService(vendorId, serviceId)` (chặn chéo vendor bằng `403 AI_ACCESS_DENIED`), trả điểm `0/10` (`NO_DATA`) khi `totalReviewsAnalyzed = 0` thay vì khen/cho điểm ảo, trích dẫn `reviewId` và tách mục giả thuyết vận hành cần kiểm chứng khi giải thích phàn nàn.
- **Ngưỡng Admin & đánh giá rủi ro chỉ đọc (`A10–A15`, `A17–A20`, `A27–A30`, `A34`):** `CopilotReportAnswer` và `AdminFeatureTool` đồng bộ quy tắc `VendorPerformanceItem` (`HIGH_CANCELLATION`: `> 20%` WARNING gồm biên `40%`, `> 40%` CRITICAL), `Velocity 3/5/3` (`velocity-demo-v1`), hàng đợi `WAITING_REVIEW`/`NEEDS_REVIEW`, loại trừ `ACTIVE_DISPUTE`, và bổ sung `admin_order_risk_assessment` (mặc định chỉ đọc `createNewAssessment=false`, gắn `fraudConfirmed=false` và `ADMIN_ORDER_RISK_ASSESSMENT_CARD`).
- **Cố vấn ca ưu tiên an toàn hàng hải (`V28–V31`):** `CopilotReportAnswer` đưa các ca `HIGH_RISK` (`18/20 (90.0%) | weather=HIGH_RISK | WEATHER_WATCH`) lên đầu báo cáo, không đề xuất tăng sức chứa khi `HIGH_RISK` hoặc `UNKNOWN`. Giao diện [CopilotArtifactCard.tsx](../../../frontend/src/components/vendor/copilot/CopilotArtifactCard.tsx) xử lý đúng mảng ngày/giờ `[2026,11,19]` / `[8,0]` thành `2026-11-19 (08:00)`.

### 3.2. Planner, vòng đời lịch trình & công cụ hội thoại khách hàng
- **Công cụ đọc & phục hồi lịch trình (`U29–U31`):** Đăng ký `ai_list_itineraries`, `ai_get_itinerary`, `ai_itinerary_history` trong `CustomerFeatureTool` & `CustomerFeatureToolDefinitions`, mở rộng `ItineraryLifecycleController` (thêm endpoint restore proposal), và ánh xạ tự động `"lịch trình số 1" -> UUID + expectedVersion` trong `ChatUseCase`.
- **Ràng buộc biên ngày & chống trùng lặp (`U23–U25`, `X06`):** `PlanItineraryUseCase` bổ sung kiểm tra `REJECTED_BY_DATE_RANGE`, `REJECTED_BY_DUPLICATE_SERVICE`, thử lại ngày kế tiếp `[tomorrow, tomorrow]` khi preview không gắn ngày ở cuối ngày bị rỗng, và `DiscoverServicesUseCase` tự mở rộng `[today, today+7]` có gắn cờ `EXPANDED_DATE_WINDOW_UPCOMING_7D` khi truy vấn không gắn ngày không còn slot hôm nay (`TravelQueryParser.mentionsToday`). `TravelRequest` chặn các ngày quá khứ, `+30 ngày` và khoảng `> 14 ngày` (`X06`).
- **Xác nhận đặt chỗ, đổi giá, hết chỗ & thời tiết (`U33–U37`, `X08`, `X10`):** `SystemPromptBuilder` chuẩn hóa yêu cầu gọi `request_booking_confirmation`; `ChatUseCase` trích xuất ứng viên từ cả `SERVICE_DETAIL` và `SERVICE_LIST` (`AVAILABLE`), định dạng số liệu lượng mưa `fmtMetric` (`10.9 mm` thay cho `10.899999999999999 mm`), và xử lý tường minh ca provider thời tiết lỗi + cache hết hạn + Quyet rủi ro thấp (`X08`: `weatherStatus=UNKNOWN`, `bookingAuthorized=false`).
- **Giao diện khách hàng (`AssistantCardList`):** Kết xuất card `PAYMENT_PROVIDERS`, `ITINERARY_LIST`, `ITINERARY_HISTORY`, nút tạo proposal chấp nhận/từ chối/khôi phục và xử lý thẻ xác nhận `PRICE_CHANGED` / `SLOT_UNAVAILABLE` / giữ chỗ `HOLD`.

### 3.3. Hiệu chỉnh Quyet Worker (`danasea-2026-11-18-v2`)
- Cập nhật mô tả rubric tại [rubrics.py](../../../ai-decision/config/rubrics.py) (nhấn mạnh phủ định đặt chỗ -> `SEARCH`, truy vấn vị trí so với thuộc tính dịch vụ không dấu -> `SEARCH`, tách biệt phản hồi/bài đăng cảnh báo phủ định chống chuyển tiền ngoài sàn) và đồng bộ `RUBRIC_VERSION = "danasea-2026-11-18-v2"` trong [QuyetDecisionAdapter.java](../../../backend/src/main/java/com/danasea/backend/modules/ai/infrastructure/quyet/QuyetDecisionAdapter.java).
- Thống nhất taxonomy 9 nhãn giữa worker, adapter Java và fixture [cases.json](../../../ai-decision/tests/fixtures/quyet-small/cases.json) (`UNCLEAR -> OTHER`), tách tập hiệu chỉnh và tập kiểm chứng độc lập [calibration_holdout.json](../../../ai-decision/tests/fixtures/quyet-small/calibration_holdout.json).

**Số liệu A/B Quyet thực đo (`/tmp/quyet-baseline-eval.json` vs `/tmp/quyet-eval.json`):**
- **Tập đối chiếu lịch sử A/B (29 đầu vào / 37 nhãn):** `danasea-2026-10-08-v1` đạt **14/29 ca (21/37 nhãn)** -> `danasea-2026-11-18-v2` đạt **28/29 ca (36/37 nhãn)**, **`0` hồi quy** trên các ca baseline từng đạt (ca duy nhất lệch là `intent-14`: kỳ vọng `OTHER`, dự đoán `SEARCH`).
- **Tập Calibration (55 ca / 85 nhãn):** Baseline đạt `67,27%` hàng / `76,47%` nhãn -> bản mới đạt **`96,36%` hàng (53/55) / `97,65%` nhãn (83/85)** (`intent`: `95,83%`, `moderation`: `100%`).
- **Tập Holdout độc lập (51 ca / 79 nhãn):** Baseline đạt `52,94%` hàng / `65,82%` nhãn -> bản mới đạt **`72,55%` hàng (37/51) / `82,28%` nhãn (65/79)** (`moderation`: **`94,44%`**, `review`/`risk`/`service_category`: **`100%`**, `intent`: `67,74%`, `complaint`: `50%`).
- **X03 & X04 thực tế trong `qa_run_3`:** `X03` trả `SEARCH` (`0,5783` > `BOOKING 0,4182`); `X04` trả `SEARCH` (`0,8337`), API tìm kiếm không dấu & có dấu trả cùng `2` ứng viên hợp lệ cho 3 khách `< 400.000đ/người`.

---

## 4. Kết quả các cổng kiểm chứng tự động (chạy sau lần sửa mã cuối)

1. **Backend Maven & Governance / Coverage (`task-1233`):**
   - `./mvnw -B clean verify`: **Tests run: `3.686`, Failures: `0`, Errors: `0`, Skipped: `3`** (`BUILD SUCCESS`).
   - `scripts/governance/check_test_reports.py --policy .ci/test-policy.json`: **`3.683` executed**, **`3` allowed_skipped** (các ca provider ngoài tùy chọn được policy cho phép), **`35` required_suites**, **`411` source_suites**, `0` unexpected skips.
   - `scripts/qa/check_coverage.py --policy .ci/coverage-policy.json`: **`passed: true`, `errors: []`** — LINE **`82,27%`** (`26.447/32.147`), INSTRUCTION **`80,60%`** (`139.197/172.705`), BRANCH **`62,26%`** (`11.734/18.848`), CLASS **`94,10%`**, METHOD **`79,01%`**.
   - `scripts/ci/run_java_worker_acceptance.py` + `scripts/ai/check_ai_acceptance.py`: **`7/7` case outcomes PASSED** (`intent` 104,19ms, `service_category` 46,74ms, `review` 71,87ms, `moderation` 112,79ms, `relevance` 45,38ms, `complaint` 80,84ms, `risk` 42,71ms; `route=direct`, `suggestion_only=true`).
2. **Quyet Worker (`task-1209`):**
   - `unittest discover` (với `QUYET_RUN_CALIBRATION_TESTS=1` chạy cả test ngưỡng model thật): **12/12 tests OK** trong `28,479s` (`0` skipped).
   - `tests/smoke_production_worker.py`: **7/7 tasks HTTP 200**, `warnings: []`, `rubricVersion: danasea-2026-11-18-v2`.
3. **Frontend & Chrome UI E2E (`task-1121`):**
   - `npm run lint` (`eslint . --max-warnings 0`): OK (`0` warnings/errors).
   - `npm run build` (`tsc -b && vite build`): OK (`2.347` modules).
   - Unit tests: `assistantCardSummary.test.ts` (**5/5**), `copilotArtifacts.test.ts` (**18/18**), `adminAiService.test.ts` (**6/6**) — tổng **29/29 pass, 0 fail, 0 skip**.
   - Playwright Chromium qua cả 3 vai trò: xác nhận hiển thị `POLICY`, `PAYMENT_PROVIDERS` (`VNPAY, PAYPAL, PAYOS, ZALOPAY`), `ITINERARY_LIST` + `ITINERARY_HISTORY` (`v0 DRAFT` / `v1 ACCEPTED`), thẻ đặt chỗ `2+1` + trạng thái giữ chỗ thành công sau bấm nút xác nhận, `COPILOT_CONTENT_ASSESSMENT_CARD` (Vendor `V25` & Admin `A31`), `VendorReviewCoaching` + `VendorSlotAdvisor` (`18/20 HIGH_RISK` và định dạng ngày `2026-11-19 (08:00)` ở `V18`/`V28`), và `ADMIN_ORDER_RISK_ASSESSMENT_CARD` (`newCaseCreated=false`, `fraudConfirmed=false` ở `A34`); `consoleErrors: []`, `pageErrors: []`. *(Lưu ý: cờ script `u09_has_vnpay_paypal` trả `false` do selector/timing kiểm tra chuỗi, nhưng ảnh chụp màn hình `customer_u06_u09.png` xác nhận UI đã render đủ danh sách `VNPAY, PAYPAL, PAYOS, ZALOPAY` và thẻ phương thức thanh toán).*
4. **Python Ops / CI Tests (`task-1242`):**
   - `python3 -m unittest discover -s scripts/tests -v`: **234 tests OK**, `16` skipped (các ca phụ thuộc kiểm tra live GitHub/VPS).

---

## 5. 6 ca còn ở mức `2/3` (`Chưa đạt`) và phạm vi còn lại

1. **`U17` (L2):** Đã lọc sạch dịch vụ lặn cho người không biết bơi (`excludedActivities=['lan']`) và trả chi tiết thuyền ngắm biển kèm dự báo an toàn `GREEN`, nhưng lời thoại chat chưa viết rõ nhắc nhở áo phao/hỗ trợ an toàn cho người không biết bơi trong `message`.
2. **`U24` (L3):** Planner chạy một lượt sạch không lặp tool và lời thoại khớp SUP Mân Thái + Sơn Trà, nhưng danh sách `alternatives` bên trong card `ITINERARY` vẫn thiếu SUP Mân Thái do `PLACE_NAME_REQUIRES_COORDINATES`.
3. **`V24` (L2):** Câu hỏi không đính kèm `serviceId` nên AI từ chối an toàn và yêu cầu đính kèm bài đăng (không bịa chứng nhận), nhưng chưa xuất gợi ý viết lại SEO cho lượt hỏi đơn này.
4. **`V29` (L3):** Nhận diện đúng công suất thấp (`< 20%`) và đề xuất Flash Sale/gộp ca an toàn, nhưng chữ `"mới"` trong câu `"chỉ mới có 1/20 khách"` làm bộ lọc chọn nhầm dịch vụ có tên chứa `"mới"` (`0/20`) thay vì ca `1/20`.
5. **`A25` (L3):** Câu hỏi độc lập thiếu mã tranh chấp nên hỏi lại `disputeId` thay vì suy đoán (phiên có đính kèm `contextType=DISPUTE` ở `A25-attached` trả đầy đủ `ADMIN_DISPUTE_BRIEF_CARD`).
6. **`X01` (L3):** Lượt live `X01-live` gọi `get_service_detail` đúng thông tin sạch không rò rỉ dữ liệu, nhưng trong `qa_run_3` chưa chèn trực tiếp chuỗi prompt injection vào mô tả dịch vụ trên DB để thử lại live end-to-end (hành vi cô lập dữ liệu không đáng tin cậy tiếp tục được bảo đảm qua backend/unit tests).

### Ghi nhận phạm vi ngoài QA cục bộ
- Ứng dụng **Mobile**, cấu hình hạ tầng **Production** (HTTPS, provider thanh toán thật, rate-limit/WAF công cộng) và các luồng CI/CD trên GitHub Actions chưa chạy trực tiếp trong phiên làm việc cục bộ này.
- Các bản sao lưu tạm thời phục vụ QA (`danasea_pre_qa.sql`, ảnh chụp và log `qa_run_3` / `browser_qa`) được giữ trong thư mục scratch ngoài repo; không có khóa API, mật khẩu hay token xác thực nào được ghi vào mã nguồn hoặc tài liệu bàn giao.
