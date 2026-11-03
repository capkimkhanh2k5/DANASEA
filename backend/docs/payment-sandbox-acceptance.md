# Payment sandbox acceptance

This is an optional protected sandbox gate. Missing credentials, buyer approval, signed deployment identity, or independent provider evidence means **blocked**, never live acceptance passed. Unit tests use synthetic network boundaries and prove runner behavior only.

## Deployment attestation

The operator inspects the actual sandbox deployment revision, gateway configuration and a dedicated owned customer/order fixture. Sign the resulting JSON using a key in protected environment variable `PAYMENT_SANDBOX_SIGNERS` (OpenSSH allowed_signers entry: `sandbox-operator ssh-ed25519 ...`). An unsigned JSON labeling itself sandbox is rejected. Expected revision must equal the candidate under test; base URL is bound to the signed deployment. Attestation is valid for at most one hour and must be refreshed from the controlled deployment if approval takes longer.

Manifest fields:

```json
{
  "environment": "sandbox",
  "revision": "<40 hex candidate SHA>",
  "base_url": "https://<sandbox backend>",
  "attested_at": "<UTC ISO timestamp>",
  "expires_at": "<UTC ISO timestamp within one hour>",
  "gateways": {
    "paypal": {"api_host": "api-m.sandbox.paypal.com"},
    "vnpay": {"host": "sandbox.vnpayment.vn"}
  },
  "fixture": {
    "customer_id": "<owned sandbox customer UUID>",
    "order_id": "<owned sandbox order UUID>",
    "amount": "500000",
    "provider_amount": "19.31",
    "provider_currency": "USD",
    "refunds": [{"sub_order_id": "<owned sub-order UUID>", "amount": "500000", "provider_amount": "19.31", "currency": "USD"}]
  }
}
```

Numbers above are illustrative; supply the actual independently inspected quote. VNPay uses VND. Sign locally with `ssh-keygen -Y sign -f <operator key> -n danasea-payment-sandbox manifest.json`; the runner reads `manifest.json.sig` and verifies identity `sandbox-operator`. Do not commit credentials, approval URL, session, private key or provider account data.

## Lifecycle

All CLI phases require `--provider`, `--base-url`, `--expected-revision`, `--deployment-manifest`, `--signers`, `--token-file`, `--session`, `--evidence-dir`.

1. `preflight`: verifies the signed, unexpired sandbox deployment and token file before network operations.
2. `create-intent --order-id <signed fixture order>`: reads owned order/amount, uses actual `paymentReference` DTO field and records persisted `providerOrderId`. Creates a mode-0600 private session. Phase retries retain the same idempotency key and must return the same payment ID.
3. The operator completes buyer/bank sandbox approval using the private session's URL. The runner never simulates approval or webhook signatures.
4. `capture`: uses the session's provider order ID; requires matching payment/order/provider/amount and verified SUCCESS on backend readback. Pending/unknown is blocked.
5. `refund`: requires verified capture, submits a stable idempotency key, stores all actual `RefundResponse.id` values and labels evidence INITIATED. This does not establish provider completion.
6. `verify --provider-receipt <signed JSON>`: requires both backend PROCESSED refunds and an independent signed provider receipt binding candidate/run/payment/order/provider transaction, provider amount/currency and refund IDs. Missing receipt or pending state cannot pass.

Provider receipt is signed in namespace `danasea-payment-provider-receipt`; fields are `environment`, `backend_revision`, `run_id`, `provider`, `order_id`, `payment_id`, `provider_order_id`, `provider_transaction_id`, `currency`, `amount`, `status=COMPLETED`, `provider_refund_ids`, and `refunds` with each `refund_id`, `provider_refund_id`, `amount`, `currency`, `status=COMPLETED`, `backend_operation_id`, `provider_operation_id`. The two attested operation IDs must match and be unique. The operator must obtain these from the real sandbox provider console/API, never manufacture them from runner output. This is explicit operator attestation, not a claim that the runner directly queried the provider.

## GitHub protected environment

Workflow `payment-sandbox-acceptance.yml` uses `payments-sandbox`. Configure protected secrets `PAYMENT_SANDBOX_CUSTOMER_TOKEN`, `PAYMENT_SANDBOX_MANIFEST`, `PAYMENT_SANDBOX_MANIFEST_SIGNATURE`, `PAYMENT_SESSION_KEY` (at least 32 random characters), and, for final verify, `PAYMENT_SANDBOX_PROVIDER_RECEIPT`/`PAYMENT_SANDBOX_PROVIDER_RECEIPT_SIGNATURE`. Configure trusted public keys in variable `PAYMENT_SANDBOX_SIGNERS`.

Each dispatch has a fresh runner. Resume with `session_run_id` of the previous dispatch. Session artifact is encrypt-then-MAC protected; authentication is checked before decryption. Public evidence contains only allowlisted IDs/status/hash, never the raw session or approval URL. Session envelope expires after one day. To inspect buyer approval locally, download the encrypted artifact and use `scripts/payment_session.py open` with the protected key in environment `PAYMENT_SESSION_KEY`. Keep plaintext local and private.

No live sandbox acceptance has been established merely by committing this runner or passing its unit tests. Do not dispatch with production credentials or targets.
