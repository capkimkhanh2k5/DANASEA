# Payment Sandbox Acceptance Specification & Operational Guide

## 1. Overview

Tài liệu này xác định giao thức kiểm chứng acceptance cho hai cổng thanh toán (PayPal và VNPay) trên môi trường sandbox theo Task F3 của kế hoạch kiểm thử backend.

Mục tiêu là đảm bảo mọi giao dịch thanh toán và hoàn tiền tuân thủ các quy tắc bất biến về an toàn tài chính:
- Không tạo hiệu ứng kép khi timeout, retry hoặc webhook đồng thời.
- Xác thực đầy đủ chữ ký, số tiền, loại tiền tệ và trạng thái từ cổng thanh toán.
- Ngăn chặn tuyệt đối việc sử dụng cấu hình hoặc tài khoản thật của production trong quá trình kiểm thử sandbox.

## 2. Safety Guards & Preflight Rules

Trước khi thực hiện bất kỳ lệnh mạng (network call) nào, công cụ `scripts/acceptance_payments.py` bắt buộc chạy quy trình preflight:
1. **Môi trường bắt buộc:** Manifest triển khai (`deployment-manifest`) phải khai báo chính xác `"environment": "sandbox"`. Nếu trỏ production hoặc môi trường không xác định, tiến trình dừng ngay lập tức với exit code != 0.
2. **Khóa cứng Host Cổng thanh toán (Sandbox Host Pinning):**
   - PayPal API host: Bắt buộc là `api-m.sandbox.paypal.com`. Mọi host khác (như `api-m.paypal.com`) bị từ chối.
   - VNPay pay/query host: Bắt buộc là `sandbox.vnpayment.vn`. Mọi host khác (như `vnpayment.vn`) bị từ chối.
3. **Tính toàn vẹn của Backend Revision:** Manifest phải chứa mã băm commit SHA hợp lệ và phiên bản session phải đồng nhất với backend revision được kiểm thử.
4. **Bảo mật Credential & Token:**
   - Mã truy cập của khách hàng (customer bearer token) và các khóa bí mật không bao giờ được ghi vào log, file session hay artifact bằng chứng (`sanitize_evidence`).
   - Các trường nhạy cảm tự động được che chắn bằng `[REDACTED]`.

## 3. Workflow & CLI Operations

Công cụ CLI: `scripts/acceptance_payments.py`

### 3.1. Preflight
Kiểm tra cấu hình, token và manifest trước khi thực hiện giao dịch:
```bash
python3 scripts/acceptance_payments.py preflight \
  --provider paypal \
  --base-url "https://sandbox.danasea.com" \
  --token-file "/path/to/customer-token.txt" \
  --session "/path/to/session.json" \
  --evidence-dir "/path/to/evidence" \
  --deployment-manifest "/path/to/sandbox-manifest.json"
```

### 3.2. Tạo Payment Intent (`create-intent`)
Khởi tạo giao dịch thanh toán cho một đơn hàng sandbox cụ thể:
```bash
python3 scripts/acceptance_payments.py create-intent \
  --provider paypal \
  --order-id "6ba7b810-9dad-11d1-80b4-00c04fd430c8" \
  --base-url "https://sandbox.danasea.com" \
  --token-file "/path/to/customer-token.txt" \
  --session "/path/to/session.json" \
  --evidence-dir "/path/to/evidence" \
  --deployment-manifest "/path/to/sandbox-manifest.json"
```
Kết quả ghi nhận `payment_id`, `approval_url` và lưu phiên làm việc vào `session.json`.

### 3.3. Phê duyệt của người mua (Buyer Approval - Bắt buộc thao tác tay)
Người vận hành kiểm thử mở URL phê duyệt (`approval_url`) trên trình duyệt:
- **PayPal Sandbox:** Đăng nhập tài khoản buyer sandbox và xác nhận thanh toán.
- **VNPay Sandbox:** Chọn phương thức thẻ test (Ngân hàng NCB test) và nhập mã OTP `123456`.

> [!IMPORTANT]
> Runner tự động **tuyệt đối không tự giả mạo** bước phê duyệt này hoặc tự tạo chữ ký webhook giả để qua mặt luồng nghiệp vụ.

### 3.4. Capture / Xác nhận thanh toán (`capture`)
Sau khi người mua hoàn tất xác nhận:
```bash
python3 scripts/acceptance_payments.py capture \
  --provider paypal \
  --base-url "https://sandbox.danasea.com" \
  --token-file "/path/to/customer-token.txt" \
  --session "/path/to/session.json" \
  --evidence-dir "/path/to/evidence" \
  --deployment-manifest "/path/to/sandbox-manifest.json"
```

### 3.5. Xác minh trạng thái đơn hàng & thanh toán (`verify`)
Đọc lại trạng thái từ backend để đối chiếu số tiền, loại tiền tệ và trạng thái `SUCCESS`:
```bash
python3 scripts/acceptance_payments.py verify \
  --provider paypal \
  --base-url "https://sandbox.danasea.com" \
  --token-file "/path/to/customer-token.txt" \
  --session "/path/to/session.json" \
  --evidence-dir "/path/to/evidence" \
  --deployment-manifest "/path/to/sandbox-manifest.json"
```

### 3.6. Yêu cầu hoàn tiền (`refund`)
Thực hiện yêu cầu hoàn tiền cho đơn hàng đã thanh toán thành công:
```bash
python3 scripts/acceptance_payments.py refund \
  --provider paypal \
  --base-url "https://sandbox.danasea.com" \
  --token-file "/path/to/customer-token.txt" \
  --session "/path/to/session.json" \
  --evidence-dir "/path/to/evidence" \
  --deployment-manifest "/path/to/sandbox-manifest.json"
```

## 4. Session & Evidence Schema

File `session.json` lưu giữ provenance của phiên kiểm thử (Schema version 1):
```json
{
  "schema_version": 1,
  "environment": "sandbox",
  "backend_revision": "4e03b8e6f3b73a598dc3042d9a3c8c351d50991a",
  "provider": "paypal",
  "run_id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "order_id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "payment_id": "550e8400-e29b-41d4-a716-446655440000",
  "approval_url": "https://www.sandbox.paypal.com/checkoutnow?token=...",
  "refund_id": "e0b8a1c8-2045-4df3-bc42-df1a52dfaa01",
  "operation_id": null,
  "created_at": "2026-11-02T10:15:30Z"
}
```

Mỗi bước sinh ra một file evidence riêng biệt (ví dụ `create-intent-paypal-<run_id>.json`), tách bạch ba nhóm dữ liệu:
1. `request_outcome`: Mã HTTP, thời gian thực thi, tóm tắt phản hồi (đã lọc secret).
2. `provider_verification`: Trạng thái từ nhà cung cấp cổng thanh toán.
3. `persisted_state_verification`: Dữ liệu đã lưu trữ được đối chiếu trong DB backend.

## 5. Trạng thái kiểm chứng hiện tại (Current Status)

> [!WARNING]
> **TRẠNG THÁI HIỆN TẠI: BLOCKED / CHƯA KIỂM CHỨNG TRÊN SANDBOX THỰC TẾ**

**Lý do:**
Việc thực hiện acceptance trực tiếp trên sandbox đòi hỏi các tài nguyên và thông tin đầu vào bên ngoài hệ thống code repository:
1. Tài khoản PayPal Developer & Sandbox Merchant Credentials (Client ID & Secret).
2. Tài khoản PayPal Sandbox Buyer để thực hiện đăng nhập và phê duyệt giao dịch thanh toán.
3. Thông tin cấu hình VNPay Sandbox (Terminal ID / `vnp_TmnCode` và Hash Secret / `vnp_HashSecret`).
4. Môi trường triển khai Sandbox hoạt động với địa chỉ URL công khai (HTTPS) để tiếp nhận webhook/IPN callback từ các cổng thanh toán.
5. Token người dùng kiểm thử (Customer Bearer JWT Token) hợp lệ trên môi trường sandbox.
6. Manifest triển khai sandbox thực tế chứa commit revision đã deploy.

Theo quy định toàn cục của plan:
- Không được dùng mock server hoặc kết quả CI pass trong môi trường dev để tự suy diễn rằng acceptance sandbox đã hoàn thành.
- Các module F1 (Concurrency PostgreSQL) và F2 (Wire contract) đã được kiểm chứng độc lập và đạt kết quả kiểm thử. Module F3 runner đã sẵn sàng vận hành an toàn và sẽ được kích hoạt ngay khi người vận hành cung cấp các prerequisite nói trên.
