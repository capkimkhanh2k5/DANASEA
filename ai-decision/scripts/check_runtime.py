"""Verify native dependencies and hardening in the final worker filesystem."""

import importlib.util
import os
import shutil
import subprocess
import sys
from pathlib import Path
from sysconfig import get_path

if os.getuid() != 10001 or os.getgid() != 10001:
    raise SystemExit("Worker must run as UID and GID 10001")
if any(shutil.which(tool) for tool in ("sh", "bash", "busybox", "apk", "pip")) or any(
    importlib.util.find_spec(module) for module in ("pip", "ensurepip")
):
    raise SystemExit("Worker runtime includes a shell or package installer")

objects = [Path(sys.executable)]
for root in (Path(get_path("stdlib")) / "lib-dynload", Path(get_path("platlib"))):
    objects.extend(path for path in root.rglob("*.so*") if path.is_file())
for path in objects:
    with path.open("rb") as binary:
        if binary.read(4) != b"\x7fELF":
            continue
    result = subprocess.run(["/usr/bin/ld.so", "--list", str(path)], capture_output=True, text=True, check=False)
    if result.returncode != 0 or "not found" in result.stdout:
        raise SystemExit(f"Unresolved runtime dependency for {path}\n{result.stdout}{result.stderr}")
