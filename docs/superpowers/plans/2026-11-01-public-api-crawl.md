# Public API crawl implementation plan

> For agentic workers: execute inline using superpowers:executing-plans.

**Goal:** Remove the robots block that makes Google render a published tour as a noindex error page.
**Architecture:** Permit only public catalog/category/active-sale API namespaces in robots.txt. Serve the same policy from the existing production Nginx bind mount so a read-only frontend container needs only a validated reload. Keep backend authentication unchanged.
**Tech Stack:** Nginx, robots.txt, Python unittest, existing Spring Boot APIs.
**Spec:** Approved scope in this chat: backend, DevOps, server, Search Console; no UI changes.

## Constraints and review focus
- Preserve all unrelated local and production changes, including the verification endpoint.
- Robots controls crawlers, not authorization; protected APIs must remain inaccessible anonymously.
- Catalog query URLs, UUID detail URLs, slug detail URLs and review URLs must be crawlable.
- Similarly named API namespaces must remain blocked; no blanket /api allow.
- Back up exact live files, validate nginx -t before reload, restore on failure.
- Google Live Test must show actual published tour content without robots-blocked APIs or noindex.

## Task 1: Policy regression and configuration
- [x] Add scripts/tests/test_public_crawl_policy.py with literal allow/deny URL cases and consistency between public robots and Nginx response.
- [x] Run test before implementation and confirm the public API cases fail.
- [x] Update frontend/public/robots.txt and only the robots location in frontend/nginx.prod.conf.
- [x] Run focused tests, scripts test suite and Python lint; record any environment-dependent skips.

## Task 2: Production and Google verification
- [x] Compare live files, save backups and validate candidate in the actual Nginx runtime.
- [x] Apply only the robots changes, reload Nginx, compare public HTTPS response byte for byte.
- [x] Check sitemap, published tour API, missing-resource statuses, verification endpoint and anonymous protected API denials.
- [x] Run Google Live Test on the previously failing tour and one additional tour; retain screenshots.
- [x] Record results, remaining boundaries and rollback steps in docs/operations/public-search-crawl.md.

## Execution rulings
- The user already approved implementation and server access; proceed without another approval checkpoint.
- Use the existing clean feat/seo-optimization-system branch for this small configuration patch; no worktree or branch switch.
- Do not deploy unrelated Nginx changes present locally but absent from the server.

## Verification ledger
- RED: 4 tests, 14 expected failing subcases/assertions against the original policy.
- GREEN: 4 focused tests passed; production candidate and installed Nginx config validated.
- Independent scoped review: no material findings.
- Complete scripts suite: 118 tests, 11 Docker-related errors, 10 skips; non-container selection 112 tests, 102 passed, 10 skips.
- Public HTTPS smoke and first Google Live Test passed; first tour indexing request accepted.
- Additional tour Google Live Test passed at 19:32; final evidence is recorded in the operations document.
- Ruling: use a Nginx geo literal for the reloadable policy because return strings interpret robots dollar end anchors as variables. Actual Nginx and byte-for-byte HTTP checks confirm literal output.
