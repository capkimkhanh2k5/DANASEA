# Kiểm tra độ đầy đủ của test BE

## Mục tiêu
Đo coverage và đối chiếu hành vi nghiệp vụ với test; bổ sung những khoảng trống đã xác nhận, không bỏ test/nới assertion hoặc policy để đạt CI.

## Công việc
- [x] Đo coverage JaCoCo trên toàn bộ suite, lưu baseline class/method/branch và outcome Surefire.
- [x] Đối chiếu 135 case ServiceTier legacy với test thực thi; sửa hoặc thay thế scaffolding bằng assertion có ý nghĩa và bỏ whitelist legacy khi đã được thay thế đầy đủ.
- [x] Rà unit/integration test cho payment/refund, inventory/booking, authentication/authorization; bổ sung các nhánh rủi ro còn thiếu.
- [x] Rà guard chống skip/missing suite/false-green; thêm kiểm tra có thể tự động chặn hồi quy độ bao phủ.
- [x] Chạy full verify, kiểm tra report policy, coverage guard và test tooling tương ứng CI; kiểm chứng thứ tự/múi giờ khác khi có thay đổi liên quan.
- [x] Cập nhật báo cáo với coverage đo được, mapping test, phần còn thiếu và ranh giới local/remote.

## Tiêu chí
Không dùng số test hoặc một lần CI xanh để khẳng định đầy đủ. Chỉ đánh dấu hoàn tất khi mọi khoảng trống đã xác nhận trong phạm vi có test thực thi hoặc lý do chính xác được ghi rõ; không có failure hoặc skip nghiệp vụ ngoài policy, và CI giữ nguyên điều kiện fail.

## Kết quả kiểm chứng ngày 02/11/2026

Hai full run UTC và random UTC+14 seed20261102 cùng 2.894 execution: 2.891 pass, 0 failure/error, 3 external-provider/model skip. 336 suite có report trên 342 source Java; 135 legacy case gốc và 2 image API case đều pass từng lượt. Tooling 151/151 và demo 36/36 pass.

Chi tiết và các giới hạn chưa được kiểm chứng: [báo cáo completeness](backend/docs/test-completeness-audit-2026-11-02.md), [inventory/coverage](backend/docs/verification/test-completeness-audit-2026-11-02.json). Hoàn tất lượt kiểm tra và các khoảng trống ưu tiên đã xử lý không có nghĩa toàn bộ nghiệp vụ đạt 100% coverage hoặc production/provider đã được acceptance. Theo dõi trạng thái công bố và remote CI qua PR gắn với nhánh `code/ci-backend-stability-audit-20261101`; các số liệu trong inventory là snapshot local. Chưa triển khai production.
