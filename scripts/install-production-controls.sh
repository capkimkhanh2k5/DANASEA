#!/usr/bin/env bash
# Run once on the production Linux host after reviewing this release.
set -Eeuo pipefail
umask 077
if [[ $EUID != 0 || ( $# != 5 && $# != 6 ) ]]; then
  echo "Usage (root): $0 <deploy-user> <app-dir> <https-origin> <CI-ssh-public-key-file> <legacy-CD-run-id> [local-backup-directory]" >&2
  exit 2
fi
user="$1"; app_dir="$2"; origin="$3"; public_key="$4"; legacy_run="$5"
[[ "$user" =~ ^[a-z_][a-z0-9_-]*$ && "$app_dir" == /* && -d "$app_dir" && "$legacy_run" =~ ^[1-9][0-9]*$ ]] || exit 2
[[ "$(id -u "$user")" != 0 ]] || { echo "Use an unprivileged deployment account, not root" >&2; exit 1; }
[[ "$(stat -c %U "$app_dir")" == "$user" ]] || { echo "App directory must belong to the deploy user" >&2; exit 1; }
for program in python3 ssh-keygen flock docker; do
  command -v "$program" > /dev/null || { echo "Install required host tool: $program" >&2; exit 1; }
done
python3 -c 'import sys; sys.exit("Python 3.11 or newer is required") if sys.version_info < (3, 11) else None'
ssh-keygen -lf "$public_key" > /dev/null
home_dir="$(getent passwd "$user" | cut -d: -f6)"
backup_dir="${6:-$home_dir/BackUp/danasea}"
[[ "$backup_dir" == /* ]] || exit 2
source_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
install -o root -g root -m 755 -d /etc/danasea
python3 - "$app_dir" "$origin" "$public_key" "$backup_dir" "$source_dir" <<'PY'
import json, pathlib, re, sys
sys.path.insert(0, sys.argv[5])
from acceptance import validate_url
app, origin, public_key, backup_dir = sys.argv[1:5]
validate_url(origin)
parts = pathlib.Path(public_key).read_text().strip().split()
if len(parts) < 2 or parts[0] != 'ssh-ed25519':
    raise SystemExit('Use the existing CI deployment Ed25519 public key')
# Refuse to silently overwrite an existing host policy or signer rotation.
values = {
    'release-signers': 'github-actions namespaces="danasea-release" ' + ' '.join(parts[:2]) + '\n',
    'deploy.json': json.dumps({'repository': 'capkimkhanh2k5/DANASEA', 'app_dir': app, 'public_origin': origin,
        'signers': '/etc/danasea/release-signers', 'backup_config': '/etc/danasea/backup.json',
        'stability_seconds': 120, 'max_outage_seconds': 60}, indent=2) + '\n',
    'backup.json': json.dumps({'container': 'danasea-postgres', 'directory': backup_dir, 'keep': 7}, indent=2) + '\n'}
for name, content in values.items():
    path = pathlib.Path('/etc/danasea') / name
    if path.exists() and path.read_text() != content:
        raise SystemExit('Existing host policy differs: review and rotate explicitly: ' + str(path))
for name, content in values.items():
    path = pathlib.Path('/etc/danasea') / name
    path.write_text(content)
    path.chmod(0o644)
PY
install -o root -g root -m 755 -d /usr/local/lib/danasea
for script in release.py backup.py acceptance.py availability.py; do
  install -o root -g root -m 755 "$source_dir/$script" "/usr/local/lib/danasea/$script"
done
install -o "$user" -g "$(id -gn "$user")" -m 700 -d "$backup_dir"
# Adopt only healthy, already deployed SHA-tagged images with known registry digests.
if [[ ! -f "$app_dir/.releases/$(cat "$app_dir/.deploy-state").json" ]]; then
  runuser -u "$user" -- python3 /usr/local/lib/danasea/release.py adopt-running \
    --root "$app_dir" --repository capkimkhanh2k5/DANASEA --run-id "$legacy_run"
fi
# Keep the existing forced command until policy validation and adoption succeed.
wrapper_tmp="$(mktemp /usr/local/bin/.danasea-deploy.XXXXXX)"
trap 'rm -f -- "$wrapper_tmp"' EXIT
install -o root -g root -m 755 "$source_dir/deploy.sh" "$wrapper_tmp"
mv -f -- "$wrapper_tmp" /usr/local/bin/danasea-deploy
printf '%s\n' 'Installed root-owned release controls. Backups run only before deployment and retain at most seven archives; no backup schedule is installed.'
