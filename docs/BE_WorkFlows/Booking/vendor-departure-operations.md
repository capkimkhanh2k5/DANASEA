# Vận hành ca và tài nguyên vendor

## Phạm vi

Tài liệu bàn giao backend cho ba phần: hủy chuyến và bảo vệ thanh toán, lịch lặp với cửa sổ mở bán 90 ngày, bảo trì và phân bổ thiết bị. Giữ ca cụ thể làm đối tượng đặt chỗ và giữ hợp đồng giá, giờ, nhóm và tính độc quyền của đơn đã xác nhận.

## Trước thay đổi

- Vendor tạo từng ca và đổi trạng thái mở/đóng qua API ca. Đóng ca ngừng lượt giữ chỗ mới. Xác nhận thanh toán cũ chỉ kiểm tra sức chứa ở bước ghi tồn.
- Vendor ghi sự cố hoặc đề xuất đổi lịch cho từng phần đơn. Luồng thời tiết có xử lý theo ca riêng.
- Ghép chỗ và thuê nguyên chiếc đã chia sẻ tồn theo từng chiếc trong cùng ca. Định danh cũ của chiếc chỉ có nghĩa trong ca. API cập nhật sức chứa chỉ cho bỏ chiếc chưa có phân bổ.
- Hoàn tiền do lỗi vendor dựa trên số tiền thực trả. Voucher thay thế ưu đãi trước đây chỉ có cho một số trường hợp lỗi hệ thống.

## Các quy tắc cần giữ khi sửa code

1. Đóng bán và hủy vận hành là hai nghiệp vụ. Đóng bán giữ các cam kết cũ. Hủy vận hành khóa chuyến và tạo công việc xử lý những phần đơn bị ảnh hưởng.
2. Trạng thái thu tiền và trạng thái cung cấp dịch vụ độc lập. Một khoản thanh toán được cổng xác nhận phải được ghi nhận kể cả khi không thể giao dịch vụ. Chỉ xác nhận và trừ tồn cho phần có thể phục vụ khi còn giữ chỗ hợp lệ. Với phần không thể phục vụ, yêu cầu hoàn tiền phải gắn với giao dịch gốc và được ghi một lần.
3. Lịch lặp sinh các ca theo giờ Việt Nam. Ngoại lệ phải được giữ lâu đủ để lần chạy sau không vô tình mở lại ngày nghỉ. Không sửa lịch sử ca đã đặt.
4. Bảo trì có thể dời chỉ thành công nếu mọi cam kết giữ được. Hỏng hóc khẩn cấp ưu tiên khóa thiết bị. Đổi chiếc cùng ca chỉ khi tương đương và đủ sức chứa. Đơn thuê riêng vẫn giữ một chiếc độc quyền, đơn ghép chỉ chia nhóm nếu khách cho phép.
5. Hoàn tiền, voucher bồi thường và phí vendor là các bút toán riêng. Voucher bồi thường được tài trợ từ phí vendor, kể cả khách nhận ca thay thế với mức mặc định 10% số tiền thực trả của phần đơn.

## API mới và API đổi hành vi

### Hủy chuyến và đặt chỗ

- `POST /api/vendor/services/{serviceId}/slots/{slotId}/cancel` nhận `{"reason":"Hỏng động cơ"}`, trả `202` cùng `slotId`, `reason` và `cancelledAt`. Chỉ vendor sở hữu dịch vụ mới gọi được. Lặp lại cùng lý do trả lại kết quả gốc. Lý do khác, ca đã tới giờ hoặc đã có người check-in trả `409 DEPARTURE_CANCELLATION_CONFLICT`. Các ca đã check-in cần chuyển vận hành xử lý.
- `GET /api/vendor/services/{serviceId}/slots/{slotId}/cancel` đọc quyết định hủy và `existingRefundsToReview[]`. Mục này cho biết phần đơn đã có yêu cầu hoàn chờ xử lý, thất bại hoặc đã hoàn một phần trước khi chuyến bị hủy. Cần đối soát với giao dịch gốc trước khi quyết định khoản bù tiếp theo. Backend giữ yêu cầu cũ để tránh gửi hoàn trùng.
- `PATCH /api/vendor/services/{id}/slots/{slotId}` vẫn dùng để đóng bán, mở bán hoặc sửa sức chứa. Dùng lệnh hủy riêng cho trạng thái `CANCELLED`. Ca bị hủy không thể mở lại qua PATCH.
- `POST /api/bookings/hold`, `POST /api/orders`, `POST /api/payments/{orderId}/create-intent` và `POST /api/payments/paypal/capture` giữ payload cũ. Checkout mới kiểm tra ca hủy và phân bổ thiết bị bị khóa. Ca chỉ đóng bán vẫn tôn trọng lượt giữ chỗ còn hiệu lực.
- Các callback thanh toán và luồng đối soát giữ payload cũ. Nếu cổng xác nhận đã thu tiền, backend ghi giao dịch và tạo yêu cầu hoàn có liên kết với khoản thu gốc cho phần không thể giao. Với giỏ nhiều dịch vụ còn giữ chỗ hợp lệ, phần không bị ảnh hưởng vẫn xác nhận. Nếu checkout đã hết hạn, hệ thống hoàn toàn bộ số tiền đã thu cho checkout đó vì các lượt giữ còn lại cũng đã mất.
- API lấy QR và `POST /api/vendor/checkin/verify` từ chối ca đã hủy hoặc phân bổ trên thiết bị bị khóa. Lệnh hủy và quét vé khóa bản ghi ca để phân định thứ tự khi cùng xảy ra.
- `PATCH /api/vendor/bookings/{id}/reject`, `POST /api/vendor/sub-orders/{id}/reschedule-proposals`, `POST /api/sub-orders/{id}/reschedule` và `POST /api/sub-orders/{id}/disruption-refund` vẫn là các thao tác ở cấp phần đơn.

### Lịch lặp và ngày nghỉ

- `GET /api/vendor/services/{id}/operating-schedule` đọc cấu hình. `PUT` cùng đường dẫn nhận `startsOn`, `endsOn` tùy chọn, `active` và `rules[]`. Mỗi rule gồm `dayOfWeek`, `startTime`, `endTime`, `inventoryType`, `capacity` hoặc `units[]`. Mỗi unit có `unitNumber`, `capacity` và tùy chọn `equipmentId`. PUT trả cấu hình và số ca tạo/đóng.
- `GET` và `POST /api/vendor/services/{id}/operating-schedule/exceptions` liệt kê hoặc tạo ngoại lệ. POST nhận `dateFrom` và `dateTo` bao gồm hai đầu, `startTime` tùy chọn và `reason`. Bỏ `startTime` để đóng cả ngày hoặc khoảng ngày. `DELETE .../exceptions/{exceptionId}` bỏ ngoại lệ. Ca đã đóng cần mở lại rõ ràng qua PATCH, còn ca chưa từng sinh sẽ được job tạo nếu lịch vẫn hợp lệ.
- Hôm nay đến hết ngày thứ 90 tính theo giờ Việt Nam, tức `today + 89`. Ca tạo thủ công theo API cũ vẫn được hỗ trợ. API công khai `GET /api/services/{serviceId}/slots?from=...&to=...` đọc được cửa sổ mới khi truyền khoảng ngày. Mặc định truy vấn ngắn cũ vẫn được giữ cho client hiện hữu.
- Dừng lịch, xóa rule hoặc rút ngắn mùa đóng bán các ca tự sinh trong tương lai không còn thuộc lịch. Ca đã tồn tại giữ ID, giờ và cấu hình tồn. Nếu đổi sức chứa hoặc thời lượng của rule có cùng giờ khởi hành, rule mới áp dụng cho ca chưa sinh. Thay đổi một ca đã có phải kiểm tra cam kết trên ca đó.

### Thiết bị và bảo trì

- `GET /api/vendor/equipment` đọc danh mục. `POST` nhận `name`, `compatibilityGroup` và `capacity`. Chọn nhóm tương thích theo loại và điều kiện sử dụng đã bán, ví dụ kayak đơn, kayak đôi và cano cùng hạng thuộc các nhóm phù hợp riêng.
- `PUT /api/vendor/equipment/{equipmentId}/slots/{slotId}/units/{unitNumber}` gắn thiết bị vào một unit trống của ca. Ca tự sinh gắn theo `equipmentId` trong rule. Backend từ chối khi cùng thiết bị đã được gán ở ca trùng thời gian, kể cả ca của dịch vụ khác thuộc vendor. Phản hồi unit của API ca vendor có thêm `equipmentId` và `equipmentBlocked`.
- `PUT /api/vendor/equipment/{equipmentId}/maintenance/{maintenanceId}` nhận `start`, `end`, `urgent`, `reason`. Dùng thời gian địa phương Việt Nam theo khoảng `[start,end)`. Client tạo UUID cho `maintenanceId` để gọi lại an toàn. `GET` cùng đường dẫn đọc số ca bị ảnh hưởng, số lần chuyển thiết bị và các mục đặt còn vướng.
- Bảo trì dự kiến trả `409 EQUIPMENT_MAINTENANCE_CONFLICT` với `conflicts[]` gồm ca và các mục đặt vướng, rồi rollback toàn bộ khi không thể giữ đủ cam kết. Sự cố khẩn cấp khóa chiếc, chuyển các phân bổ tương thích có thể chuyển và ghi các phần đơn còn vướng để job mở luồng đổi ca/hoàn. Lượt giữ chỗ trên chiếc bị khóa không thể chốt; bước hết hạn hiện có sẽ giải phóng lượt giữ. Khách đã check-in cần xử lý vận hành. Thiết bị cũ chưa gắn ID chỉ được quản lý trong từng ca.

### Voucher và đối soát

- `GET /api/customers/me/discount-codes` trả thêm `compensationDisruptionId` để nhận biết voucher bồi thường. Voucher cá nhân một lần, giữ được khi khách chấp nhận ca thay thế. Mức tối thiểu của đơn áp dụng voucher bằng giá trị voucher cộng 1 đồng để dùng hết mệnh giá và vẫn có số tiền thanh toán dương. Voucher thay thế ưu đãi do `SYSTEM_FAULT` giữ quy tắc riêng.
- API danh sách, chi tiết và chốt đối soát vendor/admin thêm `vendorFaultCompensationDeductedAmount`. Khi chốt kỳ, hệ thống khấu trừ nghĩa vụ cũ trước, tối đa bằng số dư phải trả dương. Phần chưa thu chuyển kỳ sau. Vendor cung cấp dịch vụ cho lượt dùng voucher sau này vẫn nhận phần chi trả theo cơ chế voucher nền tảng.
- `APP_REFUND_VENDOR_FAULT_COMPENSATION_BPS` mặc định `1000` (10%). Giá trị `0` tắt cấp voucher mới. `APP_REFUND_VENDOR_FAULT_COMPENSATION_VALID_DAYS` mặc định `30`. Khi bật, lấy số tiền thực trả của phần đơn nhân tỷ lệ, làm tròn xuống đồng. Ví dụ cấu hình 1.000 điểm cơ bản tương đương 10%, phần đơn 500.000 đồng tạo voucher và nghĩa vụ vendor cùng 50.000 đồng. Mức 10% thấp hơn mốc 25% giá bán lẻ của GetYourGuide, phù hợp với việc DANASEA vẫn cấp voucher khi khách đổi ca và dùng số thực trả làm cơ sở tính. Cơ chế thu qua đối soát không tự trích tài khoản ngân hàng của vendor.

## Luồng mới và các điểm kiểm tra

1. Hủy chuyến ghi trạng thái và sự kiện bền vững trong một giao dịch. Job xử lý từng phần đơn xác nhận theo cùng luồng sự cố hiện có và xử lý lượt giữ chưa tạo đơn. Ngay sau khi lệnh hủy đã commit, API hoàn tiền có thể nhận diện lỗi vendor dù job chưa chạy. Khi còn thời gian, khách có thể chấp nhận ca thay thế hoặc chọn hoàn. Đến mốc 12 giờ trước giờ chạy, job tạo hoàn toàn bộ cho phần đơn còn vướng. Nếu đã có sự cố hợp lệ, luồng đang mở tiếp tục xử lý theo nguyên nhân đã xác minh.
2. Trong thanh toán, khóa booking rồi khóa ca theo thứ tự ổn định sau khi đã khóa giao dịch và đơn. Phân biệt tiền cổng đã thu với xác nhận sức chứa. Webhook lặp nhận kết quả cũ; yêu cầu hoàn ghi theo payment và phần đơn. Trường hợp thu muộn trên checkout chưa xác nhận là đảo giao dịch chưa thể cung cấp, không tự cấp voucher lỗi vendor.
3. Khi phân bổ lại, giữ giờ, thời lượng và số lượng đã đặt. Gói thuê nguyên chiếc cần chiếc tương thích còn trống toàn bộ. Đơn ghép giữ nhóm nguyên vẹn khi trước đó không cho tách. Nếu không đủ chỗ, bảo trì có thể dời sẽ bị từ chối. Với sự cố khẩn cấp, ưu tiên phương án khả thi và giữ thứ tự xác nhận trong quyết định phần nào có thể phục vụ.
4. Migration V46–V49 bổ sung hủy ca, lịch, thiết bị và bút toán bồi thường. Không sửa lại các migration đã có. Các tích hợp bên ngoài cần chấp nhận trạng thái ca `CANCELLED` và các trường phản hồi mới.

## Đánh giá quyền lợi và các quyết định vận hành

- **Khách hàng** giữ nguyên giá và điều kiện gói khi đổi chiếc cùng ca. Đổi sang giờ/ngày khác cần lựa chọn của khách và tồn thực tế ở ca đích. Phần đơn được hoàn tính trên số tiền thực trả; trạng thái yêu cầu hoàn khác với tiền đã về. Voucher bồi thường bổ sung cho khoản hoàn và vẫn giữ khi khách chấp nhận đổi ca.
- **Vendor** có thể đóng bán trước khi phát sinh cam kết, dự kiến bảo trì mà không tự hủy đơn, và giữ các đơn vẫn có thể phục vụ. Phạt theo phần đơn có lỗi đã xác minh, miễn nguyên nhân thời tiết được xác minh. Việc đặt mức trên số thực trả hạn chế tình trạng voucher lớn hơn giá trị khách thực trả. Cần theo dõi riêng tỷ lệ hủy theo chuyến và số khách ảnh hưởng để tránh đánh đồng một chuyến hủy nhiều khách với nhiều sự cố độc lập.
- **Nền tảng** chịu rủi ro tạm ứng giá trị voucher khi vendor chưa có đủ số dư đối soát, và cần quy trình xử lý khi vendor ngừng hoạt động, khiếu nại nguyên nhân hoặc voucher hết hạn mà khách chưa dùng. Kỳ đối soát giữ nghĩa vụ chưa thu. Cần chốt chính sách xử lý số tiền đã thu tương ứng voucher hết hạn trước khi bật ở diện rộng. Phần đơn có số thực trả bằng 0 không phát sinh voucher theo công thức hiện tại. Cần quyết định riêng việc khôi phục ưu đãi đã dùng khi lỗi thuộc vendor.
- **Phạm vi tương đồng** là lịch lặp, chặn ngày, quản lý thiết bị, bảo vệ đơn hiện có, và phân luồng đổi/hoàn. Các bài tích hợp local kiểm tra dữ liệu và giao dịch ứng dụng. Để xác nhận sẵn sàng production, cần chạy sandbox thật của các cổng thanh toán, kiểm tra thông báo theo thời gian, và kiểm thử vận hành với vendor khi có sự cố sau check-in. Cần đối soát riêng nếu cổng thu tiền hai lần từ hai payment intent khác nhau trên cùng đơn. Việc chống xử lý lặp một callback không tự xử lý trường hợp có hai khoản thu thật. Đối chiếu tài liệu công khai không xác nhận hệ thống nào có cùng cách khóa hay xử lý mọi ca biên.

## Đối chiếu nền tảng

- GetYourGuide phân biệt [đóng bán](https://supply.getyourguide.support/hc/en-us/articles/26538009743645-Managing-Availability-for-your-product) và [hủy đơn theo chuyến](https://supply.getyourguide.support/hc/en-us/articles/13980989354141-Canceling-Bookings-Single-and-Batch-Cancellations-Explained). Họ công bố quy trình xác định khách bị ảnh hưởng, thông báo, lựa chọn đổi lịch hoặc hoàn và theo dõi lý do. Mốc phản hồi của họ là chính sách riêng, độc lập với mốc 12 giờ trước chuyến của DANASEA.
- [Viator](https://operatorresources.viator.com/managing-cancellations-best-practices-for-your-business-and-your-customers/) phân biệt hết chỗ và không vận hành, khuyến khích cung cấp lựa chọn thay thế. [API Viator](https://docs.viator.com/partner-api/technical/#making-a-booking) mô tả kiểm tra giá/tồn, giữ chỗ hết hạn, thanh toán và xác nhận.
- [Bókun](https://docs.bokun.io/en/articles/170-new-resource-management) quản lý tài nguyên vật lý dùng trong nhiều trải nghiệm, giới hạn sức chứa và chặn ngày sử dụng. Tài liệu công khai mô tả gán lại tài nguyên, chưa đủ để kết luận thuật toán tự phân bổ hoặc cách họ khóa giao dịch nội bộ.
- [Hướng dẫn hóa đơn GetYourGuide](https://supply.getyourguide.support/hc/en-us/articles/27692036293021-Understanding-Your-Invoice-Booking-list-in-Excel-Format) mô tả khoản điều chỉnh âm bằng 25% giá bán lẻ khi nhà cung cấp hủy. Đây là tham chiếu về cơ chế thu phí. Các nguồn trên chưa xác lập quy tắc chuyển khoản phí thành voucher cùng giá trị cho khách. DANASEA đang chọn chính sách riêng về voucher. Cửa sổ mở bán 90 ngày của DANASEA cũng độc lập với [chu kỳ đo chất lượng 90 ngày](https://supply.getyourguide.support/hc/en-us/articles/29374073400989-GetYourGuide-Performance-Quality-Standards).

## Kiểm thử khi chỉnh sửa

Chạy `./mvnw -B verify` trong thư mục `backend` với Docker local. Bộ test flash sale đọc fixture từ `DB_Demo/base`. Các bài tích hợp `VendorDepartureCancellationIntegrationTest`, `VendorOperatingScheduleE2ETest`, `EquipmentMaintenanceIntegrationTest` và `VendorFaultCompensationIntegrationTest` lần lượt kiểm tra hủy chuyến và thanh toán đến muộn, lịch 90 ngày, phân bổ thiết bị và mức bồi thường mặc định. Khi đổi một API, cập nhật cùng `backend/docs/danasea-api-tracking.md` để kiểm tra khớp endpoint.
