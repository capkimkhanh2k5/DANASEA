#!/usr/bin/env python3
"""Encrypt-then-MAC private sandbox sessions; authenticate before any decryption."""

import argparse
import base64
import hashlib
import hmac
import json
import os
import subprocess
import tempfile
from pathlib import Path


def transform(action, source, destination):
    key = os.environ.get("PAYMENT_SESSION_KEY", "")
    if len(key) < 32:
        raise ValueError(
            "Protected PAYMENT_SESSION_KEY must contain at least 32 characters"
        )
    mac_key = hashlib.sha256(b"danasea-payment-session-mac\0" + key.encode()).digest()
    command = [
        "openssl",
        "enc",
        "-aes-256-cbc",
        "-pbkdf2",
        "-pass",
        "env:PAYMENT_SESSION_KEY",
    ]
    if action == "seal":
        ciphertext = subprocess.run(
            command + ["-salt"],
            input=Path(source).read_bytes(),
            capture_output=True,
            check=True,
        ).stdout
        output = json.dumps(
            {
                "version": 1,
                "ciphertext": base64.b64encode(ciphertext).decode(),
                "mac": hmac.new(mac_key, ciphertext, hashlib.sha256).hexdigest(),
            }
        ).encode()
    else:
        envelope = json.loads(Path(source).read_text())
        ciphertext = base64.b64decode(envelope["ciphertext"], validate=True)
        expected = hmac.new(mac_key, ciphertext, hashlib.sha256).hexdigest()
        if envelope.get("version") != 1 or not hmac.compare_digest(
            expected, envelope.get("mac", "")
        ):
            raise ValueError("Private session authentication failed")
        output = subprocess.run(
            command + ["-d"], input=ciphertext, capture_output=True, check=True
        ).stdout
        json.loads(output)
    path = Path(destination)
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix="." + path.name, dir=path.parent)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(output)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("seal", "open"))
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    transform(args.action, args.input, args.output)


if __name__ == "__main__":
    main()
