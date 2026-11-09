# 📖 TÓM TẮT CÁC SƠ ĐỒ — HỆ THỐNG DANASEA AI (HYBRID ARCHITECTURE & WORKFLOWS)

> Thư mục này chứa **12 sơ đồ kỹ thuật chuẩn UML** mô tả toàn diện kiến trúc AI Lai (CLI Proxy LLM + Quyet Small Inference Worker), luồng Chat Assistant đa công cụ, cơ chế bảo vệ Moderation & Idempotency, chu trình đặt chỗ Re-validation bền vững (Flyway V25), vòng đời Lập lịch trình 3 giai đoạn (V23), hệ thống quản lý sở thích & phản hồi gợi ý (V24), luồng hỗ trợ khách hàng và hàng đợi thẩm định của Quản trị viên.

| # | Sơ đồ | Loại | Mã Nguồn (.mmd) | Nội dung cốt lõi |
|---|-------|------|-----------------|-------------------|
| 1 | `AI_Chat` | Sequence Diagram | [`codeFlows/Sequence_Diagram/AI_Chat_SD.mmd`](codeFlows/Sequence_Diagram/AI_Chat_SD.mmd) | Luồng tổng quan Chat AI (AiExecutionBudgetFilter 8s → Idempotency → LlamaGuard → LLM Tool Loop → ArtifactMapper → DB) |
| 2 | `Chat_ModerationFlow` | Sequence Diagram | [`codeFlows/Sequence_Diagram/Chat_Moderation_SD.mmd`](codeFlows/Sequence_Diagram/Chat_Moderation_SD.mmd) | Chi tiết bảo vệ: Rate Limit 2 tầng, cắt chuỗi 1800 ký tự chống Padding, Tool Calling 5 lượt, Policy Guard thu hồi câu trả lời không có căn cứ |
| 3 | `RevalidBooking` | Sequence Diagram | [`codeFlows/Sequence_Diagram/RevalidBooking_SD.mmd`](codeFlows/Sequence_Diagram/RevalidBooking_SD.mmd) | Xác nhận đặt chỗ thời gian thực: Outcome bền vững V25, Khóa phân tán Redis 30s, Ràng buộc 3 bên, Grouping gói riêng, Tái thẩm định giá/slot & Bù trừ rollback |
| 4 | `RevalidBookingFlow` | Decision Flowchart | [`codeFlows/Decision_Flowchart/RevalidBooking_DF.mmd`](codeFlows/Decision_Flowchart/RevalidBooking_DF.mmd) | Cây quyết định phân nhánh Re-validation chi tiết: Outcome replay → PENDING check → Package grouping → Slot/Price check → Retry count < 2 → Khóa tồn kho nguyên tử |
| 5 | `CliProxy_ModelFallback` | Decision Flowchart | [`codeFlows/Decision_Flowchart/CliProxy_ModelFallback_DF.mmd`](codeFlows/Decision_Flowchart/CliProxy_ModelFallback_DF.mmd) | Thử model chính và model dự phòng qua cùng CLI Proxy, kiểm tra deadline, không gọi provider trực tiếp |
| 6 | `AI_Hybrid_Architecture` | Architecture Diagram | [`codeFlows/Architecture/AI_Hybrid_Architecture_ARCH.mmd`](codeFlows/Architecture/AI_Hybrid_Architecture_ARCH.mmd) | Bức tranh kiến trúc phân tầng 3 lớp Hybrid AI toàn diện: 8 Controllers, AiExecutionBudgetFilter, 14 Use Cases, Monitoring Job, 15 Tools, Dual-Brain AI, Postgres V21-V25 & Redis |
| 7 | `Customer_AI_Discovery_Flow` | Sequence Diagram | [`codeFlows/Sequence_Diagram/Customer_AI_Discovery_SD.mmd`](codeFlows/Sequence_Diagram/Customer_AI_Discovery_SD.mmd) | Khám phá Dịch vụ Thông minh (Search, Recommend, Nearby): Quét Catalog 10k items budget 8s, Preferences Consented, Lọc thời tiết 20 slot, Quyet Relevance top 5, Tracking recommendationId |
| 8 | `Itinerary_Planning_And_Replan_Flow` | Sequence Diagram | [`codeFlows/Sequence_Diagram/Itinerary_Planning_And_Replan_SD.mmd`](codeFlows/Sequence_Diagram/Itinerary_Planning_And_Replan_SD.mmd) | Chu trình Lập lịch 3 giai đoạn: (1) Preview → Save Draft → Accept tái kiểm tra giá/thời tiết; (2) Re-plan tạo Proposal PENDING (diff); (3) Job nền giám sát nguồn tự động mỗi 60s |
| 9 | `Content_Assessment_And_Transaction_Risk_Flow` | Sequence Diagram | [`codeFlows/Sequence_Diagram/Content_Assessment_And_Transaction_Risk_SD.mmd`](codeFlows/Sequence_Diagram/Content_Assessment_And_Transaction_Risk_SD.mmd) | Kiểm duyệt văn bản (Regex + Quyet Moderation/Category), Phân tích rủi ro giao dịch (Quy tắc vận tốc 3/5/3), Hàng đợi xét duyệt Admin có khóa lạc quan |
| 10 | `AI_Travel_Assistant_MultiTool_Flow` | Sequence Diagram | [`codeFlows/Sequence_Diagram/AI_Travel_Assistant_MultiTool_SD.mmd`](codeFlows/Sequence_Diagram/AI_Travel_Assistant_MultiTool_SD.mmd) | Trợ lý du lịch đa công cụ (15 tools), Structured Artifacts (typed cards), Tạo thẻ Native Option Card trên Redis & Re-validation bền vững |
| 11 | `Customer_Preference_And_Feedback_Flow` | Sequence Diagram | [`codeFlows/Sequence_Diagram/Customer_Preference_And_Feedback_SD.mmd`](codeFlows/Sequence_Diagram/Customer_Preference_And_Feedback_SD.mmd) | Quản lý sở thích cá nhân hóa (Consent bật/tắt, Exclusions, Version), Vòng lặp phản hồi gợi ý (SHOWN, CLICK, POSITIVE, NEGATIVE) & Tác động điểm xếp hạng |
| 12 | `Customer_Support_And_Admin_Queue_Flow` | Sequence Diagram | [`codeFlows/Sequence_Diagram/Customer_Support_And_Admin_Queue_SD.mmd`](codeFlows/Sequence_Diagram/Customer_Support_And_Admin_Queue_SD.mmd) | Hỗ trợ khách hàng AI (Tra cứu đơn của chính mình, Quyet Topic/Urgency, AI Cancellation Preview tính phí hoàn), Tạo ticket có Idempotency & Hàng đợi Admin xử lý thủ công |
| 13 | `Vendor_Review_Coaching_And_Copilot_Flow` | Workflow Diagram | [`docs/FE_AI_Features_Spec.md`](../../FE_AI_Features_Spec.md) | Huấn luyện Review 4 trụ cột, Smart Reply, Listing Optimizer, Slot Advisor & Trợ lý hội thoại Vendor Copilot đa phiên (Flyway V55) |
| 14 | `Admin_Dispute_Arbitration_And_Marine_Briefing_Flow` | Workflow Diagram | [`docs/FE_AI_Features_Spec.md`](../../FE_AI_Features_Spec.md) | Hòa giải tranh chấp 3 sự thật hệ thống (QR Checkin, Weather, Policy), Executive Digest & Cẩm nang biển thông minh 24h trước giờ khởi hành |

---

## 1. AI_Chat_SD.mmd — Luồng tổng quan AI Chat

**Thành phần:** `User (Client)` → `AiExecutionBudgetFilter` → `LocaleContextFilter` → `AssistantController` → `RedisRateLimiter` → `ChatIdempotencyService` → `CliProxyModerationClient` → `ChatHistoryService` → `ChatUseCase` → `CliProxyLlmClient` → `AIToolRegistry (15 Tools)` → `AssistantArtifactMapper` → `PostgreSQL`

**Luồng xử lý:**
1. Khách gửi tin nhắn qua `POST /api/assistant/chat` kèm các headers: `Accept-Language: vi`, `Idempotency-Key` (tùy chọn) và `X-Execution-Budget-Ms: 8000`.
2. `AiExecutionBudgetFilter` thiết lập deadline xử lý tối đa 8000ms, đồng thời kiểm soát tối đa 8 request AI đồng thời trên mỗi JVM (vượt quá trả về HTTP 429 Retry-After).
3. `LocaleContextFilter` trích xuất và thiết lập ngôn ngữ chuẩn hóa (`SupportedLanguage.VI`).
4. `RedisRateLimiter` kiểm tra hạn mức tần suất theo **Token Bucket + TrustTier** (VERIFIED / UNVERIFIED).
5. `ChatIdempotencyService` kiểm tra khóa gửi trùng:
   - Nếu tìm thấy yêu cầu cùng `Idempotency-Key` hoặc hash câu hỏi đã hoàn tất trước đó trong bảng `ai_chat_requests` (V22) → Trả lại ngay kết quả đã lưu (`replay = true`), không tốn chi phí gọi LLM.
6. `ChatHistoryService.getOrCreateConversation`: Đọc hoặc tạo hội thoại. Kiểm tra ràng buộc ngôn ngữ: nếu ngôn ngữ header khác ngôn ngữ đã khởi tạo hội thoại → Ném `AiConversationLocaleMismatchException` (HTTP 400).
7. `CliProxyModerationClient` phân loại `isSafe(truncatedContent max 1800 chars)` qua gateway, chỉ chấp nhận đúng nhãn `BENIGN`:
   - Phát hiện Prompt Injection / Tấn công bẻ khóa (Jailbreak) → Ghi log vi phạm và ném ngoại lệ trả về HTTP 400.
8. `ChatUseCase` nạp lịch sử tin nhắn từ PostgreSQL, gửi ngữ cảnh tới `CliProxyLlmClient` kèm danh sách 15 công cụ kỹ thuật.
9. Vòng lặp Tool Calling: Nếu CLI Proxy yêu cầu gọi công cụ, backend thực thi và trả kết quả để CLI Proxy hoàn thiện câu trả lời tự nhiên.
10. `AssistantArtifactMapper` ánh xạ các kết quả tool thực tế thành các thẻ dữ liệu có cấu trúc (`AssistantArtifact`): `BOOKING_CONFIRMATION_CARD`, `ITINERARY_PREVIEW`, `DISCOVERY_SUGGESTION`, `SERVICE_COMPARISON`, `REVIEW_HIGHLIGHT`, `CANONICAL_POLICY`, `SUPPORT_ACTION`.
11. Lưu `AiMessage` và ghi `AssistantAuditLog`, hoàn tất claim idempotency và trả về HTTP 200 kèm cờ `generatedTextVerified = false` (văn bản do AI sinh không tự động trở thành sự thật nguồn).

---

## 2. Chat_Moderation_SD.mmd — Chi tiết Moderation & Tool Calling

**Thành phần:** `Khách Hàng` → `AssistantController` → `RedisRateLimiter (2-Layer)` → `CliProxyModerationClient` → `ChatUseCase` → `CliProxyLlmClient` → `ToolExecutor [ConversationAware]` → `PolicyGuard` → `Postgres & Redis`

**Luồng chi tiết:**
1. **Kiểm soát 2 tầng (2-Layer Rate Limiting):** Kiểm tra hạn mức theo IP và cấp độ tin cậy `TrustTier`. Khách chưa xác thực hoặc bị hạn chế nếu vượt quota sẽ nhận HTTP 429.
2. **Cắt chuỗi max 1800 ký tự (Input Truncation):** Chặn đứng kỹ thuật Padding Attack làm tràn context window của mô hình ngôn ngữ lớn.
3. **Quét an toàn nội dung (Input Moderation):**
   - Unsafe: Ghi nhận vi phạm vào `AssistantAuditLog`, trả về mã lỗi chuẩn hóa `ai.moderation.blocked`.
   - Benign: Lưu câu hỏi của User vào cơ sở dữ liệu và tiếp tục.
4. **Vòng lặp Tool Calling (Tối đa 5 lượt):**
   - CLI Proxy LLM trả `ToolCall` → Backend thực thi công cụ thông qua `ToolExecutor` với đầy đủ ngữ cảnh `ToolExecutionContext{language, convId, userId}` để đảm bảo dữ liệu truy vấn tuân thủ quyền hạn và ngôn ngữ của người dùng.
5. **Bộ bảo vệ chính sách (Policy Guard):**
   - Nếu LLM tự ý trả lời các câu hỏi về chính sách hủy vé, hoàn tiền hoặc quy chuẩn thời tiết mà **CHƯA** gọi tool `get_policy` → Hệ thống tự động **THU HỒI & THAY THẾ** phản hồi bằng văn bản chính sách quy chuẩn (`CANONICAL_POLICY`) từ hệ thống.
6. Hoàn tất phản hồi và lưu vết kiểm toán.

---

## 3. RevalidBooking_SD.png — Xác nhận đặt chỗ thời gian thực (Sequence & Bảo mật)

**Thành phần:** `User` → `AssistantController` → `ChatHistoryService` → `ConfirmBookingUseCase` → `ConfirmationOutcomeAdapter (V25)` → `RedisConfirmationCardStore` → `AiCatalogReadApi` → `CreateBookingHoldUseCase` → `CancelBookingHoldUseCase`

**Luồng xử lý:**
1. Khách bấm xác nhận trên giao diện chat → `POST /api/assistant/conversations/{id}/confirm` gửi kèm `{"cardId": "card_abc"}`.
2. **Xác thực quyền sở hữu hội thoại (Ownership Guard):**
   - `ChatHistoryService.getConversationForUser(id, currentUserId)`.
   - Hội thoại không tồn tại → HTTP 404 Not Found.
   - Hội thoại thuộc tài khoản khác → Ném `AccessDeniedException` trả về **HTTP 403 Forbidden**.
3. **Kiểm tra Outcome bền vững (Flyway V25 Durable Outcome):**
   - `durableOutcomes.completed(cardId, userId, expectedConversationId)`.
   - Nếu đã hoàn tất trước đó (kể cả khi Redis bị restart hoặc mất key cache) → Replay ngay kết quả đã lưu (`bookingId`, `holdExpiresAt`), đảm bảo tính Idempotent tuyệt đối và không tạo booking hold kép.
4. **Thiết lập Khóa phân tán (Distributed Lock):**
   - Gọi `tryAcquireProcessingLock(cardId, TTL: 30s)` trên Redis. Nếu thẻ đang được xử lý bởi tiến trình khác → Ném `IllegalStateException` trả về **HTTP 409 Conflict**.
5. **Ràng buộc 3 bên (Card — Conversation — User):**
   - Đọc Draft Card từ Redis.
   - Kiểm tra: `card.getConversationId().equals(expectedConversationId)` và `card.getOwnerId().equals(userId)`. Nếu sai lệch → **HTTP 403 Forbidden**, ngăn chặn triệt để tấn công đánh cắp thẻ giữa các hội thoại (Card Hijacking).
   - Kiểm tra trạng thái: `card.getStatus().equals(STATUS_PENDING)`.
6. **Kiểm tra phân nhóm gói riêng (Participant Grouping):**
   - Nếu tùy chọn là `PER_PACKAGE`, xác thực danh sách `participantsPerPackage` (ví dụ: nhóm 3 người chọn cano tối đa 2 người/gói → phân bổ `[2, 1]` tương ứng `quantity = 2`).
7. **Tái thẩm định thời gian thực (Live Re-validation):**
   - Truy vấn giá và slot thực tế từ CSDL qua `AiCatalogReadApi`.
   - Nếu hết chỗ hoàn toàn → Đánh dấu card `CANCELLED` → trả `out_of_stock`.
   - Nếu giá thay đổi hoặc slot không còn:
     - `retryCount >= 2` → Hủy card → trả `error` ("Biến động quá nhiều lần").
     - `retryCount < 2` → Tăng retryCount (Redis TTL 15m), hủy card cũ, tạo thẻ đề xuất thay thế `AlternativeCard` → trả `alternative_needed`.
8. **Khóa tồn kho nguyên tử & Lưu Outcome:**
   - Kích hoạt `CreateBookingHoldUseCase`: Chạy script Redis Lua khóa số chỗ và tạo bản ghi Booking trạng thái `HOLD` (15 phút) trong PostgreSQL.
   - Kiểm tra đối chiếu tổng tiền: nếu tổng tiền tính toán lệch với card → Tự động hủy hold và yêu cầu tạo thẻ mới.
   - Lưu outcome vào bảng `ai_confirmation_outcomes` (V25) và cập nhật card `CONFIRMED`.
9. **Cơ chế bù trừ tự động (Compensation Rollback):**
   - Nếu quá trình lưu outcome bền vững gặp sự cố hệ thống, tự động kích hoạt `CancelBookingHoldUseCase` giải phóng tồn kho ngay lập tức để tránh tình trạng treo slot ảo.
10. Giải phóng ProcessingLock trên Redis và trả về HTTP 200 OK.

---

## 4. RevalidBooking_DF.png — Cây quyết định phân nhánh Re-validation (Flowchart)

**Cây quyết định chi tiết:**
1. Khách gửi yêu cầu `POST /conversations/{id}/confirm` → Kiểm tra `conversation.userId == currentUserId`:
   - Không tìm thấy → **HTTP 404**
   - Thuộc người khác → **HTTP 403**
2. Kiểm tra Outcome bền vững trong bảng `ai_confirmation_outcomes` (PostgreSQL V25):
   - Đã tồn tại → Replay kết quả cũ thành công (HTTP 200: `bookingId`, `holdExpiresAt`)
3. Lấy khóa phân tán `tryAcquireProcessingLock` (TTL 30s) trên Redis:
   - Thất bại → **HTTP 409 Conflict** (Đang có luồng khác xử lý thẻ)
4. Đọc `ConfirmationCard` từ Redis:
   - Không tìm thấy / Hết hạn 15 phút → **HTTP 400 Bad Request**
5. Kiểm tra ràng buộc kép: `card.conversationId == id` và `card.ownerId == userId`:
   - Sai lệch → **HTTP 403 Forbidden** (Card Hijacking)
6. Trạng thái thẻ `card.status == PENDING`:
   - Sai → Báo lỗi trạng thái
7. Kiểm tra loại giá:
   - Nếu `PER_PACKAGE`: Xác thực phân nhóm khách trong từng gói `participantsPerPackage`. Nếu không hợp lệ → Hủy thẻ, trả về `refresh_required`.
8. Truy vấn giá và slot thực tế từ Database:
   - Hết chỗ hoàn toàn → Đánh dấu `CANCELLED` → trả `out_of_stock`.
   - Giá đổi hoặc ca yêu cầu bị đầy:
     - `retryCount >= 2` → Đánh dấu `CANCELLED` → trả `error` ("Biến động quá nhiều lần").
     - `retryCount < 2` → Tăng retryCount (Redis TTL 15m), tạo thẻ thay thế mới → trả `alternative_needed`.
9. Khớp dữ liệu 100% → Gọi `CreateBookingHoldUseCase` (Redis Lua lock + Booking HOLD 15m).
10. Kiểm tra giá giữ chỗ có khớp với card:
    - Lệch giá → Kích hoạt bù trừ `CancelBookingHoldUseCase`, hủy thẻ, trả `refresh_required`.
11. Lưu Outcome bền vững vào PostgreSQL V25 và đánh dấu Card `CONFIRMED`.
    - Lỗi khi lưu → Kích hoạt **Bù trừ tự động (Compensation)** gọi `CancelBookingHoldUseCase` giải phóng tồn kho.
12. Giải phóng khóa phân tán và trả về kết quả thành công (HTTP 200 OK).

---

## 5. CLI Proxy — Model fallback

Backend gửi request đến CLI Proxy bằng client key nội bộ (`AI_CLIPROXY_API_KEY`). Proxy sử dụng tài khoản OAuth trong thư mục auth; backend không quản lý pool API key của nhà cung cấp.

`CliProxyLlmClient` thử `AI_CLIPROXY_MODEL`, sau đó thử `AI_CLIPROXY_FALLBACK_MODEL` qua cùng gateway khi request lỗi. Nếu cả hai lỗi, client trả exception để tầng ứng dụng xử lý. Không gọi trực tiếp nhà cung cấp khác. Kiểm tra deadline trước mỗi lần gọi; timeout trả trạng thái `PARTIAL`.

`CliProxyModerationClient` cũng đi qua gateway; chỉ chấp nhận kết quả đúng `BENIGN`. Lỗi mạng, HTTP lỗi, JSON lỗi hoặc nhãn không hợp lệ đều từ chối input.

Sơ đồ nguồn: `codeFlows/Decision_Flowchart/CliProxy_ModelFallback_DF.mmd`.

---

## 6. AI_Hybrid_Architecture_ARCH.mmd — Kiến trúc phân tầng 3 lớp Hybrid AI (Toàn diện)

**Sự kết hợp hoàn chỉnh của hệ thống AI DANASEA:**
1. **Tầng Giao Diện (Client Apps):** Web App (React / Vite) và Mobile App (Flutter).
2. **Tầng Bảo Mật & Điều Phối (Security Gateway):**
   - Spring Security Context (JWT Authentication & Actor Ownership).
   - `LocaleContextFilter` (Phân giải đa ngôn ngữ VI/EN).
   - **`AiExecutionBudgetFilter`**: Kiểm soát ngân sách thực thi (`X-Execution-Budget-Ms`, deadline tối đa 8000ms), giới hạn tối đa 8 request AI đồng thời trên mỗi JVM để bảo vệ tài nguyên hệ thống.
   - `RedisRateLimiter`: Kiểm soát hạn mức tần suất theo `TrustTier`.
3. **Tầng Bộ Điều Khiển (8 API Controllers):**
   - `AssistantController`: Hội thoại trợ lý ảo, quản lý ngữ cảnh và xác nhận đặt vé.
   - `CustomerAiController`: Tìm kiếm thông minh, gợi ý, so sánh dịch vụ và đánh giá thời tiết.
   - `ItineraryLifecycleController`: Chu trình lập lịch trình (Preview, Save Draft, Accept, Revisions, Proposals).
   - `CustomerPreferenceController`: Quản lý sở thích cá nhân hóa và tiếp nhận phản hồi gợi ý.
   - `CustomerSupportWorkflowController`: Quy trình tiếp nhận yêu cầu đổi lịch và hỗ trợ khách hàng.
   - `AiAssessmentController`: Kiểm duyệt nội dung văn bản và phân loại danh mục dịch vụ.
   - `AdminAiController`: Phân tích rủi ro giao dịch (Velocity 3/5/3) và thẩm định hồ sơ vi phạm.
   - `ManualSupportController`: Hàng đợi xử lý thủ công các yêu cầu đổi lịch/hỗ trợ của Admin.
4. **Tầng Nghiệp Vụ Lõi (14 Application Use Cases):**
   - Các use case được thiết kế theo nguyên lý Clean Architecture, đảm bảo logic nghiệp vụ kiểm soát 100% quyết định, không phụ thuộc vào độ tin cậy của AI.
5. **Tầng Tác Vụ Nền (Background Jobs):**
   - `ItinerarySourceMonitoringJob`: Quét định kỳ mỗi 60s các biến động slot và cảnh báo thời tiết để tự động đề xuất phương án thay thế mà không can thiệp vào tài chính.
6. **Tầng Trí Tuệ Nhân Tạo Lai (Dual-Brain Hybrid Engine):**
   - **CLI Proxy (OAuth):** Model chính và dự phòng được cấu hình qua biến môi trường đảm nhiệm tương tác tự nhiên, Prompt Guard và trích dẫn bằng chứng từ review có ID nguồn thực tế.
   - **Quyet Small Inference Worker (Offline / Local CPU):** Máy chủ suy luận siêu tốc thực hiện 6 nhiệm vụ quyết định có cấu trúc: `INTENT`, `RELEVANCE` (top 5), `SERVICE_CATEGORY`, `MODERATION`, `RISK`, `REVIEW`.
7. **Tầng Lưu Trữ & Hạ Tầng Dữ Liệu:**
   - **PostgreSQL Database:** Bao gồm dữ liệu nghiệp vụ cốt lõi và chuỗi Migrations Flyway chuyên dụng:
     - `V21`: `ai_itineraries` (@Version, JSON snapshot), `ai_assessment_cases`.
     - `V22`: `ai_chat_requests` (Idempotency cache), `ai_conversations`, `ai_messages`.
     - `V23`: `ai_itinerary_proposals` (Diff), `ai_itinerary_previews` (TTL 15m), `ai_itinerary_revisions` (Lịch sử phiên bản), `ai_itinerary_items`.
     - `V24`: `ai_customer_preferences` (Consent), `ai_recommendations`, `ai_recommendation_feedbacks`, `ai_customer_support_requests`.
     - `V25`: `ai_confirmation_outcomes` (Lưu trữ bền vững kết quả xác nhận đặt vé).
   - **Redis Cache:** Quản lý Token Bucket, Draft Confirmation Cards (TTL 15m), Khóa phân tán Lua Script (TTL 30s), Idempotency Locks.

---

## 7. Customer_AI_Discovery_SD.png — Khám phá Dịch vụ Thông minh (Search, Recommend, Nearby)

**Thành phần:** `Client` → `CustomerAiController` → `TravelQueryParser` → `CustomerPreferenceReadApi` → `DiscoverServicesUseCase` → `AiCatalogReader (Postgres)` → `Quyet Small Worker` → `TravelWeatherPort` → `ai_recommendations`

**Quy trình chi tiết:**
1. **Phân giải yêu cầu:** Client gửi `TravelRequest` vào `/api/ai/search`, `/recommendations` hoặc `/nearby`. Bộ lọc `TravelContext.validateCurrentDates()` xác thực partySize (1–50), tổng ngân sách (0–1 tỷ VND), thời gian trong 14 ngày và không thuộc quá khứ.
2. **Xử lý thiếu thông tin (Clarification / NEEDS_INPUT):** Nếu câu hỏi cần làm rõ số người trong câu gia đình hoặc thiếu tọa độ khi tìm kiếm quanh đây → Trả về `NEEDS_INPUT` kèm danh sách câu hỏi cần người dùng bổ sung.
3. **Nạp sở thích cá nhân hóa (Consent Signals):** Nếu `useSavedPreferences == true` và khách đã đăng nhập, nạp tín hiệu sở thích đã đồng ý: danh sách sở thích, mục yêu thích (wishlist), dịch vụ đã xem gần đây, phản hồi tích cực/tiêu cực và danh mục loại trừ.
4. **NLP Query Parsing:** `TravelQueryParser` bóc tách từ khóa hoạt động, ngân sách (tự động nhân số người nếu câu gốc nêu giá mỗi người), và từ khóa loại trừ.
5. **Quét Catalog Thực Tế (Scan Budget 10.000 items):**
   - `AiCatalogReader` quét tối đa 10.000 ứng viên với deadline ngân sách 8 giây, phân trang qua `offset` và `nextOffset`.
   - Lọc bỏ các dịch vụ thuộc danh mục loại trừ (`exclusions`).
   - Lọc Option theo ngân sách cả nhóm (`PER_PERSON` hoặc `PER_PACKAGE`).
   - Lọc Slot theo khung giờ hoạt động trong ngày (`dayStart` đến `dayEnd`).
6. **Kiểm tra An toàn Thời tiết:** Nếu `weatherSafeOnly = true`, kiểm tra tối đa 20 slot nhạy cảm thời tiết (có cache theo `slotId`). Slot không đạt chuẩn sẽ bị loại khỏi danh sách có thể đặt.
7. **Định vị & Khoảng cách (Haversine Distance):** Tính khoảng cách địa lý đường chim bay. Nếu vượt bán kính `radiusKm` → bỏ qua.
8. **Chấm điểm Tương quan Quyet (Relevance Scoring):**
   - Với chế độ `RECOMMENDATION`: Lấy tối đa **5 ứng viên hàng đầu**, gọi Quyet tính điểm tương quan `RELEVANCE` (0..3 điểm) → cộng dồn vào điểm Baseline.
   - Dự đoán `INTENT` của người dùng để trả kèm kết quả.
9. **Sắp xếp & Giới hạn Kết quả:**
   - Chế độ `NEARBY`: Sắp xếp theo khoảng cách tăng dần → giá tổng tăng dần.
   - Chế độ `SEARCH / RECOMMENDATION`: Sắp xếp theo điểm Score giảm dần → Service ID tăng dần.
   - Giới hạn tối đa **15 kết quả** (không trả tràn lan).
10. **Ghi nhận Khuyến nghị (Recommendation Feedback ID):** Với gợi ý thành công, hệ thống lưu bản ghi vào bảng `ai_recommendations` (V24) và trả về `recommendationId` (UUID) để phục vụ vòng lặp thu thập phản hồi.

---

## 8. Itinerary_Planning_And_Replan_SD.png — Lập Lịch Trình, Vòng Đời & Tái Lập Lịch (3 Giai Đoạn)

**Thành phần:** `Client` → `ItineraryLifecycleController` → `PlanItineraryUseCase` → `DiscoverServicesUseCase` → `TravelWeatherPort` → `ItinerarySourceMonitoringJob` → `PostgreSQL (V21 & V23)` → `In-App Notification`

**Chu trình 3 giai đoạn hoàn chỉnh:**

### Giai đoạn 1: Vòng đời Khởi tạo (Preview → Save Draft → Accept)
1. **Khởi tạo phương án xem trước (`POST /api/ai/itineraries/preview`):**
   - Gọi `DiscoverServicesUseCase` lấy các ứng viên thực tế còn slot.
   - Chạy thuật toán **Bounded Chronological Greedy** với đệm di chuyển:
     $$\text{transitMinutes} = 15 \text{ phút} + \left\lceil \frac{\text{distanceKm}}{25} \times 60 \right\rceil \text{ phút}$$
   - Đánh giá thời tiết qua `TravelWeatherPort`.
   - Sinh tối đa **3 phương án thay thế (alternatives)** theo từng ngày, lưu vào bảng `ai_itinerary_previews` (TTL 15 phút).
2. **Lưu phương án đã chọn (`POST /api/ai/itineraries/previews/{previewId}/save`):**
   - Khách chọn 1 alternative, gửi kèm header `Idempotency-Key`.
   - Lưu vào bảng `ai_itineraries` với trạng thái `DRAFT` và `version = 0`.
3. **Chấp thuận lịch trình (`POST /api/ai/itineraries/{id}/accept`):**
   - Khách bấm chốt lịch kèm `expectedVersion = 0`.
   - Hệ thống tái kiểm tra (re-validate) lại giá và slot thời gian thực từ CSDL để đảm bảo không bị book mất chỗ.
   - Cập nhật trạng thái thành `ACCEPTED`, tăng `version = 1`.

### Giai đoạn 2: Tái lập lịch trình qua Đề xuất (Re-planning with Proposal Diff)
1. Khách gửi yêu cầu đổi lịch: `POST /api/ai/itineraries/{id}/replan` kèm `expectedVersion`, `excludedSlotIds`, `trigger`.
2. Kiểm tra quyền sở hữu và Optimistic Locking (nếu phiên bản DB đã đổi → HTTP 409 Conflict).
3. Chạy lại thuật toán tính phương án thay thế, tính toán chi tiết chênh lệch (**Diff**): `removedSlotIds`, `addedSlotIds`, `changedItems`.
4. **Tạo bản ghi Proposal PENDING:** Lưu vào bảng `ai_itinerary_proposals` (V23). **Tuyệt đối không tự ý ghi đè lịch đang chạy trong `ai_itineraries`**.
5. Khách hàng xem trước đề xuất:
   - Nếu đồng ý: Gửi `POST /api/ai/itineraries/{id}/proposals/{proposalId}/accept`.
   - Hệ thống tái kiểm tra slot & thời tiết, lưu bản chụp lịch cũ vào bảng `ai_itinerary_revisions` (V23), cập nhật `ai_itineraries` với lịch mới, tăng `@Version` lên +1 và đánh dấu proposal thành `ACCEPTED`.

### Giai đoạn 3: Giám sát nguồn tự động bằng Job nền (ItinerarySourceMonitoringJob)
1. Job nền chạy định kỳ mỗi 60 giây, quét toàn bộ lịch trình ở trạng thái `ACCEPTED` hoặc `STALE` có hoạt động trong tương lai.
2. Kiểm tra trạng thái từng slot trong catalog và cảnh báo thời tiết mới nhất từ trạm khí tượng.
3. Nếu phát hiện slot bị nhà cung cấp hủy hoặc thời tiết chuyển biến nguy hiểm (`UNSAFE`):
   - Tự động sinh `Proposal` trạng thái `PENDING` trong bảng `ai_itinerary_proposals`.
   - Gửi thông báo trong ứng dụng (**In-App Notification**) với cơ chế chống trùng lặp (dedup) để báo cho khách hàng biết.
   - **Quy tắc bất biến:** Không tự ý hủy booking đã thanh toán hoặc trừ tiền của khách hàng.

---

## 9. Content_Assessment_And_Transaction_Risk_SD.png — Kiểm Duyệt Văn Bản & Rủi Ro Giao Dịch

**Thành phần:** `User/Vendor/Admin` → `AiAssessmentController` / `AdminAiController` → `AssessTextUseCase` / `AnalyzeTransactionRiskUseCase` → `TransactionRiskReadPort` → `Quyet Small Worker` → `AssessmentCaseStorePort (PostgreSQL V21)`

**Ba phân hệ thẩm định:**
1. **Kiểm duyệt nội dung văn bản (`POST /api/ai/content-assessments`):**
   - Nhận chuỗi văn bản (1–2500 ký tự).
   - Quét Regex phát hiện thông tin liên hệ lén lút: Số điện thoại Việt Nam ((+84|0)...), liên kết ngoài (http/https), địa chỉ email.
   - Gọi song song Quyet Worker thực hiện 2 task:
     - `SERVICE_CATEGORY`: Phân loại danh mục trải nghiệm (SUP, KAYAK, DIVING...).
     - `MODERATION`: Đánh giá xác suất rủi ro giao dịch ngoài luồng (`external_payment`), thư rác (`spam`), quấy rối/xúc phạm (`abuse`).
   - Nếu phát hiện số điện thoại hoặc xác suất rủi ro $\ge 0.5$ hoặc Quyet offline → Gán trạng thái `NEEDS_REVIEW`; ngược lại `NO_RULE_SIGNAL`.
   - Lưu hồ sơ vào bảng `ai_assessment_cases` với `evidenceType = TEXT_ONLY` và `publicationAuthorized = false`.
2. **Phân tích rủi ro giao dịch Admin (`POST /api/admin/ai/risk-cases`):**
   - Admin nhập `orderId` và khiếu nại của khách.
   - Đọc dữ liệu vận tốc giao dịch thực tế qua `TransactionRiskReadPort`:
     - Số lần thanh toán thất bại trong 24 giờ (`failedPayments24h >= 3`).
     - Tần suất tạo đơn trong 1 giờ gần nhất (`orders1h >= 5`).
     - Số yêu cầu hoàn tiền trong 7 ngày qua (`refundRequests7d >= 3`).
   - Gọi Quyet Worker task `RISK` kết hợp tín hiệu hành vi và nội dung khiếu nại.
   - Nếu thỏa mãn bất kỳ ngưỡng vận tốc nào hoặc xác suất rủi ro $\ge 0.5$ → Tạo case `NEEDS_REVIEW` lưu vào `ai_assessment_cases`.
3. **Hàng đợi Admin giải quyết Case:**
   - Admin truy vấn danh sách hồ sơ cần thẩm định qua `GET /api/admin/ai/assessment-cases?status=NEEDS_REVIEW&limit=50`.
   - Admin xử lý qua `POST /api/admin/ai/assessment-cases/{id}/resolve` với `resolution` (`RESOLVED`, `DISMISSED`) và ghi chú giải trình (1–1000 ký tự).
   - Kiểm soát bằng khóa lạc quan chống 2 Admin duyệt trùng một hồ sơ.

---

## 10. AI_Travel_Assistant_MultiTool_SD.mmd — Trợ Lý Du Lịch Đa Công Cụ (15 Tools & Structured Artifacts)

**Thành phần:** `Client` → `AiExecutionBudgetFilter` → `AssistantController` → `ChatIdempotencyService` → `CliProxyLlmClient` → `AIToolRegistry (15 Tools)` → `CustomerFeatureTool` → `AssistantArtifactMapper` → `ConfirmBookingUseCase` → `CreateBookingHoldUseCase`

**Luồng thực thi:**
1. **Hội thoại & Vòng lặp gọi Tool (Tối đa 5 lượt):**
   - CLI Proxy LLM nhận danh sách **15 công cụ kỹ thuật** đã đăng ký:
     - 6 công cụ cơ bản: `search_services`, `get_service_detail`, `get_policy`, `get_weather_forecast`, `get_safety_alert`, `request_booking_confirmation`.
     - 9 công cụ khách hàng mở rộng: `ai_smart_search`, `ai_recommend_services`, `ai_nearby_services`, `ai_compare_services`, `ai_plan_itinerary`, `ai_replan_itinerary`, `ai_review_summary`, `ai_weather_slot`, `ai_customer_support`.
   - Khi LLM gọi Tool, `CustomerFeatureTool` thực thi nghiệp vụ thật từ CSDL, đảm bảo kiểm tra quyền truy cập của người dùng.
   - `AssistantArtifactMapper` bóc tách kết quả công cụ thành các thẻ giao diện tương tác (`cards`), nguồn dẫn chứng (`sources`) và hành động (`actions`).
2. **Khởi tạo Phiếu Đặt Chỗ Native Option (Draft Card):**
   - Khách chốt lịch → LLM gọi `request_booking_confirmation(service_id, option_id, slot_id, date, participants)`.
   - Xác thực Option ID và Slot ID khả dụng trong CSDL.
   - Tính toán phân nhóm gói riêng `participantsPerPackage` nếu là hình thức `PER_PACKAGE`.
   - Lưu thẻ `ConfirmationCard` vào Redis với TTL 15 phút và hiển thị thẻ lên giao diện chat kèm nút bấm **[Xác Nhận Đặt Chỗ]**.
3. **Xác nhận Đặt chỗ & Khóa Giữ Chỗ Re-validation Bền Vững:**
   - Khách bấm xác nhận → Frontend gọi `POST /api/assistant/conversations/{id}/confirm` kèm `cardId`.
   - Kiểm tra kết quả trong bảng `ai_confirmation_outcomes` (V25). Nếu đã xử lý trước đó → Replay ngay lập tức.
   - Thiết lập khóa phân tán Redis 30s chống xử lý trùng.
   - Kiểm tra ràng buộc kép: Hội thoại và Card phải thuộc về User hiện tại (chặn 403).
   - Tái thẩm định giá và slot từ CSDL. Nếu có biến động → Quản lý số lần retry (tối đa 2 lần) và tạo thẻ thay thế.
   - Khớp dữ liệu → Kích hoạt `CreateBookingHoldUseCase` thực hiện khóa tồn kho nguyên tử Redis Lua và lưu Booking `HOLD` (15 phút) vào PostgreSQL.
   - Lưu kết quả bền vững vào bảng `ai_confirmation_outcomes` và đánh dấu card `CONFIRMED`.
   - Nếu gặp sự cố khi lưu outcome: Tự động kích hoạt cơ chế bù trừ `CancelBookingHoldUseCase` giải phóng tồn kho ngay lập tức.

---

## 11. Customer_Preference_And_Feedback_SD.png — Quản Lý Sở Thích Cá Nhân Hóa & Phản Hồi Gợi Ý

**Thành phần:** `Khách Hàng (User)` → `CustomerPreferenceController` → `CustomerPreferenceUseCase` → `DiscoverServicesUseCase` → `PostgreSQL (V24 Preferences & Recommendations)`

**Ba giai đoạn cá nhân hóa:**
1. **Cấu hình Sở Thích & Quyền Riêng Tư (Consent Management):**
   - Khách xem cấu hình: `GET /api/ai/preferences`.
   - Khách cập nhật: `PUT /api/ai/preferences` gửi kèm `enabled` (Bật/tắt sự đồng thuận), danh sách `interests` (vd: lặn san hô, chèo sup), danh sách `exclusions` (vd: cano cao tốc, dù bay) và `expectedVersion` chống ghi đè.
   - Dữ liệu được lưu trong bảng `ai_customer_preferences` (Flyway V24) với cơ chế khóa lạc quan.
2. **Thu thập Phản hồi Gợi ý (Recommendation Feedback Loop):**
   - Khi nhận danh sách gợi ý kèm `recommendationId`, khách tương tác trên giao diện (Xem, Click, Thích, Bỏ qua).
   - Frontend gửi `POST /api/ai/recommendations/{recommendationId}/feedback` kèm header bắt buộc `Idempotency-Key` và body: `{ serviceId, action: "CLICK" | "POSITIVE" | "NEGATIVE" | "SHOWN" }`.
   - Backend xác thực:
     - `recommendationId` phải do chính User tạo ra trong vòng 24 giờ qua.
     - `serviceId` phải thực sự nằm trong danh sách các dịch vụ đã gợi ý trong lần đó (chống spam/fake feedback).
   - Lưu bản ghi vào bảng `ai_recommendation_feedbacks` (Flyway V24).
3. **Tác động Cá nhân hóa trong Lần Tìm kiếm Kế tiếp:**
   - Khi khách gọi `/api/ai/recommendations` hoặc `/api/ai/search` với cờ `useSavedPreferences = true`:
   - Hệ thống nạp Signals và tự động điều chỉnh:
     - **Loại bỏ hoàn toàn** các dịch vụ thuộc `exclusions`.
     - **Cộng điểm Baseline:** Khớp từ khóa sở thích (+0.5/từ), nằm trong Wishlist (+0.35), đã xem gần đây (+0.1), đã đánh giá POSITIVE (+0.25).
     - **Trừ điểm Baseline:** Dịch vụ bị đánh giá NEGATIVE (-0.5 điểm, hạ xếp hạng).

---

## 12. Customer_Support_And_Admin_Queue_SD.png — Luồng Hỗ Trợ Khách Hàng AI & Hàng Đợi Admin

**Thành phần:** `Khách Hàng (Customer)` → `Quản Trị Viên (Admin)` → `CustomerSupportWorkflowController` → `ManualSupportController` → `CustomerSupportUseCase` → `CustomerSupportRequestUseCase` → `CustomerRefundEligibilityPolicy` → `Core Order Module` → `Quyet Worker` → `PostgreSQL V24` → `In-App Notification`

**Chu trình hỗ trợ 3 bước:**
1. **Tra cứu Đơn hàng & Xem trước Hủy vé AI (`POST /api/ai/support`):**
   - Khách gửi yêu cầu kèm `orderId` và câu hỏi (ví dụ: "Tôi bận việc muốn hủy chuyến đi ngày mai").
   - **Xác thực quyền sở hữu:** Đơn hàng bắt buộc phải thuộc về tài khoản khách hàng đang đăng nhập (chặn 403 Forbidden nếu xem trộm đơn của người khác).
   - Đọc trạng thái thanh toán và thông tin ca dịch vụ từ Core Order Module.
   - Gọi Quyet Worker phân loại chủ đề (`topic = CANCELLATION`) và mức độ khẩn cấp (`urgency = MEDIUM`).
   - Nếu khách yêu cầu xem trước điều kiện hủy (`includeCancellationPreview = true`):
     - Kích hoạt `CustomerRefundEligibilityPolicy` đối chiếu mốc giờ hủy với giờ khởi hành:
       - Trước 48 giờ: Hoàn 100%.
       - Từ 24–48 giờ: Hoàn 70% (Phí hủy 30%).
       - Dưới 24 giờ: Không hoàn tiền (Phí hủy 100%).
     - Trả về `CancellationPreviewResult` chứa số tiền hoàn, phí hủy và hạn chót.
     - **Nguyên tắc bất biến:** AI chỉ đóng vai trò tư vấn và tính toán trước, **tuyệt đối không tự ý hủy vé hoặc hoàn tiền trong CSDL**.
2. **Tạo Ticket Yêu cầu Hỗ trợ / Đổi lịch có Idempotency (`POST /api/ai/support/requests`):**
   - Khách bấm xác nhận gửi yêu cầu hỗ trợ hoặc đổi lịch sang ngày mới.
   - Frontend gửi kèm header `Idempotency-Key` chống tạo trùng đơn hỗ trợ.
   - Lưu bản ghi vào bảng `ai_customer_support_requests` (Flyway V24) với trạng thái ban đầu `WAITING_REVIEW`.
3. **Hàng đợi Xử lý Thủ công của Admin (`ManualSupportController`):**
   - Nhân viên/Admin truy cập hàng đợi qua `GET /api/admin/support/requests?status=WAITING_REVIEW`.
   - Admin tiếp nhận và xử lý qua `POST /api/admin/support/requests/{id}/handle`:
     - Gửi kèm `expectedVersion`, trạng thái mới (`RESOLVED` hoặc `DECLINED`) và ghi chú giải trình `responseNote`.
     - Kiểm soát bằng khóa lạc quan `@Version` chống 2 Admin duyệt trùng một yêu cầu.
     - Hệ thống tự động gửi **In-App Notification** báo kết quả xử lý cho khách hàng.
     - Mọi thay đổi về đơn hàng hoặc hoàn tiền thực tế đều do nhân viên thực hiện qua API nghiệp vụ Core.

---

## 13. Vendor_Review_Coaching_And_Copilot_Flow — Huấn Luyện Đánh Giá & Vendor Copilot Đa Phiên

**Thành phần:** `Vendor` → `VendorAiController` → `VendorReviewCoachingUseCase` / `RoleCopilotUseCase` → `VendorAiDataPort` → `AiExtendedInsightStorePort (PostgreSQL V55)` → `Dual-Brain AI`

**Luồng xử lý:**
1. **Phân tích Review Coaching 4 trụ cột:**
   - Hệ thống quét lịch sử đánh giá của đối tác trong 30-180 ngày, bóc tách dữ liệu theo 4 khía cạnh trọng yếu: Hướng dẫn viên (`GUIDE`), An toàn & Thiết bị (`SAFETY`), Đúng giờ (`PUNCTUALITY`), Giá trị xứng đáng (`VALUE`).
   - Trích xuất điểm mạnh và điểm yếu kèm bằng chứng thực tế từ bình luận của khách, xếp hạng huy hiệu (`BadgeTier`: STANDARD → SILVER → GOLD → ELITE) và lộ trình thăng hạng.
   - Lưu trữ bản phân tích vào bảng `ai_vendor_insights` (Flyway V55).
2. **Soạn nháp phản hồi đánh giá thông minh (Smart Reply Draft):**
   - Hỗ trợ 3 tông giọng (`PROFESSIONAL`, `FRIENDLY`, `EMPATHETIC`), kiểm duyệt an toàn tự động, tích hợp voucher giữ chân khách hàng.
3. **Cố vấn công suất slot & Tối ưu bài đăng (Slot Advisor & Listing Optimizer):**
   - Chấm điểm nội dung dịch vụ (0-100), phát hiện thiếu sót về quy định an toàn biển và sinh bản dịch chuẩn SEO.
   - Phân tích tỷ lệ lấp đầy các ca và đối chiếu thời tiết 7-14 ngày để tư vấn tăng/giảm ca hoặc mở ca đón bình minh/hoàng hôn.
4. **Vendor Copilot Đa Phiên (Multi-Session):**
   - Hỗ trợ quản lý từng luồng chat riêng biệt (tạo mới, ghim, đổi tên, lưu trữ, xóa mềm), tích hợp bộ công cụ quản lý doanh thu, đặt chỗ, đánh giá trong phiên làm việc.

---

## 14. Admin_Dispute_Arbitration_And_Marine_Briefing_Flow — Hòa Giải Tranh Chấp & Cẩm Nang Biển

**Thành phần:** `Customer / Admin` → `CustomerAiController` / `AdminAiController` → `CustomerMarineBriefingUseCase` / `AdminDisputeSummaryUseCase` → `TripReminderJob` → `PostgreSQL V55`

**Luồng xử lý:**
1. **Hòa giải tranh chấp 3 sự thật khách quan (Dispute Arbitration):**
   - Đối chiếu chéo 3 nguồn dữ liệu không thể làm giả:
     - (1) Lịch sử quét mã QR vé tại bến tàu (`usedAt` có tồn tại hay không).
     - (2) Dữ liệu quan trắc thời tiết thực tế tại slot giờ đó (sóng biển, gió, lệnh cấm xuất bến).
     - (3) Chính sách hoàn hủy áp dụng cho dịch vụ.
   - Tóm tắt lập luận 2 bên, đề xuất phán quyết công tâm (`RESOLVED_REFUND`, `RESOLVED_PARTIAL`, `RESOLVED_REJECTED`) kèm bản thảo công văn giải trình cho Admin.
2. **Cẩm nang biển thông minh trước chuyến đi (Marine Briefing):**
   - Tự động chuẩn bị thông tin dự báo sóng gió, nhiệt độ nước và checklist đồ cần mang phân loại theo mức độ bắt buộc / khuyên dùng.
   - Hook tự động trong `TripReminderJob`: Khi quét các chuyến khởi hành trong vòng 24 giờ, hệ thống tự động sinh trước Marine Briefing và gửi thông báo đẩy `MARINE_BRIEFING_READY` đến điện thoại khách hàng.

---

## 🔗 Bản Đồ Liên Kết Giữa Các Sơ Đồ Hệ Thống

```
                                [Sơ đồ 6: AI_Hybrid_Architecture]
                             (Bức tranh tổng thể toàn hệ thống AI)
                                               │
             ┌─────────────────────────────────┴─────────────────────────────────┐
             │                                                                   │
    [TƯ VẤN & TRỢ LÝ ẢO]                                                [TÍNH NĂNG KHÁCH HÀNG & ADMIN]
             │                                                                   │
    ┌────────┴────────┐                                                 ┌────────┼────────┬────────┐
    │                 │                                                 │        │        │        │
[Sơ đồ 1 & 2]    [Sơ đồ 10]                                         [Sơ đồ 7] [Sơ đồ 8] [Sơ đồ 11] [Sơ đồ 12]
Luồng Chat &    Chatbot Đa Năng                                     Khám Phá  Lập Lịch  Sở Thích  Hỗ Trợ &
Moderation      15 Tools & Card                                     Dịch Vụ   3 Bước    Feedback  Queue Admin
    │                 │                                                 │        │                 │
    └────────┬────────┘                                                 └────────┼─────────────────┘
             │                                                                   │
    [Sơ đồ 3 & 4: Re-validation]                                                 │
    (Xác thực tồn kho & khóa giữ chỗ V25)                                [Sơ đồ 9: Thẩm Định]
             │                                                           (Kiểm duyệt & Rủi ro 3/5/3)
    [Sơ đồ 5: CLI Proxy & Fallback]
    (Độ tin cậy hạ tầng CLI Proxy)
```

Các sơ đồ đã đổi provider có nguồn Mermaid trong `codeFlows/`; ảnh PNG cũ tương ứng đã được xoá để tránh mô tả sai hệ thống hiện tại.
