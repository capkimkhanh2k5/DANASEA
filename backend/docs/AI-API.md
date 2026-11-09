# Tổng hợp các tính năng AI cho hệ thống DANASEA

DANASEA là sàn thương mại điện tử đa nhà cung cấp chuyên về trải nghiệm du lịch biển tại Đà Nẵng. AI hỗ trợ 3 nhóm đối tượng: Khách hàng (User), Nhà cung cấp (Vendor) và Quản trị viên (Admin).

---

## 1. AI dành cho khách hàng (Customer)

| Tính năng | Mô tả ngắn gọn |
| --- | --- |
| 1. AI Travel Assistant | Trợ lý AI tư vấn, giải đáp về dịch vụ, giá, lịch trống, chính sách và đặt trải nghiệm. |
| 2. Multi-Session Thread Control | Quản lý hội thoại đa phiên: tạo mới với auto-titling, ghim, đổi tên, lưu trữ, xóa mềm. |
| 3. AI Smart Search | Tìm dịch vụ bằng ngôn ngữ tự nhiên thay vì chỉ sử dụng bộ lọc truyền thống. |
| 4. AI Recommendation | Cá nhân hóa gợi ý theo sở thích, ngân sách, số người, vị trí và thời gian. |
| 5. AI Itinerary Planner | Tự động lập lịch trình du lịch biển theo ngân sách, thời gian và dịch vụ thực tế đang còn chỗ. |
| 6. Weather-Aware AI | Phân tích dữ liệu thời tiết và kết quả kiểm tra quy tắc an toàn để cảnh báo, gợi ý lịch phù hợp. |
| 7. AI Re-planning | Đề xuất điều chỉnh lịch trình khi thời tiết thay đổi hoặc dịch vụ bị hủy. |
| 8. AI Service Comparison | So sánh nhiều dịch vụ về giá, chất lượng, đánh giá, thời lượng và chính sách. |
| 9. AI Review Summary | Tóm tắt ưu điểm, nhược điểm từ đánh giá thực tế của khách hàng. |
| 10. AI Nearby Discovery | Đề xuất trải nghiệm gần vị trí hiện tại, phù hợp thời gian và lịch còn trống. |
| 11. AI Customer Support | Tra cứu đơn hàng, giải đáp chính sách, hỗ trợ yêu cầu đổi lịch, hủy hoặc hoàn tiền. |
| 12. AI Marine Briefing | Cẩm nang chuẩn bị đi biển, dự báo sóng gió, nhiệt độ nước và checklist đồ đạc thông minh trước 24 giờ. |

---

## 2. AI dành cho nhà cung cấp (Vendor)

| Tính năng | Mô tả ngắn gọn |
| --- | --- |
| 13. AI Review Coaching | Phân tích đánh giá theo 4 trụ cột (`GUIDE`, `SAFETY`, `PUNCTUALITY`, `VALUE`), trích xuất dẫn chứng và tư vấn lộ trình thăng hạng huy hiệu `BadgeTier`. |
| 14. AI Smart Review Reply | Soạn nháp câu trả lời phản hồi khách hàng theo 3 tông giọng (`PROFESSIONAL`, `FRIENDLY`, `EMPATHETIC`) kèm ưu đãi voucher giữ chân khách. |
| 15. AI Listing Optimizer | Chấm điểm bài đăng tour (0-100) và chỉ ra các thông tin an toàn còn thiếu. Bản nháp sử dụng các chi tiết đối tác đã cung cấp. |
| 16. AI Slot Demand Advisor | Phân tích công suất lấp đầy slot và đối chiếu dự báo thời tiết 7-14 ngày để tư vấn tăng/giảm ca hoặc mở ca đón bình minh/hoàng hôn. |
| 17. Vendor Copilot | Trợ lý kinh doanh hội thoại đa phiên, tự động kích hoạt 5 công cụ dữ liệu (doanh thu, đặt chỗ, đánh giá, slot, listing). |

---

## 3. AI dành cho quản trị viên (Admin)

| Tính năng | Mô tả ngắn gọn |
| --- | --- |
| 18. AI Content Moderation | Phát hiện văn bản dịch vụ và đánh giá có dấu hiệu vi phạm hoặc lộ thông tin liên hệ ngoài sàn; chuyển admin duyệt. |
| 19. AI Fraud Detection | Phát hiện dấu hiệu bất thường trong giao dịch (Velocity Rules 3/5/3) và tài khoản để đưa vào hàng đợi xem xét. |
| 20. AI Dispute Arbitration | Tóm tắt lời trình bày của hai bên, lượt quét vé QR và trạng thái ca để quản trị viên đối chiếu. |
| 21. AI Executive Digest | Bản tin điều hành sàn 1 chạm tổng quan (GMV, Payout, Commission, tỷ lệ hủy, cảnh báo sức khỏe vận hành vendor). |
| 22. Admin BI Copilot | Trợ lý phân tích số liệu điều hành đa phiên, tự động kích hoạt 6 công cụ dữ liệu phân tích BI toàn sàn. |

---

## 4. Danh mục API đã thực hiện (Tổng cộng 63 API)

Hiện có **63 API** trong module AI, đối chiếu đồng nhất 100% với Controller và file [`danasea-api-tracking.md`](danasea-api-tracking.md). Hệ thống áp dụng Dual-Brain AI: Quyet Small hỗ trợ phân loại, đánh giá văn bản/JSON; CLI Proxy phục vụ hội thoại; PostgreSQL Flyway V55 lưu trữ bền vững.

### 4.1. Trợ lý hội thoại & Quản lý phiên chat đa nhiệm (Customer) — 7 API

| API | Mô tả ngắn gọn |
| --- | --- |
| `POST /api/assistant/chat` | Gửi tin nhắn cho trợ lý AI; tự động sinh tiêu đề ngắn gọn cho cuộc trò chuyện mới; nhận thẻ dịch vụ, nguồn dữ liệu và hành động. |
| `POST /api/assistant/conversations/{id}/confirm` | Xác nhận thẻ đặt dịch vụ để tạo booking hold sau khi kiểm tra lại giá và chỗ trống. |
| `GET /api/assistant/conversations` | Liệt kê danh sách các cuộc hội thoại của người dùng, hỗ trợ lọc lưu trữ (`archived`). |
| `GET /api/assistant/conversations/{id}` | Xem thông tin chi tiết một hội thoại thuộc người dùng hiện tại. |
| `GET /api/assistant/conversations/{id}/history` | Lấy lịch sử tin nhắn trong hội thoại của người dùng. |
| `PATCH /api/assistant/conversations/{id}` | Cập nhật metadata hội thoại: đổi tên, ghim (`pinned`), lưu trữ (`archived`). |
| `DELETE /api/assistant/conversations/{id}` | Xóa mềm cuộc hội thoại của người dùng. |

### 4.2. Khám phá dịch vụ, cá nhân hóa & Cẩm nang biển (Customer) — 10 API

| API | Mô tả ngắn gọn |
| --- | --- |
| `POST /api/ai/search` | Tìm dịch vụ từ yêu cầu ngôn ngữ tự nhiên và tiêu chí ngân sách, ngày đi, số người. |
| `POST /api/ai/recommendations` | Gợi ý dịch vụ theo nhu cầu chuyến đi và sở thích đã lưu khi người dùng bật cá nhân hóa. |
| `POST /api/ai/nearby` | Tìm trải nghiệm gần tọa độ được cung cấp, theo bán kính và điều kiện chuyến đi. |
| `POST /api/ai/services/compare` | So sánh các dịch vụ được chọn theo giá, đánh giá, chính sách và khả năng đáp ứng nhu cầu. |
| `POST /api/ai/weather` | Đánh giá điều kiện thời tiết và quy tắc an toàn cho dịch vụ, lựa chọn và ca dự kiến. |
| `GET /api/ai/review-summaries/{serviceId}` | Tổng hợp ưu điểm, hạn chế từ các đánh giá hợp lệ của dịch vụ. |
| `GET /api/ai/orders/{orderId}/marine-briefing` | Cẩm nang an toàn và checklist cho đơn hàng của khách. Trạng thái thời tiết là `UNKNOWN` khi thiếu dự báo còn hiệu lực. |
| `GET /api/ai/preferences` | Xem hồ sơ sở thích và trạng thái bật/tắt cá nhân hóa của người dùng. |
| `PUT /api/ai/preferences` | Cập nhật sở thích, hoạt động muốn loại trừ và trạng thái cá nhân hóa. |
| `POST /api/ai/recommendations/{id}/feedback` | Ghi nhận phản hồi của người dùng về dịch vụ trong một lượt gợi ý. |

### 4.3. Lập và quản lý lịch trình (Customer) — 13 API

| API | Mô tả ngắn gọn |
| --- | --- |
| `POST /api/ai/itineraries/preview` | Xem trước các phương án lịch trình theo thời gian, ngân sách và dịch vụ có thể đáp ứng. |
| `POST /api/ai/itineraries/previews/{previewId}/save` | Lưu phương án được chọn từ bản xem trước thành lịch trình cá nhân. |
| `POST /api/ai/itineraries` | Tạo và lưu lịch trình trực tiếp từ yêu cầu chuyến đi. |
| `GET /api/ai/itineraries` | Liệt kê lịch trình đã lưu của người dùng. |
| `GET /api/ai/itineraries/page` | Liệt kê lịch trình đã lưu theo phân trang `page` và `size`. |
| `GET /api/ai/itineraries/{id}` | Xem chi tiết một lịch trình thuộc người dùng. |
| `POST /api/ai/itineraries/{id}/accept` | Chấp nhận lịch trình sau khi kiểm tra lại dữ liệu hiện tại. |
| `POST /api/ai/itineraries/{id}/archive` | Lưu trữ lịch trình khi người dùng không tiếp tục sử dụng. |
| `POST /api/ai/itineraries/{id}/replan` | Lập phương án thay thế theo yêu cầu mới, thời tiết hoặc dịch vụ/ca bị loại trừ. |
| `GET /api/ai/itineraries/{id}/proposals` | Xem các đề xuất điều chỉnh của lịch trình. |
| `POST /api/ai/itineraries/{id}/proposals/{proposalId}/accept` | Chấp nhận đề xuất điều chỉnh và cập nhật phiên bản lịch trình. |
| `POST /api/ai/itineraries/{id}/proposals/{proposalId}/reject` | Từ chối đề xuất điều chỉnh lịch trình. |
| `GET /api/ai/itineraries/{id}/revisions` | Xem lịch sử các phiên bản và thay đổi của lịch trình. |

### 4.4. Hỗ trợ khách hàng (Customer) — 6 API

| API | Mô tả ngắn gọn |
| --- | --- |
| `POST /api/ai/support` | Tra cứu đơn của khách, phân loại yêu cầu và gợi ý bước xử lý; có thể xem trước chính sách hủy/hoàn tiền. |
| `POST /api/ai/support/requests/preview` | Kiểm tra khả năng gửi yêu cầu đổi lịch hoặc chuyển cho bộ phận hỗ trợ. |
| `POST /api/ai/support/requests` | Tạo yêu cầu đổi lịch hoặc hỗ trợ thủ công sau khi khách xác nhận. |
| `GET /api/ai/support/requests` | Liệt kê các yêu cầu hỗ trợ của người dùng. |
| `GET /api/ai/support/requests/{id}` | Xem chi tiết và tiến độ xử lý yêu cầu hỗ trợ của người dùng. |
| `POST /api/ai/support/requests/{id}/cancel` | Hủy yêu cầu hỗ trợ theo trạng thái và phiên bản hiện tại. |

### 4.5. Phân loại và đánh giá nội dung — 2 API

| API | Mô tả ngắn gọn |
| --- | --- |
| `POST /api/ai/classifications/service` | Gợi ý phân loại dịch vụ dựa trên văn bản mô tả. |
| `POST /api/ai/content-assessments` | Đánh giá văn bản, phát hiện thông tin liên hệ ngoài nền tảng và dấu hiệu vi phạm; lưu hồ sơ để xem xét. |

### 4.6. Đối tác: Huấn luyện chất lượng, Tối ưu & Vendor Copilot (Vendor) — 10 API

| API | Mô tả ngắn gọn |
| --- | --- |
| `POST /api/vendor/ai/review-coaching` | Phân tích 4 trụ cột nghiệp vụ (`GUIDE`, `SAFETY`, `PUNCTUALITY`, `VALUE`), trích xuất dẫn chứng bằng chứng và tư vấn lộ trình BadgeTier. |
| `GET /api/vendor/ai/review-coaching/history` | Xem lịch sử các đợt phân tích Review Coaching đã lưu trong hệ thống. |
| `POST /api/vendor/ai/reviews/{reviewId}/reply-draft` | Soạn nháp câu trả lời phản hồi khách hàng thông minh theo 3 tông giọng (`PROFESSIONAL`, `FRIENDLY`, `EMPATHETIC`), có tích hợp voucher. |
| `POST /api/vendor/ai/listings/optimize` | Chấm điểm bài đăng và trả về checklist an toàn. Các chi tiết tiếng Anh cần đối tác bổ sung khi nguồn chưa có. |
| `POST /api/vendor/ai/slots/advisor` | Cố vấn công suất, tỷ lệ lấp đầy và đối chiếu dự báo thời tiết 7-14 ngày để tư vấn tăng/giảm ca hoặc mở ca vàng. |
| `POST /api/vendor/ai/copilot/chat` | Trò chuyện với trợ lý đối tác Vendor Copilot, tự động gọi 5 công cụ dữ liệu nội bộ. |
| `GET /api/vendor/ai/copilot/conversations` | Danh sách các phiên hội thoại của vendor, hỗ trợ lọc `archived`. |
| `GET /api/vendor/ai/copilot/conversations/{id}/history` | Xem lịch sử tin nhắn trong phiên hội thoại của vendor. |
| `PATCH /api/vendor/ai/copilot/conversations/{id}` | Cập nhật metadata phiên hội thoại vendor: đổi tên, ghim, lưu trữ. |
| `DELETE /api/vendor/ai/copilot/conversations/{id}` | Xóa mềm phiên hội thoại vendor. |

### 4.7. Quản trị viên: Đánh giá rủi ro, Hòa giải tranh chấp, BI Digest & Admin Copilot (Admin) — 15 API

| API | Mô tả ngắn gọn |
| --- | --- |
| `POST /api/admin/ai/risk-cases` | Phân tích tín hiệu bất thường của đơn/giao dịch theo Velocity Rules 3/5/3 và tạo hồ sơ đánh giá rủi ro cho admin. |
| `GET /api/admin/ai/assessment-cases` | Liệt kê hồ sơ đánh giá nội dung và rủi ro theo trạng thái. |
| `POST /api/admin/ai/assessment-cases/{id}/resolve` | Ghi nhận quyết định và ghi chú của admin đối với hồ sơ đánh giá. |
| `GET /api/admin/support/requests` | Xem hàng đợi yêu cầu hỗ trợ theo trạng thái và giới hạn số bản ghi. |
| `GET /api/admin/support/requests/{id}` | Xem chi tiết yêu cầu hỗ trợ để xử lý thủ công. |
| `POST /api/admin/support/requests/{id}/handle` | Cập nhật tiến độ xử lý yêu cầu và phản hồi cho khách. |
| `POST /api/admin/ai/disputes/{disputeId}/summarize` | Tóm tắt bằng chứng và trả về `MANUAL_REVIEW_REQUIRED` với tỷ lệ hoàn tiền để trống cho đến khi quản trị viên xác minh. |
| `GET /api/admin/ai/disputes/{disputeId}/summary` | Xem lại kết quả phân tích hòa giải tranh chấp đã lưu. |
| `GET /api/admin/ai/executive-digest` | Bản tin điều hành sàn tổng quan 1 chạm: GMV, Payout, Commission, tỷ lệ hủy theo lý do, cảnh báo sức khỏe vận hành vendor. |
| `POST /api/admin/ai/executive-digest` | Tương đương GET, hỗ trợ truy vấn điều hành sàn theo tham số mở rộng. |
| `POST /api/admin/ai/copilot/chat` | Trò chuyện với trợ lý điều hành Admin BI Copilot, tự động gọi 6 công cụ dữ liệu toàn sàn. |
| `GET /api/admin/ai/copilot/conversations` | Danh sách các phiên hội thoại của admin. |
| `GET /api/admin/ai/copilot/conversations/{id}/history` | Xem lịch sử tin nhắn trong phiên hội thoại của admin. |
| `PATCH /api/admin/ai/copilot/conversations/{id}` | Cập nhật metadata phiên hội thoại admin: đổi tên, ghim, lưu trữ. |
| `DELETE /api/admin/ai/copilot/conversations/{id}` | Xóa mềm phiên hội thoại admin. |

---

## 5. Giới hạn model và thời gian chờ

Chat và review summary dùng chung budget **120 giây cho mỗi request**. Kiểm duyệt, model chính,
model dự phòng và các lượt gọi tiếp đều trừ vào cùng deadline; fallback không được cấp lại 120 giây.
Backend hủy HTTP đang chờ khi hết budget, kể cả khi response body vẫn đang gửi nhỏ giọt.
Chat trả `PARTIAL` nếu hết thời gian; kiểm duyệt không hoàn tất vẫn từ chối xử lý.
Review summary tiếp tục dùng nguồn trích xuất đã kiểm chứng khi model không hoàn tất.
Đây là deadline cho công việc AI; truy vấn/lưu dữ liệu và truyền response có thể thêm độ trễ.
Nginx chờ đọc tối đa 125 giây để chuyển response tại deadline. Client gọi AI cần timeout lớn hơn 120 giây;
API client web dùng chung hiện vẫn có mặc định 15 giây và chưa có luồng gọi AI.

Theo [Google](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite),
`gemini-3.5-flash-lite` hỗ trợ tối đa **1.048.576 token đầu vào / 65.536 token đầu ra**.
Backend gửi `reasoning_effort=high`, `max_tokens=65536`; có thể giảm trần bằng
`AI_CLIPROXY_MAX_OUTPUT_TOKENS` (1–65536). Đây là mức trần được phép sinh, không yêu cầu model dùng hết.
Proxy trên server đã trả HTTP 200 cho cả Gemini và `gpt-oss-120b-medium` với hai tham số này;
kiểm tra ngắn đó không chứng minh proxy cho phép sinh đủ 65.536 token hoặc sử dụng toàn bộ context.
Response có `finish_reason=length` được đánh dấu `PARTIAL` và không thực thi tool call bị cắt.

Giới hạn lịch sử 20 tin nhắn, nguồn review và số lượt/tool vẫn bảo vệ luồng nghiệp vụ;
không tự mở rộng đến một triệu token. Quota theo tài khoản, lỗi 429 và độ trễ provider vẫn có thể xảy ra.

### 5.1. Quản lý ngữ cảnh và bộ nhớ trích dẫn (Context Window & Extractive Memory)

Nhằm tuân thủ nguyên tắc xử lý ngữ cảnh dài tự động ở backend mà không bắt người dùng đổi câu hỏi hay mở chat mới:
- **Nén kết quả tool**: Rút gọn payload tool lớn thành bản ghi tham chiếu (`references`), đánh dấu `evidenceComplete=false`, `fullResultInBackendCard=true` và trích xuất một phần nội dung (`excerpt`).
- **Chọn lịch sử theo budget**: Giữ nguyên yêu cầu hiện tại, các chỉ dẫn hệ thống và chuỗi gọi tool hợp lệ liền kề; lược bỏ các lượt hội thoại cũ nhất khi chạm trần budget token.
- **Bộ nhớ trích dẫn (Extractive Memory)**: Khi có lượt hội thoại bị lược bỏ, trích xuất các ràng buộc an toàn (không biết bơi, dị ứng, số lượng khách, ngân sách) và các điều chỉnh mới nhất của người dùng (`sửa lại`, `đổi thành`, `cập nhật`, `thay vì`...). Ưu tiên gọi LLM chọn tối đa 4 ID phát biểu trong 10 giây (với deadline còn lại >= 30 giây); nếu lỗi hoặc chậm, tự động fallback sang trích xuất trực tiếp bằng biểu thức chính quy. Phát biểu sau luôn được sắp xếp theo trình tự thời gian tăng dần và ưu tiên ghi đè phát biểu trước.
- **Dự phòng budget động cho bộ nhớ**: Hệ thống luôn dành một khoản dự phòng budget cho bộ nhớ ngắn; nếu sau khi ghép trích dẫn bộ nhớ mà tổng kích thước vượt trần, hệ thống tiếp tục loại bỏ thêm lượt hội thoại cũ đang giữ lại để nhường không gian cho các ràng buộc cốt lõi trước khi buộc phải giảm bớt số lượng trích dẫn.
