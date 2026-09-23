#!/usr/bin/env python3
"""Static contract tests for the production release workflow's security gates."""

from pathlib import Path
import unittest


ROOT = Path(__file__).parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "release.yml"


class ReleaseWorkflowContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = WORKFLOW.read_text()

    def test_signing_material_is_created_with_owner_only_permissions(self) -> None:
        self.assertIn("umask 077", self.workflow)
        self.assertIn("chmod 600 app-android/release.keystore", self.workflow)

    def test_signing_material_has_always_run_cleanup(self) -> None:
        self.assertIn("- name: Remove signing material", self.workflow)
        self.assertIn("if: ${{ always() }}", self.workflow)
        self.assertIn("run: rm -f app-android/release.keystore", self.workflow)

    def test_release_requires_certificate_fingerprint_and_preflight(self) -> None:
        self.assertIn("CROCODYL_RELEASE_CERT_SHA256", self.workflow)
        self.assertIn("tools/release/verify_android_release.sh", self.workflow)

    def test_release_runs_both_android_unit_variants_and_lint(self) -> None:
        self.assertIn(":app-android:testDebugUnitTest", self.workflow)
        self.assertIn(":app-android:testReleaseUnitTest", self.workflow)
        self.assertIn(":app-android:lintRelease", self.workflow)


if __name__ == "__main__":
    unittest.main()
