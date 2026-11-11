"""Regression tests for the source-manifest desktop-window guard."""

import unittest
from pathlib import Path

from verify_source_manifest_desktop import audit

REAL = Path(__file__).resolve().parents[2] / "app-android/src/main/AndroidManifest.xml"


class SourceManifestDesktopTest(unittest.TestCase):
    def setUp(self) -> None:
        self.text = REAL.read_text(encoding="utf-8")

    def test_real_manifest_passes(self) -> None:
        self.assertEqual(audit(self.text), [])

    def test_missing_layout_fails(self) -> None:
        start = self.text.index("<layout")
        end = self.text.index("/>", start) + 2
        problems = audit(self.text[:start] + self.text[end:])
        self.assertTrue(any("no <layout>" in p for p in problems))

    def test_missing_layout_attribute_fails(self) -> None:
        mutated = self.text.replace('android:minHeight="480dp"', "")
        self.assertTrue(any("minHeight" in p for p in audit(mutated)))

    def test_orientation_lock_fails(self) -> None:
        mutated = self.text.replace(
            'android:name=".MainActivity"',
            'android:name=".MainActivity" android:screenOrientation="portrait"', 1)
        self.assertTrue(any("screenOrientation" in p for p in audit(mutated)))

    def test_application_orientation_lock_fails(self) -> None:
        mutated = self.text.replace(
            "<application", '<application android:screenOrientation="portrait"', 1)
        self.assertTrue(any("screenOrientation" in p for p in audit(mutated)))

    def test_not_resizeable_fails(self) -> None:
        mutated = self.text.replace(
            "<application", '<application android:resizeableActivity="false"', 1)
        self.assertTrue(any("resizeableActivity" in p for p in audit(mutated)))

    def test_not_resizeable_activity_fails(self) -> None:
        mutated = self.text.replace(
            'android:name=".FencingActivity"',
            'android:name=".FencingActivity" android:resizeableActivity="false"', 1)
        self.assertTrue(any("resizeableActivity" in p for p in audit(mutated)))


if __name__ == "__main__":
    unittest.main()
