"""Assemble the inference runtime and retain OS package provenance for copied libraries.

The HTTP worker does not expose interactive curses/readline, Tk, SQLite, DBM or libuuid
extensions. Python's uuid module retains its standard os.urandom implementation.
No shell, package manager, mount/login utilities or build tools enter the final image.
"""
import re
import shutil
import subprocess
from pathlib import Path

target = Path('/runtime')
shutil.copytree('/usr/local', target / 'usr/local', symlinks=True)
stdlib = target / 'usr/local/lib/python3.11'
for pattern in ('_curses*.so', 'readline*.so', '_sqlite3*.so', '_uuid*.so', '_tkinter*.so', '_dbm*.so', '_gdbm*.so', 'nis*.so'):
    for path in (stdlib / 'lib-dynload').glob(pattern):
        path.unlink()
for directory in (stdlib / 'ensurepip', stdlib / 'test'):
    shutil.rmtree(directory, ignore_errors=True)
# pip is a build dependency, not a runtime dependency.
for path in (stdlib / 'site-packages').glob('pip*'):
    if path.is_dir():
        shutil.rmtree(path)
for path in (target / 'usr/local/bin').glob('pip*'):
    path.unlink()

libraries = set()
objects = [target / 'usr/local/bin/python3.11', *(target / 'usr/local').rglob('*.so'), *(target / 'usr/local').rglob('*.so.*')]
for path in objects:
    result = subprocess.run(['ldd', str(path)], capture_output=True, text=True, check=False)
    if 'not found' in result.stdout:
        raise SystemExit('Unresolved runtime dependency: ' + str(path))
    for value in re.findall(r'(?:=>\s*)?(/[^\s()]+)', result.stdout):
        library = Path(value)
        if library.is_file() and not str(library).startswith('/usr/local/') and not str(library).startswith('/runtime/'):
            libraries.add(library)
packages = set()
for library in libraries:
    relative = library.relative_to('/')
    if relative.parts[0] in ('lib', 'lib64'):
        relative = Path('usr') / relative
    destination = target / relative
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(library, destination)
    # Retain Debian's real package/version records so scanning copied shared
    # libraries remains honest rather than making the inventory disappear.
    for candidate in (library, library.resolve()):
        result = subprocess.run(['dpkg-query', '-S', str(candidate)], capture_output=True, text=True, check=False)
        matches = [re.match(r'^([a-z0-9][a-z0-9+.-]*(?::[a-z0-9]+)?): /', line) for line in result.stdout.splitlines()]
        owners = [match[1] for match in matches if match]
        if result.returncode == 0 and owners:
            packages.update(owners)
            break
    else:
        raise SystemExit('Missing OS provenance for runtime library: ' + str(library))
metadata = target / 'var/lib/dpkg/status.d'
metadata.mkdir(parents=True, exist_ok=True)
for index, package in enumerate(sorted(packages)):
    result = subprocess.run(['dpkg-query', '-s', package], capture_output=True, text=True, check=True)
    (metadata / f'python-runtime-{index}').write_text(result.stdout)
print('Runtime OS packages:', ', '.join(sorted(packages)))
