"""Keep Crocodyl's third-party notices complete, honest and identical in the repository and the app."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
LOCKFILE = ROOT / "app-android/gradle.lockfile"
NOTICES = ROOT / "THIRD_PARTY_NOTICES.md"
ASSET_COPY = ROOT / "app-android/src/main/assets/licenses/THIRD_PARTY_NOTICES.md"
SETTINGS_SCREENS = ROOT / "app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/SettingsScreens.kt"
MAIN_ACTIVITY = ROOT / "app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/MainActivity.kt"

MODEL_CARD_URL = "https://storage.googleapis.com/mediapipe-assets/Model%20Card%20BlazePose%20GHUM%203D.pdf"


def release_runtime_artifacts() -> list[tuple[str, str]]:
    """Every group:artifact the release APK ships, per the dependency lockfile."""
    artifacts = []
    for line in LOCKFILE.read_text(encoding="utf-8").splitlines():
        if line.startswith("#") or "=" not in line:
            continue
        coordinate, configurations = line.strip().split("=", 1)
        if "releaseRuntimeClasspath" not in configurations.split(","):
            continue
        group, artifact, _version = coordinate.split(":")
        artifacts.append((group, artifact))
    return sorted(set(artifacts))


def mentions_group(text: str, group: str) -> bool:
    """The group id as a whole token, so `androidx.compose` is not satisfied by `androidx.compose.ui`."""
    return re.search(rf"(?<![\w.-]){re.escape(group)}(?![\w-]|\.\w)", text) is not None


class ThirdPartyNoticesTest(unittest.TestCase):
    def test_lockfile_lists_a_real_runtime_classpath(self) -> None:
        # Guards the parser: an empty or tiny result would make the coverage test below vacuous.
        self.assertGreater(len(release_runtime_artifacts()), 100)

    def test_every_release_runtime_dependency_is_covered(self) -> None:
        text = NOTICES.read_text(encoding="utf-8")
        missing = [
            f"{group}:{artifact}"
            for group, artifact in release_runtime_artifacts()
            if not mentions_group(text, group) and f"{group}:{artifact}" not in text
        ]
        self.assertEqual(
            [],
            missing,
            "these releaseRuntimeClasspath dependencies are not covered by THIRD_PARTY_NOTICES.md; "
            "add their licence (as the library's POM declares it) to the root file and the asset copy",
        )

    def test_the_app_copy_is_byte_identical_to_the_root_file(self) -> None:
        self.assertEqual(
            NOTICES.read_bytes(),
            ASSET_COPY.read_bytes(),
            "app-android/src/main/assets/licenses/THIRD_PARTY_NOTICES.md must be an exact copy of "
            "THIRD_PARTY_NOTICES.md; edit the root file and copy it over",
        )

    def test_the_notices_carry_the_texts_the_licences_require(self) -> None:
        text = NOTICES.read_text(encoding="utf-8")
        # Apache-2.0 §4(a): recipients get a copy of the licence.
        self.assertIn("Apache License\n                           Version 2.0, January 2004", text)
        self.assertIn("END OF TERMS AND CONDITIONS", text)
        # libyuv (declared in the CameraX camera-core POM): BSD requires the notice in binary form.
        self.assertIn("androidx.camera:camera-core", text)
        self.assertIn("Copyright 2011 The LibYuv Project Authors. All rights reserved.", text)
        self.assertIn("Redistributions in binary form must reproduce the above copyright", text)
        # The non-Apache runtime components, with the licence their POM declares.
        self.assertRegex(text, r"com\.google\.protobuf: protobuf-javalite\. POM: \"BSD-3-Clause\"")
        self.assertRegex(text, r"org\.codehaus\.mojo: animal-sniffer-annotations\. POM: \"MIT license\"")
        self.assertRegex(text, r"org\.checkerframework: checker-compat-qual")
        # The bundled pose model and the primary source for its licence.
        self.assertIn("assets/pose_landmarker_lite.task", text)
        self.assertIn(MODEL_CARD_URL, text)

    def test_wording_is_licence_neutral(self) -> None:
        # The repository's own licence is not decided here (see LICENSING.md); neither this file nor the
        # app screen may claim one for Crocodyl.
        text = NOTICES.read_text(encoding="utf-8")
        self.assertIsNone(re.search(r"open[- ]source|\bFOSS\b", text, re.IGNORECASE))
        self.assertIsNone(re.search(r"[\w.+-]+@[\w-]+\.[\w.-]+", text), "no e-mail addresses in the notices")

    def test_settings_about_opens_a_screen_that_renders_the_bundled_file(self) -> None:
        screens = SETTINGS_SCREENS.read_text(encoding="utf-8")
        self.assertIn('"licenses/THIRD_PARTY_NOTICES.md"', screens)
        self.assertIn('title = "Third-party licences"', screens)
        self.assertIn("fun SettingsLicencesScreen()", screens)
        activity = MAIN_ACTIVITY.read_text(encoding="utf-8")
        self.assertIn('const val S_LICENCES = "s_licences"', activity)
        self.assertIn("composable(Routes.S_LICENCES) { SettingsLicencesScreen() }", activity)
        self.assertIn("nav.navigate(Routes.S_LICENCES)", activity)


if __name__ == "__main__":
    unittest.main()
