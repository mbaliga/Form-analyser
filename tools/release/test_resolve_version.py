"""Runtime tests for release version input validation."""

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


RESOLVER = Path(__file__).with_name("resolve_version.sh")


class ResolveVersionTest(unittest.TestCase):
    def resolve(
        self,
        values: dict[str, str],
        properties: tuple[str, str] | None = None,
        properties_text: str | None = None,
    ) -> tuple[subprocess.CompletedProcess[str], str]:
        """Run the resolver against a temp version.properties.

        `properties` is (versionName, versionCode); `properties_text` writes the file verbatim;
        with neither, the file is absent (the resolver must then fail closed).
        """
        with tempfile.TemporaryDirectory() as directory, tempfile.NamedTemporaryFile() as output:
            properties_file = Path(directory) / "version.properties"
            if properties_text is not None:
                properties_file.write_text(properties_text)
            elif properties is not None:
                properties_file.write_text(f"versionCode={properties[1]}\nversionName={properties[0]}\n")
            env = os.environ | values | {
                "GITHUB_OUTPUT": output.name,
                "VERSION_PROPERTIES_FILE": str(properties_file),
            }
            result = subprocess.run(
                ["bash", str(RESOLVER)],
                env=env,
                text=True,
                capture_output=True,
                check=False,
            )
            output.seek(0)
            return result, output.read().decode()

    def test_release_branch_semver_derives_code_above_last_issued_floor(self) -> None:
        result, output = self.resolve(
            {"INPUT_VERSION": "", "INPUT_VERSION_CODE": "", "REF_NAME": "release/v1.2.3"},
            properties=("1.2.3", "10203"),
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(output, "tag=v1.2.3\nversion=1.2.3\nversion_code=10203\n")

    def test_semver_suffix_requires_explicit_version_code(self) -> None:
        result, output = self.resolve(
            {"INPUT_VERSION": "0.6.0-beta+2", "INPUT_VERSION_CODE": "", "REF_NAME": "dispatch"}
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("require an explicit versionCode", result.stderr)
        self.assertEqual(output, "")

    def test_explicit_version_code_overrides_semver_derived_value(self) -> None:
        for version in ("0.6.0", "0.6.0-beta+2"):
            with self.subTest(version=version):
                result, output = self.resolve(
                    {"INPUT_VERSION": version, "INPUT_VERSION_CODE": "698", "REF_NAME": "dispatch"},
                    properties=(version, "698"),
                )
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(output, f"tag={version}\nversion={version}\nversion_code=698\n")

    def test_rejects_codes_at_or_below_last_issued_version(self) -> None:
        for version, version_code in (("0.6.0", "697"), ("0.6.1", "")):
            with self.subTest(version=version, version_code=version_code):
                result, output = self.resolve(
                    {"INPUT_VERSION": version, "INPUT_VERSION_CODE": version_code, "REF_NAME": "dispatch"}
                )
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("must be greater than the last issued versionCode 697", result.stderr)
                self.assertEqual(output, "")

    def test_accepts_first_code_above_last_issued_version(self) -> None:
        result, output = self.resolve(
            {"INPUT_VERSION": "0.6.1", "INPUT_VERSION_CODE": "698", "REF_NAME": "dispatch"},
            properties=("0.6.1", "698"),
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(output, "tag=0.6.1\nversion=0.6.1\nversion_code=698\n")

    def test_invalid_explicit_version_code_is_not_ignored_for_semver(self) -> None:
        result, output = self.resolve(
            {"INPUT_VERSION": "0.6.0", "INPUT_VERSION_CODE": "0", "REF_NAME": "dispatch"}
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("versionCode must be a positive integer", result.stderr)
        self.assertEqual(output, "")

    def test_custom_release_name_requires_a_positive_version_code(self) -> None:
        result, output = self.resolve(
            {"INPUT_VERSION": "fencing-beta", "INPUT_VERSION_CODE": "777", "REF_NAME": "dispatch"},
            properties=("fencing-beta", "777"),
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(output, "tag=fencing-beta\nversion=fencing-beta\nversion_code=777\n")

    def test_tag_keeps_the_raw_name_while_version_drops_one_leading_v_from_semver(self) -> None:
        cases = (
            # (input, explicit code, expected tag, expected version, expected code)
            ("v0.7.0", "", "v0.7.0", "0.7.0", "700"),
            ("0.7.0", "", "0.7.0", "0.7.0", "700"),
            ("v0.7.0", "700", "v0.7.0", "0.7.0", "700"),
            ("v0.7.0-rc.1", "701", "v0.7.0-rc.1", "0.7.0-rc.1", "701"),
            ("vv0.7.0", "701", "vv0.7.0", "vv0.7.0", "701"),
            ("vnext", "701", "vnext", "vnext", "701"),
        )
        for raw, explicit_code, tag, version, code in cases:
            with self.subTest(raw=raw, explicit_code=explicit_code):
                result, output = self.resolve(
                    {"INPUT_VERSION": raw, "INPUT_VERSION_CODE": explicit_code, "REF_NAME": "dispatch"},
                    properties=(version, code),
                )
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(output, f"tag={tag}\nversion={version}\nversion_code={code}\n")

    def test_tag_push_and_release_branch_resolve_to_the_same_tag_and_version(self) -> None:
        for ref in ("v0.7.0", "release/v0.7.0"):
            with self.subTest(ref=ref):
                result, output = self.resolve(
                    {"INPUT_VERSION": "", "INPUT_VERSION_CODE": "", "REF_NAME": ref},
                    properties=("0.7.0", "700"),
                )
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(output, "tag=v0.7.0\nversion=0.7.0\nversion_code=700\n")

    def test_release_must_match_version_properties(self) -> None:
        env = {"INPUT_VERSION": "v0.7.0", "INPUT_VERSION_CODE": "", "REF_NAME": "dispatch"}
        mismatches = (
            ("0.7.0-dev", "700"),  # name not bumped in the release commit
            ("0.7.0", "699"),  # code not bumped
            ("0.7.1", "701"),  # properties describe another release
            ("v0.7.0", "700"),  # properties carry the tag, not the version name
        )
        for properties in mismatches:
            with self.subTest(properties=properties):
                result, output = self.resolve(env, properties=properties)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(
                    "release 0.7.0/700 does not match app-android/version.properties; "
                    "bump it in the release commit",
                    result.stderr,
                )
                self.assertEqual(output, "")

    def test_missing_or_incomplete_version_properties_fail_closed(self) -> None:
        env = {"INPUT_VERSION": "v0.7.0", "INPUT_VERSION_CODE": "", "REF_NAME": "dispatch"}
        for label, kwargs in (
            ("absent", {}),
            ("empty", {"properties_text": ""}),
            ("no versionName", {"properties_text": "versionCode=700\n"}),
            ("no versionCode", {"properties_text": "versionName=0.7.0\n"}),
        ):
            with self.subTest(case=label):
                result, output = self.resolve(env, **kwargs)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("version.properties", result.stderr)
                self.assertEqual(output, "")

    def test_version_properties_tolerate_crlf_comments_and_spacing(self) -> None:
        result, output = self.resolve(
            {"INPUT_VERSION": "v0.7.0", "INPUT_VERSION_CODE": "", "REF_NAME": "dispatch"},
            properties_text="# release metadata\r\nversionCode = 700\r\nversionName=0.7.0 \r\n",
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(output, "tag=v0.7.0\nversion=0.7.0\nversion_code=700\n")

    def test_default_properties_path_is_the_checked_in_app_version_file(self) -> None:
        resolver = RESOLVER.read_text()
        self.assertIn("VERSION_PROPERTIES_FILE", resolver)
        self.assertIn("app-android/version.properties", resolver)
        self.assertTrue((RESOLVER.parents[2] / "app-android" / "version.properties").is_file())

    def test_shell_metacharacters_are_rejected_without_execution(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            marker = Path(temporary) / "executed"
            result, output = self.resolve(
                {
                    "INPUT_VERSION": f"$(touch {marker})",
                    "INPUT_VERSION_CODE": "7",
                    "REF_NAME": "dispatch",
                }
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(marker.exists())
            self.assertEqual(output, "")

    def test_colliding_components_and_custom_version_codes_are_rejected(self) -> None:
        for version, version_code in (
            ("999999999999999999999999.0.0", ""),
            ("0.100.0", ""),
            ("0.0.100", ""),
            ("custom-build", "999999999999999999999999"),
        ):
            with self.subTest(version=version, version_code=version_code):
                result, output = self.resolve(
                    {"INPUT_VERSION": version, "INPUT_VERSION_CODE": version_code, "REF_NAME": "dispatch"}
                )
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(output, "")


if __name__ == "__main__":
    unittest.main()
