# Backend Test Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. User-owned implementation is the requested handoff; do not start implementing the next phases automatically.

**Goal:** Hoàn thiện các khoảng trống test đã xác nhận, có bằng chứng CI và acceptance theo đúng phạm vi, đồng thời duy trì dependency baseline ổn định.

**Architecture:** Giữ module, port và luật nghiệp vụ hiện tại. Chia thành bốn nhánh công việc có thể review độc lập: hạ tầng/unit coverage, tài chính, AI acceptance và rehearsal staging. Mỗi task tạo test có assertion cụ thể, sửa tối thiểu khi test chứng minh lỗi, rồi commit riêng.

**Tech Stack:** Java 21, Spring Boot 4.1.2, JUnit Jupiter, Mockito subclass mock maker, PostgreSQL 16, Redis, RabbitMQ, Testcontainers, JaCoCo 0.8.12, Python 3.11, GitHub Actions.

**Spec:** [Báo cáo và giới hạn đã xác nhận](../../../backend/docs/test-completeness-audit-2026-11-02.md); [inventory theo class/method](../../../backend/docs/verification/test-completeness-audit-2026-11-02.json).

## Global Constraints

- Phần đã xử lý được công bố trước plan: commit `4e03b8e6f3b73a598dc3042d9a3c8c351d50991a`, nhánh `code/ci-backend-stability-audit-20261101`.
- Baseline local: 2.894 testcase execution; 2.891 pass, 0 failure/error, 3 provider/model skip; 336 suite trên 342 file Java. Execution bao gồm invocation parameterized.
- Coverage UTC: LINE 82,65%, BRANCH 62,93%, METHOD 78,20%. Floor CI giữ ít nhất LINE 82%, BRANCH 62%, METHOD 78%; covered LINE >=17.300, BRANCH >=7.050 và floor từng class trong `.ci/coverage-policy.json`.
- Không thêm `@Disabled`, assumption/whitelist nghiệp vụ, retry che failure, exclusion coverage hoặc nới assertion để đạt CI. Chỉ ba tên acceptance case đã khai báo trong `.ci/test-policy.json` được skip ở core suite.
- Unit test dùng domain/use case/mapper thật; mock tại port/I/O. Integration test dùng transaction/database/broker thật thuộc fixture; không dùng dịch vụ mặc định localhost của người vận hành.
- Ngày booking/slot dùng `Booking.VIETNAM_ZONE`. Mockito hiện dùng subclass mock maker; không thiết kế dựa vào static mocking.
- Không thay đổi policy giá, refund, ownership hoặc trạng thái chỉ để khớp test. Phát hiện mâu thuẫn contract thì ghi regression và phân biệt bug đã chứng minh với quyết định sản phẩm chưa có.
- Lệnh Maven dưới đây chạy từ `backend/`; Python và Git chạy từ repo root. Nhánh mới dùng prefix `code/`.
- Mỗi batch kiểm tra focused suite + blast radius; full `clean verify` tại checkpoint, sau đó source/report/coverage guard. Không chạy lặp toàn suite nếu source/phạm vi chưa đổi và không có lỗi mới.
- Không dùng kết quả mock, model inference, sandbox hoặc CI xanh để tự suy ra production/payment readiness.

## Review Focus

1. Full Spring context vô tình lấy Redis/RabbitMQ localhost: subplan hạ tầng, task I1 phải bắt được bằng binding/ownership thật.
2. PATCH option của vendor khác hoặc option thuộc service khác: task I2 kiểm tra từ chối và dữ liệu không đổi.
3. Gateway timeout rồi retry/webhook cạnh tranh: task F1 kiểm tra một operation ID và một hiệu ứng tài chính.
4. Weather provider lỗi hoặc thiếu trường: task I4 giữ dữ liệu thiếu là null, không biến thành thời tiết an toàn giả.
5. Acceptance thiếu model/key/credential vẫn xanh: task A1/A2/F3 phải fail preflight; không dùng skip hoặc fallback làm bằng chứng provider thật.

## Việc đã commit và vị trí trên Git

- [x] Audit backend/CI, regression, migration V45, khôi phục 135 legacy case và guard: commit `4e03b8e`, nhánh nêu trên.
- [x] Dependabot #166: commit `dbdd3194115bfce6d78551da8bd8db7b3e9c4c2b`; giữ Python 3.11 và ignore Python >=3.12. CI run `41399994135` đã success khi chốt trạng thái này.
- [x] Dependabot #170: commit `26a57e1a34b6bcd8f555d147d2600d8a285a50e4`; installer Torch CPU đọc từ lock + libquadmath0. CI run `41400219626` đã success.
- [x] #165, #167, #168, #169 đã có commit trên nhánh bot và CI success; không tạo commit rỗng hoặc chép lại thay đổi của bot.
- [ ] Đối chiếu CI của **head mới nhất** trên PR audit sau khi plan được push. Không lấy CI của các PR Dependabot làm bằng chứng cho audit.
- [ ] Người triển khai review/merge audit và các PR dependency cần dùng. Merge từng PR làm thay đổi base; refresh/rebase và đọc CI mới của PR tiếp theo. Push nhánh feature không đồng nghĩa merge hoặc deployment.
- [ ] Trước khi bắt đầu từng subplan: `git fetch origin`, tạo nhánh từ main đã chứa các prerequisite. Không tự merge main hoặc dispatch CD production trong bước chốt commit/plan này.

## Thứ tự triển khai

1. **Checkpoint Git/CI:** audit + #166/#170 phải nằm trong base dùng để triển khai. #166 chỉ chặn bot trên main sau khi được merge; patch/digest Python 3.11 vẫn được cập nhật.
2. **P0 — môi trường và nghiệp vụ:** [I1–I6: isolation/coverage](2026-11-02-test-isolation-and-business-coverage.md). I1 làm trước; I2–I5 có thể review riêng sau đó.
3. **P0 — tài chính:** [F1–F4: payment/refund](2026-11-02-payment-refund-acceptance.md). F1/F2 không cần credential; F3 chỉ chạy khi sandbox sẵn sàng; F4 mở rộng kiểm chứng assertion.
4. **P1 — AI:** [A1–A3: Java/worker và CLIProxy](2026-11-02-ai-provider-acceptance.md). A1 cần #170; A2 cần provider/key thật, không chặn việc triển khai A1.
5. **Release — staging:** [S1–S2: migration/rehearsal](2026-11-02-staging-migration-rehearsal.md). Chạy sau checkpoint code/CI; có backup/restore trước khi đánh giá rollout.

## Bằng chứng cần lưu sau mỗi batch

- Commit SHA, command, exit code, Surefire testcase outcome, report/coverage guard JSON, module và class thay đổi.
- Regression: kết quả trước sửa phải thất bại đúng hành vi; sau sửa pass. Compile error hoặc Docker mất kết nối không được gọi là regression nghiệp vụ thành công.
- Acceptance ngoài hệ thống: backend/model revision, provider/model thực sự đã dùng, test case và trạng thái, thời gian, token/cost nếu provider cung cấp; không chứa bearer token, client secret hoặc dữ liệu thanh toán nhạy cảm.
- Tạo evidence mới dưới `backend/docs/verification/` hoặc artifact CI. Giữ snapshot ngày 01/11–02/11 là lịch sử; không sửa số liệu cũ để giống run mới.
- Khi coverage thiếu thuộc code compatibility/dead code, xác nhận caller trước. `SettlementRepositoryAdapter`, `PostgresServiceSearchAdapter`, `AuthorizationActionUseCase` và payment compatibility wrappers không được xóa chỉ dựa vào 0% coverage.

## Checkpoint kết thúc một nhánh

- [ ] Focused tests và nhóm bị ảnh hưởng pass; invariant DB/ownership/idempotency được assert trực tiếp.
- [ ] Full backend: `./mvnw -B clean verify` thành công.
- [ ] Từ repo root: `python3 scripts/check_test_reports.py backend/target/surefire-reports --policy .ci/test-policy.json --test-source backend/src/test/java` và `python3 scripts/check_coverage.py backend/target/site/jacoco/jacoco.xml --policy .ci/coverage-policy.json` đều exit 0.
- [ ] Với thay đổi ngày/fixture/concurrency: full run `TZ=Pacific/Kiritimati ./mvnw -B clean verify -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=20261102` và hai guard lại pass.
- [ ] Quality có Python 3.11/PyYAML/bcrypt/Ruff theo `.github/workflows/ci.yml`; chạy test tooling có `RUN_DEMO_DATABASE_TESTS=1` khi thay schema/demo/deploy tooling. Không tính các DB test bị skip là pass.
- [ ] `git diff --check`, commit/push; remote SHA trùng local SHA; CI exact head success; review thread đã được xử lý. Việc commit không tự cấp quyền merge/deploy production.

## Điều kiện hoàn thiện

Đạt các test/invariant và acceptance thực tế của từng subplan; không yêu cầu đạt 100% DTO/constructor bằng test hình thức. Hạng mục phụ thuộc credential hoặc môi trường chưa có phải được ghi **chưa kiểm chứng/blocked**, cùng input còn thiếu, thay vì đánh dấu hoàn thành vì core CI xanh.
