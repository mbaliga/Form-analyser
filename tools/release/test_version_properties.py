"""Contract tests for app-android/version.properties, the committed version source.

F-Droid builds the release tag without any CI -P overrides, so the version must live in the tree.
"""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]
VERSION_PROPERTIES = ROOT / "app-android" / "version.properties"
FLOOR_FILE = ROOT / "tools" / "release" / "highest_issued_version_code.txt"
VERSION_NAME = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+(-dev)?$")


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text().splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        assert separator, f"{path}: not a key=value line: {raw!r}"
        values[key.strip()] = value.strip()
    return values


class VersionPropertiesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.values = read_properties(VERSION_PROPERTIES)

    def test_both_keys_are_present(self) -> None:
        self.assertIn("versionCode", self.values)
        self.assertIn("versionName", self.values)

    def test_version_code_is_an_integer_above_the_highest_issued_floor(self) -> None:
        raw = self.values["versionCode"]
        self.assertRegex(raw, r"^[1-9][0-9]*$")
        floor = int(FLOOR_FILE.read_text().strip())
        self.assertGreater(int(raw), floor)
        self.assertLessEqual(int(raw), 2_100_000_000)

    def test_version_name_is_semver_with_an_optional_dev_suffix(self) -> None:
        self.assertRegex(self.values["versionName"], VERSION_NAME)

    def test_version_name_validator_rejects_malformed_names(self) -> None:
        for bad in ("v0.7.0", "0.7", "0.7.0-beta", "0.7.0-dev+1", " 0.7.0", ""):
            with self.subTest(name=bad):
                self.assertIsNone(VERSION_NAME.fullmatch(bad))


if __name__ == "__main__":
    unittest.main()
