# Rà soát độ đầy đủ của test BE — 02/11/2026

## Phạm vi và cách đọc kết quả

Nhánh `code/ci-backend-stability-audit-20261101`, base main `0248965112fe6ed7b8a4e5fba2c27e26b2db2903`. Đây là phần tiếp theo của [điều tra lỗi CI](ci-test-audit-2026-11-01.md). Đối chiếu kiểm kê toàn bộ source test với Surefire, đo JaCoCo cho toàn bộ production code, rà sâu assertion và các nhánh tài chính, tenant/security, service/inventory, migration, async audit và chống xanh giả. Bổ sung test và sửa các lỗi được test tái hiện. Không dùng lượng test hoặc coverage để khẳng định mọi yêu cầu nghiệp vụ đều đã được kiểm chứng.

[Inventory và coverage theo class/method](verification/test-completeness-audit-2026-11-02.json) lưu từng suite, từng execution, hash source test và các method còn thiếu coverage. [Mapping legacy](verification/service-legacy-case-mapping.json) giữ từng tên case gốc, lỗ hổng baseline và assertion thay thế. Kết quả ngày 01/11 là snapshot lịch sử, không dùng làm số liệu hiện tại.

## Những khoảng trống đã xử lý

### 135 legacy case trước đây bị skip

Giữ nguyên danh tính 135 phương thức gốc của bốn ServiceTier, viết lại thành test thực thi và thêm hai ca image API: tổng 137 execution. Bỏ toàn bộ whitelist skip của nhóm này. Dùng Spring/MockMvc/JWT và PostgreSQL/Redis thật, schema riêng `service_legacy`, rollback fixture theo test, flush/clear rồi reload dữ liệu. Storage ngoài được mock trong ca upload/delete image; không gọi cloud để kiểm tra quyền sở hữu hoặc hàng dữ liệu.

Các ca kiểm tra status/error code cụ thể, tenant/role, dữ liệu persisted, image/service binding, phân trang thực có, vòng đời draft/review/publish/reject/resubmit và audit thật. Executor test dùng pool thật và Phaser để đợi toàn bộ công việc async, kể cả task con; ba unit test chứng minh không đọc audit sớm hoặc bỏ sót task bị từ chối. Không chuyển audit thành đồng bộ để làm test xanh.

Hai case cũ giả định vendor listing hỗ trợ query search/pagination mà implementation hiện chưa hỗ trợ. Test nay mô tả chính xác query bị bỏ qua; không ghi là tính năng đã được triển khai. Create cho phép price=0, update không cho phép; duyệt lại service bị reject phải resubmit; weather-sensitive service cần safety document được duyệt. Fixture và assertion tuân theo contract hiện hữu, không sửa nghiệp vụ để khớp kỳ vọng cũ. Case onboarding cũ bắt đầu từ vendor đã approved, vì thế không được tính là full onboarding acceptance.

### Tài chính, security và nghiệp vụ

- HMAC: vector độc lập có UTF-8, tampering, normalization, null/blank/secret. Capture/refund: kiểm tra riêng amount, currency, provider, order/payment/refund identity, trạng thái terminal và replay; không gộp nhiều input sai khiến một guard che guard khác.
- Refund: reservation, rounding phần dư, FAILED không giữ reservation, context biến mất giữa hai phase, operation đã tồn tại, snapshot/expiry/identity khi retry. `RequestRefundUseCaseTest` kiểm tra không gọi bất kỳ overload gateway nào, cùng dữ liệu PENDING persisted.
- PayPal webhook: các header xác thực, event routing, thiếu từng header, chữ ký sai, malformed event, binding refund/provider/operation. Adapter provider mock không phải bằng chứng giao dịch PayPal/VNPay thật.
- Đọc payment: real DTO mapping, owner/missing/empty và Spring method-security proxy thật cho admin/customer/vendor/anonymous.
- Admin/review/profile/safety: real use case và mapper, filter kể cả false, pagination metadata, principal/tenant binding, missing/forbidden, signed document payload; mock repository/port bên ngoài. Direct controller composition không thay thế HTTP-filter acceptance.
- Slot/option: bổ sung unit test trực tiếp cho các use case đang được controller gọi, kiểm tra input, quyền sở hữu, thời gian Việt Nam, inventory, unit/capacity và dữ liệu lưu/response. Các API legacy dùng đường xử lý khác nên không được dùng để tuyên bố các use case này đã được bao phủ.
- Đổi lịch: kiểm tra slot/service binding, trạng thái và service published qua adapter thật. Chính sách AI: kiểm tra real engine và real consumer output, đặc biệt đúng 24 giờ và 24 giờ + 1 giây.
- Confirmation store: thêm 7 test với Redis thật và 11 unit test. Hai instance độc lập kiểm tra JSON roundtrip/copy isolation, TTL pending 15 phút/confirmed 1 ngày, missing/corrupt/delete/expiry, 12 contender có đúng một winner, ownership và stale-token fencing. Fallback kiểm tra copy/delete/lease expiry cùng lỗi Redis/serialization; không dùng static mock vì repository dùng Mockito subclass mock maker. Không tuyên bố đã chờ đủ 15 phút để kiểm chứng card expiry của fallback.

### Xanh giả và lỗi production phát hiện bởi test

Ngoài lỗi shared H2, vòng đời container, weather test trả null vẫn pass và fixture ngày đã trình bày trong báo cáo 01/11:

1. Global MVC handler từng trả 500 cho UUID/enum sai, thiếu request parameter và JSON hỏng. Bốn regression đỏ trước sửa, nay trả 400/INVALID_INPUT. Handler riêng của service vẫn có thể dùng BAD_REQUEST theo contract hiện có.
2. Refund processor từng ghi lỗi cần review ở phase chuẩn bị rồi tiếp tục phase sau và ném lỗi lại. Trạng thái chuẩn bị nay tách CREATE/RECONCILE/BLOCKED; context lỗi dừng trước gateway, không đếm retry hai lần; context mất ở phase sau giữ trạng thái chờ xác minh. Unit tests không chứng minh mọi interleaving transaction thật; các suite PostgreSQL concurrency hiện hữu vẫn được giữ.
3. PostgreSQL làm tròn latitude/longitude vì cột numeric(38,2). Migration V45 nâng thành numeric(46,10), giữ 36 chữ số phần nguyên; real PostgreSQL upgrade test kiểm tra dữ liệu cũ còn nguyên và đọc/ghi đủ 10 chữ số lẻ. Dữ liệu đã bị làm tròn trước đây không tự phục hồi. Schema inventory/demo manifest cập nhật đồng bộ, không chạy migration trên production.
4. `AiPolicyReader` còn nhãn bốn khoảng giờ của policy cũ, nhưng lấy phần trăm từ STANDARD_24H_V2. Nó có thể nói 0% cho toàn khoảng 24–48 giờ dù engine hoàn 100% khi còn hơn 24 giờ. Snapshot nay dùng hai khoảng đúng: >24 giờ và ≤24 giờ; không đổi engine. [Regression real JUnit](verification/test-discovered-regressions-2026-11-02.json): 8 failure trên source cũ, 8 pass sau sửa, gồm đường consumer AI thật.
5. Tạo slot với phần tử unit null hoặc tổng capacity tràn int từng có thể ném lỗi nội bộ. Input nay bị từ chối trước persistence bằng IllegalArgumentException, đi qua HTTP 400 handler hiện hữu; không tăng/giảm giới hạn capacity nghiệp vụ.

P2 refund notification test trước đây dùng reflection và `if (event != null)` khiến có thể không assert gì. Nay dùng event typed trực tiếp, kiểm tra đầy đủ trường và assertion bắt buộc.

## Guard CI

Lệnh build vẫn là `./mvnw -B clean verify`. JaCoCo được gắn vào lifecycle, không loại package/class nào. Workflow giữ mã lỗi Maven qua `tee` bằng explicit bash/pipefail; test tooling thực thi lệnh parse từ YAML và chứng minh exit 23 không biến thành success. Guard chạy `always()` và upload Surefire, endpoint inventory, coverage và log khi lỗi.

Report guard đối chiếu source JUnit với report, yêu cầu suite có execution thành công, bắt missing/empty/failure/error/skip ngoài policy và flaky/rerun failure. Empty report của cả provider optional cũng bị từ chối: regression mới chứng minh checker cũ chấp nhận report tests=0 rồi bị sửa để fail. Các helper assumption legacy vẫn tồn tại, nhưng nếu chúng abort business case thì checker từ chối skip; không thể dùng chúng để đạt CI. Surefire nested reports đôi khi giữ counter của nested suite cuối dù chứa thêm testcase; checker đếm testcase thật và vẫn chặn thiếu testcase so với counter. Source discovery bảo đảm sự hiện diện cấp suite, không chứng minh từng phương thức đã chạy đúng ý nghĩa nghiệp vụ.

Không còn class-wide skip whitelist. Chỉ ba tên case cụ thể cho provider/local model được phép skip; không thêm assumption hoặc retry để giấu failure. Worker inference có job CI riêng, chưa được chạy lại trong lần kiểm tra BE này.

Coverage gate chặn mất report/class, số liệu sai, coverage thấp hơn baseline và mất execution của class bắt buộc. Floor toàn cục được nâng sau kiểm chứng: LINE 82%, BRANCH 62%, METHOD 78%; số LINE/BRANCH covered tối thiểu lần lượt 17.300/7.050 (baseline ban đầu 80%/61%/75%, 16.895/6.915). Các use case vừa lấp khoảng trống có floor riêng, cùng covered-count floor của payment/refund/booking/auth. Đây là hàng rào chống hồi quy dựa trên số liệu thực đo, không phải chứng nhận đầy đủ nghiệp vụ. Có test âm cho report thiếu/hỏng, policy sai, skip/flaky và regression coverage.

## Kiểm chứng và giới hạn

Sau khi source ổn định, full UTC `./mvnw -B clean verify` thành công trong **3 phút 25 giây**: **2.894 execution = 2.891 pass + 3 skip provider/model, 0 failure/error**, đủ **336 report đối chiếu với source** trên **342 file Java test** (6 base/fixture không có report riêng). Report policy xác nhận 35 suite bắt buộc; coverage gate xác nhận đủ 28 production class bắt buộc.

Chạy lại full cùng source với `TZ=Pacific/Kiritimati`, random suite order seed `20261102`: **BUILD SUCCESS trong 3 phút 09 giây**, cùng **2.894 execution, 2.891 pass, 0 failure/error, 3 skip**, 336 report và đủ các guard trên. Coverage random: LINE 82,68%, BRANCH 62,94%, METHOD 78,20%; số liệu so sánh bên dưới dùng lượt UTC thấp hơn. Chênh lệch coverage nhỏ giữa thứ tự chạy được giữ nguyên trong JSON, không diễn giải là assertion failure.

So với baseline đo đầu lượt completeness (2.683 execution = 2.545 pass + 138 skip), đã thêm **211 execution** và kích hoạt lại **135 legacy case**, tăng tổng pass 346. Đếm execution gồm các invocation parameterized, không đồng nhất với số phương thức test.

JaCoCo toàn bộ production code, không exclusion:

- LINE: **80,46% → 82,65%**, covered 16.895 → 17.367.
- BRANCH: **61,44% → 62,93%**, covered 6.915 → 7.088.
- METHOD: **75,71% → 78,20%**, covered 3.114 → 3.218.
- 13 use case từng 0 line coverage nay đạt 100% line/branch; hai adapter nghiệp vụ mới test cũng đạt 100% line. Với class không có nhánh, tỷ lệ branch=100% chỉ là quy ước mẫu số 0.
- CreateServiceOptionUseCase: 44/44 line, 26/26 branch. CreateServiceSlotUseCase: 85/85 line, 35/36 branch; nhánh `units == null` ở helper response không đi tới được từ đường execute luôn tạo list thật, không dùng reflection để chạy riêng nhánh đó.
- RedisConfirmationCardStore: **8/65 → 64/65 line**, **0/32 → 28/32 branch**, 10/10 method. Guard class yêu cầu 98% line/87% branch. Nhánh đầy cache fallback và expiry card fallback 15 phút còn giới hạn kiểm chứng, không làm giả thời gian hoặc tạo dữ liệu chỉ để đạt 100%.

Tooling quality theo lệnh CI: **151 test pass, không skip**, gồm 10 PostgreSQL acceptance test thật; demo package **36 test pass**. Ruff, actionlint, Dependabot guard, bash syntax và `git diff HEAD --check` đều qua. Bốn mutation tài chính đều bị bắt; hai regression production cuối đã chứng minh đỏ trước sửa/xanh sau sửa bằng JUnit thật. Các kết quả kỹ thuật không thay thế kiểm chứng provider, production, Flutter/frontend, worker inference và production-image vulnerability scan.

Máy local Java 21/macOS ARM64/Docker khác Ubuntu x64 của GitHub. Một lượt dài đã trùng macOS sleep 995 giây, có log sleep/wake xác nhận. Một lượt trong thư mục Documents gặp NoSuchFileException khi javac đọc class vừa tạo; chưa xác định nguyên nhân filesystem. Các lượt cuối dùng source snapshot SHA256 giống hệt, chỉ chứa file Git nonignored, dưới /tmp và caffeinate để tránh sleep. Không sửa compiler/CI để che lỗi môi trường máy. Lượt random sau đó ghi 401 error do các context không kết nối được PostgreSQL tại localhost:64899; khi tiếp tục, Docker socket không tồn tại. Đã giữ log/report riêng, mở lại Docker và kiểm chứng lại cùng seed/múi giờ; lượt lỗi không được tính là thành công và không đổi assertion/policy để bỏ qua lỗi hạ tầng. Đây là giới hạn môi trường kiểm chứng được ghi rõ.

Coverage còn thiếu ở các adapter/persistence/mapper, nhánh lỗi provider/job và helper/constructor tương thích; JSON liệt kê cụ thể. `AuthorizationActionUseCase` chưa có caller production ngoài bean registration; payment compatibility wrappers không phải đường controller bình thường. Chưa có chứng cứ đủ để xóa các thành phần này hoặc coi chúng đã được kiểm chứng. Interface default/fallback và DTO synthetic không được thêm test chỉ để làm đẹp số.

Profile test còn exclusion auto-config theo package Boot cũ; một số context có thể thử Rabbit localhost. Không mô tả toàn suite chạy offline. Không chứng minh mọi seed/ngày/tải/concurrency hoặc provider thật đều ổn định, không có full mutation score. [Bốn mutation tài chính có chủ đích](verification/test-mutation-proof-2026-11-02.json) đều bị test bắt (HMAC, capture amount, capture currency, refund currency); chỉ là bằng chứng assertion có tác dụng trên bốn lỗi đó.

Đây là snapshot kiểm chứng local trước khi công bố nhánh. Trạng thái GitHub mới nhất được đối chiếu qua run gắn với head SHA của PR `code/ci-backend-stability-audit-20261101`; snapshot local không tự biến thành bằng chứng remote CI. Chưa triển khai production.

---

## Cập nhật Subplan 1: Test Isolation và Business Coverage (I1–I6) — 02/11/2026

Triển khai hoàn tất nhánh `code/infra-test-isolation-i1`, kế thừa commit audit `4e03b8e` và scan fix `6033c853`. Chi tiết execution evidence lưu tại [subplan-i1-i6-execution-evidence-2026-11-02.json](verification/subplan-i1-i6-execution-evidence-2026-11-02.json).

### Tóm tắt các hạng mục hoàn thành (I1–I5)

1. **I1 (Hạ tầng Testcontainers - Redis & RabbitMQ):** Tạo `AbstractExternalInfrastructureIntegrationTest` và contract test `ExternalInfrastructureOwnershipIntegrationTest`, `FullContextInfrastructureContractTest`. Chuẩn hóa 11 full-context integration suites và `BaseSecurityIntegrationTest` để sở hữu container qua base class, loại bỏ hoàn toàn việc context Spring vô tình kết nối sang Redis/RabbitMQ localhost của máy vận hành.
2. **I2 (PATCH Option Semantics & Ownership):** Bổ sung `UpdateServiceOptionUseCaseTest` (15 testcases, 100% line, 100% branch) và `ServiceOptionUpdateIntegrationTest` (10 testcases) kiểm chứng chặt chẽ: vendor không sở hữu service bị từ chối 403, option sai service trả về 400, validate price <= 0 trả về 400, semantics partial patch bảo toàn các trường omitted, và maxPax bounds.
3. **I3 (Discount Admin/Vendor & Tenant Boundary):** Tạo `VendorDiscountCodeUseCaseTest` (22 testcases), `AdminDiscountCodeUseCaseTest` (21 testcases) và `DiscountCodeManagementIntegrationTest` (5 testcases). Xác thực transaction rollback khi PATCH discount không hợp lệ, tenant boundary giữa các vendor, filter admin (bao gồm boolean false) và ownership của service options.
4. **I4 (FetchWeatherJob Failure, Null & Cache TTL):** Tái hiện lỗi escaping exception khi scheduled job gặp provider lỗi, chuyển `.join()` vào phạm vi xử lý try/catch có kiểm soát trong `FetchWeatherJob.java`. Bổ sung `FetchWeatherJobTest` (8 testcases) chứng minh: TTL chính xác 30 phút từ fetchedAt duy nhất, CountDownLatch kiểm chứng hai request độc lập, dữ liệu thiếu/null không bị biến thành thời tiết an toàn giả mạo, serialization failure và cache save failure không gây loop vô hạn.
5. **I5 (Redis Confirmation Card Fallback Expiry & Cap):** Cung cấp `Clock` injection cho `RedisConfirmationCardStore` và `MutableClock` test utility. Bổ sung test kiểm chứng xác định: pending card hết hạn đúng phút 15, confirmed card hết hạn đúng ngày 1, capacity cap 10.000 từ chối key mới nhưng cho phép overwrite key cũ và tự giải phóng sau expiry. Nâng coverage store lên 100% line (68/68), 97.06% branch (33/34).

### Kết quả kiểm chứng hai lượt Full Verify (I6)

- **Lượt 1 (UTC):** `./mvnw -B clean verify` hoàn thành trong **3 phút 27 giây**.
  - Surefire: **2.980 testcases (2.977 passed, 3 skipped hợp lệ, 0 failures, 0 errors)** trên 344 suites.
  - JaCoCo: LINE **83,43%** (17.533/21.016, floor 82%), BRANCH **63,83%** (7.190/11.265, floor 62%), METHOD **78,69%** (3.239/4.116, floor 78%).
- **Lượt 2 (TZ=Pacific/Kiritimati, random seed 20261102):** Hoàn thành trong **3 phút 29 giây**.
  - Surefire: **2.980 testcases (2.977 passed, 3 skipped, 0 failures, 0 errors)** trên 344 suites.
  - JaCoCo: LINE **83,43%**, BRANCH **63,83%**, METHOD **78,69%** — bảo đảm tính tất định 100% qua múi giờ cực đoan và thứ tự ngẫu nhiên.
- **Guards:**
  - `check_test_reports.py`: Exit 0 (`{"executed": 2977, "allowed_skipped": 3, "required_suites": 35, "source_suites": 344}`).
  - `check_coverage.py`: Exit 0 (`passed: true`, `errors: []`).

### Ratchet Class Coverage Floors

Đã nâng floor trong `.ci/coverage-policy.json` theo kết quả thực tế thấp hơn của hai full run:
- `UpdateServiceOptionUseCase`: LINE 1.0 (38/38), BRANCH 1.0 (20/20)
- `VendorDiscountCodeUseCase`: LINE 1.0 (77/77), BRANCH 0.83 (40/48)
- `AdminDiscountCodeUseCase`: LINE 1.0 (80/80), BRANCH 0.94 (47/50)
- `FetchWeatherJob`: LINE 0.92 (35/38), BRANCH 1.0 (10/10)
- `RedisConfirmationCardStore`: LINE 1.0 (68/68), BRANCH 0.97 (33/34)
- Giữ nguyên các global floor: LINE 82%, BRANCH 62%, METHOD 78%; covered LINE >= 17.300, BRANCH >= 7.050.

### Trace Caller cho Code Compatibility / Dead Code

- `SettlementRepositoryAdapter`: Triển khai `SettlementRepositoryPort`. Giữ lại để đảm bảo tính toàn vẹn kiến trúc cổng - bộ điều hợp (hexagonal port-adapter); không xóa khi chưa có quyết định thay thế kiến trúc settlement.
- `PostgresServiceSearchAdapter`: Triển khai `ServiceSearchPort`. Được gọi bởi `SearchServiceTool` trong module AI cho việc tìm kiếm dịch vụ; giữ nguyên.
- `AuthorizationActionUseCase`: Khai báo bean trong `AuthorizationBeans`. Giữ nguyên để phục vụ các chính sách phân quyền chi tiết của subsystem authorization.
- `RefundPolicyEngine compatibility overloads`: Giữ lại để bảo đảm tính tương thích ngược cho các unit test hiện có mà không làm sai lệch hợp đồng tính toán refund.

