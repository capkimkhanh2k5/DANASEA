# Production releases and recovery

Current implementation status: frontend issues and the backend cache-visibility/shared-test-schema defects have been corrected under the owner's authorization. The former FE handoff file was removed. Lint/build/image gates and the new backend regressions remain mandatory.

## Release contract

CI runs Java 21 verification, mandatory Flash Sale tests with owned PostgreSQL 17/Redis, Quyet boundary and pinned CPU inference tests, blocking frontend lint (zero errors and zero warnings)/build, dependency/secret checks, workflow/deployment guards, and production image builds/scans. `Required CI gate` must pass; absent suites and unexpected skips fail it. The allowlist in `.ci/test-policy.json` contains only historical superseded ServiceTier tests and live AI probes whose runtime acceptance runs elsewhere.

CD builds candidates once, scans their immutable digests, and emits BuildKit provenance and SBOMs. Candidates have run-specific tags; application deployment and rollback use their signed digest manifests, never `latest`. Private GitHub artifact attestations require Enterprise; the existing CI Ed25519 deployment key instead signs the release manifest with namespace `danasea-release`. This authenticates release authorization/digest integrity; BuildKit provenance separately records build inputs. It is not a claim of SLSA certification or independent builder attestation.

Before signing, CD obtains the current accepted release through the restricted SSH `status` command. It rehearses baseline migration, local dump/restore, candidate migration, preservation of an existing marker, public catalog/auth behavior, authenticated local inference and a real booking/order flow in a disposable Compose project. All test data, networks and volumes belong to that project. Real payment gateways are never charged. The rehearsal seeds an explicit synthetic commission policy only in its disposable database. The signed evidence binds the candidate images and current baseline SHA; the server rejects it if another release has changed that baseline.

The server requires root-owned policy/signer files, verifies the envelope signature, rejects a deploy that would move Git history backwards, checks prerequisites, creates a local restore-verified backup, pulls the exact images and starts the application. Public HTTPS/API checks, authorization rejection, read-only booking-policy readiness, worker inference and primary/fallback provider completions must pass, followed by a 120-second stability window. One-second API sampling enforces a configurable outage budget (default 60 seconds). Only then is release state advanced atomically. Image cleanup cannot make an accepted release appear to fail. The Quyet runtime contains CPU-only PyTorch and a minimal distroless userspace; interactive Tk/curses/readline, DBM, SQLite and the optional libuuid extension are intentionally absent. Copied shared libraries retain Debian package provenance for vulnerability scanning.

## External prerequisites: do these before enabling the new CD

1. **Protect `main`:** require PRs and `Required CI gate`, require branches up to date, block force pushes/deletion and restrict bypass. Enforce independent approval/code-owner review for workflows, deployment scripts, Dockerfiles and migrations when eligible reviewers other than the author are configured. A solo configuration without those reviewers does not establish independent review; see the Vietnamese steps below to avoid an impossible self-approval rule. The live branch API must report `protected=true` and include the required gate. Account-plan limits must be resolved by the owner; this change does not upgrade billing or make the repository public.
2. **Production environment:** restrict deployments to protected branches. Store production SSH/Tailscale credentials in this environment. Add reviewers if the team's release policy requires a human gate. `environment: production` by itself does not establish those restrictions.
3. **Registry:** the deploy account needs read access to private GHCR packages. Keep accepted digests available beyond the recovery window. Candidate artifacts are retained for 90 days; GHCR retention must preserve accepted images independently of artifact retention.
4. **Host:** Linux, Python >=3.11, Docker Compose supporting `--wait`, OpenSSH signing support, `flock`, a separate unprivileged deployment user with Docker access, and the existing forced-command authorized key. Install `python3-yaml` if the proxy management panel writes YAML. Keep the current MacBook/operator access unchanged.
5. **Business configuration:** the owner approved a 10% initial platform commission. Migration V43 inserts `PLATFORM_COMMISSION_RATE=10` only when absent and preserves any existing Admin policy. Confirm the effective value after deployment; Admin API updates still override it.
6. **Local backups:** per the owner's explicit choice, backups stay on this server in `~/BackUp/danasea` unless an absolute alternative is supplied. No rclone, age key, off-host bucket or additional service is required. Each deploy attempt creates one restore-verified backup before service changes. Keep at most seven archives; no periodic backup schedule is installed. Directory mode is 700 and archives are 600.

`scripts/check_repository_controls.py` checks branch/gate/environment restrictions at release time. A missing setting, insufficient API permission or unsupported account plan blocks CD rather than treating the check as successful. The integration available in the authoring session cannot administer branch/environment protections; local `gh` also has no authenticated session. Those settings require an account administrator.

## One-time host transition

Keep `PRODUCTION_DEPLOY_ENABLED=false` while installing the reviewed controls. Do not merge/enable a new deployment protocol against an old forced-command wrapper. The new workflow calls `status` before deployment, so an old wrapper rejects it before running a new release.

Get the **public** Ed25519 key corresponding to the existing CI `SSH_PRIVATE_KEY`. Never print or copy its private key into logs. On the host, run the installer from the reviewed checkout as root:

```bash
sudo bash scripts/install-production-controls.sh \
  DEPLOY_USER /absolute/path/to/danasea https://production.example \
  /absolute/path/to/ci-deploy-key.pub SUCCESSFUL_LEGACY_CD_RUN_ID
```

An optional sixth argument chooses an existing local backup location, e.g. `/absolute/path/to/BackUp/danasea`. Otherwise the deploy user's `~/BackUp/danasea` is used. The installer does not change the existing SSH authorized key or operator keys. Verify that its forced command remains `/usr/local/bin/danasea-deploy` with `restrict`.

No long-running control agent or backup service is installed. This is a one-time update to the existing forced-command deploy script: helpers go in `/usr/local/lib/danasea` and trusted policy files in `/etc/danasea`. Configuration validation runs before helper replacement, legacy adoption must succeed before the existing entrypoint is atomically replaced, and the deploy account must be non-root. It configures a seven-archive retention limit and installs no backup service or timer. The deploy entrypoint creates one backup per deployment attempt. It imports the current legacy release only when every expected application container is healthy, uses the current SHA tag, and exposes a matching GHCR digest. This is a one-time operator adoption of existing runtime state; it does not fabricate CI rehearsal evidence for the old release. If adoption fails, inspect the actual running release and registry digests; do not invent them.

Review `/etc/danasea/deploy.json` and `/etc/danasea/backup.json`; both must remain root-owned and not group/other writable. The backup policy uses `"keep": 7`. Signer rotation and changes to local policy are explicit operations: the installer refuses silently replacing different existing policy files. If updating an earlier draft installation, explicitly change its backup retention to 7 before rerunning the installer; the new helper also caps a larger legacy retention value at seven.

Only if a previous draft backup timer was installed separately, disable it once with `sudo systemctl disable --now danasea-backup.timer`. This revision creates no timer. No host schedule has been installed or modified in the local authoring session.

Run the repository-policy checker with a token that can read the repository/environment, verify the restricted SSH `status` output, and enable `PRODUCTION_DEPLOY_ENABLED=true`. The first deployment creates and verifies its own single backup; a failed dump/restore stops service changes. The first hardened release is expected to block automatic rollback if its infrastructure configuration differs from the adopted baseline (e.g. newly pinned PostgreSQL). That requires a forward-recovery plan before rollout.

## Backups and explicit restoration

Each backup uses `pg_dump -Fc --no-owner --no-privileges`, restores the entire archive to a random `danasea_restore_*` database on the same PostgreSQL instance, checks that application and Flyway tables exist, drops only that temporary database, computes a SHA-256 checksum and publishes the verified archive. A failed dump or restore aborts deployment. The deployment lock covers backup and service changes. Each deploy attempt creates one archive; retention keeps that new verified archive and at most six previous archives, together with their checksum metadata. Rotation runs only after the new backup passes restore verification. When reusing the existing backup directory, application-generated legacy `pre-<7-hex-sha>-<14-digit-time>.sql.gz` archives count toward the same seven-archive limit; unrelated files remain untouched. On this host, supply the existing `/home/capkimkhanh/backups/danasea` as the installer's optional backup-directory argument to reuse it.

There are no periodic, background or daily backup jobs in this implementation. With no deployment, no automatic backup is created. Measure the predeploy backup duration and disk requirements with representative data. External uptime alerts remain outside this change, consistent with the prior operational choice. Local-only storage does not provide recovery from losing this server; it is the explicitly selected scope.

To rehearse an existing archive, first compare its SHA-256 with its `.json` metadata. Create a new database whose name starts with `danasea_restore_`, then restore the custom archive into it with `pg_restore --no-owner --no-privileges --exit-on-error`. Never pass the live database as the target. The Python `backup.restore(archive, container, database)` helper enforces that temporary-name boundary. Validate business invariants in the restored database before considering any production recovery.

A live database replacement requires a maintenance window, stopped writers/consumers, a current backup of the current state, transaction reconciliation and explicit operator approval. Automatic application rollback never restores a database or silently discards post-backup bookings/payments.

## Rollback and forward recovery

Normal deployment advances the accepted Git ancestry. An old CI rerun cannot downgrade production. For an intentional rollback, manually dispatch CD from `main`, providing a previously accepted SHA and the successful CD run containing its `accepted-release` artifact. The pipeline rescans/rehearses those exact images, signs action `rollback`, and the server verifies that the target was previously accepted and is an ancestor of the running release.

Both automatic and manual rollback require unchanged migration files and compatible data-service, proxy, worker mount/runtime, network and volume configuration. Backend, frontend and worker are the only restarted services during automatic rollback (`--no-deps`). Changed migrations disable rollback regardless of a green startup probe: recover forward using an additive migration/new image, or perform an explicitly planned database recovery. Design schema changes as expand/contract releases so older application versions remain usable across the expansion phase.

If health, acceptance or the outage budget fails, release state remains on the last accepted SHA. The script attempts application rollback only when the compatibility contract passes. If rollback fails or is blocked, inspect local service state, recover forward and rerun acceptance. State describes accepted releases, not a guarantee that currently running containers match it after a failed deployment.

## Availability and operational boundary

This remains a single-host, single-instance Compose deployment. Public API samples and measured outage budgets are retained in `.release-events/<sha>.json`. They quantify this rollout's observed interruption; they do not create zero downtime, host failover, load capacity or an availability SLA. Rehearsal elapsed time similarly does not measure production downtime. Blue/green traffic switching, another host and recovery from host loss require additional infrastructure and an explicit availability requirement.

The rollout starts infrastructure, runs the migration to completion, waits for Quyet, then replaces backend and frontend individually with `--no-deps`. This keeps the old public applications available while the model loads. It still measures the entire rollout against the same 60-second outage budget; a backend replacement can interrupt requests. Every completed monitor writes a timestamped attempt report, including failed attempts, before cleanup or rollback. The accepted SHA report is written only on success. Backend health is probed every five seconds so traffic does not wait for a 30-second probe interval after startup.

### Dependency maintenance

Keep `Required CI gate`, strict up-to-date branches and protected `main`. `.github/dependabot.yml` groups supported ecosystems into one `weekly-maintenance` PR each Monday at 08:00 Asia/Ho_Chi_Minh. Validate the complete combined lockfiles/images in that PR, then merge once; this avoids invalidating 26 independently tested branches one by one. Security alerts remain enabled; grouped version updates are not a replacement for urgent vulnerability fixes. See [GitHub multi-ecosystem groups](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/configuring-multi-ecosystem-updates).

The 2026-10-29 consolidation incorporates PRs #120, #121, #123, #126, #127, #128, #129, #130, #132, #133, #135, #138, #139, #140, #141, #142 and #144. React and React DOM must resolve to the same release; Pydantic 2.14.1 requires pydantic-core 2.50.1. Regenerate and verify their lockfiles together. PR #143 (lucide-react) was already merged before consolidation.

The following proposed versions are deliberately deferred, rather than reported as installed:

- #118 / #122: Maven build image changes JDK to 26 and runtime image changes JRE to 22. Retain Java 21 in build, CI and runtime. Maven Docker tags encode two versions, so that image is maintained manually; Maven Wrapper updates remain automated.
- #119: Node 26 image changes the tested Node 22 build baseline.
- #124 / #125: RabbitMQ 4.4 and Redis 8 require a separate persisted-data upgrade and rollback rehearsal. Retain RabbitMQ 4.3 and Redis 7; RabbitMQ updates >=4.4 and runtime major version updates are excluded from routine version PRs.
- #134: TypeScript 7 is outside the installed typescript-eslint supported peer range (<6.1).
- #136: huggingface-hub 2.3 conflicts with the installed Transformers constraint (<2).
- #137: mpmath 1.4.1 conflicts with SymPy 1.14's constraint (<1.4).
- #131: NetworkX 3.7 requires Python >=3.12; worker runtime and CI use Python 3.11.

Remove the corresponding version exclusions only when the owning runtime/library migration is implemented and tested. Closing superseded or incompatible PRs is housekeeping; it must never be represented as successfully applying those upgrades.

Provider acceptance performs one small completion through each configured primary/fallback route and suppresses response content. It proves connectivity/authentication/response contracts at that moment; it does not grade AI quality, payment-provider settlement, authorization isolation for every role or business load capacity. The database/booking/order rehearsal uses synthetic data; provider-specific payment acceptance remains a separate controlled validation.

## Local validation

```bash
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_test_reports.py backend/target/surefire-reports --policy .ci/test-policy.json
bash -n scripts/deploy.sh scripts/install-production-controls.sh
actionlint
```

Run `./mvnw -B clean verify` from `backend`, `npm ci && npm run lint && npm run build` from `frontend`, and the worker unittest/smoke commands in CI. For a disposable local image rehearsal:

```bash
python3 scripts/rehearse.py \
  --model-dir /absolute/path/to/verified/Quyet-Small \
  --backend-image LOCAL_BACKEND_IMAGE \
  --frontend-image LOCAL_FRONTEND_IMAGE \
  --quyet-image LOCAL_WORKER_IMAGE \
  --output /tmp/danasea-rehearsal.json
```

CI/CD use signed, namespace-validated digest manifests instead of local image names. Rehearsal diagnostics contain only disposable data and stay in a mode-600 local failure log; production diagnostics intentionally omit application/provider/customer logs. Do not upload whole database dumps, `.env`, OAuth tokens, SSH keys, private manifests or raw provider responses as workflow artifacts.

## Thiết lập GitHub từng bước cho server local

Thực hiện trên repository [capkimkhanh2k5/DANASEA](https://github.com/capkimkhanh2k5/DANASEA), bằng tài khoản có quyền quản trị. Các tên nút bên dưới giữ nguyên tiếng Anh để đối chiếu với giao diện GitHub.

### Bước 1 — Giữ deploy tắt trong lúc cấu hình

1. Vào **Settings → Secrets and variables → Actions → Variables**.
2. Trong **Repository variables**, chọn **New repository variable** hoặc sửa biến đã có.
3. Đặt tên `PRODUCTION_DEPLOY_ENABLED`, giá trị `false`, rồi lưu.
4. Tạo/sửa `VITE_GOOGLE_CLIENT_ID` với Google OAuth Client ID đang dùng cho ứng dụng web. Đây là client ID công khai, không phải client secret.

Hai biến này phải ở **Repository variables**. Không chỉ đặt trong environment `production`: điều kiện chạy job deploy cần biến trước khi job vào environment, còn job build FE không dùng environment đó. Khi tắt deploy, CD vẫn có thể build image; dấu xanh của phần build chưa chứng minh server đã được cập nhật.

### Bước 2 — Kiểm tra GitHub Actions được phép chạy

1. Vào **Settings → Actions → General**.
2. Kiểm tra chính sách cho phép các action đang được ghim trong workflow: `actions/*`, `docker/*`, `tailscale/github-action`.
3. Không cần cấp quyền ghi mặc định cho mọi workflow. CI/CD đã khai báo quyền ở từng job; `GITHUB_TOKEN` do GitHub tự tạo, không cần thêm secret cùng tên hoặc tạo PAT chỉ để build/push image.
4. Mở **Actions → CI** hoặc trang PR đang làm; xác nhận có check **Required CI gate**. Nếu chưa hiện, chờ workflow CI của PR chạy để GitHub nhận diện tên check.

### Bước 3 — Bảo vệ nhánh `main`

1. Vào **Settings → Branches**.
2. Chọn **Add classic branch protection rule**. Dùng classic rule vì script kiểm tra release hiện đọc required checks trên branch protection.
3. Trong **Branch name pattern**, nhập chính xác `main`.
4. Bật **Require a pull request before merging**.
5. Bật **Require status checks to pass before merging**; tìm và chọn **Required CI gate**. Chọn nguồn **GitHub Actions** nếu có ô chọn nguồn check.
6. Bật **Require branches to be up to date before merging**.
7. Bật **Require conversation resolution before merging**.
8. Bật **Do not allow bypassing the above settings** nếu giao diện có mục này. Giữ **Allow force pushes** và **Allow deletions** tắt.
9. Với cấu hình team lead hiện tại, giữ **Require approvals** và **Require review from Code Owners** tắt trong classic rule. Approval được thực thi bằng ruleset riêng bên dưới; bật lại ở classic sẽ chặn cả admin tự merge.
10. Chọn **Save changes**. Giữ **Do not allow bypassing the above settings** bật để cả admin vẫn phải qua PR và CI xanh.

Cấu hình review đã chọn cho repository public:

- **Settings → Rules → Rulesets → main-review-team-lead**: trạng thái **Active**, target **Default branch** (`main`).
- Bật **Require a pull request before merging**, chọn **Required approvals = 1** và **Require review from Code Owners**.
- Bypass chỉ cho **Repository admin**, chế độ **For pull requests only**. Không cấp bypass cho Write/Maintain hay ứng dụng.
- `.github/CODEOWNERS` có dòng mặc định `* @capkimkhanh2k5`, yêu cầu lead review thay đổi của thành viên trên mọi đường dẫn.
- Thành viên nên có quyền Write; ai được cấp Admin cũng nhận ngoại lệ approval này. Bypass dựa trên **người bấm merge**, không phải tác giả PR: admin có thể merge cả PR của thành viên mà chưa có approval. Việc lead thực sự review trước khi tự bấm merge vẫn là trách nhiệm của lead.
- Ruleset này chỉ tách yêu cầu review. Classic rule tiếp tục bắt buộc PR, `Required CI gate`, cập nhật nhánh, giải quyết conversation và cấm force push/xoá nhánh; ngoại lệ review không bỏ qua các yêu cầu CI đó.

Nếu repository chuyển lại private: branch protection cần gói hỗ trợ private repository, ví dụ GitHub Pro cho tài khoản cá nhân hoặc Team cho tổ chức. Nếu GitHub yêu cầu nâng cấp, chủ tài khoản phải xử lý gói trước; không coi việc lưu một rule không được thực thi là đã bảo vệ. Không cần chuyển repository sang public. [Tài liệu GitHub về branch protection](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/managing-a-branch-protection-rule).

### Bước 4 — Tạo environment `production`

1. Vào **Settings → Environments**.
2. Chọn environment `production` nếu đã tồn tại; nếu chưa có, chọn **New environment**, nhập `production`, rồi **Configure environment**.
3. Tìm **Deployment branches and tags** hoặc **Deployment branches**.
4. Chọn **Protected branches only**, rồi lưu. Cấu hình này phụ thuộc việc `main` đã được bảo vệ ở bước 3. Script release hiện yêu cầu đúng chế độ này.
5. Nếu gói tài khoản hỗ trợ và đội có người duyệt, có thể thêm **Required reviewers**. Đây là phê duyệt deploy, tách biệt với review PR. Tính năng này có giới hạn gói riêng; Pro/Team không mặc nhiên có required environment reviewers cho repository private.

Nếu không thấy Environments hoặc deployment branch restrictions do repository private/gói tài khoản, cần xử lý gói tài khoản trước khi bật deploy. Chỉ viết `environment: production` trong YAML không tự tạo ra bảo vệ. [Tài liệu GitHub về environments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).

### Bước 5 — Kiểm tra SSH/Tailscale secrets

Trong **Settings → Environments → production → Environment secrets**, dùng **Add environment secret** cho từng tên:

- `SERVER_TAILSCALE_IP`: IP Tailscale của server; lần kiểm tra gần nhất là `100.112.174.58`.
- `SERVER_USER`: tài khoản deploy không phải root; server hiện dùng `capkimkhanh`.
- `SSH_PRIVATE_KEY`: toàn bộ private key Ed25519 của CI đang được server cho phép deploy. Cùng key này ký release manifest; không thay bằng key cá nhân một cách tùy ý.
- `SSH_KNOWN_HOSTS`: dòng host key đã xác minh của đúng server/IP trên. Giữ kiểm tra host key nghiêm ngặt.
- `TS_OAUTH_CLIENT_ID`: OAuth client ID Tailscale hiện có.
- `TS_OAUTH_SECRET`: OAuth client secret tương ứng. Workflow dùng `tag:ci`; client/tailnet policy phải cho phép tag này và đường SSH tới server.

CD cũ đã dùng SSH/Tailscale thành công, nên ưu tiên tái sử dụng các giá trị đúng đang có. Nếu secrets đang ở **Repository secrets**, job vẫn có thể đọc chúng khi environment chưa có secret cùng tên. GitHub không cho xem lại giá trị secret; chỉ chuyển sang environment khi có giá trị gốc, và không xoá cấu hình cũ trước khi kiểm chứng đường mới. Không đưa key/token vào chat, ảnh chụp hoặc PR. Không cần mở cổng SSH public cho server local.

### Bước 6 — Hoàn thành điều kiện phía server

Đây là bước chuẩn bị trước khi bật CD, không phải cài một dịch vụ backup chạy nền:

1. Cập nhật một lần forced-command script/policy theo phần **One-time host transition** bên trên. Wrapper cũ chưa hiểu lệnh `status` và signed release manifest của workflow mới.
2. Tái sử dụng thư mục backup `/home/capkimkhanh/backups/danasea` bằng tham số thứ sáu của installer. Mỗi lần deploy chỉ tạo một backup trước thay đổi dịch vụ, giữ tối đa **7 bản tổng cộng**, gồm backup cũ nhận diện được. Không cài timer/service backup định kỳ.
3. Giữ quyền đọc private GHCR packages của user deploy; không xoá image digest đang được chấp nhận hoặc còn cần rollback.
4. Chủ hệ thống đã xác nhận mức mặc định **10%**. Migration `V43__default_platform_commission.sql` tự thêm `PLATFORM_COMMISSION_RATE=10` khi chưa có, và giữ nguyên giá trị Admin đã cấu hình. Không cần gọi PUT chỉ để khởi tạo 10%. Sau deploy, đọc lại `GET /api/admin/settings/PLATFORM_COMMISSION_RATE` bằng token ADMIN để xác nhận. Khi muốn đổi tỷ lệ, dùng `PUT` cùng endpoint với trường `value` là chuỗi phần trăm từ `0` đến `100`. Trang FE Admin Settings hiện vẫn dùng mock/state cục bộ; nút lưu tại đó chưa ghi database.
5. Xác minh restricted SSH `status` trả về release đang được chấp nhận, và policy checker đọc được branch/environment. Khi có `gh` đã đăng nhập bằng tài khoản đủ quyền, có thể kiểm tra từ máy local:

```bash
python3 scripts/check_repository_controls.py --repository capkimkhanh2k5/DANASEA
```

Lệnh phải kết thúc thành công. Lỗi quyền API, gói tài khoản hoặc thiếu setting vẫn chặn release; không bỏ qua checker để ép deploy. Lần authoring này chưa cài wrapper mới hay deploy lên production.

### Bước 7 — Bật deploy sau khi mọi điều kiện đạt

1. Chờ **Required CI gate** xanh trên commit muốn merge; hoàn tất review theo quy tắc đã cấu hình.
2. Sau khi bước 3–6 đều đạt và đã chuẩn bị khôi phục phù hợp cho lần chuyển giao đầu tiên, sửa **Repository variable** `PRODUCTION_DEPLOY_ENABLED` thành `true`.
3. Merge PR đã review vào `main`. Không push trực tiếp lên `main`.
4. Mở **Actions → CI** của commit `main`: phải xanh. Sau đó **Actions → CD** phải build/scan đủ ba image và job deploy phải thực sự chạy, không ở trạng thái **skipped**.
5. Kiểm tra job deploy hoàn thành acceptance/stability và artifact `accepted-release` trỏ đúng SHA; đối chiếu với SSH `status` trên server.

Nếu đã merge trong lúc deploy tắt, sau khi chuẩn bị xong hãy bật biến rồi **Re-run all jobs** của lần CI có event **push** trên đúng commit `main` đã review. Không chọn CI của PR và không dùng **Run workflow** của CD để deploy thường: manual dispatch của CD dành cho rollback với SHA/run ID đã được chấp nhận. Nếu CI đang chạy cho commit đó, chờ nó hoàn tất trước khi quyết định rerun.

Nếu deploy lỗi, giữ nguyên gate và đọc job lỗi. Tắt biến chỉ ngăn job deploy được lên lịch ở lần sau; không dừng job đang chạy và không tự rollback server. Không bật deploy chỉ vì các job build đang xanh.
