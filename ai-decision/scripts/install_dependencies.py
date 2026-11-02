"""Install the locked Torch release from the CPU index before other worker dependencies."""
import re
import subprocess
import sys
from pathlib import Path


def install(lockfile):
    requirements = [line.strip() for line in lockfile.read_text().splitlines()]
    torch_requirements = [line for line in requirements if re.match(r'^torch(?:[=<>!~@\s\[])', line)]
    if len(torch_requirements) != 1 or not re.fullmatch(r'torch==[A-Za-z0-9.+_-]+', torch_requirements[0]):
        raise ValueError('Worker lock must contain exactly one pinned torch==version requirement')
    torch_requirement = torch_requirements[0]
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', '--no-cache-dir',
                           '--index-url', 'https://download.pytorch.org/whl/cpu', torch_requirement])
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', '--no-cache-dir', '-r', str(lockfile)])
    subprocess.check_call([sys.executable, '-c',
                           'import torch\nif torch.version.cuda is not None:\n    raise SystemExit("Worker requires CPU-only Torch")'])


if __name__ == '__main__':
    install(Path(sys.argv[1]))
