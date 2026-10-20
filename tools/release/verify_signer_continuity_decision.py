#!/usr/bin/env python3
"""Fail closed until production signer/update migration has explicit owner approval."""

import argparse
import json
import sys
from pathlib import Path


ROOT = Path(__file__).parents[2]
DEFAULT_DECISION = ROOT / "tools" / "release" / "signer_continuity_decision.json"
CHANNELS = {"google-play", "direct-apk"}
STRATEGIES = {"play-app-signing", "apk-signing-lineage", "new-application-id"}
EXPECTED_KEYS = {
    "status",
    "distribution_channels",
    "strategy",
    "owner_approval_reference",
    "verification_evidence",
}


def validate(path: Path) -> list[str]:
    try:
        decision = json.loads(path.read_text())
    except (OSError, json.JSONDecodeError) as error:
        return [f"Cannot read signer-continuity decision: {error}"]

    if not isinstance(decision, dict) or set(decision) != EXPECTED_KEYS:
        return [f"Decision must be a JSON object with exactly: {', '.join(sorted(EXPECTED_KEYS))}."]
    if decision["status"] != "approved":
        return ["No owner-approved signer-continuity decision is recorded; production release is blocked."]

    errors = []
    channels = decision["distribution_channels"]
    if not isinstance(channels, list) or not channels or any(
        not isinstance(channel, str) or channel not in CHANNELS for channel in channels
    ):
        errors.append("distribution_channels must list google-play and/or direct-apk.")
    strategy = decision["strategy"]
    if not isinstance(strategy, str) or strategy not in STRATEGIES:
        errors.append(f"strategy must be one of: {', '.join(sorted(STRATEGIES))}.")
    if not isinstance(decision["owner_approval_reference"], str) or not decision["owner_approval_reference"].strip():
        errors.append("owner_approval_reference must identify the recorded owner decision.")
    evidence = decision["verification_evidence"]
    if not isinstance(evidence, list) or not evidence or any(
        not isinstance(item, str) or not item.strip() for item in evidence
    ):
        errors.append("verification_evidence must list at least one verified migration/update evidence reference.")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--decision-file", type=Path, default=DEFAULT_DECISION)
    path = parser.parse_args().decision_file
    errors = validate(path)
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print(f"Owner-approved signer-continuity decision recorded: {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
