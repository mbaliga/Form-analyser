import datetime as dt
import json
import tempfile
import unittest
from pathlib import Path

from validate_directory import validate_directory


VALID = {
    "id": "coach-example",
    "kind": "coach",
    "name": "Example Coach",
    "country": "IN",
    "region": "Karnataka",
    "source": "community",
    "sourceUrl": "https://example.org/listing",
    "lastVerifiedAt": "2026-09-20",
    "license": "CC BY 4.0",
    "website": "https://example.org",
}


class DirectoryValidationTest(unittest.TestCase):
    def write(self, root: Path, name: str, value: object) -> None:
        (root / name).write_text(json.dumps(value), encoding="utf-8")

    def test_valid_record_passes(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.write(root, "coach.json", VALID)
            self.assertEqual([], validate_directory(root, dt.date(2026, 9, 22)))

    def test_missing_provenance_and_contact_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            record = {key: value for key, value in VALID.items() if key not in {"sourceUrl", "website"}}
            self.write(root, "bad.json", record)
            errors = validate_directory(root, dt.date(2026, 9, 22))
            self.assertTrue(any("missing required field" in error for error in errors))
            self.assertTrue(any("contact field" in error for error in errors))

    def test_duplicate_ids_and_future_dates_fail(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            future = dict(VALID, lastVerifiedAt="2026-09-23")
            self.write(root, "a.json", VALID)
            self.write(root, "b.json", future)
            errors = validate_directory(root, dt.date(2026, 9, 22))
            self.assertTrue(any("duplicate id" in error for error in errors))
            self.assertTrue(any("future" in error for error in errors))

    def test_invalid_email_format_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.write(root, "bad-email.json", {**{key: value for key, value in VALID.items() if key != "website"}, "email": "not-an-email"})
            errors = validate_directory(root, dt.date(2026, 9, 22))
            self.assertTrue(any("valid address format" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
