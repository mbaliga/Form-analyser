#!/usr/bin/env python3
"""Contract tests for the dependency-free Crocodyl desktop viewer."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).parents[2]
VIEWER = ROOT / "web-viewer"


class ViewerContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.html = (VIEWER / "index.html").read_text()
        cls.script = (VIEWER / "viewer.js").read_text()
        cls.readme = (VIEWER / "README.md").read_text()

    def test_picker_accepts_current_and_forward_extensions(self) -> None:
        self.assertRegex(self.html, r'accept="[^"]*\.crocbak[^"]*\.croc')

    def test_viewer_is_dependency_free_and_local_only(self) -> None:
        self.assertNotRegex(self.html, r"<script[^>]+src=[\"']https?://")
        self.assertNotIn("fetch(", self.script)
        self.assertIn("arrayBuffer()", self.script)
        self.assertIn("not upload", self.readme.lower())

    def test_manifest_and_integrity_contract_are_present(self) -> None:
        self.assertIn("manifest.json", self.script)
        self.assertIn("contentChecksum", self.script)
        self.assertIn("crypto.subtle.digest('SHA-256'", self.script)
        self.assertIn("Payload checksum mismatch", self.script)

    def test_zip_and_json_paths_are_bounded_to_known_entries(self) -> None:
        self.assertIn("name.startsWith('tables/')", self.script)
        self.assertIn("name.endsWith('.json')", self.script)
        self.assertIn("isZip(bytes)", self.script)


if __name__ == "__main__":
    unittest.main()
