#!/usr/bin/env python3
"""Validate Crocodyl community-directory records with Python's standard library only."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse


ID_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{2,127}$")
EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
KINDS = {"coach", "training_centre", "store"}
SOURCES = {"curated", "community", "imported"}
REQUIRED = {
    "id", "kind", "name", "country", "region", "source", "sourceUrl",
    "lastVerifiedAt", "license",
}
ALLOWED = REQUIRED | {
    "city", "description", "website", "email", "phone", "latitude",
    "longitude", "tags",
}
CONTACT_FIELDS = ("website", "email", "phone")


def _fail(path: Path, message: str) -> str:
    return f"{path}: {message}"


def _url(value: object, field: str, path: Path) -> str | None:
    if not isinstance(value, str) or not value:
        return _fail(path, f"{field} must be a non-empty string")
    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        return _fail(path, f"{field} must be an http(s) URL")
    return None


def validate_entry(entry: object, path: Path, today: dt.date | None = None) -> list[str]:
    errors: list[str] = []
    if not isinstance(entry, dict):
        return [_fail(path, "record must be a JSON object")]

    missing = sorted(REQUIRED - entry.keys())
    unknown = sorted(set(entry) - ALLOWED)
    if missing:
        errors.append(_fail(path, f"missing required field(s): {', '.join(missing)}"))
    if unknown:
        errors.append(_fail(path, f"unknown field(s): {', '.join(unknown)}"))

    record_id = entry.get("id")
    if not isinstance(record_id, str) or not ID_RE.fullmatch(record_id):
        errors.append(_fail(path, "id must match ^[a-z0-9][a-z0-9._-]{2,127}$"))
    if entry.get("kind") not in KINDS:
        errors.append(_fail(path, f"kind must be one of: {', '.join(sorted(KINDS))}"))
    for field, maximum in (("name", 160), ("region", 120), ("city", 120), ("description", 1000), ("license", 160)):
        if field in entry and (not isinstance(entry[field], str) or not entry[field].strip()):
            errors.append(_fail(path, f"{field} must be a non-empty string"))
        elif field in entry and len(entry[field]) > maximum:
            errors.append(_fail(path, f"{field} exceeds {maximum} characters"))
    if not isinstance(entry.get("country"), str) or len(entry["country"]) != 2:
        errors.append(_fail(path, "country must be a two-letter code"))
    if entry.get("source") not in SOURCES:
        errors.append(_fail(path, f"source must be one of: {', '.join(sorted(SOURCES))}"))

    for field in CONTACT_FIELDS:
        if field in entry and not isinstance(entry[field], str):
            errors.append(_fail(path, f"{field} must be a string"))
    if "email" in entry and isinstance(entry["email"], str) and not EMAIL_RE.fullmatch(entry["email"]):
        errors.append(_fail(path, "email must have a valid address format"))
    if not any(isinstance(entry.get(field), str) and entry[field].strip() for field in CONTACT_FIELDS):
        errors.append(_fail(path, "at least one contact field is required: website, email or phone"))
    for field in ("website", "sourceUrl"):
        if field in entry:
            error = _url(entry[field], field, path)
            if error:
                errors.append(error)

    verified = entry.get("lastVerifiedAt")
    try:
        verified_date = dt.date.fromisoformat(verified)
        if today is not None and verified_date > today:
            errors.append(_fail(path, "lastVerifiedAt cannot be in the future"))
    except (TypeError, ValueError):
        errors.append(_fail(path, "lastVerifiedAt must be an ISO date (YYYY-MM-DD)"))

    for field, lower, upper in (("latitude", -90, 90), ("longitude", -180, 180)):
        if field in entry and (not isinstance(entry[field], (int, float)) or isinstance(entry[field], bool) or not lower <= entry[field] <= upper):
            errors.append(_fail(path, f"{field} must be a number between {lower} and {upper}"))
    if "tags" in entry and (not isinstance(entry["tags"], list) or len(entry["tags"]) > 32 or any(not isinstance(tag, str) or len(tag) > 48 for tag in entry["tags"])):
        errors.append(_fail(path, "tags must contain at most 32 strings of at most 48 characters"))
    return errors


def validate_directory(directory: Path, today: dt.date | None = None) -> list[str]:
    if not directory.is_dir():
        return [f"{directory}: directory does not exist"]
    errors: list[str] = []
    seen: dict[str, Path] = {}
    for path in sorted(directory.rglob("*.json")):
        if path.name.startswith("."):
            continue
        try:
            entry = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            errors.append(_fail(path, f"cannot read JSON: {error}"))
            continue
        errors.extend(validate_entry(entry, path, today))
        if isinstance(entry, dict) and isinstance(entry.get("id"), str):
            previous = seen.get(entry["id"])
            if previous:
                errors.append(_fail(path, f"duplicate id {entry['id']!r}; already defined in {previous}"))
            else:
                seen[entry["id"]] = path
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path, help="directory containing one JSON object per record")
    args = parser.parse_args(argv)
    errors = validate_directory(args.directory, dt.date.today())
    if errors:
        print("Community directory validation failed:", file=sys.stderr)
        print("\n".join(f"- {error}" for error in errors), file=sys.stderr)
        return 1
    print(f"Community directory valid: {args.directory}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
