# Acceptance repair after independent review — 03/11/2026

Scope: repair findings against Gemini head `d5ca15e9`; preserve substantive tests, CI failures and coverage floors. Dependency fixes from #166/#170 are integrated on this feature branch; the original PRs remain separate until merged by the owner.

## Repairs

- Report checker returns structured results and enforces exact testcase counts. Both previously unannotated discount boundary methods execute and are individually required by core policy.
- AI runners use real report checking, fresh reports/observations and measured Java outcomes. No invented seven-task labels, direct proxy routing, business-success flags or zero costs. Provider/policy facts not measured remain null/unknown. Missing acceptance observations fail.
- Financial mutation proofs require a clean fully executed baseline, no failed containers, and actual assertion failures from test methods. Setup/runtime exceptions cannot count as killed. Refund concurrency observes a PostgreSQL lock conflict; recovery creates a different processor; async work drains before absence assertions.
- Payment runner uses actual DTO fields, binds signed deployment/customer/order/quote and private session identities, preserves idempotency, rejects pending/mismatched responses and separates initiation from completion. Final live acceptance requires independent signed provider evidence. Workflow resumes authenticated encrypted sessions between dispatches.
- Migration runner rejects stale artifacts, wrong nonce/rows, skips, absent measurements, nonfinite budgets and missing integrity evidence. Real PostgreSQL test uses independent `pg_dump`/`pg_restore` into another database, hashes all baseline table content, checks all coordinates, measures observed PostgreSQL contention and proves corruption detection. This database fixture does not establish baseline-image startup or a production downtime SLA.
- Candidate-image rehearsal rejects mutable inputs, labels/validates owned resources before cleanup, checks all ten coordinate decimals and exercises application recovery on a distinct restored database. S2 is not passed without actual candidate/baseline image execution and its artifact.

## Verification and boundaries

Local full UTC Java verify: 2,993 passed, zero failures/errors, three exact optional skips, 348 discovered suites. Report and coverage guards passed (83.48% line, 64.07% branch, 78.72% method). Full tooling with PostgreSQL enabled: 222 passed; demo package: 36 passed. Ruff/actionlint and all four targeted mutation proofs passed. Exact-head GitHub CI is tracked on the associated PR. Historical 02/11 evidence remains historical and must not be promoted to release acceptance.

F3 live payment, A2 live provider and S2 staging candidate/baseline acceptance remain **unverified/blocked** until the protected operator inputs and actual environment runs exist. Missing credentials are not test passes. No main merge, production dispatch, production migration or user-data replacement is authorized by this code repair.
