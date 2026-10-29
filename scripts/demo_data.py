#!/usr/bin/env python3
"""Signed, serialized demo replacement with private durable recovery state."""
import argparse
import contextlib
import fcntl
import json
import os
import re
import secrets
import sys
import tempfile
from pathlib import Path

TERMINAL = {'complete', 'blocked', 'recovered', 'inspected'}
PRECOMMIT = {'authenticated', 'maintenance', 'guarded', 'backup_verified', 'rehearsal_verified'}


def atomic_json(path, value):
    path = Path(path)
    descriptor, temporary = tempfile.mkstemp(prefix='.' + path.name, dir=path.parent)
    try:
        with os.fdopen(descriptor, 'w') as output:
            os.fchmod(output.fileno(), 0o600)
            json.dump(value, output, sort_keys=True)
            output.write('\n')
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
        descriptor = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
    finally:
        Path(temporary).unlink(missing_ok=True)


class Journal(dict):
    def __init__(self, path, **values):
        super().__init__(values)
        self.path = path

    def save(self, phase=None):
        if phase:
            self['phase'] = phase
        atomic_json(self.path, self)


@contextlib.contextmanager
def deployment_lock(app_dir):
    descriptor = os.open(Path(app_dir) / '.deploy.lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        try:
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError('Another deployment or backup is in progress') from None
        yield
    finally:
        os.close(descriptor)


def unfinished(state):
    return [path for path in (state / 'operations').glob('*/journal.json')
            if json.loads(path.read_text()).get('phase') not in TERMINAL]


def execute(body, password, runtime, state):
    """External effects are injectable; persistence and state transitions are real."""
    state = Path(state)
    operations = state / 'operations'
    nonces = state / 'nonces'
    operations.mkdir(parents=True, mode=0o700, exist_ok=True)
    nonces.mkdir(mode=0o700, exist_ok=True)
    descriptor = os.open(state, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
    pending = unfinished(state)
    if pending and body['action'] == 'replace':
        raise ValueError('An unfinished operation requires a named restore')
    nonce = body['nonce']
    if not isinstance(nonce, str) or not re.fullmatch(r'[0-9a-f]{32}', nonce):
        raise ValueError('Invalid request nonce')
    try:
        descriptor = os.open(nonces / nonce, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError:
        raise ValueError('Request replay rejected') from None
    with os.fdopen(descriptor, 'w') as output:
        output.write(body['sha'] + '\n')
        output.flush()
        os.fsync(output.fileno())
    descriptor = os.open(nonces, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
    operation_id = secrets.token_hex(16)
    directory = operations / operation_id
    directory.mkdir(mode=0o700)
    descriptor = os.open(operations, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
    journal = Journal(directory / 'journal.json', operation_id=operation_id,
                      request=body, phase='authenticated', target_may_have_changed=False)
    journal.save()
    summary = {'operation_id': operation_id, 'action': body['action'], 'sha': body['sha']}
    maintenance = False
    commit_possible = False
    resume_attempted = False
    point = None
    try:
        runtime.preflight(body)
        # An interrupted previous operation owns the original DB role/limit settings.
        previous_operations = [json.loads(path.read_text()) for path in pending]
        for previous in previous_operations:
            if 'database_gate' in previous:
                journal['database_gate'] = previous['database_gate']
            if previous.get('recovery_point', {}).get('backup_id'):
                summary['backup_id'] = previous['recovery_point']['backup_id']
        if body['action'] == 'inspect':
            if pending and all(p.get('phase') in PRECOMMIT or p.get('target_may_have_changed') is False
                               for p in previous_operations):
                # Only a durable proof of no target commit permits backup-free recovery.
                # This also repairs a crash immediately after stopping services/gating DB.
                maintenance = True
                journal.save('maintenance')
                runtime.enter_maintenance(journal)
                runtime.verify(demo=False)
                journal.save('ready_to_resume')
                resume_attempted = True
                runtime.resume(journal)
                for path, previous in zip(pending, previous_operations):
                    previous.update(phase='recovered', recovered_by=operation_id)
                    atomic_json(path, previous)
            journal.save('inspected')
            return dict(summary, status='INSPECTED')
        selected = runtime.select_backup(body['backup_id']) if body['action'] == 'restore' else None
        journal.save('maintenance')
        maintenance = True
        runtime.enter_maintenance(journal)
        runtime.safe_state(journal)
        journal.save('guarded')
        point = runtime.snapshot(directory)
        journal['recovery_point'] = point
        summary['backup_id'] = point['backup_id']
        journal.save('backup_verified')
        if body['action'] == 'replace':
            runtime.rehearse(point, password)
            journal.save('rehearsal_verified')
        # Set before the call: a crash or missing importer receipt is an uncertain commit.
        journal['target_may_have_changed'] = True
        journal.save('commit_possible')
        commit_possible = True
        if selected:
            runtime.restore(selected)
        else:
            result = runtime.replace(point, password, directory)
            summary['canonical_records'] = result['totalVerifiedInDb']
        runtime.invalidate()
        journal.save('verifying')
        runtime.verify(demo=selected is None, password=password)
        journal.save('ready_to_resume')
        resume_attempted = True
        runtime.resume(journal)
        journal.save('complete')
        for path in pending:
            previous = json.loads(path.read_text())
            previous.update(phase='recovered', recovered_by=operation_id)
            atomic_json(path, previous)
        return dict(summary, status='RESTORED' if selected else 'REPLACED')
    except (Exception, KeyboardInterrupt):
        # Never propagate exception text: subprocess outputs may contain secrets.
        if not maintenance:
            journal.save('blocked')
            return dict(summary, status='BLOCKED')
        if resume_attempted:
            # Public access may already have resumed. Never discard newly accepted writes.
            journal.save('recovery_required')
            try:
                runtime.freeze(journal)
            except Exception:
                pass
            return dict(summary, status='RECOVERY_REQUIRED')
        try:
            if commit_possible:
                journal.save('recovering')
                runtime.freeze(journal)
                runtime.restore(point)
                runtime.invalidate()
            runtime.verify(demo=False)
            runtime.resume(journal)
            journal.save('recovered' if commit_possible else 'blocked')
            return dict(summary, status='ROLLED_BACK' if commit_possible else 'BLOCKED')
        except (Exception, KeyboardInterrupt):
            journal.save('recovery_required')
            try:
                runtime.freeze(journal)
            except Exception:
                pass
            return dict(summary, status='RECOVERY_REQUIRED')


def trusted(path, directory=False):
    path = Path(path)
    status = path.lstat()
    if path.is_symlink() or status.st_uid != 0 or status.st_mode & 0o022:
        raise ValueError('Untrusted deployment path')
    if directory and (not path.is_dir() or status.st_mode & 0o077):
        raise ValueError('Private state directory must have mode 700')
    return path


def private_state(path):
    path = Path(path)
    status = path.lstat()
    if (path.is_symlink() or not path.is_dir() or status.st_uid != os.geteuid()
            or status.st_mode & 0o077):
        raise ValueError('Private state must belong to the deployment account with mode 700')
    return path


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sha', required=True)
    parser.add_argument('--policy', required=True, type=Path)
    args = parser.parse_args()
    try:
        if not re.fullmatch(r'[0-9a-f]{40}', args.sha):
            raise ValueError('Invalid invocation')
        tools = Path(__file__).resolve().parent
        trusted(tools)
        for name in ('demo_data.py', 'demo_runtime.py', 'demo_request.py', 'backup.py', 'release.py'):
            trusted(tools / name)
        policy = json.loads(trusted(args.policy).read_text())
        signers = trusted(Path(policy.get('demo_signers', '/etc/danasea/demo-data-signers')))
        import demo_request
        from demo_runtime import Runtime
        envelope = sys.stdin.read(131073)
        if len(envelope.encode()) > 131072:
            raise ValueError('Oversized request')
        body, password = demo_request.verify(envelope, signers, args.sha, policy['repository'])
        state = Path(policy.get('demo_state_dir', '/var/lib/danasea/demo-data'))
        state.mkdir(mode=0o700, parents=True, exist_ok=True)
        private_state(state)
        app = Path(policy['app_dir'])
        if not app.is_absolute() or not app.is_dir() or state.is_relative_to(app.resolve()):
            raise ValueError('Invalid application/state directory')
        with deployment_lock(app):
            summary = execute(body, password, Runtime(policy, state), state)
        print(json.dumps(summary))
        return 0 if summary['status'] in ('INSPECTED', 'REPLACED', 'RESTORED') else 1
    except (Exception, KeyboardInterrupt):
        print(json.dumps({'status': 'BLOCKED'}))
        return 1


if __name__ == '__main__':
    sys.exit(main())
