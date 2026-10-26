#!/usr/bin/env bash
# DanaSea production deploy entrypoint (runs on the home server).
#
# Installed as a root-owned forced SSH command so the CI key can do nothing else:
#   sudo install -m 755 scripts/deploy.sh /usr/local/bin/danasea-deploy
#   ~/.ssh/authorized_keys:
#     command="/usr/local/bin/danasea-deploy",restrict ssh-ed25519 AAAA... github-actions-deploy
#
# Usage (via SSH or locally): deploy <40-char git sha>
set -Eeuo pipefail
umask 077

APP_DIR="${APP_DIR:-$HOME/danasea}"
COMPOSE_FILE="docker-compose.prod.yml"
STATE_FILE="$APP_DIR/.deploy-state"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups/danasea}"
BACKUP_KEEP=7
WAIT_TIMEOUT=300

log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }

compose() {
  local tag="$1"; shift
  IMAGE_TAG="$tag" docker compose -f "$COMPOSE_FILE" "$@"
}

# --- Validate input ("deploy <sha>" from SSH_ORIGINAL_COMMAND or CLI args) ---
read -r action tag _ <<< "${SSH_ORIGINAL_COMMAND:-$*}" || true
if [[ "${SSH_ORIGINAL_COMMAND:-$*}" != "deploy ${tag:-}" || "${action:-}" != "deploy" || ! "${tag:-}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Usage: deploy <40-char git sha>" >&2
  exit 2
fi

cd "$APP_DIR"

# --- Prevent concurrent deploys ---
exec 9>"$APP_DIR/.deploy.lock"
flock -n 9 || { echo "Another deploy is in progress" >&2; exit 1; }

prev_tag="$(cat "$STATE_FILE" 2>/dev/null || true)"
if [[ -n "$prev_tag" && ! "$prev_tag" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Invalid previous release state" >&2
  exit 2
fi
log "Deploying ${tag:0:7} (previous: ${prev_tag:0:7})"

prepare_public_config() {
  # Only this tracked, non-secret file is mounted into a container with another UID.
  if [[ -f frontend/nginx.prod.conf ]]; then
    chmod 644 frontend/nginx.prod.conf
  fi
}

diagnostics() {
  compose "$1" ps --all || true
  compose "$1" logs --tail 50 frontend redis rabbitmq backend quyet 2>&1 \
    | sed -E 's/(Using generated security password: )[[:alnum:]-]+/\1[REDACTED]/g' || true
  docker inspect --format '{{.Name}} {{json .State.Health}}' \
    danasea-frontend danasea-backend danasea-redis danasea-rabbitmq danasea-quyet cli-proxy-api 2>/dev/null || true
}

rollback_ready=false
rollback() {
  if [[ -z "$prev_tag" ]]; then
    log "No previous release to roll back to"
    return
  fi
  if [[ "$rollback_ready" != true ]]; then
    log "Rollback blocked: infrastructure compatibility is unverified or changed; keep current data services and recover forward"
    return
  fi
  log "Rolling back to ${prev_tag:0:7}"
  git checkout --quiet --force --detach "$prev_tag"
  prepare_public_config
  # Never downgrade or recreate databases/brokers during application rollback.
  if ! compose "$prev_tag" up -d --no-deps --wait --wait-timeout "$WAIT_TIMEOUT" quyet backend frontend; then
    log "Application rollback failed, manual action required"
    diagnostics "$prev_tag"
  fi
}

# --- 1. Sync compose/config files to the exact commit being deployed ---
git fetch --quiet origin main
git cat-file -e "${tag}^{commit}"
git merge-base --is-ancestor "$tag" origin/main || { echo "Release is not on origin/main" >&2; exit 1; }
git checkout --quiet --force --detach "$tag"
prepare_public_config

# Fail before changing running services when private files, keys or the subnet are not ready.
python3 scripts/production.py check

# Render private manifests without checking out the previous release. Comparing full
# data-service/network configs also catches changes to credentials and storage paths.
runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/danasea-deploy.XXXXXX")"
trap 'rm -rf -- "$runtime_dir"' EXIT
if [[ -n "$prev_tag" ]] \
  && git show "$prev_tag:$COMPOSE_FILE" > "$runtime_dir/rollback-source.yml" \
  && IMAGE_TAG="$prev_tag" docker compose --project-directory "$APP_DIR" \
    -f "$runtime_dir/rollback-source.yml" config --format json > "$runtime_dir/previous.json" 2>/dev/null \
  && compose "$tag" config --format json > "$runtime_dir/current.json" 2>/dev/null; then
  if python3 - "$runtime_dir/previous.json" "$runtime_dir/current.json" <<'PY'
import json
import sys

try:
    with open(sys.argv[1]) as source:
        previous = json.load(source)
    with open(sys.argv[2]) as source:
        current = json.load(source)
    services = ('postgres', 'redis', 'rabbitmq')
    compatible = all(previous['services'].get(name) == current['services'].get(name)
                     for name in services)
    # Roll back the worker image with the backend only when its mount/runtime contract is unchanged.
    old_worker = dict(previous['services'].get('quyet') or {})
    new_worker = dict(current['services'].get('quyet') or {})
    old_worker.pop('image', None)
    new_worker.pop('image', None)
    compatible = compatible and bool(old_worker) and old_worker == new_worker
    compatible = compatible and all(previous.get(key) == current.get(key)
                                    for key in ('networks', 'volumes'))
except (OSError, ValueError, KeyError, TypeError):
    compatible = False
sys.exit(0 if compatible else 1)
PY
  then
    rollback_ready=true
  fi
fi
if [[ "$rollback_ready" != true && -n "$prev_tag" ]]; then
  log "Automatic rollback disabled for this release: data services or network/storage configuration differ"
fi

# --- 2. Backup database before touching anything ---
if [[ -n "$(compose "$tag" ps -q postgres 2>/dev/null)" ]]; then
  mkdir -p "$BACKUP_DIR"
  backup_file="$BACKUP_DIR/pre-${tag:0:7}-$(date +%Y%m%d%H%M%S).sql.gz"
  log "Backing up database -> $backup_file"
  compose "$tag" exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' | gzip > "$backup_file"
  ls -1t "$BACKUP_DIR"/pre-*.sql.gz | tail -n +$((BACKUP_KEEP + 1)) | xargs -r rm --
fi

# --- 3. Pull prebuilt images (no building on this 4GB box) ---
log "Pulling images"
compose "$tag" pull

# --- 4. Start and wait for healthchecks; roll back on failure ---
log "Starting containers"
if ! compose "$tag" up -d --wait --wait-timeout "$WAIT_TIMEOUT" --remove-orphans; then
  log "Deploy failed healthchecks"
  diagnostics "$tag"
  rollback
  exit 1
fi

echo "$tag" > "$STATE_FILE"

# --- 5. Clean up images unused for a week (running + recent ones are kept for rollback) ---
docker image prune -af --filter "until=168h" > /dev/null

log "Deploy ${tag:0:7} succeeded"
