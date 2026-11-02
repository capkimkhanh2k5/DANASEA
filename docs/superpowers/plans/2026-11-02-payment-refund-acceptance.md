# Payment and Refund Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chứng minh refund/capture không tạo hiệu ứng tài chính trùng khi timeout, restart hoặc callback cạnh tranh; bổ sung wire contract và sandbox acceptance có provenance.

**Architecture:** Tách PostgreSQL concurrency, HTTP adapter contract và provider sandbox thành ba mức bằng chứng. Test mặc định không gọi gateway thật; acceptance sandbox có preflight và thao tác phê duyệt của người vận hành. Giữ nguyên refund policy, currency binding và operation ID hiện tại.

**Tech Stack:** Java 21, JUnit, PostgreSQL 16/Testcontainers, RestClient, JDK HttpServer, Python 3.11, GitHub protected environments.

**Spec:** [Master plan](2026-11-02-backend-test-completion.md); `RefundProcessingService`, `PaymentCaptureConcurrencyIntegrationTest`, `RefundPersistenceIntegrationTest`, `PayPalPaymentAdapter`, `VNPayPaymentAdapter` là source of truth hiện tại.

## Global Constraints

- Kế thừa master, giữ tất cả guard amount/currency/provider/order/refund identity và trạng thái terminal; không đổi retry/status policy để làm test pass.
- Tiền ghi nhận bằng BigDecimal; dùng fixture `1000000` VND / `19.31` USD theo snapshot provider, không dùng double hoặc tính expected bằng chính hàm production.
- Không tắt FK/transaction/lock để tạo tình huống mà database thực tế không cho phép.
- Sandbox là gate riêng: thiếu credential, approval, callback hoặc môi trường thì ghi blocked; không gọi đó là test pass và không chuyển sang mock.
- Không dùng production key/account/URL hoặc thực hiện giao dịch thật. Các bước tương tác buyer/bank sandbox cần người vận hành chủ động hoàn thành.

## Review Focus

1. Response timeout sau khi provider đã nhận request: F1 phải query cùng operation ID, không tạo request tiền lần hai.
2. Worker và webhook cùng hoàn tất một refund: F1 kiểm tra DB, inventory và notification một lần.
3. Payload chữ ký hợp lệ nhưng amount/currency/ID sai: F2 kiểm tra từng lỗi riêng, không để một guard che guard khác.
4. Credential có thật nhưng trỏ production: F3 fail trước khi gọi provider.
5. Mutation compile/test-init lỗi nhưng bị gọi là killed: F4 phân biệt inconclusive với assertion bắt được lỗi.

## F1 — Refund concurrency/recovery trên PostgreSQL thật

**Files:**
- Create `backend/src/test/java/com/danasea/backend/modules/order/application/RefundProcessingConcurrencyIntegrationTest.java` và `backend/src/test/java/com/danasea/backend/configs/TrackingAsyncTestExecutor.java`.
- Modify test-only `BaseServiceE2ETest.java` và `ServiceAuditExecutorTest.java` khi chuyển executor đã được kiểm chứng sang shared support; không đổi method identity hoặc làm task chạy đồng bộ.
- Modify nếu regression chứng minh bug: `backend/src/main/java/com/danasea/backend/modules/order/application/RefundProcessingService.java`.
- Reference `PaymentCaptureConcurrencyIntegrationTest`, `RefundPersistenceIntegrationTest`, `RefundProcessingSafetyBranchesTest`, `RequestRefundUseCaseTest`.

**Interfaces:** Giữ `processRefund(UUID): boolean` và `processPayPalNotification(String eventId, String providerRefundId, String operationId): RefundWebhookResponse`. Dùng repository/PlatformTransactionManager thật, mock đúng PaymentGatewayPort bên ngoài; không mock TransactionTemplate.

- [ ] Chuyển executor tracking thật đã có ở BaseServiceE2ETest sang shared test support, giữ `awaitAll(Duration)`, registration-before-submit và ba proof tests. Financial test config dùng executor này; drain async trước assertion absence/exact-count và cleanup, kể cả task con. Chạy lại ba proof tests và bốn ServiceTier suite để bảo vệ hành vi cũ.
- [ ] Tạo fixture refund PENDING thuộc payment SUCCESS và order PAID; lưu sub-order/booking/slot/ledger liên quan cần cho assertion side effect. Đối chiếu fixture đã có trong hai persistence/concurrency suite; ID mới mỗi case, không TRUNCATE database chung.
- [ ] `twoWorkersCreateOneRefundOperation`: hai executor thread, latch chặn request gateway đầu tiên, future có timeout hữu hạn. Sau release: `requestRefund` đúng một lần, gateway request ID ổn định; refund PROCESSED và các field tiền/provider/refund ID đúng snapshot.
- [ ] `workerRacingWithVerifiedNotificationAppliesEffectsOnce`: callback và worker cùng refund, cùng persisted operation; chỉ một transition hoàn tất, inventory/booking/notification không bị áp dụng hai lần. Đếm dữ liệu persisted theo fixture ID, không chỉ verify mock.
- [ ] `freshProcessorReconcilesTimedOutRequestWithoutResendingMoney`: worker đầu timeout sau khi request ID đã commit; instance worker mới cùng database query cùng ID, xử lý kết quả xác minh; request count vẫn 1. Reader dùng transaction/thread độc lập chứng minh metadata đã commit.
- [ ] `unverifiableResultRemainsPendingWithoutMoneyEffects`: query timeout/NOT_FOUND/amount hoặc currency mismatch từng case riêng; giữ PENDING và lỗi chờ xác minh, không tạo operation mới, không phát completed event hoặc release inventory.
- [ ] Chạy trước sửa bằng `./mvnw -B -Dtest=RefundProcessingConcurrencyIntegrationTest test`; các invariant đã đúng phải pass. Nếu có assertion đỏ, sửa riêng nguyên nhân, không thêm production hook để ép thứ tự test hoặc tăng MAX_RETRIES.
- [ ] Chạy blast radius `./mvnw -B -Dtest=RefundProcessingConcurrencyIntegrationTest,PaymentCaptureConcurrencyIntegrationTest,RefundPersistenceIntegrationTest,RefundProcessingSafetyBranchesTest,RefundProcessingServiceTest,DiscountRefundRegressionTest test`.
- [ ] Full verify + random order tại checkpoint tài chính; lưu trạng thái trước/sau, call count và invariant DB. Commit `test(order): verify refund concurrency and restart recovery`.

## F2 — Wire contract của hai gateway

**Files:**
- Create `backend/src/test/java/com/danasea/backend/modules/order/infrastructure/adapters/PayPalPaymentAdapterHttpContractTest.java`, `VNPayPaymentAdapterHttpContractTest.java`.
- Create fixtures trong `backend/src/test/resources/payments/provider-contract/`: `paypal-capture-completed.json`, `paypal-refund-completed.json`, `vnpay-query-completed.json`, `vnpay-refund-pending.json`, `README.md`.
- Modify adapter tương ứng khi test chứng minh parser/signature/request bug; không bypass `verifyApiResponse` hoặc webhook verification.

**Interfaces:** Dùng constructor thật của PayPalPaymentAdapter `(RestClient.Builder, PayPalProperties, ObjectMapper, VNPayPaymentAdapter)` và VNPayPaymentAdapter `(VNPayProperties, RestClient.Builder, ObjectMapper)`. HttpServer loopback có port ngẫu nhiên; đóng server/executor sau mỗi class.

- [ ] Fixture ghi rõ synthetic contract data; expected ID `ORDER-ACCEPT-1`, `CAPTURE-ACCEPT-1`, `REFUND-ACCEPT-1`, amount `19.31`, currency USD. Ghi tài liệu provider/field mapping đã đối chiếu; không gán fixture giả thành giao dịch thu thập từ provider thật.
- [ ] PayPal: assert OAuth body/header, request path, PayPal-Request-Id ổn định, capture/refund/query payload; parser giữ đúng amount/currency/transaction ID/status provider báo. Kiểm tra 401/429/500, malformed JSON và pending result theo adapter contract hiện hữu.
- [ ] VNPay: assert amount x100, timestamp theo Vietnam zone, request/refund/query ID và chữ ký; expected HMAC dùng vector độc lập, không gọi private production signer để tính expected.
- [ ] Response VNPay đổi từng field amount/currency/reference/status/hash phải không được chấp nhận như success; PayPal webhook thiếu/sai từng verification field bị từ chối. Binding với persisted amount/order tiếp tục được kiểm tra ở service tests, không suy ra từ parser đơn lẻ.
- [ ] Chạy focused trước sửa, sửa tối thiểu lỗi đã tái hiện; sau đó chạy hai contract suite + `PayPalPaymentAdapterTest,VNPayPaymentAdapterTest,PaymentGatewaySafetyTest,PayPalRefundWebhookDispatchTest`.
- [ ] Commit `test(payments): exercise gateway HTTP and signature contracts`. Đây là transport/parser evidence, chưa đánh dấu F3 hoàn thành.

## F3 — Acceptance sandbox có preflight và phê duyệt buyer

**Prerequisite do người triển khai cung cấp:** PayPal sandbox merchant/buyer, VNPay sandbox merchant/hash key, HTTPS callback của backend sandbox, customer token test, dịch vụ/slot sandbox hợp lệ, backend revision và chứng cứ cấu hình gateway của chính deployment đó. Không hỏi các input này để cản F1/F2.

**Files:**
- Create `scripts/acceptance_payments.py`, `scripts/tests/test_payment_acceptance.py`.
- Create `.github/workflows/payment-sandbox-acceptance.yml` và `backend/docs/payment-sandbox-acceptance.md`.

**Interface CLI cần triển khai:** `acceptance_payments.py preflight|create-intent|capture|verify|refund --provider paypal|vnpay --base-url URL --token-file PATH --session PATH --evidence-dir PATH --deployment-manifest PATH`. Các hành động sau preflight dùng session do create-intent tạo, không nhận arbitrary payment/refund ID từ môi trường khác.

**Session/evidence:** JSON schema_version=1 có environment=sandbox, backend revision, provider, run ID, owned payment/order/refund/operation IDs và approval URL; evidence tách request outcome, provider verification và persisted-state verification. Token/secret/raw authorization không nằm trong JSON.

- [ ] Viết test CLI preflight: thiếu file/key, manifest sai revision, environment khác sandbox, PayPal API host khác `api-m.sandbox.paypal.com`, VNPay host khác `sandbox.vnpayment.vn` hoặc session thuộc backend khác đều exit !=0 trước network call. Manifest phải lấy từ deployment kiểm soát được và đối chiếu revision/config; không coi một JSON tự ghi nhãn sandbox là attestation đủ.
- [ ] Viết test đỏ cho expected provider callback/query status thiếu, payment mismatch và duplicate completion; output không chứa bearer token/hash secret. Mock network chỉ dùng để test runner, không được ghi là F3 live pass.
- [ ] Implement create-intent và lưu owned session. Buyer hoàn thành approval PayPal/bank sandbox qua provider; runner không tự đánh dấu payment approved hoặc tự giả chữ ký để vượt bước này.
- [ ] Chạy capture/verify/refund trên session đã approved; kiểm tra amount/currency/owner/operation/status cả API và dữ liệu sandbox readback có quyền. Duplicate event/query không sinh hiệu ứng lần hai; kết quả bất định giữ PENDING và ghi chưa xác minh.
- [ ] Workflow `workflow_dispatch`, environment `payments-sandbox`, preflight bắt buộc và concurrency theo environment; không có `continue-on-error`, không tự dispatch production. Unit test runner vẫn chạy trong quality job mặc định.
- [ ] DoD live: artifact từng provider chứng minh create → approval → capture/callback → refund/query; thiếu một bước ghi blocked với input còn thiếu. Không upload session chứa PII/secret hoặc database dump công khai. Commit `test(payments): add guarded sandbox acceptance runner`.

## F4 — Mutation proof lặp lại được cho financial guards

**Files:** Create `scripts/verify_financial_mutations.py`, `scripts/tests/test_financial_mutations.py`, `backend/src/test/java/com/danasea/backend/tools/FinancialMutationLauncher.java`; modify `backend/pom.xml` để khai báo JUnit platform launcher test-scope theo BOM đang dùng nếu cần; create `.github/workflows/financial-mutations.yml`.

**Interfaces:** Python CLI `--backend-dir PATH --output PATH`; launcher `main(String[] testClassNames)` xuất found/passed/failed/skipped/aborted và failure type. Sinh source/class mutant dưới temporary directory, đứng trước classpath gốc; không sửa workspace production.

- [ ] Chuyển bốn mutation đã có bằng chứng thành runner repo-owned: HMAC payload hằng, bỏ capture amount guard, capture currency guard, refund currency guard. Thêm identity/status/idempotency mutant sau khi có assertion độc lập cho từng cái.
- [ ] Unit test runner: pattern không khớp đúng một chỗ, compile lỗi, không phát hiện test, baseline đỏ, aborted/skipped hoặc lỗi khởi tạo đều **inconclusive/fail**, không tính killed.
- [ ] Derive classpath từ Maven, không hardcode `/Users/.../.m2`. Baseline phải pass trước; mutant chỉ killed khi cùng số test được chạy và assertion thực sự bắt lỗi; giữ source SHA và phép biến đổi trong JSON.
- [ ] Chạy workflow thủ công trên candidate SHA trước khi đưa thành gate cho thay đổi tài chính. Giữ core Maven/report/coverage checks như cũ; không công bố full mutation score từ một nhóm mutant có chọn lọc.
- [ ] Commit `test(order): automate targeted financial mutation proofs`.
