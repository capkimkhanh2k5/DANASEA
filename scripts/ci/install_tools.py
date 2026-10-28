#!/usr/bin/env python3
"""Install version- and checksum-pinned release tools without executing installers."""
import hashlib
import io
import os
import platform
import tarfile
import urllib.request
from pathlib import Path

TOOLS = {
    'Linux-x86_64': {
        'trivy': ('aquasecurity/trivy/v0.76.1/trivy_0.76.1_Linux-64bit.tar.gz', 'c7cbe3137fb8a48a17574bede3dd6beb16742d1e9772929940fc90082c39d9c0'),
        'actionlint': ('rhysd/actionlint/v1.7.12/actionlint_1.7.12_linux_amd64.tar.gz', '8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8'),
    },
    'Darwin-arm64': {
        'trivy': ('aquasecurity/trivy/v0.76.1/trivy_0.76.1_macOS-ARM64.tar.gz', '11a9b6cd2752011b54f01ea4bd0dbfa9f0c41dc6538d5a3f9d23955f471bf4e5'),
        'actionlint': ('rhysd/actionlint/v1.7.12/actionlint_1.7.12_darwin_arm64.tar.gz', 'aba9ced2dee8d27fecca3dc7feb1a7f9a52caefa1eb46f3271ea66b6e0e6953f'),
    },
}


def main():
    directory = Path(os.environ.get('RUNNER_TEMP', '/tmp')) / 'danasea-tools'
    directory.mkdir(parents=True, exist_ok=True)
    for name, (resource, digest) in TOOLS[platform.system() + '-' + platform.machine()].items():
        owner, repo, version, asset = resource.split('/')
        with urllib.request.urlopen(f'https://github.com/{owner}/{repo}/releases/download/{version}/{asset}', timeout=120) as response:
            archive = response.read()
        if hashlib.sha256(archive).hexdigest() != digest:
            raise SystemExit('Tool checksum verification failed: ' + name)
        with tarfile.open(fileobj=io.BytesIO(archive)) as bundle:
            data = bundle.extractfile(name).read()
        target = directory / name
        target.write_bytes(data)
        target.chmod(0o755)
    if os.environ.get('GITHUB_PATH'):
        with open(os.environ['GITHUB_PATH'], 'a') as output:
            output.write(str(directory) + '\n')
    print(directory)


if __name__ == '__main__':
    main()
