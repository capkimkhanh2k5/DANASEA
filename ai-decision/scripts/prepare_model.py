"""Download the pinned Quyet Small release and verify its published hashes."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

from huggingface_hub import snapshot_download

MODEL_ID = "chinhnc/Quyet-1.0-Small"
REVISION = "233167bba5df61b5375a522bf8a042d8d2189379"
MODEL_DIR = Path.home() / ".cache/danasea/quyet-small/models" / REVISION


def main() -> None:
    snapshot_download(
        MODEL_ID,
        revision=REVISION,
        local_dir=MODEL_DIR,
        max_workers=4,
    )
    checked = []
    for line in (MODEL_DIR / "MANIFEST.sha256").read_text().splitlines():
        if not line.strip():
            continue
        expected, filename = line.split(maxsplit=1)
        filename = filename.lstrip("*")
        path = (MODEL_DIR / filename).resolve()
        if not path.is_relative_to(MODEL_DIR.resolve()):
            raise ValueError("The manifest contains a path outside the model directory")
        with path.open("rb") as stream:
            actual = hashlib.file_digest(stream, "sha256").hexdigest()
        if actual != expected:
            raise ValueError(f"Checksum mismatch: {filename}")
        checked.append(filename)
    if "model.safetensors" not in checked:
        raise ValueError("The published manifest does not cover the model weights")
    output = {
        "model_id": MODEL_ID,
        "revision": REVISION,
        "model_dir": str(MODEL_DIR),
        "verified_files": checked,
        "weights_bytes": (MODEL_DIR / "model.safetensors").stat().st_size,
    }
    output_path = (
        Path(__file__).resolve().parents[1] / "reports/quyet-small/download.json"
    )
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(output, indent=2) + "\n")
    print(json.dumps(output, indent=2), flush=True)


if __name__ == "__main__":
    main()
