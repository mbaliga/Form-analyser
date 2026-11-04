#!/usr/bin/env python3
"""Fail closed if a GitHub workflow uses a mutable third-party action ref."""

from pathlib import Path
import re
import sys

WORKFLOWS = Path(__file__).parents[2] / ".github" / "workflows"
USES = re.compile(r"^\s*-?\s*uses:\s*([^\s#]+)")
SHA = re.compile(r"^[0-9a-f]{40}$")


def main() -> int:
    failures: list[str] = []
    workflow_files = sorted(WORKFLOWS.glob("*.y*ml"))
    for workflow in workflow_files:
        for number, line in enumerate(workflow.read_text().splitlines(), start=1):
            match = USES.match(line)
            if not match:
                continue
            ref = match.group(1)
            if ref.startswith("./") or ref.startswith("docker://"):
                continue
            if "@" not in ref:
                failures.append(f"{workflow}:{number}: action has no immutable ref: {ref}")
                continue
            action, commit = ref.rsplit("@", 1)
            if not SHA.fullmatch(commit):
                failures.append(
                    f"{workflow}:{number}: {action} must use a 40-character commit SHA, got {commit}"
                )
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print(f"Verified immutable action refs in {len(workflow_files)} workflow files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
