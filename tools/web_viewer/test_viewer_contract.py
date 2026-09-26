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

    def test_archive_expansion_and_entry_paths_fail_closed(self) -> None:
        self.assertIn("MAX_ARCHIVE_BYTES", self.script)
        self.assertIn("MAX_ZIP_ENTRIES", self.script)
        self.assertIn("MAX_EXPANDED_BYTES", self.script)
        self.assertIn("Unsafe ZIP entry path", self.script)
        self.assertIn("Duplicate ZIP entry", self.script)
        self.assertIn("Archive expands beyond the local viewer limit", self.script)
        self.assertIn("inflated.byteLength", self.script)
        self.assertIn("ZIP entry name mismatch", self.script)
        self.assertIn("Invalid ZIP local header", self.script)

    def test_exports_require_checksum_and_manifest_table_match(self) -> None:
        self.assertIn("Manifest does not declare a payload checksum", self.script)
        self.assertIn("Manifest table list does not match the archive", self.script)
        self.assertIn("Unsupported table payload", self.script)

    def test_file_drop_zone_is_keyboard_accessible(self) -> None:
        self.assertIn('role="button"', self.html)
        self.assertIn('aria-controls="file"', self.html)
        self.assertIn("e.key === 'Enter'", self.script)
        self.assertIn("e.key === ' '", self.script)

    def test_viewer_fails_closed_on_unknown_manifest_schema(self) -> None:
        self.assertIn("manifest.schemaVersion !== 1", self.script)
        self.assertIn("Unsupported manifest schema", self.script)

    def test_verified_exports_have_bounded_read_only_table_previews(self) -> None:
        self.assertIn("MAX_DISPLAY_ROWS", self.script)
        self.assertIn("MAX_DISPLAY_CHARS", self.script)
        self.assertIn("table-preview", self.script)
        self.assertIn("JSON.stringify", self.script)
        self.assertIn("read-only preview", self.script)


if __name__ == "__main__":
    unittest.main()
