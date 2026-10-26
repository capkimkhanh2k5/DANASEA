# CLI Proxy cho DANASEA

Backend chỉ gọi LLM và moderation qua CLI Proxy. Proxy dùng các tài khoản OAuth trong `cli-proxy/auths/`; không cấu hình API key trực tiếp của nhà cung cấp trong backend.

`AI_CLIPROXY_API_KEY` là **client key nội bộ** để backend xác thực với proxy. `CLIPROXY_MANAGEMENT_KEY` là khóa quản trị riêng. Hai khóa này không phải API key của model provider.

## Chuẩn bị cấu hình riêng

Từ thư mục gốc repo, tạo `.env` từ `.env.example`, điền các cấu hình dịch vụ, sau đó:

```bash
python3 scripts/production.py prepare
```

Script sinh các khóa còn thiếu và config riêng `private/cli-proxy/config.yaml`. Không ghi giá trị khóa vào Git, không ghi đè cấu hình proxy đã tồn tại, không xoá token OAuth. File `cli-proxy/config.yaml` trong Git chỉ là mẫu với management tắt và danh sách client key rỗng.

Production đã có service `cli-proxy-api` trong `docker-compose.prod.yml`; không chạy thêm Compose độc lập cùng lúc vì trùng tên container và cổng. Trong môi trường local development, khi cần bật proxy cùng hệ thống hoặc cho backend chạy trực tiếp trên host:

```bash
docker compose --profile ai up -d cli-proxy-api
```

## Quản trị qua SSH

Trên Mac:

```bash
ssh -L 8318:localhost:8318 -L 8085:localhost:8085 <user>@<server-tailscale-ip>
```

Mở `http://localhost:8318/management.html` và dùng management key trong `.env` của máy chạy proxy. Đăng nhập tài khoản tại mục OAuth; giữ `cli-proxy/auths/` ngoài Git. Kiểm tra `/v1/models` rồi cấu hình `AI_CLIPROXY_MODEL` và `AI_CLIPROXY_FALLBACK_MODEL` theo model tài khoản thực sự được cấp quyền.

Production backend dùng `http://cli-proxy-api:8317/v1` qua mạng Docker `danasea_ai`. Backend chạy trực tiếp trên host dùng `http://127.0.0.1:8318/v1`. Không dùng địa chỉ localhost của host cho backend trong container.

Các cổng publish chỉ bind `127.0.0.1`. Không Funnel management/OAuth; Funnel chỉ trỏ frontend. Proxy production chạy UID/GID cấu hình trong `CLIPROXY_UID`/`CLIPROXY_GID`; thư mục token, config và static cần cho user đó đọc/ghi. Giữ nguyên token khi chuyển quyền sở hữu, không xoá thư mục auth để sửa lỗi quyền.

Các test HTTP local kiểm tra route, header xác thực, fallback và moderation. Chúng không chứng minh tài khoản OAuth trên server đã được refresh hoặc model thật gọi thành công; cần kiểm tra riêng trước khi deploy.
