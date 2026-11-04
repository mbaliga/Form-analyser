"""Contract tests for the F-Droid recipe, the listing files it depends on and the new CI gates.

These use plain text matching on purpose: they run on a bare Python with no YAML library.
"""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]
APP_ID = "com.asoc.crocodyl"
METADATA = ROOT / "fdroid" / "metadata" / f"{APP_ID}.yml"
VERSION_PROPERTIES = ROOT / "app-android" / "version.properties"
APP_BUILD = ROOT / "app-android" / "build.gradle.kts"
WORKFLOWS = ROOT / ".github" / "workflows"
FASTLANE = ROOT / "fastlane" / "metadata" / "android" / "en-US"
FORBIDDEN_LICENCE_CLAIMS = re.compile(r"open[ -]source|\bFOSS\b", re.IGNORECASE)


def properties() -> dict[str, str]:
    values = {}
    for line in VERSION_PROPERTIES.read_text().splitlines():
        if line.strip() and not line.lstrip().startswith("#"):
            key, _, value = line.partition("=")
            values[key.strip()] = value.strip()
    return values


def top_level(text: str, key: str) -> str:
    """Value of a top-level `Key: value` line, without its trailing comment."""
    match = re.search(rf"^{re.escape(key)}:[ \t]*(.*?)[ \t]*$", text, re.MULTILINE)
    assert match, f"{key} is missing from the metadata"
    value = match.group(1)
    quoted = re.match(r"""^("[^"]*"|'[^']*')""", value)
    if quoted:
        return quoted.group(1)
    return re.sub(r"[ \t]+#.*$", "", value)


def release_name() -> str:
    """The version the next release will carry: versionName without the -dev marker."""
    return re.sub(r"-dev$", "", properties()["versionName"])


class FdroidMetadataTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.text = METADATA.read_text()

    def test_file_is_named_after_the_application_id(self) -> None:
        self.assertRegex(APP_BUILD.read_text(), rf'applicationId\s*=\s*"{re.escape(APP_ID)}"')
        self.assertTrue(METADATA.is_file())

    def test_versions_follow_version_properties_and_the_release_tag(self) -> None:
        code = properties()["versionCode"]
        name = release_name()
        self.assertEqual(top_level(self.text, "CurrentVersion"), name)
        self.assertEqual(top_level(self.text, "CurrentVersionCode"), code)
        builds = self.text.split("\nBuilds:\n", maxsplit=1)[1].split("\nAllowedAPKSigningKeys:", maxsplit=1)[0]
        self.assertRegex(builds, rf"(?m)^\s+- versionName: {re.escape(name)}$")
        self.assertRegex(builds, rf"(?m)^\s+versionCode: {code}$")
        self.assertRegex(builds, rf"(?m)^\s+commit: v{re.escape(name)}$")

    def test_build_recipe_has_no_flavour_and_builds_the_unsigned_release(self) -> None:
        builds = self.text.split("\nBuilds:\n", maxsplit=1)[1]
        self.assertEqual(len(re.findall(r"(?m)^  - versionName:", builds)), 1)
        for line in (
            "subdir: app-android",
            "gradle: [yes]",
            "gradleprops: [withAndroid=true]",
            "output: build/outputs/apk/release/app-android-release-unsigned.apk",
        ):
            self.assertIn(line, builds)

    def test_binaries_url_matches_the_release_asset_names(self) -> None:
        binaries = top_level(self.text, "Binaries")
        self.assertEqual(
            binaries, "https://github.com/mbaliga/Form-analyser/releases/download/v%v/crocodyl-%v.apk"
        )
        release = (WORKFLOWS / "release.yml").read_text()
        self.assertIn('"crocodyl-${RELEASE_VERSION}.apk"', release)
        self.assertIn("release-assets/crocodyl-${{ needs.sign.outputs.version }}.apk", release)
        self.assertIn("tag_name: ${{ needs.sign.outputs.tag }}", release)

    def test_update_checks_read_the_committed_version_file(self) -> None:
        mode = top_level(self.text, "UpdateCheckMode")
        self.assertTrue(mode.startswith("Tags "))
        tag_pattern = re.compile(mode.removeprefix("Tags "))
        self.assertTrue(tag_pattern.search("v0.7.0"))
        self.assertFalse(tag_pattern.search("v0.7.0-rc.1"))
        self.assertFalse(tag_pattern.search("release/v0.7.0"))
        data = top_level(self.text, "UpdateCheckData").split("|")
        self.assertEqual(len(data), 4)
        self.assertEqual(data[0], "app-android/version.properties")
        self.assertEqual(data[2], ".")
        text = VERSION_PROPERTIES.read_text()
        self.assertEqual(re.search(data[1], text).group(1), properties()["versionCode"])
        self.assertEqual(re.search(data[3], text).group(1).strip(), properties()["versionName"])

    def test_signing_key_is_a_quoted_fingerprint_so_yaml_keeps_it_a_string(self) -> None:
        # Unquoted, an all-digit value is read as a number and fdroidserver rejects it.
        self.assertRegex(top_level(self.text, "AllowedAPKSigningKeys"), r'^"[0-9a-f]{64}"$')

    def test_only_non_free_net_is_declared_for_the_optional_coach(self) -> None:
        self.assertEqual(top_level(self.text, "AntiFeatures"), "[NonFreeNet]")
        description = self.text.split("Description: |-\n", maxsplit=1)[1].split("\nAntiFeatures:", maxsplit=1)[0]
        self.assertIn("OPTIONAL AI COACH", description)
        self.assertIn("your own API key", description)

    def test_description_carries_the_whole_store_listing_and_explains_the_anti_feature(self) -> None:
        # fdroid/README.md: the Description is a hand-kept copy of the fastlane listing. Whitespace
        # and the bullet glyph differ between the two formats; the words may not.
        description = self.text.split("Description: |-\n", maxsplit=1)[1].split("\nAntiFeatures:", maxsplit=1)[0]

        def normalise(text: str) -> str:
            return " ".join(text.replace("\u2022", "*").split())

        copy = normalise(description)
        listing = (FASTLANE / "full_description.txt").read_text().strip()
        paragraphs = re.split(r"\n[ \t]*\n", listing)
        self.assertGreater(len(paragraphs), 5)
        for paragraph in paragraphs:
            first_line = paragraph.splitlines()[0]
            with self.subTest(paragraph=first_line):
                self.assertTrue(
                    normalise(paragraph) in copy,
                    f"the F-Droid Description lacks or has changed the listing paragraph starting {first_line!r}; "
                    "copy fastlane/.../full_description.txt into it again",
                )
        # The reason NonFreeNet is declared is part of the F-Droid text only.
        self.assertIn("Non-Free Network Services anti-feature because the optional AI coach", copy)
        self.assertNotIn("anti-feature", normalise(listing))

    def test_no_licence_claims_in_fdroid_files_or_the_store_listing(self) -> None:
        files = [
            *sorted((ROOT / "fdroid").rglob("*")),
            FASTLANE / "short_description.txt",
            FASTLANE / "full_description.txt",
        ]
        files += sorted((FASTLANE / "changelogs").glob("*.txt"))
        for path in files:
            if path.is_file():
                with self.subTest(path=path.relative_to(ROOT).as_posix()):
                    self.assertIsNone(FORBIDDEN_LICENCE_CLAIMS.search(path.read_text()))

    def test_signer_decision_is_still_the_owners_to_make(self) -> None:
        decision = (ROOT / "tools" / "release" / "signer_continuity_decision.json").read_text()
        self.assertIn('"status": "unresolved"', decision)


class FastlaneListingTest(unittest.TestCase):
    def test_short_description_fits_and_has_no_trailing_period(self) -> None:
        text = (FASTLANE / "short_description.txt").read_text().rstrip("\n")
        self.assertLessEqual(len(text), 80)
        self.assertFalse(text.endswith("."))
        self.assertEqual(text, top_level(METADATA.read_text(), "Summary"))

    def test_changelog_exists_for_the_current_version_code_within_the_limit(self) -> None:
        changelog = FASTLANE / "changelogs" / f"{properties()['versionCode']}.txt"
        self.assertTrue(changelog.is_file(), f"{changelog} is missing")
        self.assertLessEqual(len(changelog.read_text()), 500)
        self.assertFalse((FASTLANE / "changelogs" / "1.txt").exists())


class WorkflowContractTest(unittest.TestCase):
    def workflow(self, name: str) -> str:
        return (WORKFLOWS / name).read_text()

    def test_fdroid_workflow_lints_and_scans_with_pinned_inputs(self) -> None:
        text = self.workflow("fdroid.yml")
        self.assertRegex(text, r"FDROIDDATA_REF: [0-9a-f]{40}\n")
        self.assertRegex(text, r"FDROIDSERVER_VERSION: [0-9]+\.[0-9]+\.[0-9]+\n")
        self.assertIn('fdroid lint "$APP_ID"', text)
        self.assertIn('fdroid scanner "$APP_ID" --exit-code', text)
        self.assertIn("pull_request:", text)
        self.assertNotIn("\n  push:", text)
        for path in ('"fdroid/**"', '"app-android/**"', '"*.gradle.kts"'):
            self.assertIn(path, text)
        self.assertIn("permissions:\n  contents: read\n", text)
        self.assertNotIn("contents: write", text)

    def test_fdroid_scan_step_fails_on_reported_problems_and_is_last(self) -> None:
        text = self.workflow("fdroid.yml")
        scan = text.index("- name: Scan the source tree")
        self.assertLess(text.index("- name: Lint the metadata"), scan)
        # fdroidserver ignores --exit-code for source scans, so the step must read the count itself.
        self.assertIn("problems? found", text[scan:])
        self.assertIn("exit 1", text[scan:])
        self.assertNotIn("- name:", text[scan + 1 :])

    def test_reproducible_workflow_builds_twice_from_separate_clean_checkouts(self) -> None:
        text = self.workflow("reproducible.yml")
        self.assertEqual(len(re.findall(r"actions/checkout@", text)), 2)
        self.assertIn("path: build-a", text)
        self.assertIn("path: build-b", text)
        build = "./gradlew -PwithAndroid :app-android:assembleRelease --no-build-cache --no-daemon"
        self.assertEqual(text.count(build), 2)
        self.assertIn('cmp "build-a/$UNSIGNED_APK" "build-b/$UNSIGNED_APK"', text)
        self.assertIn("app-android-release-unsigned.apk", text)
        self.assertIn('- "v*"', text)
        for path in ('"app-android/**"', '"*.gradle.kts"', '"gradle/**"'):
            self.assertIn(path, text)
        self.assertIn("install --disable-pip-version-check diffoscope", text)
        self.assertEqual(text.count("steps.compare.outcome == 'failure'"), 2)
        self.assertNotIn("contents: write", text)

    def test_release_workflow_publishes_the_raw_tag_but_names_assets_with_the_version(self) -> None:
        text = self.workflow("release.yml")
        self.assertEqual(text.count("      tag: ${{ steps.ver.outputs.tag }}\n"), 1)
        self.assertEqual(text.count("      tag: ${{ needs.verify.outputs.tag }}\n"), 1)
        self.assertIn("tag_name: ${{ needs.sign.outputs.tag }}", text)
        self.assertNotIn("needs.sign.outputs.version }}\n          target_commitish", text)
        self.assertNotIn("outputs.tag }}.apk", text)
        self.assertNotIn("outputs.tag }}.aab", text)

    def test_dependency_review_degrades_to_a_warning_only_when_the_graph_is_disabled(self) -> None:
        text = self.workflow("dependency-review.yml")
        probe = text.index("- name: Check that the dependency graph is enabled")
        review = text.index("- name: Review dependency changes for known vulnerabilities")
        warning = text.index("- name: Warn that the review was skipped")
        self.assertLess(probe, review)
        self.assertLess(review, warning)
        self.assertIn("id: graph", text[probe:review])
        self.assertIn("continue-on-error: true", text[probe:review])
        self.assertIn("GH_TOKEN: ${{ github.token }}", text[probe:review])
        self.assertIn('gh api "repos/$REPOSITORY/dependency-graph/sbom"', text[probe:review])
        self.assertIn("if: steps.graph.outcome == 'success'", text[review:warning])
        self.assertIn("if: steps.graph.outcome != 'success'", text[warning:])
        self.assertIn(
            "::warning::Dependency graph disabled; owner must enable it (Settings → Code security and analysis)",
            text[warning:],
        )
        # Only the probe may fail softly; the review itself must keep failing the check.
        self.assertEqual(text.count("continue-on-error"), 1)


if __name__ == "__main__":
    unittest.main()
