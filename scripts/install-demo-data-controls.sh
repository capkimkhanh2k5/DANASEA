#!/usr/bin/env bash
# One-time reviewed root upgrade; does not connect to or modify the database.
set -Eeuo pipefail
umask 077
if [[ $EUID != 0 || $# != 1 ]]; then
  echo "Usage (root): $0 <existing-CI-ssh-public-key-file>" >&2
  exit 2
fi
for program in python3 ssh-keygen; do
  command -v "$program" > /dev/null || { echo "Install required host tool: $program" >&2; exit 1; }
done
ssh-keygen -lf "$1" > /dev/null
source_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# Validate all existing trust and input before replacing any installed file. The
# accepted release namespace/key remains untouched; demo has a separate policy.
python3 - "$source_dir" "$1" <<'PY'
import json
import os
import pathlib
import pwd
import stat
import sys
import tempfile

source, public = map(pathlib.Path, sys.argv[1:])
tools = pathlib.Path('/usr/local/lib/danasea')
wrapper = pathlib.Path('/usr/local/bin/danasea-deploy')
scripts = ('demo_data.py', 'demo_runtime.py', 'demo_request.py', 'backup.py', 'release.py', 'acceptance.py', 'availability.py')


def trusted(path, directory=False):
    status = path.lstat()
    expected = stat.S_ISDIR if directory else stat.S_ISREG
    if not expected(status.st_mode) or status.st_uid != 0 or status.st_mode & 0o022:
        raise ValueError('Existing host control must be root-owned and not writable: ' + str(path))


def absolute(value):
    if not isinstance(value, str) or not value or not pathlib.Path(value).is_absolute() or '..' in pathlib.Path(value).parts:
        raise ValueError('Invalid host control path')
    return pathlib.Path(value)


def trusted_parents(path):
    for parent in reversed(path.parents):
        trusted(parent, directory=True)


def atomic(path, data, mode=0o755):
    descriptor, name = tempfile.mkstemp(prefix='.' + path.name, dir=path.parent)
    try:
        with os.fdopen(descriptor, 'wb') as output:
            os.fchmod(output.fileno(), mode)
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        os.replace(name, path)
        directory = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        pathlib.Path(name).unlink(missing_ok=True)


try:
    if sys.version_info < (3, 11):
        raise ValueError('Python 3.11 or newer is required')
    policy_path = pathlib.Path('/etc/danasea/deploy.json')
    trusted_parents(policy_path)
    trusted(policy_path)
    policy = json.loads(policy_path.read_text())
    app = absolute(policy['app_dir'])
    if not app.is_dir() or app.is_symlink() or app.stat().st_uid == 0:
        raise ValueError('Existing app directory must belong to an unprivileged deploy account')
    owner = pwd.getpwuid(app.stat().st_uid)
    release = absolute(policy['signers'])
    trusted_parents(release)
    trusted(release)
    parts = public.read_text().strip().split()
    if len(parts) < 2 or parts[0] != 'ssh-ed25519':
        raise ValueError('Use the existing CI Ed25519 deployment key')
    key = ' '.join(parts[:2])
    if release.read_text() != 'github-actions namespaces="danasea-release" ' + key + '\n':
        raise ValueError('CI key differs from existing release trust; explicit rotation required')
    signers = absolute(policy.get('demo_signers', '/etc/danasea/demo-data-signers'))
    trusted_parents(signers)
    content = ('github-actions namespaces="danasea-demo-data" ' + key + '\n').encode()
    if signers.exists() or signers.is_symlink():
        trusted(signers)
        if signers.read_bytes() != content:
            raise ValueError('Existing demo trust differs; explicit rotation required')
    trusted_parents(wrapper)
    trusted(wrapper)
    trusted_parents(tools)
    trusted(tools, directory=True)
    for name in scripts + ('deploy.sh',):
        if not (source / name).is_file() or (source / name).is_symlink():
            raise ValueError('Reviewed demo tools are incomplete')
    state = absolute(policy.get('demo_state_dir', '/var/lib/danasea/demo-data'))
    # Parent directories are root-owned; only the private leaf belongs to deploy.
    missing = []
    parent = state.parent
    while not parent.exists():
        missing.append(parent)
        parent = parent.parent
    trusted(parent, directory=True)
    trusted_parents(parent)
    if state.exists() or state.is_symlink():
        status = state.lstat()
        if not stat.S_ISDIR(status.st_mode) or status.st_uid != owner.pw_uid or status.st_mode & 0o077:
            raise ValueError('Existing demo state must be private and owned by deploy account')
    for parent in reversed(missing):
        parent.mkdir(mode=0o755)
        parent.chmod(0o755)
    if not state.exists():
        state.mkdir(mode=0o700)
        os.chown(state, owner.pw_uid, owner.pw_gid)
    atomic(signers, content, 0o644)
    for name in scripts:
        atomic(tools / name, (source / name).read_bytes())
    # The existing forced command remains active until every helper is installed.
    atomic(wrapper, (source / 'deploy.sh').read_bytes())
except (ValueError, OSError, KeyError, TypeError) as error:
    print('Demo bootstrap rejected: ' + str(error), file=sys.stderr)
    raise SystemExit(1)
PY
printf '%s\n' 'Installed demo controls and private recovery state. Existing release trust is unchanged; no database operation was run.'
