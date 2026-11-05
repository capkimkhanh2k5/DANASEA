# Provider Wire Contract Synthetic Fixtures

Tài liệu mô tả dữ liệu mẫu hợp đồng dây (wire contract) phục vụ kiểm thử tích hợp HTTP cho hai cổng thanh toán PayPal và VNPay.

> **CẢNH BÁO QUAN TRỌNG:**
> Toàn bộ các tệp JSON trong thư mục này là **dữ liệu tổng hợp (synthetic contract data)** được xây dựng phục vụ automated testing theo đúng định dạng contract của PayPal REST API v2 và VNPay Merchant WebAPI 2.1.0.
> Tuyệt đối KHÔNG chứa dữ liệu giao dịch thật, không chứa access token, client secret, hash secret của môi trường production hay sandbox của người vận hành.

---

## 1. Định danh mẫu và quy ước

- **Order Identity:** `ORDER-ACCEPT-1`
- **Capture Identity:** `CAPTURE-ACCEPT-1`
- **Refund Identity:** `REFUND-ACCEPT-1`
- **Refund Request ID (Idempotency):** `REFUND-REQ-ACCEPT-1`
- **Tiền tệ & Số tiền:**
  - PayPal: `19.31` USD
  - VNPay: `1000000` VND (chuẩn hóa x100 thành `100000000` trên wire)

---

## 2. Danh mục fixtures

| Tệp | Cổng | Endpoint | Mục đích |
|---|---|---|---|
| `paypal-capture-completed.json` | PayPal | `GET /v2/checkout/orders/{id}` | Trả về thông tin capture đã hoàn thành (`COMPLETED`) với custom_id/reference_id, amount `19.31` USD |
| `paypal-refund-completed.json` | PayPal | `POST /v2/payments/captures/{id}/refund`, `GET /v2/payments/refunds/{id}` | Trả về kết quả hoàn tiền thành công (`COMPLETED`) với link trỏ về capture `CAPTURE-ACCEPT-1` |
| `vnpay-query-completed.json` | VNPay | `POST /merchant_webapi/api/transaction` (`querydr`) | Kết quả tra cứu giao dịch hoàn tiền thành công (`vnp_ResponseCode=00`, `vnp_TransactionStatus=00`, kèm HMAC SHA512 hợp lệ) |
| `vnpay-refund-pending.json` | VNPay | `POST /merchant_webapi/api/transaction` (`refund`) | Kết quả tạo yêu cầu hoàn tiền đang chờ xử lý (`vnp_TransactionStatus=05`, kèm HMAC SHA512 hợp lệ) |

---

## 3. Mapping trường đối soát

### PayPal
- `id` -> PayPal order ID / capture ID / refund ID
- `purchase_units[0].payments.captures[0]` -> Capture record
  - `status` -> `COMPLETED`, `PENDING`, `DECLINED`
  - `amount.value` -> Số tiền (`19.31`)
  - `amount.currency_code` -> Đơn vị tiền tệ (`USD`)
- `links` -> Link quan hệ: `rel="up"` trỏ về capture cha

### VNPay
- `vnp_Command` -> `querydr` hoặc `refund`
- `vnp_ResponseCode` -> Mã kết quả API (`00` thành công)
- `vnp_TransactionStatus` -> Trạng thái giao dịch (`00` thành công, `05` đang chờ xử lý)
- `vnp_Amount` -> Số tiền x100 (`100000000` tương đương 1.000.000 VND)
- `vnp_SecureHash` -> HMAC-SHA512 của chuỗi tham số theo thứ tự alphabet hoặc thứ tự quy định của checksum API
