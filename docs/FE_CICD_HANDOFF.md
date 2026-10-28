# Bàn giao lỗi Frontend cho Role FE

## Phạm vi và trạng thái hiện tại

- Theo yêu cầu người dùng ngày 2026-10-28, toàn bộ thay đổi trong `frontend/` của nhánh CI/CD hardening đã được gỡ khỏi trạng thái hiện tại. Mã FE trùng với baseline main `d0bda178ec4624aa2d5167a18c240258bdf9cfcd`, gồm cả `Dockerfile` và `package.json`.
- Nhánh làm việc: `codex/production-cicd-hardening`. Các commit FE từng sửa vẫn còn trong lịch sử Git; bản bàn giao không áp dụng chúng.
- Role FE phụ trách sửa mã FE và image FE. Giữ nguyên những tính năng admin/vendor/auth/locale đã có trên main; không sửa Java backend hoặc hạ các gate CI để làm xanh pipeline.
- Đo lại trên FE đã khôi phục: **16 lỗi ESLint, 35 cảnh báo, 19 file có chẩn đoán**. Lint strict exit 1. Đây là chẩn đoán tĩnh, không đồng nghĩa mọi mục đã được chứng minh thành lỗi runtime.
- `npm run build`: exit 0; TypeScript/Vite build được. Có cảnh báo bundle >500 kB (JS hiện khoảng 1,297.49 kB, gzip 303.94 kB). Cảnh báo kích thước không được tính vào 35 cảnh báo ESLint.
- `npm audit --audit-level=high`: exit 0, 0 vulnerabilities. Kết quả npm không thay thế quét package OS trong image.
- CI vẫn dùng `npm run lint -- --max-warnings 0` tại `.github/workflows/ci.yml`; không sửa package.json chỉ để mang cờ lint vào FE. Pipeline sẽ tiếp tục bị chặn đến khi FE sửa đạt.

## Lệnh tái hiện

Chạy từ thư mục frontend, sau khi cài dependencies theo lockfile:

```bash
npm ci
npm run lint -- --max-warnings 0
npx eslint . --max-warnings 0 --format json > /tmp/danasea-fe-eslint.json
npm run build
npm audit --audit-level=high
```

Dòng/cột bên dưới khớp baseline nêu trên. Sau mỗi lần sửa, chạy lại toàn bộ lint vì ESLint có thể hiện thêm vị trí trước đó bị che bởi chẩn đoán đầu tiên.

## Danh sách đầy đủ theo file

### `frontend/src/components/common/PageLoader.tsx`

- Hướng xử lý: Tách trạng thái ban đầu/reset khỏi effect đồng bộ; giữ animation theo requestAnimationFrame và dọn cả RAF/timer khi kết thúc hoặc unmount.
- **ERROR 23:7** — `react-hooks/set-state-in-effect`: Error: Calling setState synchronously within an effect can trigger cascading renders
  Vị trí hiện tại: `` setProgress(0); ``.

### `frontend/src/components/customer/AuthCinematicBackdrop.tsx`

- Hướng xử lý: Xóa state/setter progressKey đã không còn được render sau khi bỏ progress bar; giữ carousel/parallax hiện có.
- **WARN 51:10** — `@typescript-eslint/no-unused-vars`: 'progressKey' is assigned a value but never used. Allowed unused vars must match /^_/u.

### `frontend/src/layouts/CustomerLayout.tsx`

- Hướng xử lý: Đồng bộ toast theo sự kiện/store hoặc mô hình state phù hợp; giữ thời gian vào/ra, toast mới, đóng thủ công và cleanup timer.
- **ERROR 25:7** — `react-hooks/set-state-in-effect`: Error: Calling setState synchronously within an effect can trigger cascading renders
  Vị trí hiện tại: `` setIsExiting(false); ``.

### `frontend/src/mockData.ts`

- Hướng xử lý: Khai báo kiểu dữ liệu dispute rõ ràng và nullability đúng dữ liệu; tránh thay any bằng kiểu rộng che lỗi.
- **WARN 208:29** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.

### `frontend/src/pages/customer/Auth/ForgotPassword.tsx`

- Hướng xử lý: Để catch là unknown, thu hẹp kiểu lỗi HTTP trước khi đọc response/message; giữ nguyên thông báo backend và luồng OTP/reset.
- **WARN 53:19** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.
- **WARN 77:19** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.
- **WARN 140:19** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.

### `frontend/src/pages/customer/Auth/index.tsx`

- Hướng xử lý: Thu hẹp kiểu lỗi ở các catch; bảo toàn đăng nhập, đăng ký, xác thực email, đổi mật khẩu và Google Sign-In.
- **WARN 140:19** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.
- **WARN 238:19** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.
- **WARN 335:19** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.
- **WARN 372:19** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.
- **WARN 431:19** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.

### `frontend/src/pages/customer/Checkout/Checkout.tsx`

- Hướng xử lý: Rà soát state contact/booking hold, giá trị ngẫu nhiên và điều hướng gateway. Giữ loading khi gọi lại, user-input người dùng đã sửa và cơ chế chống giữ chỗ lặp. Không tạo orderCode ngẫu nhiên trong render. Đối chiếu nguồn chẩn đoán immutability tại điều hướng và dùng API điều hướng phù hợp.
- **ERROR 59:26** — `react-hooks/set-state-in-effect`: Error: Calling setState synchronously within an effect can trigger cascading renders
  Vị trí hiện tại: `` if (user.fullName) setFullName(user.fullName); ``.
- **ERROR 84:7** — `react-hooks/set-state-in-effect`: Error: Calling setState synchronously within an effect can trigger cascading renders
  Vị trí hiện tại: `` setIsHolding(true); ``.
- **ERROR 301:65** — `react-hooks/purity`: Error: Cannot call impure function during render
  Vị trí hiện tại: `` orderCode: `DNS-${sessionCode || Math.floor(10000 + Math.random() * 90000)}`, ``.
- **ERROR 328:11** — `react-hooks/immutability`: Error: This value cannot be modified
  Vị trí hiện tại: `` window.location.href = gatewayUrl; ``.

### `frontend/src/pages/customer/Checkout/PaymentResult.tsx`

- Hướng xử lý: Dùng khởi tạo snapshot hoặc mô hình state phù hợp thay setState đồng bộ trong effect chỉ để đọc sessionStorage; giữ xử lý JSON lỗi, success/failure/cancel và giỏ hàng.
- **ERROR 75:9** — `react-hooks/set-state-in-effect`: Error: Calling setState synchronously within an effect can trigger cascading renders
  Vị trí hiện tại: `` setOrder(parsed); ``.

### `frontend/src/pages/customer/ExperienceDetail/index.tsx`

- Hướng xử lý: Xử lý state khi đổi route/id, dependency memo và reset carousel. Giữ subscription language để API refetch khi đổi locale; không để language nằm ngoài scope khi tách component. Rà soát waiver đang không dùng và ý nghĩa nghiệp vụ trước khi xóa.
- **ERROR 50:7** — `react-hooks/set-state-in-effect`: Error: Calling setState synchronously within an effect can trigger cascading renders
  Vị trí hiện tại: `` setLoadError(true); ``.
- **WARN 264:6** — `react-hooks/exhaustive-deps`: React Hook useMemo has missing dependencies: 'activeSlot' and 'service'. Either include them or remove the dependency array.
- **WARN 287:9** — `@typescript-eslint/no-unused-vars`: 'localizedWaiverContent' is assigned a value but never used. Allowed unused vars must match /^_/u.
- **ERROR 295:39** — `react-hooks/preserve-manual-memoization`: Compilation Skipped: Existing memoization could not be preserved
  Vị trí hiện tại: `` const allImages: string[] = useMemo(() => { ``.
- **ERROR 310:5** — `react-hooks/set-state-in-effect`: Error: Calling setState synchronously within an effect can trigger cascading renders
  Vị trí hiện tại: `` setTrackIndex(allImages.length > 1 ? 1 : 0); ``.

### `frontend/src/pages/customer/Home/index.tsx`

- Hướng xử lý: Khai báo kiểu ChatMessage/BeachInfo/metrics thay any; ổn định displayMessages để tránh effect chạy mọi render; loại bỏ state/helper thực sự không dùng.
- **WARN 30:37** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.
- **WARN 81:42** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.
- **WARN 113:25** — `@typescript-eslint/no-unused-vars`: 'setActiveAIBeach' is assigned a value but never used. Allowed unused vars must match /^_/u.
- **WARN 117:50** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.
- **WARN 266:9** — `react-hooks/exhaustive-deps`: The 'displayMessages' array makes the dependencies of useEffect Hook (at line 273) change on every render. To fix this, wrap the initialization of 'displayMessages' in its own useMemo() Hook.
- **WARN 344:9** — `@typescript-eslint/no-unused-vars`: 'scrollToSection' is assigned a value but never used. Allowed unused vars must match /^_/u.

### `frontend/src/pages/customer/Inbox/Inbox.tsx`

- Hướng xử lý: Rà soát biến activeChat không được đọc; giữ setter nếu thao tác chọn chat vẫn dùng.
- **WARN 5:10** — `@typescript-eslint/no-unused-vars`: 'activeChat' is assigned a value but never used. Allowed unused vars must match /^_/u.

### `frontend/src/pages/customer/Profile/Orders.tsx`

- Hướng xử lý: Rà soát fetchOrders và state loading khi mount, đổi trang, retry, đổi auth. Dependency serviceDetails có thể gây fetch lại theo kết quả vừa tải; cần kiểm chứng và tránh vòng lặp. Bỏ import RefundRequest không dùng.
- **WARN 12:3** — `@typescript-eslint/no-unused-vars`: 'RefundRequest' is defined but never used. Allowed unused vars must match /^_/u.
- **ERROR 134:7** — `react-hooks/set-state-in-effect`: Error: Calling setState synchronously within an effect can trigger cascading renders
  Vị trí hiện tại: `` fetchOrders(page); ``.

### `frontend/src/pages/customer/Search/index.tsx`

- Hướng xử lý: Tách đồng bộ URL category khỏi effect setState không cần thiết; giữ lọc/sort và refetch theo language. Xóa import/biến dư, định kiểu union sort thay any.
- **WARN 1:47** — `@typescript-eslint/no-unused-vars`: 'useRef' is defined but never used. Allowed unused vars must match /^_/u.
- **WARN 1:55** — `@typescript-eslint/no-unused-vars`: 'useCallback' is defined but never used. Allowed unused vars must match /^_/u.
- **WARN 9:15** — `@typescript-eslint/no-unused-vars`: 'ServiceSummaryDto' is defined but never used. Allowed unused vars must match /^_/u.
- **WARN 79:21** — `@typescript-eslint/no-unused-vars`: 't' is assigned a value but never used. Allowed unused vars must match /^_/u.
- **ERROR 127:5** — `react-hooks/set-state-in-effect`: Error: Calling setState synchronously within an effect can trigger cascading renders
  Vị trí hiện tại: `` setSelectedCategorySlug(categoryParam); ``.
- **WARN 182:77** — `@typescript-eslint/no-unused-vars`: 'idx' is defined but never used. Allowed unused args must match /^_/u.
- **WARN 216:77** — `@typescript-eslint/no-unused-vars`: 'idx' is defined but never used. Allowed unused args must match /^_/u.
- **WARN 456:66** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.

### `frontend/src/pages/vendor/Orders/VendorOrders.tsx`

- Hướng xử lý: Loại bỏ biến tổng hợp không dùng và khai báo kiểu filter/sort thay any; giữ dữ liệu doanh thu đang hiển thị.
- **WARN 610:9** — `@typescript-eslint/no-unused-vars`: 'totalNetRevenue' is assigned a value but never used. Allowed unused vars must match /^_/u.
- **WARN 809:71** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.

### `frontend/src/pages/vendor/Promotions/Promotions.tsx`

- Hướng xử lý: Loại bỏ tổng hợp không dùng và định kiểu lựa chọn/filter thay any.
- **WARN 230:9** — `@typescript-eslint/no-unused-vars`: 'totalUsed' is assigned a value but never used. Allowed unused vars must match /^_/u.
- **WARN 524:64** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.

### `frontend/src/pages/vendor/Schedule/Schedule.tsx`

- Hướng xử lý: Rà soát loading dịch vụ/slot khi khởi tạo, queryServiceId thay đổi, chuyển dịch vụ và retry. Dọn navigate nếu không dùng; không làm mất spinner hoặc hiển thị slot của dịch vụ trước.
- **WARN 16:9** — `@typescript-eslint/no-unused-vars`: 'navigate' is assigned a value but never used. Allowed unused vars must match /^_/u.
- **ERROR 61:5** — `react-hooks/set-state-in-effect`: Error: Calling setState synchronously within an effect can trigger cascading renders
  Vị trí hiện tại: `` setIsLoadingServices(true); ``.
- **ERROR 102:7** — `react-hooks/set-state-in-effect`: Error: Calling setState synchronously within an effect can trigger cascading renders
  Vị trí hiện tại: `` loadSlots(selectedServiceId); ``.

### `frontend/src/pages/vendor/Services/ServiceForm.tsx`

- Hướng xử lý: Tách tải categories khỏi tải service đang chỉnh sửa. Effect hiện phụ thuộc categoryId và gọi lại loadServiceData: kiểm chứng nguy cơ chọn danh mục làm tải lại/ghi đè form. Giữ create/edit, loading, dữ liệu chưa lưu và toast upload; xóa msg dư.
- **ERROR 138:7** — `react-hooks/set-state-in-effect`: Error: Calling setState synchronously within an effect can trigger cascading renders
  Vị trí hiện tại: `` loadServiceData(serviceId); ``.
- **WARN 282:13** — `@typescript-eslint/no-unused-vars`: 'msg' is assigned a value but never used. Allowed unused vars must match /^_/u.
- **WARN 391:13** — `@typescript-eslint/no-unused-vars`: 'msg' is assigned a value but never used. Allowed unused vars must match /^_/u.

### `frontend/src/pages/vendor/Services/Services.tsx`

- Hướng xử lý: Tách khởi tạo/tải dữ liệu khỏi setState đồng bộ trong effect; giữ spinner và error state khi refresh/retry, các thao tác submit/pause/resume.
- **ERROR 53:5** — `react-hooks/set-state-in-effect`: Error: Calling setState synchronously within an effect can trigger cascading renders
  Vị trí hiện tại: `` loadData(); ``.

### `frontend/src/types/google.d.ts`

- Hướng xử lý: Khai báo interface notification của Google prompt theo các trường/phương thức component đang dùng; không đổi callback sang unknown rồi bỏ kiểm tra kiểu.
- **WARN 39:46** — `@typescript-eslint/no-explicit-any`: Unexpected any. Specify a different type.

## Image FE: gate bảo mật đang không đạt

- Đã build lại Dockerfile nguyên bản bằng `docker build -t danasea-fe-handoff:local frontend`: exit 0, Linux arm64.
- Image kiểm chứng: `sha256:c2235994bbf5235f06ece72e46aee1f60629404aa59623918bbd2da3c7b1bf6e`.
- Trivy 0.76.1, snapshot kiểm tra ngày 2026-10-28: **3 phát hiện HIGH trên 3 package, thuộc 2 CVE; 0 CRITICAL**. Scan với `--exit-code 1` trả exit 1.

- `CVE-2026-111497` — `brotli-libs` `1.2.0-r1`; bản sửa scanner ghi nhận: `1.2.1-r0`; severity `HIGH`.
- `CVE-2026-92392` — `curl` `8.22.0-r0`; bản sửa scanner ghi nhận: `8.23.0-r0`; severity `HIGH`.
- `CVE-2026-92392` — `libcurl` `8.22.0-r0`; bản sửa scanner ghi nhận: `8.23.0-r0`; severity `HIGH`.

Role FE cần chọn runtime/base image đã vá, pin digest cho cả Node builder và Nginx runtime, build/quét lại actual image và giữ USER non-root. Không dùng ignore CVE hoặc hạ severity gate để bỏ qua lỗi. Các digest/image FE từng quét đạt ở commit cũ không còn đại diện cho Dockerfile hiện tại.

Lệnh kiểm tra image tương đương CI (từ repo root, Trivy phải được cài đúng phiên bản/checksum bằng tooling của repo):

```bash
docker build -t danasea-fe-handoff:local frontend
trivy image --image-src docker --scanners vuln \
  --severity HIGH,CRITICAL --exit-code 1 danasea-fe-handoff:local
```

## Điều kiện bàn giao hoàn tất

- [ ] Lint toàn frontend: 0 lỗi và 0 cảnh báo, không eslint-disable/rule downgrade để che lỗi.
- [ ] TypeScript và production build exit 0; xử lý hoặc ghi nhận riêng bundle-size advisory, không tự tuyên bố đạt performance từ build.
- [ ] npm audit không có HIGH/CRITICAL; actual frontend image không có HIGH/CRITICAL theo scanner tại thời điểm kiểm chứng.
- [ ] Docker builder/runtime được pin digest; image tiếp tục chạy non-root và khởi động qua production Compose.
- [ ] Browser không có uncaught exception/React render loop ở các flow bị sửa; xác minh loading, retry, auth, locale, đổi route/id và timer cleanup.
- [ ] Đặc biệt: đổi category trong form chỉnh sửa không ghi đè dữ liệu chưa lưu; đổi trang/retry đơn hàng không gây fetch loop; carousel/toast/loading giữ đúng hành vi.
- [ ] Tích hợp bản FE sửa xong rồi chạy lại full Required CI gate và rehearsal. Kết quả rehearsal trước khi gỡ FE không chứng minh trạng thái bàn giao này đạt release.
- [ ] Commit FE riêng, cung cấp kết quả lệnh thực tế; chỉ push khi có lệnh rõ ràng từ người dùng theo quy định repo.

Chưa push, chưa deploy production, chưa gửi thông báo sang chat/agent khác. File này là đầu vào để Role FE nhận và xử lý.
