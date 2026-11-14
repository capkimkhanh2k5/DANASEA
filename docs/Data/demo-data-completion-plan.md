# Hoàn thiện dữ liệu demo DANASEA

Mục tiêu là mỗi vai trò có thể thực hiện các luồng demo trên cùng một bộ dữ liệu, từ catalog đến đặt chỗ, vận hành và đối soát. Mỗi bản ghi nghiệp vụ phải truy ngược được đến khách, vendor, dịch vụ và giao dịch đúng của nó. Một UUID tồn tại chỉ chứng minh bản ghi đích có mặt. Tính hợp lệ còn phụ thuộc vào quyền sở hữu, trạng thái, thời điểm và số tiền.

## 1. Phạm vi và kết quả rà soát

Rà soát ngày 14/11/2026 theo giờ Việt Nam, đối chiếu PR [#211](https://github.com/capkimkhanh2k5/DANASEA/pull/211), commit `8b166252`, với `main` tại `64db3a16`. Phạm vi gồm toàn bộ JSON dưới `DB_Demo/base` và `DB_Demo/transactions`, bộ nạp, bộ kiểm thử, danh mục schema V58, quy tắc backend và workflow thay dữ liệu trên server.

- **Dữ liệu nền:** 265 dòng JSON, gồm 12 danh mục, 35 tài khoản (4 admin, 16 vendor, 15 khách), 16 vendor, 24 dịch vụ, 33 option, 24 mẫu slot, 25 tài liệu vendor, 72 ảnh và 24 hồ sơ an toàn dịch vụ. Importer dựng thêm 1 slot lịch sử và 73 slot unit, thành **339** dòng nền trước khi sinh lịch 90 ngày.
- **Giao dịch chuẩn:** **344** dòng, gồm 50 booking, 50 booking item, 27 allocation, 50 đơn tổng, 50 đơn con, 50 thanh toán, 6 hoàn tiền, 20 hóa đơn, 11 kỳ đối soát, 19 dòng đối soát và 11 yêu cầu chi trả. Tổng nền và giao dịch chuẩn là **683**.
- **Dữ liệu mở rộng:** **263** dòng trong 7 JSON, gồm 50 review, 6 mã giảm giá, 20 lượt sử dụng mã, 3 flash sale, 4 khiếu nại, 10 hội thoại, 45 tin nhắn, 25 thông báo, 20 wishlist, 20 lượt xem gần đây, 40 QR, 12 thiết bị, 4 lịch bảo trì, 2 gián đoạn, 1 đề xuất đổi ca và 1 bồi thường vendor. Đây là số dòng đầu vào, chưa phải số dòng đã xác nhận tồn tại trong DB sau import. [Importer](../../DB_Demo/install_demo_data.py) · [Fixture giao dịch chuẩn](../../DB_Demo/transactions/demo_transactions.json)
- **Schema V58 trên main:** 81 bảng, 96 khóa ngoại và 5 trigger theo danh mục đã duyệt. 37 bảng có dòng từ gói hiện tại sau khi chuẩn hóa. Mục 4 ghi cách xử lý 44 bảng còn lại.
- **Kiểm thử tại commit PR:** 43/43 test package qua. Bộ test PostgreSQL 17 chạy 11 ca, 2 qua, 3 fail và 6 error. CI dừng ở bước kiểm tra schema của đường `replace`. Bản thử tạm ở lượt chẩn đoán, khi dùng schema V58, đi tiếp đến lỗi rating giữa dữ liệu đã nạp và fixture kỳ vọng. [CI chính](https://github.com/capkimkhanh2k5/DANASEA/actions/runs/43544031085/job/132290930216) · [Demo Data CI](https://github.com/capkimkhanh2k5/DANASEA/actions/runs/43544031075)

## 2. Khôi phục đường import và kiểm tra toàn bộ gói

### P0. Đồng bộ schema và hợp đồng import

- [ ] Khôi phục danh mục schema V1–V58 từ `main` và dùng đúng phiên bản V58 trong importer. Rà lại giá trị mặc định và snapshot cho `vendors.rejection_reason`, `services.operation_type`, các cột check-in của `sub_orders`, sự cố hủy ca và các cột xử lý payout. Kiểm tra lại trên PostgreSQL 17 đã chạy đầy đủ Flyway. Giữ bước kiểm tra danh mục và khóa bảng trước khi xóa. [Danh mục schema](../../DB_Demo/expected_database_schema.json) · [V58](../../backend/src/main/resources/db/migration/V58__checkin_completion_fifo_late_noshow.sql)
- [ ] Nạp, chuẩn hóa và kiểm tra cả 7 JSON mở rộng trước khi ghi. Mỗi bảng cần có danh sách cột, khóa chính hoặc khóa ghép, cột bắt buộc, quan hệ và thứ tự nạp rõ ràng. Hiện nhánh mở rộng có thể bỏ qua file hoặc bảng rỗng và lấy cột từ dòng đầu. Riêng `vendor_fault_compensations` có khóa `disruption_id`, còn `vendor_fault_compensation_recoveries` dùng khóa ghép `(disruption_id, settlement_id)`. [Importer](../../DB_Demo/install_demo_data.py#L739) · [V49](../../backend/src/main/resources/db/migration/V49__vendor_fault_compensation.sql)
- [ ] Đối chiếu cả liên kết do PostgreSQL quản lý lẫn liên kết do ứng dụng quản lý. Kiểm tra UUID/khóa nghiệp vụ duy nhất, cột bắt buộc, enum, khóa đa hình như `related_entity_type + related_entity_id` và các thuộc tính phải khớp qua nhiều bảng. Nếu một quan hệ sai, báo tên file, bảng, khóa dòng và quy tắc vi phạm để có thể sửa tại nguồn.
- [ ] Thay mốc **646** ở runtime và test bằng kỳ vọng lấy từ bộ dữ liệu đã xác minh. Báo riêng số bản ghi chuẩn, mở rộng và lịch/thiết bị sinh thêm, với số kỳ vọng và số tìm thấy theo bảng. Xác minh nội dung và liên kết các dòng theo khóa trước commit, kể cả khi lệnh insert gặp conflict. [Runtime](../../scripts/demo/demo_runtime.py#L320) · [Test PostgreSQL](../../scripts/tests/test_demo_database.py)
- [ ] Tính rating/count/badge, số lượt dùng mã và sequence hội thoại theo quy tắc backend rồi đưa các giá trị đó vào cùng bước xác minh. Giới hạn cập nhật vào bản ghi demo. Hai lần import cùng gói và cùng ngày mốc phải cho cùng khóa, trạng thái và số liệu nghiệp vụ. Mật khẩu băm, thời điểm chạy và nhật ký có thể khác theo thiết kế.
- [ ] Xác định rõ hành vi `sync`, `replace` và `cleanup` với fixture mở rộng. Nếu hỗ trợ `sync/cleanup`, đưa khóa và fingerprint của các dòng đó vào receipt và xóa theo thứ tự phụ thuộc. Với bảng chỉ hỗ trợ `replace`, chặn đường gọi ngoài phạm vi này. Hiện receipt và cleanup đi theo danh sách bảng chuẩn; phần mở rộng có thể insert hoặc update mà receipt chưa theo dõi. Dữ liệu cấu hình được bảo vệ phải giữ nguyên qua replace và restore.
- [ ] Cập nhật `MANIFEST.sha256` khi sửa fixture/importer, rồi chạy cả suite package và suite PostgreSQL. Khi bổ sung demo, thêm kiểm thử trên từng quan hệ mới, gồm trường hợp UUID trỏ tới một bản ghi có thật nhưng thuộc khách, vendor, dịch vụ hoặc đơn khác.

## 3. Sửa và bổ sung theo chuỗi nghiệp vụ

### 3.1. Tài khoản → vendor → hồ sơ → catalog

**Đã đối chiếu:** 24/24 dịch vụ đã publish, đều thuộc vendor được duyệt và có hồ sơ an toàn ở trạng thái `APPROVED`. Có 2 vendor và 2 hồ sơ vendor bị từ chối. Hai vendor bị từ chối thiếu `rejection_reason`. 7/25 hồ sơ vendor có URL trong fixture nhưng thiếu liên kết qua manifest media. Rà soát mới xác minh liên kết trong gói; khả năng tải và nội dung file từ kho lưu trữ còn cần kiểm tra.

- [ ] Xác minh `vendors.user_id → users.id`, role, email/slug duy nhất, trạng thái vendor, actor duyệt đúng quyền và trình tự thời gian. Ghi lý do từ chối vendor vào `vendors.rejection_reason`. Với tài liệu vendor, ghi lý do qua luồng review tạo audit log theo backend. [Xử lý tài liệu vendor](../../backend/src/main/java/com/danasea/backend/modules/admin/application/usecases/ReviewVendorDocumentUseCase.java#L29)
- [ ] Bổ sung hồ sơ xin vai trò vendor đang chờ, gắn với một tài khoản đủ điều kiện theo use case. Dùng tài khoản và hồ sơ riêng cho các kết quả duyệt/từ chối đã xử lý để có thể demo một yêu cầu mới.
- [ ] Bổ sung một dịch vụ `PENDING_REVIEW` có ảnh, option và hồ sơ an toàn đang chờ. Dùng vendor đủ điều kiện và chừa dịch vụ đó khỏi dữ liệu bán. Chỉ bổ sung `DRAFT`, `REJECTED` hoặc `PAUSED` khi có kịch bản quản trị tương ứng. Cập nhật ràng buộc số lượng trong test khi thay đổi fixture.
- [ ] Kiểm tra category/parent, service/vendor, option/service, giá, đơn vị tính, tọa độ, thời lượng, yêu cầu waiver và hồ sơ an toàn theo loại dịch vụ. Tạo ví dụ `FIXED_DEPARTURE`, `DURATION_BASED`, `SHARED` và `PRIVATE` đúng mô hình backend. Với media, xác minh nội dung, quyền dùng, checksum và URL tải được; ghi rõ ảnh/tài liệu tổng hợp và phần minh họa.

Nguồn [Fixture nền](../../DB_Demo/base) · [V57 hồ sơ vai trò](../../backend/src/main/resources/db/migration/V57__create_vendor_role_applications.sql) · [Đặc tả seed dịch vụ](SEED_SERVICES_DATA_SPEC.md)

### 3.2. Lịch → booking → đơn → check-in → review

**Đã đối chiếu:** 50/50 booking chỉ có một item, 50/50 đơn tổng chỉ có một đơn con. 28 QR đánh dấu đã dùng, trong đó 9 đơn vẫn `CONFIRMED`; cả 28 có `used_at` khác `checked_in_at` của đơn tương ứng hoặc đơn thiếu thời điểm check-in. 50/50 review trỏ đến đơn con tồn tại và khớp khách/vendor/dịch vụ, nhưng **31 thuộc đơn chưa hoàn thành** (11 `CONFIRMED`, 10 `PENDING`, 10 `CANCELLED`). Use case tạo review yêu cầu `COMPLETED` và tối đa một review mỗi đơn con.

Ví dụ, review `66666666-6666-6666-6666-000000000010` trỏ tới đơn con `00020000-0000-0000-0000-000000000010`. Đơn tồn tại, có đúng khách và đang `CONFIRMED`. Validator phải từ chối ca này vì thiếu điều kiện hoàn thành đơn.

- [ ] Gắn chuỗi `customer → booking → booking_item → master_order → sub_order`. Buộc cùng khách, đúng booking, đúng option của dịch vụ, đúng vendor của dịch vụ và đúng slot. Kiểm tra tổng tiền, giá snapshot, số lượng và waiver theo từng item/đơn. Thêm một giỏ nhiều dịch vụ hoặc nhiều vendor để demo trạng thái đơn tổng `PARTIALLY_COMPLETED` và hội thoại theo từng vendor.
- [ ] Gắn `service → schedule → slot → slot unit/equipment` và `booking_item → allocation → slot/unit`. Kiểm tra sức chứa, ghế giữ cho `PRIVATE`, số ghế thực chiếm, giải phóng sau hủy và lịch ngoại lệ. Giữ ngày của slot lịch sử. Những ca tương lai để demo cần cùng ngày mốc với booking, QR, giữ chỗ và thông báo.
- [ ] Đồng bộ trạng thái đơn, slot thực tế, `checked_in_at`, người check-in và trạng thái token. Chuẩn bị đơn đã thanh toán sắp đến giờ đi, một ca check-in trễ có lý do, một ca dùng `actual_slot_id` đúng dịch vụ nếu điều phối sang ca khác và một ca `NO_SHOW` đã qua giờ. Sinh QR dùng để quét qua API; dữ liệu lưu sẵn chỉ chứa hash/trạng thái cần cho ca lịch sử.
- [ ] Sửa 31 review sai trạng thái bằng cách loại fixture không hợp lệ hoặc nối review với một giao dịch hoàn thành đúng khách, vendor và dịch vụ. Nội dung review phải phù hợp với dịch vụ, thời điểm sau hoàn thành, và phản hồi vendor phải thuộc đúng vai trò. Chừa một đơn hoàn thành chưa có review, một review chờ vendor phản hồi và một review được gắn cờ để admin kiểm duyệt. Dùng quy tắc rating/badge của backend cho tất cả bản ghi liên quan, kể cả khi số review hiển thị về 0.

Nguồn [Check-in](../../DB_Demo/transactions/checkin.json) · [Review fixture](../../DB_Demo/transactions/reviews.json) · [Quy tắc tạo review](../../backend/src/main/java/com/danasea/backend/modules/operation/application/usecases/CreateReviewUseCase.java) · [Tính rating và badge](../../backend/src/main/java/com/danasea/backend/modules/operation/domain/services/ReviewRatingService.java)

### 3.3. Giảm giá và flash sale → đơn → sử dụng ưu đãi

**Đã đối chiếu:** Cả 20 lượt dùng mã có khách và đơn hợp lệ nhưng `master_orders.discount_code_id` đều trống, và không lượt nào khớp `master_orders.discount_amount`. Flash sale `ENABLED` chỉ bắt đầu 01/12/2026, sau ngày rà soát. Gói chưa có `flash_sale_allocations`.

Ví dụ, redemption `12121212-1212-1212-1212-000000000001` khai báo giảm 65.000 đồng cho đơn `00010000-0000-0000-0000-000000000001`. Đơn đó ghi số tiền giảm bằng 0. Ca này phải fail kiểm tra tổng tiền và đối chiếu mã dù cả hai bản ghi đều tồn tại.

- [ ] Với từng lượt dùng mã, nối `discount_code → discount_redemption → master_order → sub_orders`. Kiểm tra mã/phạm vi, khách đích, vendor, dịch vụ, nhà tài trợ, thời điểm hiệu lực, hạn mức và điều kiện đơn. Đối chiếu tiền giảm giữa đơn tổng, đơn con, tiền khách trả và cơ sở hoa hồng theo backend. Tính lại `used_count` từ các lượt hợp lệ.
- [ ] Thêm một mã platform, một mã vendor và một voucher cá nhân có đơn áp dụng hợp lệ. Thêm một tình huống bị từ chối do sai khách hoặc hết hạn để xác minh chính sách.
- [ ] Chuẩn bị một flash sale có hiệu lực vào thời điểm demo. Nối flash sale với đúng option và dịch vụ, booking item, allocation, giá gốc, subsidy, quota, ngân sách và trạng thái giữ/tiêu thụ/giải phóng. Dùng use case booking để tạo hold và allocation nếu trigger đòi đúng trình tự của hệ thống.

Nguồn [Fixture giảm giá](../../DB_Demo/transactions/discounts.json) · [V39 flash sale](../../backend/src/main/resources/db/migration/V39__platform_funded_flash_sales.sql) · [V40 cập nhật chính sách commission](../../backend/src/main/resources/db/migration/V40__unify_platform_commission_policy.sql)

### 3.4. Thời tiết, đổi ca, thiết bị và bồi thường

**Đã đối chiếu:** 2 gián đoạn `AWAITING_DECISION` gắn với đơn `COMPLETED`, quá hạn ngày 06/11/2026 và thời điểm khởi hành khai báo lệch slot gốc. Đề xuất `PENDING` đã hết hạn và trỏ tới slot của dịch vụ khác. Dòng bồi thường vendor trỏ tới gián đoạn `WEATHER` với đơn, slot và voucher không khớp nguồn nghiệp vụ. Cả 4 lịch bảo trì ghi `affected_slots = 1`; gói chưa có bảng nối `equipment_maintenance_impacts` hoặc `equipment_maintenance_moves`.

- [ ] Tạo riêng ca hủy do thời tiết và sự cố thuộc trách nhiệm vendor. Gắn đúng slot gốc, thời điểm theo giờ Việt Nam, đơn đã thanh toán, lý do, actor và deadline. Chuẩn bị ca đang chờ quyết định còn hạn và một ca đã kết thúc với hoàn tiền hoặc đổi ca theo lựa chọn của khách.
- [ ] Nối đề xuất đổi ca với đúng vendor và slot đích của cùng dịch vụ, kiểm tra còn chỗ và tương thích option. Sau khi chấp nhận, ghi lịch sử đổi ca, slot gốc/slot mới, capacity, allocation và thông báo theo use case. Xác minh cả từ chối hoặc hết hạn khi kịch bản cần.
- [ ] Nối `vendor_fault_compensations → disruption → sub_order → original_slot → vendor`, voucher phải dành cho đúng khách và trỏ ngược về đúng sự cố. Kiểm tra tiền làm căn cứ, tỷ lệ, số dư và thu hồi ở kỳ đối soát nếu ca demo có thu hồi.
- [ ] Gắn thiết bị bảo trì với `service_slot_units.equipment_id` và booking bị ảnh hưởng. Thêm ca bảo trì có thiết bị thay thế tương thích và dòng di chuyển chỗ, rồi đối chiếu bộ đếm ảnh hưởng, số ghế và lịch bảo trì với các dòng chi tiết. Lịch/thiết bị sinh tự động và fixture cài sẵn phải dùng cùng danh tính khi nói về cùng một tài sản.

Nguồn [Fixture gián đoạn](../../DB_Demo/transactions/disruptions.json) · [Fixture bảo trì](../../DB_Demo/transactions/maintenance.json) · [V48 bảo trì](../../backend/src/main/resources/db/migration/V48__vendor_equipment_and_maintenance.sql) · [V49 bồi thường](../../backend/src/main/resources/db/migration/V49__vendor_fault_compensation.sql)

### 3.5. Thanh toán → hoàn tiền → hóa đơn → đối soát → chi trả

**Đã đối chiếu:** 50 payment cho 50 đơn tổng. Có 44 `SUCCESS`, 4 `PENDING`, 2 `FAILED`. Sáu refund đều `PROCESSED`, chỉ có lý do hủy của khách hoặc thời tiết. 11 kỳ đối soát đều `FINALIZED`; payout có 10 `APPROVED` và 1 `REQUESTED` đã có `processed_by`. 20 hóa đơn chưa có `pdf_url`. 25/50 payment dùng MOMO/SEPAY, hai kênh backend hiện không cho chọn khi checkout.

- [ ] Bổ sung một đơn thanh toán lỗi rồi thử lại, một đơn chờ thanh toán còn hạn và một đơn hết hạn. Kiểm tra `attempt_number`, trạng thái attempt, deadline, attempt active/canonical và idempotency theo V52–V53. Cập nhật validator hiện tại để đánh giá attempt lịch sử theo trình tự của cả đơn.
- [ ] Bổ sung hoàn tiền toàn phần, một phần và đang chờ; làm rõ ca thất bại nếu demo retry. Mỗi refund phải trỏ đúng đơn con và payment thuộc cùng đơn tổng, cùng provider; tổng tiền hoàn không vượt tiền đã thu. Các mục được báo cáo phải phân biệt tiền đã hoàn với yêu cầu đang chờ.
- [ ] Nối hóa đơn với đơn đủ điều kiện. Nếu demo tải hóa đơn, tạo/tải tài liệu minh họa bằng cơ chế backend và kiểm tra truy cập đúng khách. Tạo kỳ đối soát với dòng chi tiết đủ điều kiện và payout `REQUESTED`, `PAID`, `REJECTED` có đúng actor/trạng thái. Chỉ thêm tham chiếu ngân hàng minh họa nếu ca demo cần và thể hiện rõ là dữ liệu tổng hợp.
- [ ] Chuẩn bị dữ liệu cùng vendor ở kỳ hiện tại và kỳ trước. Đối chiếu báo cáo với ledger cho tiền thu, tiền hoàn, hoa hồng, tiền phải trả vendor và bồi thường thu hồi. Chọn provider có sẵn trên môi trường lúc demo. Các giá trị lịch sử tổng hợp không được dùng làm bằng chứng callback hoặc giao dịch thật; kiểm thử end-to-end qua sandbox.

Nguồn [Fixture giao dịch](../../DB_Demo/transactions/demo_transactions.json) · [V52 payment attempt](../../backend/src/main/resources/db/migration/V52__extend_payments_for_5plus5_attempts.sql) · [Provider khả dụng](../../backend/src/main/java/com/danasea/backend/modules/order/application/services/PaymentProviderAvailability.java) · [Payment Sandbox](../../.github/workflows/payment-sandbox-acceptance.yml)

### 3.6. Khiếu nại, chat, thông báo, wishlist và lượt xem

**Đã đối chiếu:** Bốn khiếu nại khớp khách và đơn. Hai ca đã xử lý hoàn toàn phần/một phần đều thiếu refund cùng đơn con. Cả 10 hội thoại có vendor không thuộc đơn được gắn, dù người gửi tin nhắn thuộc hội thoại đã seed. 25 thông báo có đơn và đúng khách theo liên kết đang khai báo; mỗi loại thông báo vẫn cần đối chiếu với sự kiện bên dưới.

- [ ] Với khiếu nại, nối `customer → master_order → sub_order → dispute → evidence/response → resolution → refund` theo trạng thái. Bổ sung bản ghi đang chờ admin, một ca vendor có phản hồi, cùng tài liệu demo mở được. Xác minh rằng ca từ chối, ca hoàn và ca hoàn một phần đi theo đúng hành vi backend. Kiểm tra quyền đọc bằng tài khoản đúng và tài khoản khác.
- [ ] Sửa hội thoại theo bộ ba `(customer_id, vendor_id, master_order_id)`. Sau khi đổi liên kết, rà lại tác giả và nội dung từng tin. Kiểm tra sequence duy nhất theo hội thoại, `last_message_sequence` khớp tin cuối, trạng thái đã đọc và quyền truy cập file đính kèm.
- [ ] Chuẩn hóa thông báo theo sự kiện thật của fixture. Thông báo thanh toán thành công phải khớp payment thành công, thông báo check-in phải khớp đơn đủ điều kiện, và thông báo voucher/gián đoạn phải truy được đến nguồn phù hợp với hợp đồng backend. So khớp người nhận, trạng thái đã đọc, thời điểm gửi, khóa idempotency và đích điều hướng.
- [ ] Kiểm tra wishlist theo cặp khách–dịch vụ duy nhất. Lượt xem phải thuộc người dùng hoặc phiên demo hợp lệ và đúng dịch vụ, với thứ tự thời gian hữu ích cho phần gợi ý. Dùng thông báo in-app cho kịch bản seed. Với email, dùng bộ gửi thử nghiệm khi cần diễn tập luồng gửi.

Nguồn [Khiếu nại](../../DB_Demo/transactions/disputes.json) · [Chat và thông báo](../../DB_Demo/transactions/communications.json) · [Quy tắc tạo hội thoại](../../backend/src/main/java/com/danasea/backend/modules/communication/application/usecases/CreateOrGetConversationUseCase.java) · [Xử lý khiếu nại](../../backend/src/main/java/com/danasea/backend/modules/dispute/application/usecases/ResolveDisputeUseCase.java)

### 3.7. Dữ liệu đầu vào cho AI và dashboard

- [ ] Review Insights dùng nhóm review hiển thị hợp lệ, có đủ mẫu tích cực và tiêu cực cho cùng service/vendor trong một khoảng thời gian. Mỗi kết luận và trích dẫn phải truy về review nguồn. Business Insights và Provider Quality đọc dữ liệu giao dịch, lịch, gián đoạn, đánh giá và đối soát đã khớp giữa hai kỳ. Dispute Copilot đọc cùng bộ hồ sơ và bằng chứng mà admin thấy.
- [ ] Tạo một kịch bản AI có thể thao tác qua API với người dùng được gán đúng role/owner. Nếu demo lịch sử, bổ sung hoặc tạo trước một itinerary có bản sửa đổi, hay một yêu cầu hỗ trợ đang chờ, theo API của tính năng tương ứng. Sinh kết quả AI và hàng chờ từ dữ liệu đầu vào, rồi lưu kết quả theo contract hiện có nếu kịch bản cần mở lại lịch sử.
- [ ] Xác minh cả ca có dữ liệu và ca dữ liệu trống/thiếu. Kiểm tra khách/vendor/admin chỉ đọc được dữ liệu trong phạm vi cho phép. Ghi rõ kết quả quan sát trên provider sandbox nếu demo dùng model thật. [CI kiểm chứng AI provider](../../.github/workflows/ai-provider-acceptance.yml)

## 4. Quyết định đối với các bảng chưa có fixture trực tiếp

Danh sách dưới đây bao phủ 44 bảng còn lại của danh mục V58 tại bản rà soát. Một bảng cần seed khi có kịch bản cần trạng thái ban đầu trong bảng đó, hoặc cần tính đầy đủ cho một quan hệ đang seed.

- **Giữ cấu hình và migration (6):** `category_safety_rules`, `commission_rules`, `exchange_rates`, `flyway_schema_history`, `system_configs`, `vendor_commission_policies`. Giữ fingerprint và toàn bộ bản ghi được chúng tham chiếu. Dùng cấu hình của môi trường đích cho rule đang hiệu lực.
- **Sinh từ lịch khi replace (2):** `service_operating_schedules`, `service_operating_schedule_slots`. Kiểm tra lịch 90 ngày, khóa liên kết và các slot có thể đặt. `service_slots`, `service_slot_units` và `vendor_equipment` có cả dữ liệu gói lẫn kết quả sinh thêm.
- **Bổ sung nếu cần trạng thái nghiệp vụ có sẵn (9):** `vendor_role_applications`, `vendor_departure_cancellations`, `sub_order_reschedule_history`, `vendor_fault_compensation_recoveries`, `service_operating_schedule_exceptions`, `equipment_maintenance_impacts`, `equipment_maintenance_moves`, `flash_sale_allocations`, `safety_rule_evaluations`. Nạp cùng khóa cha và các bản ghi ảnh hưởng. Tạo bằng use case khi kết quả phụ thuộc vào chuyển trạng thái hoặc điều kiện môi trường.
- **Tạo khi chạy API, scheduler hoặc luồng thử nghiệm (7):** `flash_sale_hold_requests`, `transactional_email_delivery`, `audit_logs`, `assistant_audit_logs`, `weather_caches`, `password_reset_tokens`, `refresh_tokens`. Các phiên, token và sự kiện gửi được sinh cho môi trường thử nghiệm tương ứng.
- **Dữ liệu AI đầu vào hoặc kết quả do tính năng tạo (19):** `ai_assessment_cases`, `ai_chat_requests`, `ai_confirmation_outcomes`, `ai_conversations`, `ai_messages`, `ai_copilot_conversations`, `ai_copilot_messages`, `ai_customer_preferences`, `ai_customer_support_requests`, `ai_dispute_summaries`, `ai_itineraries`, `ai_itinerary_items`, `ai_itinerary_previews`, `ai_itinerary_proposals`, `ai_itinerary_revisions`, `ai_marine_briefings`, `ai_recommendations`, `ai_recommendation_feedback`, `ai_vendor_insights`. Nếu seed một lịch sử, đưa vào đủ bảng cha/con và trạng thái của tính năng đó. Với các ca còn lại, chạy luồng qua API để tạo kết quả có thể truy nguồn.
- **Bảng đối soát đời trước (1):** `settlement_items`. Fixture hiện dùng `settlement_line_items`. Kiểm tra caller còn dùng bảng cũ trước khi quyết định tạo thêm dữ liệu lịch sử.

## 5. Nghiệm thu trước khi đưa lên server

1. **Tạo danh sách kịch bản để kiểm tra.** Mở rộng danh sách `scenarios` hiện có nếu phù hợp. Mỗi kịch bản ghi vai trò, tài khoản demo, mã đơn hoặc slug ổn định, đối tượng liên quan, thời gian hiệu lực tương đối, bước thao tác và kết quả mong đợi. Ví dụ ca review phải đi được từ khách → booking → đơn con đã hoàn thành → dịch vụ/vendor → review → rating. Ca admin cần có đối tượng đang chờ thực sự để thao tác.
2. **Kiểm tra gói trước import.** Tất cả file khớp manifest, mọi fixture đều được đọc, tất cả khóa và trạng thái được xác minh. Kết quả không còn orphan, liên kết sai quyền/sai đối tượng, lệch tiền, ca cần thao tác hết hạn hoặc URL fixture bắt buộc chưa xác minh. Test lỗi phải dùng cả trường hợp bản ghi cha thiếu và trường hợp bản ghi cha có thật nhưng sai nghiệp vụ.
3. **Kiểm tra trên PostgreSQL 17.** Chạy trên DB trống với Flyway V58 và trên bản sao có dữ liệu cũ. Kiểm tra lần thay đầu, lần lặp lại, dry-run rollback, lỗi giữa chừng, dữ liệu cấu hình và quyền DB sau restore. Thử cùng bộ dữ liệu với hai ngày mốc khác nhau để phát hiện chênh lệch giữa slot, thanh toán, QR, ưu đãi và các deadline.
4. **Kiểm tra qua API trên môi trường demo.** Đăng nhập bằng khách, vendor và admin, chạy các kịch bản ở mục 3 và đối chiếu cả API lẫn DB. Ghi theo từng kịch bản kết quả quan sát, thời điểm, trạng thái và phép đối chiếu số tiền. Gateway/provider có bằng chứng riêng từ sandbox khi nằm trong phạm vi demo.
5. **Triển khai theo workflow đã có.** Sau khi CI xanh, merge và CD được chấp nhận trên đúng SHA của `main`. Kiểm tra bootstrap của server và secrets theo runbook, chạy `inspect`, rồi `replace` trong cửa sổ bảo trì khi đã xác định DB đích. Workflow phải có backup đã xác minh, import thử trên bản sao restore, receipt `COMMITTED`, kiểm tra backend và điểm khôi phục. Xác minh lại các luồng cần trình diễn trên server sau khi thay dữ liệu. [Runbook](demo-data-workflow.md)

Chạy tại gốc repo với Python 3.11, Docker và `bcrypt==4.3.0`. Trên máy ARM, dùng `DOCKER_DEFAULT_PLATFORM=linux/amd64` cho suite PostgreSQL nếu image Flyway không có bản ARM.

```bash
python3 -B -m unittest discover -s DB_Demo/tests -v
RUN_DEMO_DATABASE_TESTS=1 python3 -B -m unittest discover -s scripts/tests -p 'test_demo_database.py' -v
git diff --check
```

Ưu tiên thực hiện mục 2 cùng các lỗi liên kết đã xác nhận ở mục 3 trước khi tăng số dòng. Sau đó hoàn thành kịch bản của từng vai trò theo mục 5. Các mục có checkbox là công việc còn phải triển khai. Kết quả ở mục 1 phản ánh fixture và môi trường PostgreSQL dùng để kiểm tra tại thời điểm rà soát; dữ liệu và cấu hình server còn cần xác minh khi triển khai.
