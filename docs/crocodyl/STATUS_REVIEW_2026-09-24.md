# Crocodyl and Bocal status review — 2026-09-24

This is an evidence-based snapshot of the two products and their shared local-session work. It
does not replace the release acceptance checklist or convert a beta boundary into a production
claim.

## Crocodyl

Current branch: `codex/production-hardening`.

| Surface | Status | What is still required |
|---|---|---|
| Recurve Android core | QA candidate | Replay Android compile/unit/lint in CI, then complete physical-device and range acceptance. |
| Arrow Atelier | Implemented for the current arrow-first scope | Owner review of the committed component-separated geometry and final device visual review. |
| Fencing | Scaffold | Sabre/Foil/Epee metadata and manual referee console exist; automatic camera adjudication is not implemented. |
| Paired Session | First-run walkthrough plus beta transport boundary | Discovery, trust/reconnect policy, two-device acceptance, and production UX wiring. |
| Desktop fallback | Working offline metadata viewer | Current `.crocbak` integrity verification is implemented; signed `.croc` verification and full report rendering remain. |
| Directory / news / advanced search / Baseline | Documented integration direction | The external repository contracts and production data pipelines still need to be selected and wired. |
| Distribution | Not production-approved | Production keystore/CI secrets, versioned artifact, clean-install/update matrix, physical devices, and owner sign-off. |

The viewer was opened locally for this review. Its visible contract is: choose a `.crocbak` export
or future `.croc` package, read it in-browser without upload, and use WhatsApp/USB/Bluetooth/
LocalSend or another file-transfer mechanism to move the file. The viewer has no account or
network dependency.

## Bocal

Current checkout: `/Users/tulikapandey/Documents/Codex/2026-09-14/e-2/work/Bocal`, branch
`main`, seven commits ahead of its configured remote at the time of review.

| Surface | Status | What is still required |
|---|---|---|
| Web practice product | Broad beta surface | Browser e2e/theme gates and supported-device audio/WebGL acceptance. |
| Android shell | Debug-build evidence exists | Real release signing, connected instrumentation, device launch/audio/thermal checks, and teacher review. |
| Local multiplayer | Shared beta bridge only | Bocal does not yet have production live cross-device multiplayer; discovery, reconnect, identity and event mapping remain app-owned work. |
| Shared session dependency | Correct architectural home | Shared-Libraries-asoc owns session identity, packet vocabulary, ordering, dedupe and bounded TCP frames; Crocodyl and Bocal consume it through beta bridges. |

## Shared work already preserved

- The earlier arrow decomposition and variant library remains in the Crocodyl tree and is not being
  replaced by the multiplayer work.
- The local-session core remains in Shared-Libraries-asoc and is consumed by both Crocodyl and Bocal.
- The performance-report utility is present in Crocodyl and is intended to consume the shared
  performance-analysis library for beta evidence.
- Fencing is deliberately a scaffold, not represented as live automatic referee adjudication.

## Practical completion estimate

For the current Recurve Android scope, the software hardening is near the QA-candidate boundary;
the remaining calendar time is dominated by CI/device/range evidence and release-owner decisions,
not by a safe local code change. A production claim should wait for the release checklist to show
the production-signed artifact, physical acceptance matrix, and range/coach acceptance.

For Bocal multiplayer and Crocodyl paired sessions, the existing transport is a beta foundation,
not a finished product feature. A realistic estimate requires the two-device discovery/reconnect
implementation and a physical acceptance run; it should not be counted as complete merely because
both repositories compile against the shared module.

