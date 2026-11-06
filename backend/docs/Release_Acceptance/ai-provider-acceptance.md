# AI acceptance evidence

A1 is a required independent CI job: install CPU Torch from the lock-driven installer, verify the pinned model, start an owned loopback worker, then run Java adapter acceptance with zero skips. Runner keys stay outside the model cache, and the child must remain alive throughout the run. Maven uses a clean report directory; exact testcase identities/counts and seven measured Java observations are required. Unknown/removed tasks cannot be replaced by fabricated labels.

Java tests write observations only after live assertions pass. Worker output records actual `DecisionTask.wireName`, model revision, latency and suggestion-only. Proxy output records the two reasoning cases and compaction, with measured timing and available finish/usage facts. Unmeasured owned-order policy evaluation is null. CLIProxy is a proxy, so upstream provider and route are null/unknown unless independently observed; an ANSWERED response alone does not establish provider attribution. Cost is null when not supplied.

Run `scripts/ai/check_ai_acceptance.py <artifact>` to validate semantic consistency, exact phase-specific cases, revision, failure status, latency, usage and provenance. The two runner unit suites exercise the real report checker and evidence validator, mocking only external process/model/provider boundaries.

A2 is manual and protected by environment `ai-provider-sandbox`. Set secret `AI_PROXY_API_KEY` and variable `AI_PROXY_ALLOWED_HOSTS` to a comma-separated explicit host allowlist. A dispatch URL outside that allowlist or a non-loopback HTTP target fails before Maven. Invocation:

```sh
AI_PROXY_ALLOWED_HOSTS=<approved host> python3 scripts/ci/run_proxy_acceptance.py \
  --base-url https://<approved host>/v1 --key-file <private key file> --output <artifact path>
```

The actual two live methods must produce three exact executions. Existing 120-second/six-request logical budgets and 8192 output ceiling remain. The compaction case preserves the user's non-swimming constraint. Core CI still permits exactly three named external acceptance skips; targeted A1/A2 allow none.

No live proxy success is claimed without a real candidate run and its measured artifact. A1 worker success does not establish semantic accuracy, owned-order business policy acceptance, upstream provider success or production readiness.
