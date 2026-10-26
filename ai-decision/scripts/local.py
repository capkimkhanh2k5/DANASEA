"""Start the local worker using private credentials stored outside the repository."""

from __future__ import annotations

import argparse
import os
import secrets
from pathlib import Path

import uvicorn


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--key-file",
        type=Path,
        default=Path.home() / ".cache/danasea/quyet-small/service.key",
    )
    parser.add_argument("--port", type=int, default=8091)
    args = parser.parse_args()
    args.key_file.parent.mkdir(parents=True, exist_ok=True)
    if not args.key_file.exists():
        descriptor = os.open(args.key_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w") as stream:
            stream.write(secrets.token_urlsafe(32))
    args.key_file.chmod(0o600)
    os.environ["AI_QUYET_API_KEY"] = args.key_file.read_text().strip()
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    os.environ["TOKENIZERS_PARALLELISM"] = "false"
    uvicorn.run(
        "app.quyet_service:app",
        app_dir=str(Path(__file__).resolve().parents[1]),
        host="127.0.0.1",
        port=args.port,
        workers=1,
        access_log=False,
    )


if __name__ == "__main__":
    main()
