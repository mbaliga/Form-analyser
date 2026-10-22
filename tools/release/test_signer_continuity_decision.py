"""Tests for the fail-closed production signer-continuity decision gate."""

import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]
SCRIPT = ROOT / "tools" / "release" / "verify_signer_continuity_decision.py"
DECISION = ROOT / "tools" / "release" / "signer_continuity_decision.json"


class SignerContinuityDecisionTest(unittest.TestCase):
    def run_gate(self, value: object) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "decision.json"
            path.write_text(json.dumps(value))
            return subprocess.run(
                ["python3", str(SCRIPT), "--decision-file", str(path)],
                text=True,
                capture_output=True,
                check=False,
            )

    def test_checked_in_decision_fails_closed_as_unresolved(self) -> None:
        result = subprocess.run(["python3", str(SCRIPT)], text=True, capture_output=True, check=False)
        self.assertEqual(result.returncode, 1)
        self.assertIn("No owner-approved signer-continuity decision", result.stderr)

    def test_approved_decision_requires_strategy_channels_approval_and_evidence(self) -> None:
        result = self.run_gate(
            {
                "status": "approved",
                "distribution_channels": ["direct-apk"],
                "strategy": "apk-signing-lineage",
                "owner_approval_reference": "release decision record",
                "verification_evidence": ["device update and data-continuity report"],
            }
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Owner-approved signer-continuity decision recorded", result.stdout)

    def test_approved_decision_without_migration_evidence_is_rejected(self) -> None:
        result = self.run_gate(
            {
                "status": "approved",
                "distribution_channels": ["google-play"],
                "strategy": "play-app-signing",
                "owner_approval_reference": "decision-1",
                "verification_evidence": [],
            }
        )
        self.assertEqual(result.returncode, 1)
        self.assertIn("verification_evidence must list", result.stderr)

    def test_invalid_channel_or_strategy_is_rejected(self) -> None:
        result = self.run_gate(
            {
                "status": "approved",
                "distribution_channels": ["unknown"],
                "strategy": "reuse-public-debug-key",
                "owner_approval_reference": "decision-1",
                "verification_evidence": ["report"],
            }
        )
        self.assertEqual(result.returncode, 1)
        self.assertIn("distribution_channels must list", result.stderr)
        self.assertIn("strategy must be one of", result.stderr)

    def test_non_string_channel_or_strategy_is_rejected_without_crashing(self) -> None:
        result = self.run_gate(
            {
                "status": "approved",
                "distribution_channels": [{}],
                "strategy": {},
                "owner_approval_reference": "decision-1",
                "verification_evidence": ["report"],
            }
        )
        self.assertEqual(result.returncode, 1)
        self.assertIn("distribution_channels must list", result.stderr)
        self.assertIn("strategy must be one of", result.stderr)

    def test_decision_schema_rejects_extra_or_missing_keys(self) -> None:
        decision = json.loads(DECISION.read_text())
        decision["unreviewed_override"] = True
        result = self.run_gate(decision)
        self.assertEqual(result.returncode, 1)
        self.assertIn("exactly", result.stderr)


if __name__ == "__main__":
    unittest.main()
