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

    def test_all_signing_inputs_are_checked_before_keystore_materialization(self) -> None:
        verify = self.workflow.index("- name: Verify production signing inputs")
        prepare = self.workflow.index("- name: Prepare release signing")
        self.assertLess(verify, prepare)
        for token in ("STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD", "CERT_SHA256"):
            self.assertIn(f'test -n "${token}"' if token != "CERT_SHA256" else "NORMALIZED_CERT", self.workflow[verify:prepare])
        self.assertIn("^[0-9a-f]{64}$", self.workflow[verify:prepare])

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

    def test_artifact_preflight_binds_package_identity(self) -> None:
        verifier = (ROOT / "tools" / "release" / "verify_android_release.sh").read_text()
        self.assertIn("package: name='xyz.mdhv.formanalyser'", verifier)
        self.assertIn("certificate SHA-256 digest", verifier)
        self.assertIn("keytool -printcert -jarfile", verifier)


if __name__ == "__main__":
    unittest.main()
