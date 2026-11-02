# Test Isolation and Business Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Full Spring tests dùng hạ tầng do fixture sở hữu; lấp các khoảng trống option, discount, weather và Redis fallback đã được đo.

**Architecture:** Unit thuần giữ Mockito tại port và không khởi tạo Spring/Docker. Full-context integration dùng common Redis/RabbitMQ theo vòng đời JVM; PostgreSQL/H2 giữ đúng ownership/schema hiện tại. Các thay đổi production giới hạn ở bug có regression hoặc constructor Clock để test thời gian xác định.

**Tech Stack:** Java 21, Spring Boot 4.1.2, JUnit, Testcontainers, PostgreSQL 16, Redis/RabbitMQ, Clock, JaCoCo.

**Spec:** [Master plan và global constraints](2026-11-02-backend-test-completion.md).

## Global Constraints

- Kế thừa toàn bộ constraints của master; floor global tối thiểu 82% LINE /62% BRANCH /78% METHOD; không giảm floor class hiện tại.
- Giữ 135 method legacy + 2 image API case thực thi, không thay DB thật thành mock để làm test ổn định.
- Redis/RabbitMQ image dùng version/digest đã review trong `docker-compose.prod.yml` của base sau merge. Không kết nối broker/cache mặc định của máy; listener tắt mặc định, chỉ bật trong messaging acceptance riêng.
- Từ chối input phải assert không có DB/storage/message effect tương ứng; positive case phải reload/capture dữ liệu thực sự được lưu.

## Review Focus

1. Context cùng sở hữu một container nhưng một suite dừng container quá sớm: I1 giữ lifecycle đúng và replay class order.
2. PATCH giá 0/âm: I2 kiểm chứng Bean Validation HTTP 400; không suy ra unit use case tự thực hiện validation của controller.
3. Discount hết hạn/hết lượt hoặc vendor/service mismatch: I3 assert từ chối và counter/dữ liệu không thay đổi.
4. CompletableFuture lỗi trước khối try của job: I4 chứng minh scheduled invocation kết thúc có kiểm soát và không ghi cache lỗi.
5. Fallback cache đầy nhưng overwrite cùng ID, hoặc hết TTL đúng biên: I5 kiểm tra cap/expiry không dùng sleep 15 phút/static mock.

## I1 — Sở hữu hạ tầng cho full Spring context

**Files:**
- Create: `backend/src/test/java/com/danasea/backend/configs/AbstractExternalInfrastructureIntegrationTest.java`.
- Create: `backend/src/test/java/com/danasea/backend/configs/ExternalInfrastructureOwnershipIntegrationTest.java`.
- Create: `backend/src/test/java/com/danasea/backend/configs/FullContextInfrastructureContractTest.java`.
- Modify: `backend/src/test/java/com/danasea/backend/security/authorization/BaseSecurityIntegrationTest.java`, `backend/src/test/resources/application-test.properties` và các concrete full-context suite được contract test phát hiện.
- Reference: `TestDatabaseIsolationTest`, `SharedSecuritySchemaIntegrationTest`, `CacheWriteVisibilityIntegrationTest`, `PasswordResetSecurityIntegrationTest`.

**Interfaces:** Common abstract base cung cấp `protected static final GenericContainer<?> redis`, `rabbit`; static `@DynamicPropertySource registerExternalInfrastructure(DynamicPropertyRegistry registry)`. `BaseSecurityIntegrationTest` kế thừa base mới và tiếp tục cung cấp `postgres`; giữ tên field `redis` cho subclass hiện tại. Unit/JPA slice không phụ thuộc hai base này.

- [ ] Viết contract test quét các top-level Java test class, load với `Class.forName(name, false, loader)`, dùng Spring merged annotation để tìm concrete `@SpringBootTest` kể cả kế thừa; assert chúng kế thừa common base. Không khởi tạo static fixture chỉ để quét metadata.
- [ ] Viết ownership integration: Redis factory host/port và AMQP factory host/port phải bằng mapped endpoint container; Redis trả PONG; AMQP channel mở được; datasource của H2 context vẫn riêng. Chạy contract/ownership trên trạng thái cũ để ghi bằng chứng thiếu wiring; lỗi Docker không được tính là assertion đỏ mong muốn.
- [ ] Tạo singleton container test-scoped cho Redis/Rabbit; Rabbit dùng credential test và chờ server ready, có startup timeout hữu hạn. Đăng ký host/port/credential động; tắt cả simple/direct listener auto-startup mặc định.
- [ ] Chuyển Redis ownership ở `BaseSecurityIntegrationTest` vào common base, giữ PostgreSQL/Flyway `validate` và schema isolation. Migrate full-context suite từng nhóm; loại duplicate property ownership/Redis container theo class, không để DynamicPropertySource cha/con tranh cùng một host/port.
- [ ] Xóa ba auto-config exclusion theo package Boot cũ sau khi các context đã có fixture thật. Sửa comment profile; không thay bằng exclusion mới làm mất bean `CacheConfig`/`RabbitMQConfig` đang cần. Không sửa wiring production để phục vụ test.
- [ ] Messaging acceptance sau này dùng vhost riêng và listener riêng; không purge queue chung, không gửi email/provider thật trong suite mặc định.
- [ ] Chạy ownership/contract rồi các suite reference; sau đó full verify khi localhost 6379/5672 không có broker/cache riêng, Docker vẫn hoạt động. Assert không có connection đến port mặc định ngoài fixture. Không tự stop container production của người vận hành để tạo điều kiện này; dùng runner sạch.
- [ ] Chạy full random UTC+14 + guards, lưu ownership endpoint và context lifecycle evidence đã redact credential. Commit `test(infra): own Redis and RabbitMQ in integration fixtures`.

## I2 — PATCH option: ownership, validation và persisted payload

**Files:**
- Create: `backend/src/test/java/com/danasea/backend/modules/service/application/usecases/UpdateServiceOptionUseCaseTest.java`.
- Create: `backend/src/test/java/com/danasea/backend/modules/service/ServiceOptionUpdateIntegrationTest.java`.
- Modify khi regression chứng minh bug: `backend/src/main/java/com/danasea/backend/modules/service/application/usecases/UpdateServiceOptionUseCase.java`.
- Reference: `UpdateServiceOptionRequest`, `VendorServiceOptionController`, `ServiceOption`, `ServiceOptionsAndInventoryAllocationIntegrationTest`.

**Interface:** Giữ `execute(UUID userId, UUID serviceId, UUID optionId, UpdateServiceOptionRequest request): ServiceOptionResponse`. Mock `ServiceRepositoryPort`, `ServiceOptionRepositoryPort`, `VendorPort`; dùng domain thật.

- [ ] Thêm `patchUpdatesOnlySpecifiedFieldsAndReturnsSavedPayload`: name `"  Kayak private  "` thành `"Kayak private"`, price `250000`, benefits trim, status và private max pax được lưu; ID/service/type/pricingUnit/createdAt không bị thay. Response lấy từ entity repository trả về, gồm updatedAt.
- [ ] Thêm null request, missing vendor/service/option, foreign vendor, option thuộc service khác; assert exception cụ thể và `verify(options, never()).save(any())`.
- [ ] Thêm partial patch: null giữ trường cũ, blank name bị bỏ qua theo contract hiện tại, empty benefits được trim; private max pax 0/âm bị từ chối, 1 được chấp nhận; shared option vẫn đi nhánh hợp lệ.
- [ ] Chạy focused unit để xem nhánh nào đỏ thực sự; các positive case hiện có thể pass. Không sửa code nếu test đã xác nhận behavior đúng.
- [ ] Integration dùng JWT/MockMvc + PostgreSQL thật: owner patch rồi flush/clear/reload; foreign vendor/service bị từ chối; giá 0 và âm nhận 400 từ Bean Validation và DB không đổi. Dùng mapping chính xác từ controller, không tạo API mới cho test.
- [ ] Sửa tối thiểu lỗi đã chứng minh; chạy `./mvnw -B -Dtest=UpdateServiceOptionUseCaseTest,ServiceOptionUpdateIntegrationTest,CreateServiceOptionUseCaseTest,ServiceOptionsAndInventoryAllocationIntegrationTest test`.
- [ ] Đo JaCoCo: execute/toResponse được chạy; mọi ownership/validation branch reachable có assertion. Sau đo mới thêm floor class bằng coverage thực đo, không exclude helper để đạt số. Commit `test(service): cover option update ownership and patch semantics`.

## I3 — Discount admin/vendor và tenant boundary

**Files:**
- Create: `backend/src/test/java/com/danasea/backend/modules/order/application/usecases/VendorDiscountCodeUseCaseTest.java`, `AdminDiscountCodeUseCaseTest.java`, `DiscountCodeManagementIntegrationTest.java`.
- Modify nếu có bug: hai use case cùng đường dẫn trong `backend/src/main/java/`.
- Reference: `DiscountRefundRegressionTest`, `DiscountConcurrencyIntegrationTest`, `CreateDiscountCodeRequest`, `UpdateDiscountCodeRequest`, `DiscountCodeResponse`.

**Interfaces:** Giữ các method `getVendorDiscountCodes/getVendorDiscountCodeById/createVendorDiscountCode/updateVendorDiscountCode` và `getDiscountCodes/getDiscountCodeById/createDiscountCode/updateDiscountCode`; kiểu request/response/page lấy trực tiếp từ source hiện tại.

- [ ] Tạo fixture real entity/mapping và repository mocks; test pagination/filter, empty page và response đầy đủ, không chỉ status/non-null.
- [ ] Pin case/expected theo `DiscountCodeRules`: `"  kayak10  "` chuẩn hóa `KAYAK10`; duplicate → CODE_ALREADY_EXISTS; ID thiếu → DISCOUNT_NOT_FOUND; service/vendor mismatch → DISCOUNT_SERVICE_INVALID; vendor edit sai owner/scope/sponsor → AccessDeniedException; admin vendor scope thiếu vendor ID → VENDOR_ID_REQUIRED. Mỗi lỗi assert không save.
- [ ] Percentage 100 được nhận, 101 → DISCOUNT_PERCENTAGE_INVALID; giá trị 0/âm → INVALID_DISCOUNT; validTo bằng/trước validFrom → DISCOUNT_DATE_RANGE_INVALID. minOrderAmount=0 hợp lệ, âm không hợp lệ; cap/usage limit=0 không hợp lệ. Replacement voucher vẫn personal, platform FIXED và single-use; không chuyển maxUses thành 2.
- [ ] Kiểm tra update sử dụng `findByIdForUpdate`; usedCount=3, maxUses=2 → MAX_USES_LESS_THAN_USED; maxUses=3 được nhận và usedCount vẫn 3. Integration dùng transaction thật để chứng minh PATCH invalid rollback cả các field đã mutate trước exception; `verify(no save)` trong unit không thay thế bằng chứng này.
- [ ] Chạy focused tests trước sửa; sửa tối thiểu phần đỏ. Chạy thêm `DiscountRefundRegressionTest,DiscountConcurrencyIntegrationTest` để giữ bằng chứng usage/refund bằng DB thật.
- [ ] Nâng class floor từ baseline vendor 42/77 line, 13/48 branch và admin 49/80 line, 16/50 branch theo kết quả mới. Commit `test(order): cover discount scope and update invariants`.

## I4 — FetchWeatherJob: future failure, null và cache TTL

**Files:** Create `backend/src/test/java/com/danasea/backend/modules/weather/FetchWeatherJobTest.java`; modify `backend/src/main/java/com/danasea/backend/modules/weather/infrastructure/jobs/FetchWeatherJob.java` khi test đỏ chứng minh lỗi.

**Interface:** Giữ `void fetchAndCacheWeatherInfo()`, `WeatherProviderPort` và `WeatherCacheRepositoryPort`. ObjectMapper thật; chỉ mock provider/cache I/O.

- [ ] Success: cả hai provider nhận latitude `16.089035780716284`, longitude `108.24959555394304`; captured row có location `MAN_THAI_BEACH`, numeric weather/marine đúng và rawPayload deserialize ra cùng dữ liệu. TTL chính xác 30 phút từ fetchedAt.
- [ ] Dùng latch để chứng minh hai request được khởi chạy độc lập; release trong finally và đọc kết quả future, không dùng Thread.sleep.
- [ ] Null weather/marine/numeric field phải giữ null trong payload/metric tương ứng; không tự tạo 0 hoặc dữ liệu thời tiết an toàn.
- [ ] `providerFailureDoesNotPersistPartialCacheOrEscapeScheduledInvocation`: một provider throw; assert không save và invocation kết thúc có kiểm soát. Source hiện gọi `allOf(...).join()` trước try, vì vậy test này phải tái hiện lỗi trước sửa.
- [ ] JSON serialization failure/cache save failure được xử lý có kiểm soát; assert số lần save và không có retry không giới hạn. Nếu sửa, đặt join trong phạm vi xử lý lỗi; dùng một fetchedAt để tính expiresAt.
- [ ] Chạy `FetchWeatherJobTest,SlotWeatherMonitoringJobTest,OpenMeteoApiClientTest` và suite adapter thời tiết hiện có. Đo và thêm floor cho job; baseline đang 1/37 line, 0/10 branch. Commit `fix(weather): handle scheduled fetch failures with regression tests`.

## I5 — Redis fallback hết TTL và đầy cache

**Files:** Modify `backend/src/main/java/com/danasea/backend/modules/ai/infrastructure/redis/RedisConfirmationCardStore.java`, `backend/src/test/java/com/danasea/backend/modules/ai/infrastructure/redis/RedisConfirmationCardStoreTest.java`; create `backend/src/test/java/com/danasea/backend/modules/ai/support/MutableClock.java`.

**Interfaces:** Giữ public/autowired constructor `(StringRedisTemplate, ObjectMapper)` và thêm package-visible constructor `(StringRedisTemplate, ObjectMapper, Clock)`. Constructor production dùng `Clock.systemUTC()`. Test clock triển khai `Clock`, có `advance(Duration)`; không dùng static mocking hay reflection private map.

- [ ] Pending card đọc được trước 15 phút, hết hạn đúng phút 15; confirmed outcome sống đến trước 1 ngày và hết hạn đúng 1 ngày. Test qua API store với null Redis template.
- [ ] Đổ 10.000 ID vào fallback trong **một case boundary**, overwrite ID cũ vẫn được; ID 10.001 bị từ chối và không xuất hiện khi đọc. Sau advance hết hạn, ID mới được nhận; không kiểm tra cap bằng sửa private state.
- [ ] Lease hết hạn và stale-token fencing giữ behavior cũ khi Clock được inject; giữ nguyên 7 test Redis thật và 11 case unit gốc, thêm assertion cần thiết thay vì bỏ case.
- [ ] Chạy focused trước sửa, thêm Clock overload và thay các `Instant.now()` của store bằng `Instant.now(clock)`; không đổi TTL 15 phút/1 ngày, cap 10.000 hoặc exception contract.
- [ ] Chạy cả `RedisConfirmationCardStoreTest,RedisConfirmationCardStoreIntegrationTest,DurableAiConfirmationIntegrationTest,AiToolContractHardeningTest`. Floor store hiện 98% line/87% branch chỉ được giữ/nâng. Commit `test(ai): make confirmation fallback expiry deterministic`.

## I6 — Chốt baseline và danh mục các nhánh còn thiếu

**Files:** Modify `.ci/coverage-policy.json`; create evidence mới trong `backend/docs/verification/` và cập nhật phần kết quả mới trong `backend/docs/test-completeness-audit-2026-11-02.md` mà không ghi đè số liệu lịch sử.

- [ ] Full verify, source/report guard và coverage guard; full UTC+14/random seed20261102 sau I1/I4/I5 vì có lifecycle/time/concurrency thay đổi.
- [ ] Kiểm kê lại suite/source và testcase; không suy ra số testcase bằng số source file. Giải thích counter nested Surefire nếu khác testcase thực tế.
- [ ] Trace caller của code 0% compatibility/dead code trước khi quyết định thêm test hoặc xóa; ghi file/caller và lý do. Không dùng reflection để ép chạy constructor/nhánh unreachable.
- [ ] Nâng floor cho class vừa cải thiện theo kết quả thấp hơn của hai full run; giữ mọi global floor của master. Commit `test(coverage): ratchet verified business coverage baselines`.
