"""Contract tests for the committed BlazePose model.

The release is reproducible and builds offline (F-Droid, the reproducible-build job), so the model
is a committed asset rather than a build-time download.
"""

import hashlib
import os
import re
import stat
import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]
MODEL = ROOT / "app-android" / "src" / "main" / "assets" / "pose_landmarker_lite.task"
PREFLIGHT = ROOT / "tools" / "release" / "verify_android_release.sh"
GITIGNORE = ROOT / ".gitignore"
APP_BUILD = ROOT / "app-android" / "build.gradle.kts"
MODEL_BYTES = 5_777_746


def preflight_pose_sha256() -> str:
    match = re.search(r"^POSE_SHA256='([0-9a-f]{64})'$", PREFLIGHT.read_text(), re.MULTILINE)
    assert match, "verify_android_release.sh no longer defines POSE_SHA256"
    return match.group(1)


class PoseModelAssetTest(unittest.TestCase):
    def test_model_is_committed_with_the_expected_size(self) -> None:
        self.assertTrue(MODEL.is_file(), f"{MODEL} is missing; the model must be committed, not downloaded")
        self.assertEqual(MODEL.stat().st_size, MODEL_BYTES)

    def test_model_digest_equals_the_release_preflight_constant(self) -> None:
        digest = hashlib.sha256(MODEL.read_bytes()).hexdigest()
        self.assertEqual(digest, preflight_pose_sha256())

    def test_model_is_a_plain_data_file_not_executable_and_not_an_lfs_pointer(self) -> None:
        mode = MODEL.stat().st_mode
        self.assertFalse(mode & (stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH), oct(mode))
        self.assertFalse(MODEL.is_symlink())
        self.assertNotEqual(MODEL.read_bytes()[:24], b"version https://git-lfs.", "LFS pointer")

    def test_gitignore_no_longer_excludes_model_files(self) -> None:
        text = GITIGNORE.read_text()
        self.assertNotIn("assets/*.task", text)
        self.assertNotIn("assets/*.partial", text)

    def test_build_no_longer_downloads_the_model(self) -> None:
        build = APP_BUILD.read_text()
        for forbidden in ("downloadPoseModel", "storage.googleapis.com", ".openStream()"):
            self.assertNotIn(forbidden, build)


if __name__ == "__main__":
    unittest.main()
