# Staging Migration Rehearsal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Có bằng chứng V44→V45 và recovery trên dữ liệu đủ lớn trước rollout, phân biệt schema correctness với thời gian lock và triển khai production.

**Architecture:** Chạy benchmark trên PostgreSQL/volume disposable có ownership label, sau đó chạy candidate images trong môi trường staging cô lập bằng tooling rehearsal hiện có. Khôi phục bằng backup đã kiểm chứng; không giả định Flyway có thể tự undo migration.

**Tech Stack:** PostgreSQL 16, Flyway, Testcontainers, Docker Compose, `scripts/rehearse.py`, `scripts/backup.py`, `scripts/acceptance.py`.

**Spec:** [Master plan](2026-11-02-backend-test-completion.md); `V45__preserve_service_coordinate_precision.sql`, `PostgreSqlMigrationIntegrationTest`, demo schema inventory/MANIFEST.

## Global Constraints

- V45: numeric(38,2) → numeric(46,10), giữ 36 chữ số phần nguyên. Chữ số đã làm tròn trước V45 không tự khôi phục.
- Không chạy benchmark hoặc thay dữ liệu trên production. Volume/compose project phải do run tạo; không down -v hoặc TRUNCATE tài nguyên ngoài ownership đó.
- Backup chứa dữ liệu riêng tư không được commit/upload artifact công khai. Evidence chỉ chứa hash, row counts, schema/invariant và số đo đã redact.
- Model directory, baseline/candidate image identity, service-size và maintenance budget là input thật cần cung cấp; thiếu thì ghi blocked, không invent số liệu.

## Review Focus

1. Migration đúng schema nhưng giữ ACCESS EXCLUSIVE lock quá lâu: S1 đo dưới reader/writer đồng thời.
2. Chỉ khôi phục schema mà mất dòng hoặc financial/config data: S1/S2 so counts, hash và FK/invariant trước/sau restore.
3. Candidate image/tag đổi trong lúc chạy: S2 dùng release manifest và immutable image identity.
4. Rehearsal đọc nhầm `.env`/volume production: S2 kiểm tra isolate/labels trước mọi thao tác.
5. Backup tồn tại nhưng chưa restore thử: recovery chưa pass cho đến khi baseline khởi động với bản restore.

## S1 — Benchmark migration và recovery trên PostgreSQL thật

**Files:** Create `backend/src/test/java/com/danasea/backend/PostgreSqlMigrationRehearsalTest.java`, `scripts/rehearse_migration.py`, `scripts/tests/test_migration_rehearsal.py`, `backend/docs/migration-v45-runbook.md`; extend `PostgreSqlMigrationIntegrationTest.java` khi cần reuse fixture nhỏ.

**Interfaces:** Core test chạy mặc định với JVM properties `migration.rehearsal.rows=10000`, `migration.rehearsal.max-lock-seconds=5`, `migration.rehearsal.max-migration-seconds=60`: đây là ngưỡng kỹ thuật cho fixture CI, không phải SLA production. Runner staging `rehearse_migration.py --rows N --max-lock-seconds N --max-migration-seconds N --output PATH` bắt buộc operator cung cấp cả ba số dương và xuất provenance; thiếu input thì fail trước Maven/Docker. Runner truyền các giá trị vào JVM properties trên, không thêm assumption/skip vào core suite.

- [ ] Viết unit test runner: thiếu/âm/0 budget hoặc rows phải fail trước subprocess; không đủ ownership/evidence thì không đánh dấu release pass. Core test vẫn chạy được trong full Maven mặc định, không cần environment staging.
- [ ] Test database ở target44 với fixture đầy đủ FK. Seed 10.000 service ở run kỹ thuật; run staging dùng row count/snapshot đã đo từ môi trường cần đánh giá, có nguồn dữ liệu ghi trong evidence.
- [ ] Lưu counts/hash/invariants và backup; migrate45, kiểm tra information_schema numeric_precision=46/numeric_scale=10 cho cả hai cột; giá trị cũ giữ nguyên, tọa độ mới 10 chữ số lẻ đọc lại đúng.
- [ ] Có reader/writer transaction cạnh tranh, ghi thời gian chờ lock và tổng migration bằng đồng hồ monotonic. Thiếu budget phải fail preflight staging, không mặc định suy ra SLA production từ dataset 10.000 dòng.
- [ ] Restore backup V44 vào database khác thuộc run; baseline schema/data/FK khớp và baseline application khởi động được. Không viết down-migration làm mất phần thập phân mới.
- [ ] Lưu evidence revision, PostgreSQL version, row count, size, lock/migration duration, backup hash và restore result. Chạy `PostgreSqlMigrationIntegrationTest,PostgreSqlMigrationRehearsalTest` cùng tooling DB acceptance khi thay schema snapshot.
- [ ] Commit `test(db): rehearse coordinate migration and verified recovery`.

## S2 — Candidate-image rehearsal và release handoff

**Files:** Extend `scripts/rehearse.py`, `scripts/tests/test_rehearse.py` nếu thiếu V45 evidence/ownership check; create `backend/docs/staging-release-checklist.md`.

**Interfaces:** Giữ CLI hiện có `--model-dir`, `--manifest`, `--baseline`, `--backend-image`, `--frontend-image`, `--quyet-image`, `--output`. Dùng release manifest từ tooling hiện có; không thay bằng tag latest.

- [ ] Unit test isolate phải bỏ container_name/host ports, tạo network/volume riêng và chặn destructive operation khi ownership label hoặc expected revision sai.
- [ ] Review source, image/manifest identity và database backup của staging. Chạy rehearsal của candidate với model đã pin; giữ scheduler tắt theo tooling mặc định khi chưa kiểm tra side effect.
- [ ] Kiểm tra health, JWT/tenant, service create/update/readback tọa độ, slot/booking, capture/refund **test boundary**, và seven-task worker inference. Payment/provider thật vẫn phải có artifact F3 riêng, không suy từ stub của rehearsal.
- [ ] Thử failure có kiểm soát và recovery vào volume disposable khác; baseline image hoạt động sau restore. Chỉ cleanup tài nguyên có labels của run.
- [ ] Handoff ghi đủ: candidate SHA/images, core CI, A1/A2, F3, S1/S2, các mục blocked và maintenance budget đã được người vận hành chấp nhận. Việc dispatch CD production/restore dữ liệu thật là thao tác riêng, cần authorization/environment approval thực tế.
- [ ] Commit `test(release): record staging migration and recovery acceptance`.
