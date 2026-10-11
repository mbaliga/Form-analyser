#!/usr/bin/env python3
"""Static contract tests for the production release workflow's security gates."""

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "release.yml"
DEPENDENCY_METADATA = ROOT / "gradle" / "verification-metadata.xml"


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

    def test_release_version_is_bounded_and_not_interpolated_into_shell_source(self) -> None:
        resolver = (ROOT / "tools" / "release" / "resolve_version.sh").read_text()
        self.assertIn("bash tools/release/resolve_version.sh", self.workflow)
        self.assertIn("^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$", resolver)
        self.assertIn("version_code=\"$(python3 -c", resolver)
        self.assertIn("Semver prerelease/build labels require an explicit versionCode.", resolver)
        self.assertLess(resolver.index('if [[ -n "${INPUT_VERSION_CODE:-}" ]]'), resolver.index("elif [[ \"$version\" =~"))
        self.assertIn("RELEASE_VERSION: ${{ steps.ver.outputs.version }}", self.workflow)
        self.assertIn('"$RELEASE_VERSION"', self.workflow)
        self.assertNotIn('run: ./gradlew :app-android:bundleRelease :app-android:assembleRelease -PwithAndroid --no-daemon -PcrocodylVersionName="${{', self.workflow)
        self.assertNotIn('script: ./gradlew :app-android:connectedDebugAndroidTest -PwithAndroid --no-daemon -PcrocodylVersionName="${{', self.workflow)

    def test_release_write_permission_is_isolated_to_publication_job(self) -> None:
        build_job, publish_job = self.workflow.split("\n  publish:", maxsplit=1)
        self.assertIn("permissions:\n  contents: read", self.workflow)
        self.assertIn("permissions:\n      contents: read", build_job)
        self.assertIn("persist-credentials: false", build_job)
        self.assertNotIn("contents: write", build_job)
        self.assertIn("permissions:\n      contents: write", publish_job)
        self.assertEqual(self.workflow.count("contents: write"), 1)
        self.assertLess(
            self.workflow.index("actions/upload-artifact@"),
            self.workflow.index("actions/download-artifact@"),
        )
        self.assertGreater(publish_job.index("softprops/action-gh-release@"), 0)

    def test_release_runs_both_android_unit_variants_and_lint(self) -> None:
        self.assertIn(":app-android:testDebugUnitTest", self.workflow)
        self.assertIn(":app-android:testReleaseUnitTest", self.workflow)
        self.assertIn(":app-android:lintRelease", self.workflow)

    def test_artifact_preflight_binds_package_identity(self) -> None:
        verifier = (ROOT / "tools" / "release" / "verify_android_release.sh").read_text()
        self.assertIn("package: name='xyz.mdhv.formanalyser'", verifier)
        self.assertIn("certificate SHA-256 digest", verifier)
        self.assertIn("keytool -printcert -jarfile", verifier)
        self.assertIn("sha256sum", verifier)
        self.assertIn("shasum -a 256", verifier)

    def test_release_preflight_audits_merged_manifest_surface(self) -> None:
        verifier = (ROOT / "tools" / "release" / "verify_android_release.sh").read_text()
        self.assertIn('dump xmltree "$APK" --file AndroidManifest.xml > "$MANIFEST"', verifier)
        self.assertIn('verify_manifest_contract.py "$MANIFEST"', verifier)

    def test_dependency_verification_metadata_requires_sha256_for_every_artifact(self) -> None:
        metadata = ET.parse(DEPENDENCY_METADATA).getroot()
        namespace = metadata.tag.partition("}")[0].removeprefix("{")
        qualified = lambda name: f"{{{namespace}}}{name}"
        configuration = metadata.find(qualified("configuration"))
        self.assertIsNotNone(configuration)
        assert configuration is not None
        self.assertEqual(configuration.findtext(qualified("verify-metadata")), "true")

        components = metadata.findall(f"./{qualified('components')}/{qualified('component')}")
        self.assertGreater(len(components), 0)
        component_keys = [
            (component.get("group"), component.get("name"), component.get("version"))
            for component in components
        ]
        self.assertEqual(len(component_keys), len(set(component_keys)))

        artifact_count = 0
        for component in components:
            artifacts = component.findall(qualified("artifact"))
            self.assertGreater(len(artifacts), 0, ET.tostring(component, encoding="unicode"))
            artifact_names = [artifact.get("name") for artifact in artifacts]
            self.assertEqual(len(artifact_names), len(set(artifact_names)))
            for artifact in artifacts:
                hashes = artifact.findall(qualified("sha256"))
                self.assertGreaterEqual(len(hashes), 1, ET.tostring(artifact, encoding="unicode"))
                for digest in hashes:
                    self.assertRegex(digest.get("value", ""), re.compile(r"^[0-9a-f]{64}$"))
                artifact_count += 1
        self.assertGreater(artifact_count, 0)

    def test_ci_path_filters_watch_sport_modules_and_dependency_lock(self) -> None:
        for workflow_name in ("ci.yml", "android.yml"):
            workflow = (ROOT / ".github" / "workflows" / workflow_name).read_text()
            for path in (
                "muaythai-module/**",
                "bouldering-module/**",
                "gradle/**",
            ):
                with self.subTest(workflow=workflow_name, path=path):
                    # Both push and pull_request path filters must include the contract.
                    self.assertGreaterEqual(workflow.count(f'      - "{path}"'), 2)


if __name__ == "__main__":
    unittest.main()
