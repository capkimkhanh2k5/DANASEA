# Staging Release & Migration Rehearsal Checklist

> Code availability is not execution evidence. Current release state: F3/A2/S2 unverified until actual protected environment runs and their artifacts exist. See the migration and provider runbooks in this directory.

## 1. Định danh Bản phát hành (Release Identity & Manifest)
- [ ] **Commit SHA Candidate:** Xác định chính xác 40-character Git SHA từ head của nhánh release.
- [ ] **Release Manifest:** Cung cấp file manifest JSON chứa digest-pinned images (không dùng tag mutable `latest`):
  - `backend`: `ghcr.io/<repo>/danasea-backend@sha256:...`
  - `frontend`: `ghcr.io/<repo>/danasea-frontend@sha256:...`
  - `quyet`: `ghcr.io/<repo>/danasea-quyet@sha256:...`
- [ ] **Quyet Model Directory:** Model directory tồn tại, SHA trùng khớp bản model đã pin (`233167bba5df61b5375a522bf8a042d8d2189379`).

## 2. Kiểm tra Môi trường & Cô lập (Environment Isolation)
- [ ] **Project Name:** Tạo dự án docker compose riêng biệt với prefix ngẫu nhiên (`danasea_rehearse_<hex>`).
- [ ] **Network & Port Isolation:**
  - Bỏ `container_name` tĩnh và các dải subnet production.
  - Loại bỏ port binding ra host đối với các dịch vụ nội bộ (PostgreSQL, Redis, RabbitMQ, Quyet, CLIProxy).
  - Chỉ expose cổng ngẫu nhiên localhost cho `frontend` (`127.0.0.1::8080`).
- [ ] **Biến môi trường Schedulers:**
  - `APP_SCHEDULER_ENABLED=false` (tránh chạy scheduled jobs vô ý trong quá trình rehearsal).
  - `MANAGEMENT_HEALTH_MAIL_ENABLED=false`.

## 3. Diễn tập Migration V45 & Benchmark Ngân sách Khóa (Migration Rehearsal)
- [ ] **Ngân sách Downtime:** Đã được người vận hành xác nhận:
  - Dataset: số liệu staging do người vận hành cung cấp; 10.000 dòng là fixture CI minh họa.
  - Budget query blocking do người vận hành chấp nhận; 5 giây chỉ là ngưỡng fixture CI, không phải số đo chính xác thời gian giữ khóa.
  - Trần thời gian chạy migration: $\le$ 60,0 giây.
- [ ] **Thực thi diễn tập:**
  ```bash
  python3 scripts/ops/rehearse_migration.py \
    --rows 10000 \
    --max-lock-seconds 5 \
    --max-migration-seconds 60 \
    --output backend/docs/verification/migration-v45-rehearsal.json
  ```
- [ ] **Xác minh Schema V45:** Cả hai cột `latitude` và `longitude` đạt `numeric_precision=46` và `numeric_scale=10`.
- [ ] **Bảo toàn dữ liệu cũ & Tọa độ 10 số lẻ:** Dữ liệu cũ giữ nguyên; ghi và đọc lại tọa độ chính xác cao thành công.

## 4. Thử nghiệm Phục hồi Sao lưu (Backup & Recovery Proof)
- [ ] **Snapshot trước Migration:** Tạo bản pg_dump `--no-owner --no-privileges` từ database V44.
- [ ] **Thử nghiệm Restore vào Database Tạm:**
  - Tạo database/schema tạm thời (`danasea_restore_*` hoặc `rehearsal_v44_restored`).
  - Phục hồi dữ liệu từ bản sao lưu.
  - Chạy `Flyway validate` kiểm tra toàn bộ 44 migration baseline thành công.
  - So sánh checksum / hash của dữ liệu dịch vụ trước và sau phục hồi.
- [ ] **Dọn dẹp tài nguyên sở hữu:** Xóa toàn bộ database và volume tạm thời thuộc nhãn rehearsal của phiên chạy.

## 5. Kiểm tra Tích hợp Nghiệp vụ Staging (Business Probing)
- [ ] **Health & Readiness:** Frontend và Backend khởi động thành công, vượt qua health check.
- [ ] **Authentication & Tenant:** Đăng ký và đăng nhập tài khoản người dùng/đối tác tạo JWT token hợp lệ.
- [ ] **Quản lý Dịch vụ & Đọc lại Tọa độ:** Tạo/cập nhật dịch vụ với tọa độ địa lý, gọi API đọc lại dữ liệu và kiểm tra độ chính xác tọa độ qua lớp REST API.
- [ ] **Booking & Hold Invariants:** Giữ chỗ (hold slot) tính đúng tổng tiền và giữ chỗ an toàn trước tranh chấp.
- [ ] **AI Worker Decision:** Thực hiện kiểm tra quyết định (intent detection) với Quyet Small worker model thành công, không có cảnh báo vi phạm ngân sách.

## 6. Trạng thái Kiểm chứng & Biên giới Tài chính (Verification Status Handoff)
- [ ] **Subplan 1 (Test Isolation & Business Coverage):** Đã hoàn tất, 6 commit `071f3b0b` → `e37c3f09`.
- [ ] **Subplan 2 (Payment & Refund Acceptance):**
  - F1, F2, F4: Đã hoàn tất với integration tests, HTTP contracts và 4 đột biến tài chính bị diệt.
  - F3 (Sandbox Live Acceptance): **Chưa kiểm chứng / Blocked** do thiếu thông tin đăng nhập PayPal/VNPay sandbox thật từ người vận hành (không dùng mock để che giấu).
- [ ] **Subplan 3 (AI Provider Acceptance):**
  - A1: Đã tích hợp kiểm chứng worker model thật vào CI gate.
  - A2: Runner live proxy với 3 provider checks đã sẵn sàng.
  - A3: Schema/validator cần artifact thật; policy chưa được đo giữ null, proxy upstream/route giữ unknown.
- [ ] **Subplan 4 (Staging Migration Rehearsal):**
  - S1: Script `rehearse_migration.py` và test `PostgreSqlMigrationRehearsalTest` hoàn tất.
  - S2: Script `rehearse.py` đã bổ sung kiểm tra V45 và checklist release đã hoàn thiện.

> [!IMPORTANT]
> Việc commit hoặc push code/kịch bản lên Git **KHÔNG** tự động kích hoạt deploy lên Production. Mọi thao tác deploy hoặc restore trên production phải do người vận hành trực tiếp phê duyệt và thực hiện theo đúng thẩm quyền.
