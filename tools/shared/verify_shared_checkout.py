#!/usr/bin/env python3
"""Verify that an optional beta shared-library checkout matches Crocodyl's lock."""

import argparse
import json
import subprocess
from pathlib import Path
from urllib.parse import urlsplit


ROOT = Path(__file__).parents[2]
LOCK = ROOT / "shared-libraries.lock"


def git(checkout: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(checkout), *args], text=True).strip()


def normalized_remote(url: str) -> str:
    parsed = urlsplit(url.replace("git@github.com:", "https://github.com/"))
    path = parsed.path.removeprefix("/").removesuffix(".git")
    return f"{parsed.hostname}/{path}".lower()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("checkout", type=Path)
    args = parser.parse_args()
    lock = json.loads(LOCK.read_text())
    checkout = args.checkout.resolve()
    actual_revision = git(checkout, "rev-parse", "HEAD")
    actual_remote = normalized_remote(git(checkout, "remote", "get-url", "origin"))
    expected_remote = normalized_remote(lock["repository"])
    if actual_remote != expected_remote:
        raise SystemExit(f"shared checkout remote mismatch: {actual_remote} != {expected_remote}")
    if actual_revision != lock["revision"]:
        raise SystemExit(f"shared checkout revision mismatch: {actual_revision} != {lock['revision']}")
    ref = lock["ref"]
    ref_candidates = []
    for namespace in (f"refs/remotes/origin/{ref}", f"refs/heads/{ref}"):
        try:
            ref_candidates.append(git(checkout, "rev-parse", "--verify", namespace))
        except subprocess.CalledProcessError:
            pass
    if lock["revision"] not in ref_candidates:
        raise SystemExit(f"shared checkout ref mismatch: {ref} does not point to {lock['revision']}")
    print(f"Shared-Libraries-asoc lock verified: {lock['ref']} @ {actual_revision}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
