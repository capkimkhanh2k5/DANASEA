#!/usr/bin/env python3
"""Fetch only the pinned Quyet Small model, then verify its complete manifest."""
import argparse
import os
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    os.environ['HF_HUB_OFFLINE'] = '0'
    from huggingface_hub import snapshot_download
    directory = args.directory.resolve()
    snapshot_download('chinhnc/Quyet-1.0-Small', revision='233167bba5df61b5375a522bf8a042d8d2189379', local_dir=directory)
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from production import validate_quyet_model
    # Only model assets are public; no credentials are written into this directory.
    for path in directory.rglob('*'):
        if path.is_file():
            path.chmod(0o644)
        elif path.is_dir():
            path.chmod(0o755)
    directory.chmod(0o755)
    validate_quyet_model(str(directory))
    print('Pinned model verified')


if __name__ == '__main__':
    main()
