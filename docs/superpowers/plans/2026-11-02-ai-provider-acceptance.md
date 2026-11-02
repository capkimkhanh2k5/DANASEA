# AI Provider Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bổ sung bằng chứng Java adapter → worker/model thật và CLIProxy thật, tách rõ model inference, fallback và chất lượng nghiệp vụ.

**Architecture:** Giữ core test policy hiện tại; thêm một job bắt buộc chạy Java acceptance với worker do job sở hữu. Provider CLIProxy dùng workflow thủ công có key/budget và fail preflight nếu thiếu. Cả hai dùng test hiện có, không tạo đường bỏ qua xác thực hoặc can thiệp booking/payment.

**Tech Stack:** Java 21, Python 3.11, locked CPU Torch, Quyet Small, uvicorn, JUnit/Surefire, GitHub Actions.

**Spec:** [Master plan](2026-11-02-backend-test-completion.md); `QuyetDecisionAdapterTest`, `ProxyLiveSmokeTest`, `scripts/ci/download_model.py`.

## Global Constraints

- Phụ thuộc #170 đã merge để Docker và CI dùng cùng installer đọc Torch pin từ lock. Không hardcode lại Torch version ở workflow mới.
- Model `chinhnc/Quyet-1.0-Small`, revision `233167bba5df61b5375a522bf8a042d8d2189379`; validate đầy đủ manifest bằng `production.validate_quyet_model`.
- Key test sinh riêng từng run, file 0600 ngoài model cache; không artifact/cache credential. Worker bind loopback và là child process của runner.
- Core backend được giữ đúng ba tên acceptance case optional hiện có; job acceptance mới bắt buộc chạy case real worker không skip. Đây không phải thêm class-wide whitelist.
- Model response là suggestion-only; không suy ra semantic accuracy hoặc production capacity từ HTTP 200/inference thành công.

## Review Focus

1. Health của worker khác đang chiếm port bị dùng nhầm: A1 yêu cầu child process còn sống và revision đúng.
2. Model cache sai/thiếu nhưng code chuyển fake model: A1 fail trước Maven, không fallback.
3. Maven trả 0 vì case vẫn bị disable: A1/A2 checker bắt exact testcase phải executed, không skipped/aborted.
4. Provider thất bại nhưng fallback trả lời được: A2 không ghi live-provider pass chỉ từ ANSWERED nếu không có bằng chứng route tương ứng.
5. JSON response có suggestion/constraint sai dù transport thành công: A3 giữ kết quả kỹ thuật và đánh giá nghiệp vụ thành hai nhóm riêng.

## A1 — Java adapter gọi worker/model thật trong CI bắt buộc

**Files:** Create `scripts/ci/run_java_worker_acceptance.py`, `scripts/tests/test_java_worker_acceptance.py`, `.ci/worker-acceptance-policy.json`; modify `.github/workflows/ci.yml`; reuse `backend/src/test/java/com/danasea/backend/modules/ai/QuyetDecisionAdapterTest.java`.

**CLI:** `run_java_worker_acceptance.py --model-dir PATH --key-file PATH --port 18091 --output PATH`. Runner kiểm tra model, tạo key test nếu file chưa có, khởi động worker rồi gọi Maven; luôn terminate/wait child trong finally. Không tái sử dụng worker có sẵn.

- [ ] Unit tests runner: model/key sai hoặc port bị chiếm fail trước Maven; child chết/health sai revision fail; Maven lỗi/acceptance skip fail; log và artifact không chứa key. Không kill process không thuộc runner.
- [ ] Cài worker bằng installer từ #170, fetch model bằng `scripts/ci/download_model.py`, cache chỉ model đã pin. Truyền `QUYET_MODEL_DIR`, `QUYET_API_KEY`, `QUYET_CPU_THREADS=2` cho child; bind `127.0.0.1:18091`.
- [ ] Runner gọi Maven từ backend với argv `./mvnw`, `-B`, `-Dtest=QuyetDecisionAdapterTest`, `-Dai.local.smoke=true`, base-url loopback từ port đã bind, key-file từ `args.key_file.resolve()`, `test`. Ví dụ chạy runner trong CI: `python3 scripts/ci/run_java_worker_acceptance.py --model-dir "$RUNNER_TEMP/quyet-model" --key-file "$RUNNER_TEMP/quyet-java.key" --port 18091 --output "$RUNNER_TEMP/quyet-java-acceptance.json"`.
- [ ] Assert testcase `actualPinnedLocalWorkerExecutesAllSevenTasksThroughJavaAdapter` thực sự chạy, không skip; bảy DecisionTask đều available, revision đúng và suggestionOnly=true. Checker policy riêng có required class và **allowed_skipped_tests/classes rỗng**, không truyền toàn bộ test-source vào một targeted run.
- [ ] Thêm job `backend-worker-contract`, upload Surefire/summary đã redact; thêm job vào needs và kết quả được required CI gate kiểm tra. Thời gian setup/model không lấy từ budget 15 phút của backend job hiện tại; đo rồi chọn timeout cho job riêng.
- [ ] Chạy job trên PR trước khi merge; thiếu artifact/case hoặc inference error làm gate đỏ. Commit `test(ai): require real Java worker model acceptance in CI`.

## A2 — CLIProxy live acceptance có budget

**Files:** Create `.github/workflows/ai-provider-acceptance.yml`, `.ci/proxy-acceptance-policy.json`, `backend/docs/ai-provider-acceptance.md`; modify `ProxyLiveSmokeTest.java` nếu cần thêm provenance/assertion; create test runner/preflight trong `scripts/ci/run_proxy_acceptance.py` và `scripts/tests/test_proxy_acceptance.py`.

**CLI:** `run_proxy_acceptance.py --base-url URL --key-file PATH --output PATH`. Workflow dùng environment `ai-provider-sandbox`, manual dispatch và key do operator cấp; không gọi thay người dùng ở bước lập plan này.

- [ ] Preflight key/base URL trống, file không đọc được, endpoint ngoài allowlist môi trường hoặc thiếu model support phải exit !=0; không skip hay tạo fake reply.
- [ ] Runner gọi `./mvnw -B -Dtest=ProxyLiveSmokeTest -Dai.proxy.smoke=true` với hai system property `ai.proxy.base-url` và `ai.proxy.key-file` lấy từ argument đã validate, rồi goal `test`; không đưa secret lên command line. Trong workflow gọi runner bằng `python3 scripts/ci/run_proxy_acceptance.py --base-url "$PROXY_BASE_URL" --key-file "$RUNNER_TEMP/proxy.key" --output "$RUNNER_TEMP/proxy-acceptance.json"`.
- [ ] Hai method live hiện có phải tạo **ba execution** khi enabled: hai model cho reasoning/output-ceiling, một compaction giữ constraint không biết bơi. Kiểm tra exact testcase outcomes, không chỉ Maven exit code.
- [ ] Giữ budget hiện có 120 giây/6 logical request mỗi case và output ceiling 8192; artifact ghi requested model, response/finish status, reported tokens, elapsed time và route/fallback evidence thực sự có. Provider attribution không được trả về thì ghi unknown, không tự suy từ model alias.
- [ ] Thiếu usage hoặc response rỗng không được gọi là provider success. Giữ toàn bộ constraint, đặc biệt `I cannot swim`, qua compaction; fallback chỉ ghi kết quả fallback riêng.
- [ ] Policy targeted không cho skip. Workflow tự động không phụ thuộc key để core PR CI chạy, nhưng pre-release cần live run của đúng candidate SHA. Commit `test(ai): add guarded live proxy acceptance evidence`.

## A3 — Report tách kỹ thuật, routing và kết quả nghiệp vụ

**Files:** Create `backend/docs/verification/ai-acceptance-schema.json`, `scripts/check_ai_acceptance.py`, `scripts/tests/test_ai_acceptance.py`; update hai runner A1/A2.

**Schema:** schema_version=1; backend_revision, model_revision nếu có, requested_model, actual_provider (nullable), route=direct|fallback|unknown, executed_cases, case outcomes, timing và token/cost provenance. Chi phí không có số liệu để null, không tự điền 0.

- [ ] Test âm cho thiếu revision/case, skip, non-200 bị gán success, fallback gắn nhãn direct, thiếu token/cost nhưng tự ghi đã đo.
- [ ] Ghi riêng technical_pass và business_evaluation; business evaluation bao gồm constraint preservation, owned-order policy boundary và suggestion-only. Không chuyển toàn bộ thành semantic accuracy score nếu chưa có golden dataset/nhãn chuyên môn.
- [ ] Chạy checker trên artifact thật của A1/A2; ghi phần chưa kiểm chứng. Commit `test(ai): validate provider and acceptance provenance`.
