#!/usr/bin/env bash
# Install root-owned with scripts/install-production-controls.sh. The CI key is a
# restricted forced command; its signed release envelope arrives on standard input.
set -Eeuo pipefail
umask 077
APP_DIR="${APP_DIR:-}"
TOOLS_DIR="${TOOLS_DIR:-/usr/local/lib/danasea}"
DEPLOY_POLICY="${DEPLOY_POLICY:-/etc/danasea/deploy.json}"
COMPOSE_FILE="docker-compose.prod.yml"
STATE_FILE="$APP_DIR/.deploy-state"
WAIT_TIMEOUT=300
log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }

resolve_app_dir() {
  if [[ -z "$APP_DIR" ]]; then
    APP_DIR="$(python3 - "$DEPLOY_POLICY" <<'PYCODE'
import pathlib, sys, json
p = pathlib.Path(sys.argv[1])
if p.stat().st_uid != 0 or p.stat().st_mode & 0o022:
    raise SystemExit('Deployment policy must be root-owned and not group/other writable')
value = json.loads(p.read_text())['app_dir']
if not isinstance(value, str) or not pathlib.Path(value).is_absolute() or not pathlib.Path(value).is_dir():
    raise SystemExit('Invalid deployment app directory')
print(value)
PYCODE
)"
  fi
  STATE_FILE="$APP_DIR/.deploy-state"
}

if [[ "${SSH_ORIGINAL_COMMAND:-$*}" == status ]]; then
  resolve_app_dir
  cd "$APP_DIR"
  exec 9>"$APP_DIR/.deploy.lock"
  flock -s -w 60 9
  python3 - "$APP_DIR" "$TOOLS_DIR" <<'PYCODE'
import pathlib, re, sys
sys.path.insert(0, sys.argv[2])
import release
root = pathlib.Path(sys.argv[1])
sha = (root / '.deploy-state').read_text().strip()
if not re.fullmatch(r'[0-9a-f]{40}', sha):
    raise SystemExit('Invalid deployment state')
body = release.read(root / '.releases' / (sha + '.json'), sha=sha)
import json
print(json.dumps(body))
PYCODE
  exit 0
fi

read -r action tag _ <<< "${SSH_ORIGINAL_COMMAND:-$*}" || true
if [[ "${SSH_ORIGINAL_COMMAND:-$*}" != "${action:-} ${tag:-}" || ! "${action:-}" =~ ^(deploy|rollback|demo-data)$ || ! "${tag:-}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Usage: deploy|rollback|demo-data <40-char git sha>" >&2
  exit 2
fi
if [[ "$action" == demo-data ]]; then
  exec python3 "$TOOLS_DIR/demo_data.py" --sha "$tag" --policy "$DEPLOY_POLICY"
fi
resolve_app_dir
cd "$APP_DIR"
exec 9>"$APP_DIR/.deploy.lock"
flock -n 9 || { echo "Another deploy or backup is in progress" >&2; exit 1; }
runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/danasea-deploy.XXXXXX")"
monitor_pid=''
cleanup() {
  if [[ -n "$monitor_pid" ]]; then kill "$monitor_pid" 2>/dev/null || true; wait "$monitor_pid" 2>/dev/null || true; fi
  rm -rf -- "$runtime_dir"
}
trap cleanup EXIT

# Policy is installed by root outside the repository. Never source a shell .env.
python3 - "$DEPLOY_POLICY" > "$runtime_dir/policy" <<'PY'
import json, os, pathlib, sys
p = pathlib.Path(sys.argv[1])
if p.stat().st_uid != 0 or p.stat().st_mode & 0o022:
    raise SystemExit('Deployment policy must be root-owned and not group/other writable')
v = json.loads(p.read_text())
for name in ('repository', 'public_origin', 'signers', 'backup_config'):
    value = v[name]
    if not isinstance(value, str) or not value or any(c.isspace() for c in value):
        raise SystemExit('Invalid deployment policy field')
    print(value)
seconds = v.get('stability_seconds', 120)
if type(seconds) is not int or not 30 <= seconds <= 900:
    raise SystemExit('Stability window must be between 30 and 900 seconds')
print(seconds)
budget = v.get('max_outage_seconds', 60)
if type(budget) is not int or not 1 <= budget <= 900:
    raise SystemExit('Outage budget must be between 1 and 900 seconds')
print(budget)
signers = pathlib.Path(v['signers'])
if signers.stat().st_uid != 0 or signers.stat().st_mode & 0o022:
    raise SystemExit('Release signer policy must be root-owned and not group/other writable')
PY
policy=()
while IFS= read -r value; do policy+=("$value"); done < "$runtime_dir/policy"
repository="${policy[0]}"
public_origin="${policy[1]}"
signers="${policy[2]}"
backup_config="${policy[3]}"
stability_seconds="${policy[4]}"
max_outage_seconds="${policy[5]}"
python3 "$TOOLS_DIR/release.py" verify --sha "$tag" --repository "$repository" \
  --action "$action" --signers "$signers" --output "$runtime_dir/release.json"

prev_tag="$(cat "$STATE_FILE" 2>/dev/null || true)"
if [[ -n "$prev_tag" && ! "$prev_tag" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Invalid previous release state" >&2; exit 2
fi
python3 - "$runtime_dir/release.json" "$prev_tag" <<'PYCODE'
import json, sys
if json.load(open(sys.argv[1])).get('baseline_sha') != sys.argv[2]:
    raise SystemExit('Deployment state changed since rehearsal; rerun release verification')
PYCODE
# Reject altered images on an already known SHA before touching the checkout.
if [[ -f "$APP_DIR/.releases/$tag.json" ]]; then
  python3 - "$APP_DIR/.releases/$tag.json" "$runtime_dir/release.json" <<'PY'
import json, sys
old, new = [json.load(open(p)) for p in sys.argv[1:]]
if old['images'] != new['images'] or old['repository'] != new['repository']:
    raise SystemExit('Refusing image replacement for an existing release')
PY
fi

compose() {
  local release="$1"; shift
  local manifest="$APP_DIR/.releases/$release.json"
  [[ "$release" != "$tag" ]] || manifest="$runtime_dir/release.json"
  python3 "$TOOLS_DIR/release.py" images "$manifest" > "$runtime_dir/images"
  local backend_image='' frontend_image='' quyet_image='' key value
  while IFS='=' read -r key value; do
    case "$key" in
      BACKEND_IMAGE) backend_image="$value" ;;
      FRONTEND_IMAGE) frontend_image="$value" ;;
      QUYET_IMAGE) quyet_image="$value" ;;
      *) return 1 ;;
    esac
  done < "$runtime_dir/images"
  IMAGE_TAG="$release" BACKEND_IMAGE="$backend_image" FRONTEND_IMAGE="$frontend_image" \
    QUYET_IMAGE="$quyet_image" docker compose -f "$COMPOSE_FILE" "$@"
}
prepare_public_config() { chmod 644 frontend/nginx.prod.conf; }
diagnostics() {
  # Application logs may contain customer/provider data; retain them on the host.
  compose "$1" ps --all || true
  docker inspect --format '{{.Name}} {{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' \
    danasea-frontend danasea-backend danasea-redis danasea-rabbitmq danasea-quyet cli-proxy-api 2>/dev/null || true
}

log "Preparing $action ${tag:0:7} (previous: ${prev_tag:0:7})"
git fetch --quiet origin main
if [[ "$action" == deploy ]]; then
  python3 "$TOOLS_DIR/release.py" history --root "$APP_DIR" --sha "$tag" --previous "$prev_tag"
else
  [[ -n "$prev_tag" && -f "$APP_DIR/.releases/$tag.json" ]] || { echo "Rollback requires a previously accepted digest release" >&2; exit 1; }
  git merge-base --is-ancestor "$tag" "$prev_tag"
  python3 "$TOOLS_DIR/release.py" compatible --root "$APP_DIR" --sha "$tag" --previous "$prev_tag"
fi
git checkout --quiet --force --detach "$tag"
prepare_public_config
python3 scripts/production.py check

rollback_ready=false
if [[ -n "$prev_tag" && -f "$APP_DIR/.releases/$prev_tag.json" ]] \
  && python3 "$TOOLS_DIR/release.py" compatible --root "$APP_DIR" --sha "$tag" --previous "$prev_tag" \
  && git show "$prev_tag:$COMPOSE_FILE" > "$runtime_dir/rollback-source.yml" \
  && IMAGE_TAG="$prev_tag" docker compose --project-directory "$APP_DIR" -f "$runtime_dir/rollback-source.yml" config --format json > "$runtime_dir/previous.json" \
  && compose "$tag" config --format json > "$runtime_dir/current.json"; then
  if python3 - "$runtime_dir/previous.json" "$runtime_dir/current.json" <<'PY'
import json, sys
previous, current = [json.load(open(p)) for p in sys.argv[1:]]
compatible = all(previous['services'].get(n) == current['services'].get(n) for n in ('postgres', 'redis', 'rabbitmq', 'cli-proxy-api'))
old_worker, new_worker = [dict(c['services'].get('quyet') or {}) for c in (previous, current)]
for worker in (old_worker, new_worker):
    worker.pop('image', None)
compatible = compatible and bool(old_worker) and old_worker == new_worker
compatible = compatible and all(previous.get(k) == current.get(k) for k in ('networks', 'volumes'))
sys.exit(0 if compatible else 1)
PY
  then rollback_ready=true; fi
fi
# Explicit rollback also obeys infrastructure compatibility, not just SQL history.
if [[ "$action" == rollback && "$rollback_ready" != true ]]; then
  echo "Rollback blocked: infrastructure compatibility is unverified or changed" >&2; exit 1
fi
rollback() {
  if [[ "$rollback_ready" != true ]]; then
    log "Rollback blocked: schema/infrastructure compatibility is unverified or changed; recover forward"
    return 1
  fi
  log "Rolling back applications to ${prev_tag:0:7}"
  git checkout --quiet --force --detach "$prev_tag" || return 1
  prepare_public_config || return 1
  compose "$prev_tag" up -d --no-deps --wait --wait-timeout "$WAIT_TIMEOUT" quyet backend frontend || return 1
  python3 "$TOOLS_DIR/acceptance.py" --url "$public_origin" --stability-seconds 30 || return 1
}

# The local dump must restore successfully into a disposable database before migration.
python3 "$TOOLS_DIR/backup.py" --config "$backup_config"
compose "$tag" pull
started=$SECONDS
python3 "$TOOLS_DIR/availability.py" --url "$public_origin/api/services?size=1" \
  --stop-file "$runtime_dir/monitor.stop" --output "$runtime_dir/availability.json" \
  --max-outage-seconds "$max_outage_seconds" &
monitor_pid=$!
failed=false
# Keep the public applications running while dependencies and the model start.
# --no-deps prevents Compose from recreating an already verified dependency.
if ! compose "$tag" up -d --wait --wait-timeout "$WAIT_TIMEOUT" postgres redis rabbitmq cli-proxy-api \
  || ! compose "$tag" run --rm --no-deps migrate \
  || ! compose "$tag" up -d --no-deps --wait --wait-timeout "$WAIT_TIMEOUT" quyet \
  || ! compose "$tag" up -d --no-deps --wait --wait-timeout "$WAIT_TIMEOUT" backend \
  || ! compose "$tag" up -d --no-deps --wait --wait-timeout "$WAIT_TIMEOUT" frontend \
  || ! python3 "$TOOLS_DIR/acceptance.py" --url "$public_origin" --stability-seconds "$stability_seconds"; then
  failed=true
fi
touch "$runtime_dir/monitor.stop"
if ! wait "$monitor_pid"; then failed=true; fi
monitor_pid=''
event_path="$APP_DIR/.release-events/$tag-$(date -u +%Y%m%dT%H%M%S)-$$.json"
if [[ -f "$runtime_dir/availability.json" ]] \
  && mkdir -p "$APP_DIR/.release-events" \
  && cp "$runtime_dir/availability.json" "$event_path"; then
  log "Availability evidence retained: $event_path"
  cat "$event_path" || true
else
  failed=true
  log "Availability evidence unavailable; release cannot be accepted"
fi
if [[ "$failed" == true ]]; then
  log "Release failed healthchecks, acceptance or its outage budget"
  diagnostics "$tag"
  rollback || log "Manual recovery required; successful release state was not advanced"
  exit 1
fi
cp "$runtime_dir/availability.json" "$APP_DIR/.release-events/$tag.json"
python3 "$TOOLS_DIR/release.py" publish "$runtime_dir/release.json" --root "$APP_DIR"
log "Release ${tag:0:7} accepted; deploy/verification elapsed $((SECONDS - started)) seconds"
# Housekeeping cannot turn an accepted deployment into a failed release.
docker image prune -af --filter "until=168h" > /dev/null || log "Image cleanup failed; release remains accepted"
