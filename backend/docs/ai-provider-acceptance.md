# AI Provider Acceptance Specification & Verification Protocol

## 1. Overview

Tài liệu này quy định kiến trúc và giao thức kiểm thử nghiệm thu (acceptance testing) cho hệ thống AI của DANASEA theo Subplan 3 (Tasks A1–A3):
1. **Mô hình cục bộ (Local Worker):** Java adapter giao tiếp với tiến trình worker chạy mô hình phân loại cục bộ Quyet Small (`chinhnc/Quyet-1.0-Small`).
2. **Cổng Proxy trực tiếp (CLIProxy Live Acceptance):** Java client giao tiếp với CLIProxy để thực thi các tác vụ sinh ngôn ngữ lớn (LLM), kiểm duyệt an toàn (moderation) và nén ngữ cảnh (context window compaction).

---

## 2. A1 — Java Adapter to Local Quyet Worker (CI Bắt buộc)

### 2.1. Mục tiêu & Nguyên tắc cách ly
- Kiểm chứng kết nối thực tế giữa Spring Boot `QuyetDecisionAdapter` và tiến trình worker FastAPI / Torch CPU (`app.quyet_service:app`).
- Toàn bộ 7 tác vụ ra quyết định (`DecisionTask`: `RISK`, `CLASSIFY`, `MODERATE`, `ROUTE`, `VERIFY`, `INSPECT`, `SCORE`) phải được thực thi thành công, trả về `available = true`, mã revision chính xác và `suggestionOnly = true`.
- Tuyệt đối **không skip** (`allowed_skipped_tests: []`) trong policy kiểm thử `.ci/worker-acceptance-policy.json`.

### 2.2. Kiểm soát Preflight & Vòng đời Worker
- **Kiểm tra tính toàn vẹn của mô hình:** Thư mục mô hình phải khớp mã băm SHA-256 đã ghim (`9ef88d40e9fe14003195b15d181e3bd7bb2802ba5100998c198338b7a640a457`) qua `production.validate_quyet_model`.
- **Khóa cổng mạng (Port Guard):** Trước khi khởi chạy, runner kiểm tra cổng (mặc định `18091`). Nếu cổng đã có tiến trình khác sử dụng, runner dừng ngay lập tức; không tái sử dụng worker bên ngoài.
- **Khóa bí mật (API Key):** Key được tạo tự động với quyền `0600` và truyền vào worker qua biến môi trường `AI_QUYET_API_KEY`. Phía Maven chỉ nhận đường dẫn file (`-Dai.local.key-file`), không xuất hiện chuỗi secret trong log hay artifact.
- **Dọn dẹp tiến trình con:** Khối `finally` đảm bảo chỉ dừng đúng tiến trình con sở hữu (`proc.terminate()`), không ảnh hưởng đến các tiến trình khác trên máy chủ.

### 2.3. Lệnh thực thi cục bộ & CI
```bash
python3 scripts/ci/run_java_worker_acceptance.py \
  --model-dir "/path/to/quyet-model" \
  --key-file "/path/to/service.key" \
  --port 18091 \
  --output "/path/to/quyet-java-acceptance.json"
```

---

## 3. A2 — CLIProxy Live Acceptance (Budget & Constraint Preservation)

### 3.1. Phạm vi & Kịch bản kiểm thử
Sử dụng suite `ProxyLiveSmokeTest.java`, bao gồm đúng **3 lần thực thi (executions)** bắt buộc:
1. `realModelAcceptsHighReasoningAndMaximumOutputCeiling` với model `gemini-3.5-flash-lite`.
2. `realModelAcceptsHighReasoningAndMaximumOutputCeiling` với model `gpt-oss-120b-medium`.
3. `realProxyCompactionKeepsOldNonSwimmingConstraintAndAnswersCurrentQuestion`: Kiểm tra cơ chế tóm tắt & nén ngữ cảnh (Context Compaction).

### 3.2. Quy tắc an toàn & Giới hạn ngân sách
- **Ngân sách thực thi (Execution Budget):** Tối đa 120 giây và 6 yêu cầu logic cho mỗi test case (`AiExecutionBudget.open(Duration.ofSeconds(120), 6)`).
- **Trần đầu ra (Output Ceiling):** Giới hạn 8.192 tokens.
- **Bảo toàn ràng buộc an toàn (Constraint Preservation):** Kịch bản nén ngữ cảnh thêm dữ liệu nhiễu lớn nhưng phải bảo toàn nguyên vẹn ràng buộc quan trọng của khách hàng: `"I cannot swim. Please remember this safety constraint."`. Khi được hỏi lại sau khi nén, câu trả lời bắt buộc phải xác nhận người dùng không biết bơi.
- **Không che giấu lỗi:** Nếu API trả về phản hồi rỗng, thiếu thông tin usage tokens hoặc xảy ra fallback do lỗi, runner ghi nhận chính xác và không đánh dấu là hoàn thành provider thành công.

### 3.3. Lệnh thực thi
```bash
python3 scripts/ci/run_proxy_acceptance.py \
  --base-url "https://cliproxy.internal" \
  --key-file "/path/to/proxy.key" \
  --output "/path/to/proxy-acceptance.json"
```

---

## 4. Trạng thái kiểm chứng hiện tại

| Hạng mục | Cơ chế thực thi | Trạng thái hiện tại |
|---|---|---|
| **A1: Local Quyet Worker** | Job `backend-worker-contract` trong CI | **Sẵn sàng & Tự động chạy trong CI** (sử dụng weights đã ghim) |
| **A2: CLIProxy Live Acceptance** | Workflow `ai-provider-acceptance.yml` | **Chưa kiểm chứng / Blocked** (Cần người vận hành cấu hình `AI_PROXY_API_KEY` trong environment `ai-provider-sandbox`) |
