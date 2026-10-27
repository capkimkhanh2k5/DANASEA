# DANASEA — NGUYÊN TẮC QUẢN LÝ GIT & ĐẨY MÃ NGUỒN (GIT & PUSH PRINCIPLES)

> **Tài liệu quy định bắt buộc cho mọi thành viên và Trợ lý AI (Agent) trong dự án DANASEA.**  
> Ràng buộc trực tiếp với **Quy tắc cứng số 5 (Hard Rule 5)** trong [`AGENTS.md`](../AGENTS.md) và [`docs/WORKING_PRINCIPLES.md`](WORKING_PRINCIPLES.md).

---

## 1. NGUYÊN TẮC CỐT LÕI (CORE PUSH PRINCIPLE)

1. **Tuyệt đối không push khi chưa có lệnh:**  
   AI Agent hoặc lập trình viên **KHÔNG ĐƯỢC TỰ Ý** thực hiện lệnh `git push` lên GitHub Remote (`origin`) dưới bất kỳ hình thức nào khi chưa có sự cho phép / yêu cầu rõ ràng bằng văn bản từ người dùng (User).
2. **Toàn bộ công việc trung gian chỉ lưu tại Local:**  
   Mọi commit độc lập phải được lưu trữ an toàn tại máy cục bộ (Local Git) với thông điệp chuẩn Conventional Commits.
3. **Cấm tuyệt đối `git push --force`:**  
   Không được dùng cờ `-f` hoặc `--force` làm ghi đè lịch sử Git trên remote.

---

## 2. NHỮNG GÌ ĐƯỢC PHÉP PUSH (WHAT WILL BE PUSHED)

Khi người dùng ra lệnh cho phép push, chỉ những nội dung sau mới được đẩy lên remote:

| Thành phần | Yêu cầu kiểm tra bắt buộc |
| :--- | :--- |
| **Nhánh tính năng cụ thể** | Chỉ push lên đúng nhánh làm việc (VD: `git push origin feat/vendor-services-slots-api`). **Tuyệt đối không bao giờ push trực tiếp lên nhánh `main`**. |
| **Mã nguồn Frontend chuẩn hóa** | Toàn bộ mã nguồn mới/sửa trong `frontend/src/` phải vượt qua `npm run build` (Exit code 0, 0 lỗi TypeScript) và 0 lỗi đỏ runtime console. |
| **Commit chuẩn Conventional Commits** | Các commit rõ ràng: `feat(...)`, `fix(...)`, `style(...)`, `refactor(...)`, `docs(...)`. |
| **Tài liệu dự án liên quan** | Các file markdown hướng dẫn, sơ đồ quy trình trong `docs/` nếu có cập nhật tương ứng. |

---

## 3. NHỮNG GÌ TUYỆT ĐỐI KHÔNG ĐƯỢC PUSH (WHAT WILL NOT BE PUSHED)

| STT | Danh mục cấm | Chi tiết & Lý do |
| :---: | :--- | :--- |
| ❌ 1 | **Nhánh chính `main`** | `main` là nhánh được bảo vệ (Protected Branch). Push lên `main` sẽ kích hoạt GitHub Actions tự động build image và chạy quy trình triển khai Server (CI/CD). Mọi code vào `main` bắt buộc phải qua Pull Request (PR) có review. |
| ❌ 2 | **Mã nguồn Backend Java (`backend/src/main/java`)** | Tuân thủ **Hard Rule 1 (Backend Zero Mutation)**: Giữ nguyên vẹn 100% mã nguồn Java Backend. |
| ❌ 3 | **File môi trường & Khóa bí mật (Secrets / Credentials)** | `.env`, `.env.local`, `.product.env`, `private/`, `.deploy.lock`, `.deploy-state`, các file chứa API Key thật, token OAuth, mật khẩu DB, thông tin thẻ/tài khoản ngân hàng/VNPay/PayPal. |
| ❌ 4 | **Thư viện phụ thuộc & Thư mục Build** | `node_modules/`, `frontend/dist/`, `backend/target/`, `__pycache__/`, `*.zip`. |
| ❌ 5 | **Script tạm & File nháp cá nhân** | Các file `.py` / `.bat` / `.sh` tạo tạm để debug/test, file log `cli-proxy/logs/`, file cấu hình cá nhân `.vscode/`, `.DS_Store`. |
| ❌ 6 | **Mã nguồn chưa kiểm thử hoặc đang lỗi** | Code chưa pass `npm run build`, còn lỗi type `any`/`@ts-ignore`, hoặc gây lỗi đỏ runtime trên giao diện. |

---

## 4. BẢNG KIỂM TRA TRƯỚC KHI PUSH (PRE-PUSH QUALITY GATE)

Trước khi thực hiện lệnh push theo yêu cầu của người dùng, bắt buộc phải đối chiếu qua 5 bước kiểm tra:

- [ ] **Bước 1 (Diff Check):** Chạy `git status` và `git diff` để đảm bảo không có file lạ, file rác, file `.env` hay file nhạy cảm bị stage.
- [ ] **Bước 2 (Build Check):** Chạy `cd frontend && npm run build` đạt Exit code 0 (0 TS errors).
- [ ] **Bước 3 (Console Check):** Xác nhận giao diện F12 đạt chuẩn 0 lỗi đỏ runtime.
- [ ] **Bước 4 (Commit Check):** Các commit tuân thủ đúng chuẩn Conventional Commits.
- [ ] **Bước 5 (User Permission Gate):** **Chỉ gõ `git push` khi người dùng đã ra lệnh rõ ràng.**
