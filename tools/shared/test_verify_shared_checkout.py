#!/usr/bin/env python3
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]


class SharedCheckoutLockContract(unittest.TestCase):
    def test_ci_and_release_fetch_and_verify_the_locked_checkout(self) -> None:
        for workflow in (ROOT / ".github/workflows/ci.yml", ROOT / ".github/workflows/release.yml"):
            source = workflow.read_text()
            self.assertIn("Fetch and verify locked shared-library checkout", source)
            self.assertIn("git clone --depth 1 --branch", source)
            self.assertIn("tools/shared/verify_shared_checkout.py .ci/shared-libraries-asoc", source)

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
        self.assertIn("refs/remotes/origin/", source)
        self.assertIn("ref mismatch", source)
        self.assertIn("revision mismatch", source)
        self.assertIn("remote mismatch", source)

    def test_wrong_checkout_is_rejected_executable(self) -> None:
        script = ROOT / "tools/shared/verify_shared_checkout.py"
        with tempfile.TemporaryDirectory() as temp:
            checkout = Path(temp) / "shared"
            checkout.mkdir()
            subprocess.run(["git", "-C", str(checkout), "init", "-q"], check=True)
            subprocess.run(["git", "-C", str(checkout), "config", "user.email", "test@example.invalid"], check=True)
            subprocess.run(["git", "-C", str(checkout), "config", "user.name", "Test"], check=True)
            (checkout / "placeholder").write_text("wrong revision\n")
            subprocess.run(["git", "-C", str(checkout), "add", "placeholder"], check=True)
            subprocess.run(["git", "-C", str(checkout), "commit", "-qm", "wrong"], check=True)
            subprocess.run(["git", "-C", str(checkout), "remote", "add", "origin", "https://example.invalid/wrong.git"], check=True)
            result = subprocess.run(["python3", str(script), str(checkout)], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("remote mismatch", result.stderr + result.stdout)


if __name__ == "__main__":
    unittest.main()
