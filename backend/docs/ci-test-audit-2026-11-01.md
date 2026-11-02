# Điều tra và ổn định test BE/CI — 01/11/2026

> Báo cáo lịch sử của giai đoạn ổn định CI ngày 01/11. Kết quả mới nhất, thay đổi nghiệp vụ phát hiện qua test, coverage và việc thay thế 135 legacy skip nằm trong [báo cáo kiểm tra độ đầy đủ ngày 02/11](test-completeness-audit-2026-11-02.md). Những con số 138 skip và mô tả chưa đo coverage bên dưới chỉ áp dụng cho giai đoạn lịch sử này.

## Phạm vi và kết luận chính

Nhánh điều tra: `code/ci-backend-stability-audit-20261101`, tách từ `main` mới nhất tại `0248965112fe6ed7b8a4e5fba2c27e26b2db2903`. Bản vá tập trung vào môi trường test, test HTTP thời tiết và cơ chế kiểm chứng CI. Không sửa luật nghiệp vụ hay cấu hình deployment.

[CI đang lỗi trên main](https://github.com/capkimkhanh2k5/DANASEA/actions/runs/41319966440) chạy 2.681 test, có 6 error, 0 assertion failure và 138 skip. Sáu error cùng thuộc `Milestone1EmpiricalPersistenceChallengerTest`: H2 không còn các bảng `conversations`, `messages`, `disputes`.

Merge commit chỉ thay đổi 9 file trong `vendor_mobile/`. Ba commit dưới đây có **cùng Git tree hash của thư mục backend**: `fa137dbc433a1acf54412699bccd018d40f3cc63`:

- `61eb090a`: main trước merge mobile; [CI thành công](https://github.com/capkimkhanh2k5/DANASEA/actions/runs/41317534406).
- `f75973b3`: PR mobile đã cập nhật main; [CI thành công](https://github.com/capkimkhanh2k5/DANASEA/actions/runs/41319389571).
- `02489651`: merge PR mobile; CI thất bại nêu trên.

Workflow không lọc theo đường dẫn: mọi PR vào main và mọi push main đều chạy backend, worker, frontend, quality, ba production-image job và required gate. Thay đổi mobile vì thế vẫn kích hoạt test BE. CI hiện chưa có job Flutter dành cho `mobile/` hoặc `vendor_mobile/`.

## Lỗi mới nhất và bằng chứng tái hiện

Cấu hình test cũ dùng chung `jdbc:h2:mem:testdb` và `spring.jpa.hibernate.ddl-auto=create-drop`. Các Spring context khác nhau có datasource/entity-manager riêng nhưng cùng sở hữu một schema. Việc tạo lại hoặc hủy schema của một context có thể phá dữ liệu/bảng mà context khác đang dùng. Spring cache và thứ tự phát hiện test khiến lỗi phụ thuộc thứ tự chạy.

Hai test mới trong [TestDatabaseIsolationTest](../src/test/java/com/danasea/backend/configs/TestDatabaseIsolationTest.java) dùng cấu hình profile `test` thật, datasource H2 thật và Hibernate thật:

- `startingAnotherContextDoesNotEraseExistingConversations`: chèn một conversation, mở context khác, kiểm tra context khác rỗng và bản ghi ở context đầu vẫn tồn tại. Trên cấu hình cũ: mong đợi 1, thực tế 0.
- `closingAnotherContextDoesNotDropTheSurvivingSchema`: đóng context thứ hai rồi truy vấn bảng của context đầu. Trên cấu hình cũ: bảng không tồn tại.

Đã quan sát cả hai test thất bại trước sửa và thành công sau sửa. Lần chạy toàn bộ code gốc tại máy lại thành công (2.681 test), nên không dùng một lần chạy xanh để phủ nhận lỗi trên GitHub. Reproducer chứng minh trực tiếp lỗi chia sẻ schema; không giả định máy local có đúng thứ tự phát hiện test như runner GitHub.

Sáu phương thức lỗi trên CI gốc:

- `testPaginationQueries`
- `testFindByCustomerIdAndVendorIdAndMasterOrderId`
- `testFindByMasterOrderIdCollisionBehavior`
- `testDisputeSubOrderInQueries`
- `testMessageOrderingAndPolling`
- `testUnreadCountLogic`

## Các thay đổi đã thực hiện

1. **Cô lập H2 theo Spring context.** URL rỗng trong `application-test.properties` chặn URL deployment kế thừa từ `application.yml`; `generate-unique-name=true` để Spring tạo database riêng. Các suite PostgreSQL vẫn cung cấp URL/driver bằng `DynamicPropertySource`. Giữ `create-drop` để kiểm tra vòng đời schema, không đổi thành `update` để che lỗi.
2. **Đóng context theo vòng đời container.** `PasswordResetSecurityIntegrationTest`, `ReviewConcurrencyIntegrationTest`, `AdminTransactionIntegrationTest` có container theo class nhưng context có thể nằm lại trong Spring cache. Thêm `DirtiesContext(AFTER_CLASS)`. Lần chạy baseline có 21 cảnh báo PostgreSQL connection đã đóng; lượt đầy đủ UTC sau sửa có 0.
3. **Bỏ kết quả xanh giả của test thời tiết.** Hai test Open-Meteo trước đây gọi Internet nhưng chỉ assert trong `if (response != null)`. Mất mạng, timeout hoặc provider lỗi vẫn có thể pass. Thay bằng server HTTP loopback với port ngẫu nhiên, bắt buộc response khác null, kiểm tra dữ liệu 384/192 giờ, tọa độ và `forecast_days`. Giữ 6 test MockRestServiceServer và 22 test adversarial. Đây là test transport/payload, không phải chứng nhận provider thật.
4. **Bắt buộc suite chống tái phát.** Thêm test cô lập H2 và suite persistence từng lỗi vào `.ci/test-policy.json`: 11 suite bắt buộc. Surefire ghi tên Java class ở `testsuite.name` nhưng dùng `DisplayName` cho testcase lồng nhau; checker nhận diện cả hai, chỉ đánh dấu suite khi có test chạy thành công. Failure/error, skip ngoài policy, suite rỗng hoặc thiếu vẫn bị từ chối.
5. **Giữ bằng chứng khi CI đỏ.** Checker chạy cả khi Maven lỗi; artifact gồm Surefire, endpoint inventory và log Maven. Bước pipeline `Maven | tee` khai báo `shell: bash` để GitHub bật `pipefail`. Test workflow thực thi lệnh được parse từ YAML bằng wrapper Maven giả, chứng minh mã lỗi 23 vẫn được giữ và log vẫn được ghi; trường hợp thành công cũng được kiểm tra.
6. **Sửa thêm fixture lệch ngày được tìm thấy khi kiểm chứng.** Lượt full random seed `20261101` với `TZ=Pacific/Kiritimati` làm `reviewStartedSlotMustRejectHold` thất bại: máy đã sang ngày 02/11 trong khi tại Việt Nam vẫn là 01/11, nên slot lấy từ `LocalDate.now()` thực ra còn ở tương lai. Production đang dùng giờ Việt Nam đúng. Cả ngày seed và ngày slot đã bắt đầu nay dùng `LocalDate.now(Booking.VIETNAM_ZONE)`; assertion thu hẹp từ `RuntimeException` thành `SlotNotAvailableException`.

## Những lần CI đỏ trước đó không cùng một nguyên nhân

100 lần chạy workflow CI gần nhất tại thời điểm điều tra: 72 thành công, 23 thất bại, 5 bị hủy. Đây là thống kê lần chạy, không phải tỷ lệ flaky; các lần chạy chứa commit/dependency khác nhau.

Đã đọc chi tiết các log đại diện:

- [41129131153](https://github.com/capkimkhanh2k5/DANASEA/actions/runs/41129131153), [41146070808](https://github.com/capkimkhanh2k5/DANASEA/actions/runs/41146070808): ba test search/journey P2 dùng fixture/ngày không khớp. Main đã có sửa trong `6004c8dc`; bản vá này không lặp lại thay đổi đó.
- [41188940167](https://github.com/capkimkhanh2k5/DANASEA/actions/runs/41188940167): tracking API lệch route slug; contract test còn mong 400 cho slug không tồn tại trong khi API trả 404. Main đã cập nhật tracking và contract tương ứng.
- [41292466927](https://github.com/capkimkhanh2k5/DANASEA/actions/runs/41292466927): backend xanh; quality lỗi ở demo database acceptance do schema inventory không khớp schema mới. Đây là guard phát hiện schema drift, không nên bỏ qua bằng nới assertion. Các acceptance test database hiện tại đã được chạy lại.
- [41149505677](https://github.com/capkimkhanh2k5/DANASEA/actions/runs/41149505677): backend xanh, frontend lint đỏ.
- [41319966440](https://github.com/capkimkhanh2k5/DANASEA/actions/runs/41319966440): lỗi H2 mới nhất, được xử lý trong nhánh này. Required gate đỏ vì backend đỏ; CD bị skip theo điều kiện CI.

Không coi mọi dòng `ERROR` trong log là test thất bại: nhiều test cố ý gây lỗi DB, HTTP 429/500, rollback hoặc timeout. Kết luận dựa vào outcome Surefire, exit code Maven và checker.

## Kiểm kê toàn bộ test BE

Baseline có 319 file Java test, 313 Surefire report. Sáu file không có report riêng đều là cơ sở/fixture: `BaseE2ETest`, `BaseFlashSaleIntegrationTest`, `BaseServiceE2ETest`, `BaseSecurityIntegrationTest`, `ReportFinancialTestFixtures`, `WeatherCancellationFixture`. Sau thêm regression: 320 file Java và 314 report.

Kiểm kê tự động đối chiếu từng source với report; không có test class thực thi nào bị thiếu report. Danh sách source, dấu hiệu phụ thuộc thời gian/infrastructure, outcome từng suite và tên từng testcase được lưu trong [inventory JSON](verification/ci-test-audit-2026-11-01.json). Dấu hiệu source là bộ lọc để rà soát, không phải chỉ số coverage hay kết luận mỗi lần dùng thời gian/mocking đều là lỗi.

Baseline có 140 file dùng thời gian hệ thống, 12 file dùng `Thread.sleep` và 4 file có `Clock.fixed`. Đã rà các điểm ảnh hưởng tới fixture ngày, deadline HTTP, test concurrency, migration readiness, vòng đời container và context cache. Không thay toàn bộ bằng clock cố định một cách máy móc: test timeout/transaction cạnh tranh vẫn cần kiểm chứng thời gian và luồng thật.

138 skip giữ nguyên:

- 135 test của bốn suite ServiceTier legacy đã bị vô hiệu hóa từ trước, được thay thế một phần bởi các integration/controller/use-case suite đang thực thi. Không tính 135 test đó là coverage đã chạy, cũng không khẳng định có ánh xạ thay thế đầy đủ từng case legacy.
- 2 test `ProxyLiveSmokeTest` cần bật provider smoke.
- 1 test Quyet Java adapter cần bật local inference smoke; CI có job worker inference riêng.

Các skip ngoài whitelist vẫn làm CI thất bại. H2 regression mới không có skip.

Các nhóm test trong 2.683 case sau sửa:

- Order/payment/refund: 402; booking/inventory: 109; flash sale: 31; check-in: 52. Các suite persistence/concurrency vẫn dùng PostgreSQL/Redis thật; các adapter thanh toán giả lập không chứng minh giao dịch provider thật.
- Authentication/authorization/security infrastructure: 133; account: 12. Bao gồm reset-password, invalidation cache và schema security bắt buộc.
- Service: 457 (135 legacy skip); vendor: 54; admin: 48. CI tracking tiếp tục đối chiếu endpoint thực tế với tài liệu, không tự động bỏ kiểm tra khi route thay đổi.
- AI: 352 (3 provider/local-smoke skip); weather: 232. Tách phản hồi kỹ thuật/mock/fallback khỏi kết quả inference/provider thật.
- Report: 294; settlement: 89; operation/review: 34; dispute: 162; communication: 59; audit: 4. Các test rollback và lỗi cưỡng bức vẫn được thực thi.
- P2 E2E: 86; integration dùng chung: 12; test cấp root/migration/chat: 30; configs: 16; shared: 13; tools: 2.

## Kiểm chứng đã có kết quả

- Baseline code gốc, `./mvnw -B clean verify`: **2.681 test, 0 failure/error, 138 skip**, checker xác nhận 9 suite.
- Reproducer trên cấu hình H2 cũ: **2 test thất bại** đúng lỗi mất dữ liệu/mất bảng.
- Sau sửa, focused H2/persistence/Open-Meteo: **17 test thành công**, không skip.
- Sau sửa, `TZ=UTC ./mvnw -B clean verify`: **2.683 test, 0 failure/error, 138 skip**, checker xác nhận **2.545 test thực thi thành công và 11 suite bắt buộc**; `BUILD SUCCESS`, 3 phút.
- Kiểm chứng bổ sung bằng thứ tự random seed `20261101`, `TZ=Pacific/Kiritimati`: lượt đầu phát hiện **1 failure** ở fixture ngày của booking; test đó chạy riêng đã xanh sau sửa.
- Sau sửa fixture, chạy lại toàn bộ với **cùng seed và UTC+14**: **2.683 test, 0 failure/error, 138 skip**, checker xác nhận 11 suite; `BUILD SUCCESS`, 2 phút 54 giây.
- Trên bản vá cuối cùng, chạy lại nguyên lệnh CI với **UTC**: **2.683 test, 0 failure/error, 138 skip**, checker xác nhận **2.545 test thành công và đủ 11 suite**; `BUILD SUCCESS`, 2 phút 52 giây. Cả hai lượt cuối đều có 0 cảnh báo pool PostgreSQL dùng connection đã đóng.
- Bộ test scripts sau toàn bộ thay đổi: **122 test thành công, không skip** khi `RUN_DEMO_DATABASE_TESTS=1`, gồm 10 test PostgreSQL database acceptance và hai test workflow mới.
- Gói `demo_data_handoff`: **36 test thành công**.
- `actionlint` 1.7.12, Ruff 0.16.10 theo lựa chọn rule của CI, Dependabot config guard và `bash -n` cho ba deployment entrypoint đều qua.

Maven local dùng Java 21.0.10 trên macOS ARM64 với Docker, CI gốc dùng Java 21.0.12.1 trên Ubuntu x64. Các lượt local là bằng chứng hồi quy, không phải một lần chạy GitHub Actions mới trên bản vá.

## Giới hạn của kết luận

- Bản vá đang ở nhánh local; chưa push/merge và chưa có CI từ xa của bản vá.
- Không chạy lại live payment/model/provider acceptance, frontend/mobile build hay production-image vulnerability scan trong lượt sửa test này. Các job ngoài backend của CI mới nhất đã thành công, nhưng đó là kết quả của commit gốc.
- Không đo line/branch coverage hoặc mutation score. Thực thi toàn bộ test hiện có không chứng minh toàn bộ luật nghiệp vụ đã được bao phủ.
- Chưa chứng minh ổn định dưới mọi seed, tải máy, ngày tháng hoặc sự cố Docker. Kiểm thử đảo thứ tự và múi giờ là bổ sung bằng chứng, không bảo đảm tuyệt đối không thể có lỗi mới.
- Profile test còn ba tên auto-configuration exclusion theo package Spring Boot cũ. Đã đối chiếu JAR Boot 4.1.2: Redis nằm trong `org.springframework.boot.data.redis.autoconfigure`, Rabbit trong `org.springframework.boot.amqp.autoconfigure`; không thể dựa vào comment đầu file để kết luận Redis/Rabbit đã bị tắt. Một số context vẫn có thể thử kết nối Rabbit mặc định `localhost:5672` (đã thấy trong log). Không đổi toàn bộ wiring trong bản vá này vì các suite integration cần bean infrastructure thật; việc cô lập đầy đủ các context H2 khỏi mọi dịch vụ ngoài cần một thay đổi riêng. Những kết quả ở đây không được mô tả là toàn bộ suite chạy offline.
- Các cảnh báo API deprecated và một số log i18n fallback vẫn là nợ kỹ thuật; chúng không phải failure/error được Surefire ghi nhận trong những lượt xanh trên.
