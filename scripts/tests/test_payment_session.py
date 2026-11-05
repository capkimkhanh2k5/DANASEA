import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.payment_session import transform


class PrivateSessionTest(unittest.TestCase):
    def test_real_encryption_roundtrip_and_authenticated_envelope(self):
        with (
            tempfile.TemporaryDirectory() as temp,
            patch.dict(os.environ, {"PAYMENT_SESSION_KEY": "a" * 40}),
        ):
            root = Path(temp)
            source = root / "private.json"
            sealed = root / "session.enc"
            opened = root / "restored.json"
            source.write_text(
                '{"approval_url":"https://sandbox/pay?token=private-buyer-reference"}'
            )
            transform("seal", source, sealed)
            self.assertNotIn("private-buyer-reference", sealed.read_text())
            opened.write_text("old private session")
            opened.chmod(0o644)
            transform("open", sealed, opened)
            self.assertEqual(opened.read_bytes(), source.read_bytes())
            self.assertEqual(opened.stat().st_mode & 0o777, 0o600)
            envelope = json.loads(sealed.read_text())
            envelope["mac"] = "0" * 64
            sealed.write_text(json.dumps(envelope))
            with (
                patch("subprocess.run") as decrypt,
                self.assertRaisesRegex(ValueError, "authentication"),
            ):
                transform("open", sealed, root / "bad.json")
            decrypt.assert_not_called()

    def test_wrong_key_and_short_key_are_rejected(self):
        with (
            tempfile.TemporaryDirectory() as temp,
            patch.dict(os.environ, {"PAYMENT_SESSION_KEY": "a" * 40}),
        ):
            root = Path(temp)
            source = root / "private.json"
            source.write_text("{}")
            sealed = root / "session.enc"
            transform("seal", source, sealed)
            for key in ("b" * 40, "short"):
                with (
                    patch.dict(os.environ, {"PAYMENT_SESSION_KEY": key}),
                    self.assertRaises(ValueError),
                ):
                    transform("open", sealed, root / "bad.json")
