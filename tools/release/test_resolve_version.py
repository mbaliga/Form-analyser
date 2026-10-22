"""Runtime tests for release version input validation."""

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


RESOLVER = Path(__file__).with_name("resolve_version.sh")


class ResolveVersionTest(unittest.TestCase):
    def resolve(self, values: dict[str, str]) -> tuple[subprocess.CompletedProcess[str], str]:
        with tempfile.NamedTemporaryFile() as output:
            env = os.environ | values | {"GITHUB_OUTPUT": output.name}
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
            {"INPUT_VERSION": "", "INPUT_VERSION_CODE": "", "REF_NAME": "release/v1.2.3"}
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(output, "version=v1.2.3\nversion_code=10203\n")

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
                    {"INPUT_VERSION": version, "INPUT_VERSION_CODE": "698", "REF_NAME": "dispatch"}
                )
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(output, f"version={version}\nversion_code=698\n")

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
            {"INPUT_VERSION": "0.6.1", "INPUT_VERSION_CODE": "698", "REF_NAME": "dispatch"}
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(output, "version=0.6.1\nversion_code=698\n")

    def test_invalid_explicit_version_code_is_not_ignored_for_semver(self) -> None:
        result, output = self.resolve(
            {"INPUT_VERSION": "0.6.0", "INPUT_VERSION_CODE": "0", "REF_NAME": "dispatch"}
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("versionCode must be a positive integer", result.stderr)
        self.assertEqual(output, "")

    def test_custom_release_name_requires_a_positive_version_code(self) -> None:
        result, output = self.resolve(
            {"INPUT_VERSION": "fencing-beta", "INPUT_VERSION_CODE": "777", "REF_NAME": "dispatch"}
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(output, "version=fencing-beta\nversion_code=777\n")

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
