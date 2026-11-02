# Runbook: PostgreSQL Migration V45 (Preserve Service Coordinate Precision)

## 1. Giới thiệu và Bối cảnh (Overview & Context)
- **Migration:** `V45__preserve_service_coordinate_precision.sql`
- **Mục tiêu:** Nâng cấp kiểu dữ liệu của cột `latitude` và `longitude` trong bảng `services` từ `numeric(38,2)` lên `numeric(46,10)`.
- **Ràng buộc bảo toàn:**
  - Giữ nguyên 36 chữ số phần nguyên ($46 - 10 = 36$).
  - Lưu trữ chính xác đến 10 chữ số phần thập phân cho tọa độ địa lý.
  - Các tọa độ đã bị làm tròn trước thời điểm V45 sẽ giữ nguyên giá trị hiện có (không thể tự khôi phục dữ liệu đã mất trước đó).

## 2. Đặc tính Khóa & Ngân sách Downtime (Lock Characteristics & Budget)
- **Loại khóa:** `ALTER TABLE services ALTER COLUMN latitude TYPE numeric(46,10), ALTER COLUMN longitude TYPE numeric(46,10)` yêu cầu khóa **`ACCESS EXCLUSIVE`** trên bảng `services`.
- **Khả năng ảnh hưởng:** Các truy vấn SELECT, INSERT, UPDATE, DELETE đồng thời tới bảng `services` sẽ phải chờ cho đến khi hoàn tất việc viết lại bảng (table rewrite).
- **Kết quả diễn tập (Rehearsal Benchmark):**
  - Dataset: 10.000 dòng dữ liệu dịch vụ có đầy đủ ràng buộc khóa ngoại (foreign key), ràng buộc duy nhất (slug index) và cột sinh (`search_vector`).
  - Thời gian giữ khóa ghi nhận: **~0,031 giây** (ngân sách trần CI/Rehearsal: 5,0 giây).
  - Tổng thời gian chạy migration: **~0,072 giây** (ngân sách trần CI/Rehearsal: 60,0 giây).
  - Tỷ lệ dữ liệu bảo toàn: 100%, giá trị tọa độ cũ không đổi, tọa độ 10 chữ số thập phân ghi và đọc chính xác tuyệt đối.

## 3. Quy trình Tiền Triển khai (Pre-deployment Procedure)
Trước khi áp dụng migration lên bất kỳ môi trường nào (Staging hoặc Production):
1. **Tạo snapshot backup đã kiểm chứng:**
   ```bash
   python3 scripts/backup.py --config /path/to/backup_config.json
   ```
   Đảm bảo bản backup đã được restore thử nghiệm thành công vào database tạm (`danasea_restore_*`).
2. **Chạy kịch bản diễn tập migration:**
   ```bash
   python3 scripts/rehearse_migration.py \
     --rows 10000 \
     --max-lock-seconds 5 \
     --max-migration-seconds 60 \
     --output backend/docs/verification/migration-v45-rehearsal.json
   ```
   Kiểm tra exit code = 0 và file bằng chứng JSON hợp lệ.

## 4. Thực thi Migration (Execution)
Migration được áp dụng thông qua Flyway:
```bash
# Qua Maven wrapper trong backend:
cd backend && ./mvnw -B test -Dtest=PostgreSqlMigrationIntegrationTest,PostgreSqlMigrationRehearsalTest

# Hoặc qua container migrate trong môi trường staging:
docker compose -f docker-compose.prod.yml run --rm migrate
```

## 5. Hậu kiểm sau Migration (Post-migration Verification)
1. **Kiểm tra thông tin schema:**
   ```sql
   SELECT column_name, data_type, numeric_precision, numeric_scale
   FROM information_schema.columns
   WHERE table_name = 'services' AND column_name IN ('latitude', 'longitude');
   ```
   *Kỳ vọng:* `numeric_precision` = 46, `numeric_scale` = 10.
2. **Kiểm tra tính toàn vẹn dữ liệu cũ:**
   ```sql
   SELECT count(*) FROM services;
   ```
   *Kỳ vọng:* Số dòng trước và sau migration khớp hoàn toàn.
3. **Thử nghiệm tọa độ chính xác cao:**
   ```sql
   UPDATE services SET latitude = 16.0543219876, longitude = 108.2412345678 WHERE id = '<TEST_SERVICE_ID>';
   SELECT latitude, longitude FROM services WHERE id = '<TEST_SERVICE_ID>';
   ```

## 6. Kế hoạch Phục hồi Thảm họa (Rollback & Disaster Recovery)
> [!CAUTION]
> Flyway không hỗ trợ tự động rollback (down-migration). Không được tạo down-migration thủ công ép kiểu về `numeric(38,2)` vì sẽ làm mất vĩnh viễn các tọa độ 10 chữ số lẻ vừa được người dùng/đối tác cập nhật sau V45.

Nếu có sự cố nghiêm trọng trong quá trình migrate:
1. Dừng container/ứng dụng backend đang kết nối.
2. Khôi phục từ bản backup snapshot đã xác minh ở bước Tiền triển khai:
   ```bash
   # Sử dụng pg_restore vào database đích:
   pg_restore -U "$POSTGRES_USER" --clean --no-owner --no-privileges -d "$POSTGRES_DB" <pre-migration-backup.dump>
   ```
3. Xác minh tính toàn vẹn của bảng và các khóa ngoại sau khi khôi phục.
