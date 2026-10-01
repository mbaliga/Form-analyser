#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]


class SharedCheckoutLockContract(unittest.TestCase):
    def test_lock_points_at_published_transport_revision(self) -> None:
        lock = json.loads((ROOT / "shared-libraries.lock").read_text())
        self.assertEqual(lock["ref"], "codex/evidence-schema-v1")
        self.assertEqual(len(lock["revision"]), 40)
        self.assertIn("dev.aarso:local-session-core:0.1.0", lock["modules"])
        self.assertIn("dev.aarso:performance-analysis:0.1.0", lock["modules"])

    def test_verifier_is_fail_closed_on_revision_and_remote(self) -> None:
        source = (ROOT / "tools/shared/verify_shared_checkout.py").read_text()
        self.assertIn('"rev-parse", "HEAD"', source)
        self.assertIn('"remote", "get-url", "origin"', source)
        self.assertIn("revision mismatch", source)
        self.assertIn("remote mismatch", source)


if __name__ == "__main__":
    unittest.main()
