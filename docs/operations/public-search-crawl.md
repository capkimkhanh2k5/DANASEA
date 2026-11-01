# Public search crawl operations

## Purpose

Published tours fetch their content from public catalog APIs. Blocking those XHR URLs in robots.txt makes Google render the existing application error state with `noindex`. On 2026-11-01 Google Live Test reproduced this on `/tour/cau-ca-bien-son-tra-danafi` and identified `/api/services/by-slug/cau-ca-bien-son-tra-danafi` as blocked by robots.txt.

## Source and server policy

- `frontend/public/robots.txt` is the policy shipped in future frontend images.
- `frontend/nginx.prod.conf` serves the identical policy at `/robots.txt` with `Content-Type: text/plain` and a five-minute cache lifetime. Keep the literal and source file synchronized; `scripts/tests/test_public_crawl_policy.py` checks their agreement and URL behavior.
- The production frontend image has a read-only root filesystem. Its existing read-only Nginx configuration bind mount can be updated on the host and reloaded without replacing the image.
- Nginx `geo` stores a literal string, preserving the robots `$` end anchors. A direct Nginx `return` string would interpret those anchors as variable syntax. The location returns this literal variable without expanding its contents again.
- Exceptions cover only catalog/detail/review URLs under `/api/services`, public categories and the active flash-sale endpoint. `/api/` remains disallowed otherwise, including similarly named namespaces. The backend's authentication and authorization rules are unchanged.
- Robots is crawler guidance, not access control. Private data must remain protected by backend authorization independently.

## Validation

Run:

```sh
python3 -m unittest discover -s scripts/tests -p test_public_crawl_policy.py -v
python3 -m unittest discover -s scripts/tests -v
```

Use the Nginx binary inside the actual running frontend container to validate any candidate. Stage the complete main configuration in `/tmp`, replacing its `include /etc/nginx/conf.d/*.conf;` with the candidate server configuration, then run `nginx -t -c <candidate-file>`. Nginx requires a seekable config file; `/dev/stdin` is unsuitable for this check. Delete only the temporary candidate afterward.

After applying the host file, run `docker exec danasea-frontend nginx -t`, then `docker exec danasea-frontend nginx -s reload`. Verify public HTTPS `/robots.txt` matches the source byte for byte, including literal `$` characters. This was also tested with the deployed Nginx binary on 2026-11-01.

Check `/`, `/healthz` and the Google verification file remain available. Validate sitemap XML, unique same-origin URLs, non-future `lastmod`, and tour slug equality against the public published catalog. Check anonymous protected API requests still return 401 and a missing tour API returns 404. HTTP 200 on an SPA shell alone does not establish indexability.

## Applied production change, 2026-11-01

The change was applied under the deployment lock and backed up to:

`/home/capkimkhanh/BackUp/search-console/20261101T123032Z-crawl/`

Backup files: `nginx.prod.conf` and `robots.txt`. The exact live Nginx file was patched with only the robots additions; unrelated differences between local source and the running server were preserved. Both a staged candidate and the installed config passed `nginx -t`; reload and health checks passed. No container was recreated and no database was changed.

Public HTTPS verification passed:

- Robots response matches source, cache lifetime 300 seconds.
- Sitemap contains 19 unique URLs: 3 static routes and 16 published tour slugs; all 19 returned HTTP 200. Existing tour `lastmod` values parse and are not in the future.
- Public category, active-sale, slug detail, UUID detail and review APIs return 200.
- Missing tour API returns 404.
- Anonymous `/api/admin/users`, `/api/orders` and `/api/bookings` return 401.
- Homepage, health check and original Google ownership verification URL remain available.

## Test boundary

Final verification on 2026-11-01:

- `RUN_DEMO_DATABASE_TESTS=1 python -m unittest discover -s scripts/tests -v`: **118 passed, 0 skipped**, including real disposable PostgreSQL/Flyway acceptance and Docker bootstrap/installer tests. The local Docker daemon was started before this final run.
- The four focused crawl regression tests are included in that suite. They were first observed failing against the original policy (14 failing subcases/assertions).
- Ruff checks for scripts and worker build/entrypoint tooling pass with the CI rule selection `E4,E7,E9,F,I`.
- `bash -n` passes for deployment and bootstrap entrypoints; `git diff --check` passes.
- Frontend lint and production build pass; built `dist/robots.txt` matches source byte for byte.
- Fresh public HTTP checks confirm the deployed robots policy, 19/19 sitemap URLs returning 200, protected APIs returning 401, and healthy read-only Nginx with a passing configuration check.

These are local and live-server results, not a claim that GitHub CI/CD or the full backend Maven suite ran. No backend application code changed.

## Google acceptance

Google Live Test after the reload, at 19:31 Asia/Ho_Chi_Minh on 2026-11-01, changed the original tour from excluded by noindex to eligible for indexing. Tested HTML contains the actual tour title, `index, follow`, and the tour canonical; the resources report says all resources loaded. A subsequent request indexing action was accepted and the URL entered Google's priority crawl queue. A second tour, `/tour/mo-to-nuoc-danabeach`, independently passed Live Test at 19:32 with eligibility for indexing.

A live test establishes eligibility, not completed indexing, ranking or validation of the truth of structured-data ratings. Sitemap discovery and search indexing can update later. Existing UI/content/structured-data findings remain outside this server-policy patch.

## Rollback

1. Acquire `/home/capkimkhanh/danasea/.deploy.lock` exclusively so a deployment cannot run concurrently.
2. Verify the recorded backup and current files have not changed since the operator's review.
3. Restore the two backed-up files to `frontend/nginx.prod.conf` and `frontend/public/robots.txt` in the server checkout. Write the Nginx destination **in place**, preserving the inode of the file already bind-mounted into the running container.
4. Run `docker exec danasea-frontend nginx -t` and reload only if validation passes.
5. Verify `/healthz`, `/`, verification file and the restored robots response. Rollback restores the original crawl restriction, so it also restores the known Google rendering problem.

Keep these source changes in the next reviewed release so later configuration synchronization does not restore the old crawl policy.

## Delivery through existing CI/CD

Commit the policy, Nginx configuration and regression test together. CI automatically discovers `scripts/tests/test_public_crawl_policy.py` in the existing deployment-guard test step. The frontend build copies `public/robots.txt` into `dist/robots.txt`; the Dockerfile copies `dist` into the image.

Automatic CD is triggered by a successful push CI run on `main`; pushing only `feat/seo-optimization-system` does not trigger production CD. Production deployment also requires the existing `PRODUCTION_DEPLOY_ENABLED=true` gate and all configured checks and approvals. This task verified those conditions in workflow source but did not verify the current GitHub variable value because the local GitHub CLI is not authenticated.

The existing deploy entrypoint fetches `main`, checks out the exact accepted commit SHA, and uses its `frontend/nginx.prod.conf` through the Compose read-only bind mount. Thus a release containing this commit carries the policy automatically; no repeat manual SSH patch is needed for that release. No CI/CD YAML change is required.

The production build was rerun successfully after the source update, and `dist/robots.txt` was compared byte for byte with the policy source.
