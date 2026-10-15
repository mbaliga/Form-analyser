#!/usr/bin/env python3
"""Static contract tests for the production release workflow's security gates."""

import re
import subprocess
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
        for token in ("STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"):
            self.assertIn(f'test -n "${token}"', self.workflow[verify:prepare])
        self.assertIn('bash tools/release/verify_production_signer.sh "$CERT_SHA256"', self.workflow[verify:prepare])
        prepare_step = self.workflow[prepare:self.workflow.index("- name: Assemble signed release artifacts")]
        self.assertIn('test -n "$KEYSTORE_BASE64"', prepare_step)
        self.assertIn('printf \'%s\' "$KEYSTORE_BASE64" | base64 --decode > app-android/release.keystore', prepare_step)

    def test_signing_credentials_are_scoped_to_the_steps_that_need_them(self) -> None:
        verify = self.workflow.index("- name: Verify production signing inputs")
        password_check = self.workflow.index("- name: Verify release password inputs")
        resolve = self.workflow.index("- name: Resolve version name")
        verify_step = self.workflow[verify:password_check]
        password_step = self.workflow[password_check:resolve]
        self.assertIn("CROCODYL_RELEASE_CERT_SHA256", verify_step)
        self.assertNotIn("CROCODYL_RELEASE_KEYSTORE_BASE64", verify_step)
        self.assertNotIn("CROCODYL_RELEASE_STORE_PASSWORD", verify_step)
        self.assertNotIn("KEYSTORE_BASE64", password_step)

    def test_production_release_rejects_the_committed_qa_signer(self) -> None:
        verify = self.workflow.index("- name: Verify production signing inputs")
        prepare = self.workflow.index("- name: Prepare release signing")
        signing_gate = self.workflow[verify:prepare]
        qa_certificate = "36f33a33f9415fbe4f31556a3dde9bc010d03c502bb3dfe6b2bfaf0cbafdaebc"
        guard = ROOT / "tools" / "release" / "verify_production_signer.sh"
        self.assertIn('bash tools/release/verify_production_signer.sh "$CERT_SHA256"', signing_gate)

        qa_result = subprocess.run(
            ["bash", str(guard), ":".join(qa_certificate[i:i + 2] for i in range(0, 64, 2)).upper()],
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertNotEqual(qa_result.returncode, 0)
        self.assertIn("QA/debug certificate", qa_result.stderr)

        production_result = subprocess.run(
            ["bash", str(guard), "A" * 64],
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(production_result.returncode, 0, production_result.stderr)

        invalid_result = subprocess.run(
            ["bash", str(guard), "not-a-certificate"],
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(invalid_result.returncode, 2)

    def test_signing_material_has_always_run_cleanup(self) -> None:
        self.assertIn("- name: Remove signing material", self.workflow)
        self.assertIn("if: ${{ always() }}", self.workflow)
        self.assertIn("run: rm -f app-android/release.keystore", self.workflow)

    def test_production_keystore_is_materialized_only_after_unsigned_validation(self) -> None:
        prepare = self.workflow.index("- name: Prepare release signing")
        assemble = self.workflow.index("- name: Assemble signed release artifacts")
        debug_smoke = self.workflow.index("- name: Run on-device Android smoke tests")
        source_contracts = self.workflow.index("- name: Validate packaged equipment source contracts")
        self.assertLess(debug_smoke, source_contracts)
        self.assertLess(source_contracts, prepare)
        self.assertLess(prepare, assemble)

    def test_keystore_is_removed_before_non_signing_release_checks(self) -> None:
        remove = self.workflow.index("- name: Remove signing material after signed instrumentation")
        standalone_smoke = self.workflow.index("- name: Run minified release smoke tests")
        verify = self.workflow.index("- name: Verify signed release artifacts")
        self.assertLess(remove, standalone_smoke)
        self.assertLess(standalone_smoke, verify)
        self.assertIn("run: rm -f app-android/release.keystore", self.workflow[remove:standalone_smoke])

    def test_release_requires_certificate_fingerprint_and_preflight(self) -> None:
        self.assertIn("CROCODYL_RELEASE_CERT_SHA256", self.workflow)
        self.assertIn("tools/release/verify_android_release.sh", self.workflow)

    def test_release_preserves_versioned_r8_mapping_outside_public_release_assets(self) -> None:
        mapping = self.workflow.index("- name: Preserve minified-release mapping for crash diagnostics")
        upload = self.workflow.index("- name: Upload R8 mapping and digest as CI-only artifact")
        cleanup = self.workflow.rindex("- name: Remove signing material")
        self.assertLess(mapping, upload)
        self.assertLess(upload, cleanup)
        mapping_steps = self.workflow[mapping:cleanup]
        self.assertIn("app-android/build/outputs/mapping/release/mapping.txt", mapping_steps)
        self.assertIn('test -s "$mapping"', mapping_steps)
        self.assertIn('sha256sum "crocodyl-${RELEASE_VERSION}-r8-mapping.txt"', mapping_steps)
        self.assertIn("retention-days: 90", mapping_steps)
        self.assertIn("if-no-files-found: error", mapping_steps)
        publish = self.workflow.split("\n  publish:", maxsplit=1)[1]
        self.assertNotIn("r8-mapping", publish)

    def test_release_version_is_bounded_and_not_interpolated_into_shell_source(self) -> None:
        resolver = (ROOT / "tools" / "release" / "resolve_version.sh").read_text()
        version_floor = (ROOT / "tools" / "release" / "highest_issued_version_code.txt").read_text().strip()
        self.assertIn("bash tools/release/resolve_version.sh", self.workflow)
        self.assertEqual(version_floor, "697")
        self.assertIn("last_issued_version_code", resolver)
        self.assertIn("version_code <= last_issued_version_code", resolver)
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

    def test_release_contracts_run_when_release_workflow_or_tests_change(self) -> None:
        ci = (ROOT / ".github" / "workflows" / "ci.yml").read_text()
        self.assertIn('".github/workflows/**"', ci)
        self.assertIn('"tools/release/**"', ci)
        self.assertIn("python3 -m unittest discover -s tools/release -p 'test_*.py'", ci)

    def test_ci_and_release_execute_offline_report_analysis_tests(self) -> None:
        ci = (ROOT / ".github" / "workflows" / "ci.yml").read_text()
        release = (ROOT / ".github" / "workflows" / "release.yml").read_text()
        command = "node tools/web_viewer/test_report_analysis.mjs"
        self.assertIn(command, ci)
        self.assertIn(command, release)

    def test_play_candidate_targets_api_36_in_build_and_emulator_jobs(self) -> None:
        app_build = (ROOT / "app-android" / "build.gradle.kts").read_text()
        self.assertRegex(app_build, r"compileSdk\s*=\s*36\b")
        self.assertRegex(app_build, r"targetSdk\s*=\s*36\b")
        for workflow_path in (
            ROOT / ".github" / "workflows" / "android.yml",
            WORKFLOW,
        ):
            self.assertRegex(workflow_path.read_text(), r"api-level:\s*36\b")

    def test_debug_and_signed_release_ci_include_verified_16_kb_emulators(self) -> None:
        android_workflow = (ROOT / ".github" / "workflows" / "android.yml").read_text()
        self.assertIn("Run debug smoke tests on 16 KB page-size emulator", android_workflow)
        debug_16kb_lane = android_workflow.split(
            "      - name: Run debug smoke tests on 16 KB page-size emulator", maxsplit=1
        )[1].split("\n      - name:", maxsplit=1)[0]
        self.assertIn("target: google_apis_ps16k", debug_16kb_lane)
        self.assertIn('test "$page_size" = 16384', debug_16kb_lane)
        self.assertIn("adb shell settings put system font_scale 1.3", debug_16kb_lane)
        self.assertIn('test "$font_scale" = 1.3', debug_16kb_lane)
        self.assertIn("target: google_apis_ps16k", android_workflow)
        self.assertIn('test "$page_size" = 16384', android_workflow)
        self.assertIn(":app-android:connectedDebugAndroidTest", android_workflow)

        release_workflow = WORKFLOW.read_text()
        self.assertIn("Run signed minified release smoke tests on 16 KB page-size emulator", release_workflow)
        release_16kb_lane = release_workflow.split(
            "      - name: Run signed minified release smoke tests on 16 KB page-size emulator", maxsplit=1
        )[1].split("\n      - name:", maxsplit=1)[0]
        self.assertIn("target: google_apis_ps16k", release_16kb_lane)
        self.assertIn('test "$page_size" = 16384', release_16kb_lane)
        self.assertIn("adb shell settings put system font_scale 1.3", release_16kb_lane)
        self.assertIn('test "$font_scale" = 1.3', release_16kb_lane)
        self.assertIn("target: google_apis_ps16k", release_workflow)
        self.assertIn('test "$page_size" = 16384', release_workflow)
        self.assertIn(":app-android:connectedReleaseAndroidTest", release_workflow)
        self.assertIn("-PcrocodylInstrumentRelease=true", release_workflow)
        for workflow in (android_workflow, release_workflow):
            self.assertNotIn("android-emulator-runner@1dcd0090116d15e7c562f8db72807de5e036a4ed", workflow)
            self.assertIn("android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d # v2.38.0", workflow)

    def test_current_app_smoke_runs_on_android_15_in_debug_and_signed_release(self) -> None:
        android_workflow = (ROOT / ".github" / "workflows" / "android.yml").read_text()
        debug_lane = android_workflow.split(
            "      - name: Run debug smoke tests on Android 15", maxsplit=1
        )[1].split("\n      - name:", maxsplit=1)[0]
        self.assertIn("api-level: 35", debug_lane)
        self.assertIn("target: google_apis", debug_lane)
        self.assertIn(":app-android:connectedDebugAndroidTest", debug_lane)

        release_lane = self.workflow.split(
            "      - name: Run signed minified release smoke tests on Android 15", maxsplit=1
        )[1].split("\n      - name:", maxsplit=1)[0]
        self.assertIn("api-level: 35", release_lane)
        self.assertIn("target: google_apis", release_lane)
        self.assertIn("CROCODYL_RELEASE_STORE_FILE", release_lane)
        self.assertIn(":app-android:connectedReleaseAndroidTest", release_lane)
        self.assertIn("-PcrocodylInstrumentRelease=true", release_lane)

    def test_artifact_preflight_binds_package_identity(self) -> None:
        verifier = (ROOT / "tools" / "release" / "verify_android_release.sh").read_text()
        self.assertIn("package: name='xyz.mdhv.formanalyser'", verifier)
        self.assertIn("targetSdkVersion:'36'", verifier)
        self.assertIn("certificate SHA-256 digest", verifier)
        self.assertIn("keytool -printcert -jarfile", verifier)
        self.assertIn("sha256sum", verifier)
        self.assertIn("shasum -a 256", verifier)

    def test_release_preflight_audits_merged_manifest_surface(self) -> None:
        verifier = (ROOT / "tools" / "release" / "verify_android_release.sh").read_text()
        self.assertIn('dump xmltree "$APK" --file AndroidManifest.xml > "$MANIFEST"', verifier)
        self.assertIn('verify_manifest_contract.py "$MANIFEST"', verifier)
        manifest_contract = (ROOT / "tools" / "release" / "verify_manifest_contract.py").read_text()
        self.assertIn("Release application must not be debuggable", manifest_contract)
        self.assertIn("Release application must not be marked testOnly", manifest_contract)

    def test_release_preflight_checks_16_kb_elf_alignment(self) -> None:
        verifier = (ROOT / "tools" / "release" / "verify_android_release.sh").read_text()
        self.assertIn('verify_elf_alignment.py "$APK" "$AAB"', verifier)

    def test_connected_debug_and_minified_results_are_retained_on_failure(self) -> None:
        for artifact_name, report_path, log_path in (
            (
                "crocodyl-release-debug-test-reports",
                "app-android/build/reports/androidTests/connected/debug/**",
                "app-android/build/diagnostics/release-debug-logcat.txt",
            ),
            (
                "crocodyl-release-minified-test-reports",
                "app-android/build/reports/androidTests/connected/release/**",
                "app-android/build/diagnostics/release-minified-logcat.txt",
            ),
        ):
            with self.subTest(artifact=artifact_name):
                start = self.workflow.index(f"name: {artifact_name}")
                upload = self.workflow.rfind("- name: Upload", 0, start)
                next_step = self.workflow.find("\n      - name:", upload + 1)
                step = self.workflow[upload:next_step if next_step != -1 else None]
                self.assertIn("if: always()", step)
                self.assertIn(report_path, step)
                self.assertIn(log_path, step)

        android_workflow = (ROOT / ".github" / "workflows" / "android.yml").read_text()
        self.assertIn("adb logcat -d -v threadtime > app-android/build/diagnostics/debug-logcat.txt", android_workflow)
        self.assertIn("crocodyl-android-debug-test-reports", android_workflow)
        self.assertIn("app-android/build/reports/androidTests/connected/debug/**", android_workflow)
        upload_start = android_workflow.index("- name: Upload Android debug test reports and diagnostics")
        self.assertIn("if: always()", android_workflow[upload_start:])

        android_workflow = (ROOT / ".github" / "workflows" / "android.yml").read_text()
        self.assertIn("adb logcat -d -v threadtime > app-android/build/diagnostics/debug-logcat.txt", android_workflow)
        self.assertIn("crocodyl-android-debug-test-reports", android_workflow)
        self.assertIn("app-android/build/reports/androidTests/connected/debug/**", android_workflow)
        upload_start = android_workflow.index("- name: Upload Android debug test reports and diagnostics")
        self.assertIn("if: always()", android_workflow[upload_start:])

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
