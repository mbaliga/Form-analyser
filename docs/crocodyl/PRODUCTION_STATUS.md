# Crocodyl production status

Last reviewed: 2026-09-24

Latest verification replay: root JVM `test` passed on the current tree with Temurin JDK 21 and
Gradle 8.14.3 (42 actionable tasks). The Android compile was provisioned with API 35/build-tools
35.0.0 and caught a missing `PairedSessionCard` import; that defect is fixed in `2e7cdbd`. A full
Android test/lint completion remains to be replayed in a normal SDK/Kotlin-daemon environment; no
Android pass is claimed from the interrupted sandbox run.

This is the current release boundary for the Recurve Android app. It separates evidence-backed
completion from gates that require a production signing environment, a connected device, or range
participants.

| Area | Current status | Evidence / remaining gate |
|---|---|---|
| Recurve Android build | QA production candidate | JVM, Android unit tests, lint, release artifact preflight and contract replays are recorded in the release checklist. Re-run on the final CI JDK before distribution. |
| Data safety and recovery | Implemented and tested | Room recovery, atomic exports, vault staging cleanup, manifest/checksum and privacy filtering are covered by tests and release checks. |
| Equipment Atelier | Implemented for current arrow-first visual scope | Component-separated arrow library, variants and mobile/desktop previews are committed. Owner/device visual review remains a release-quality gate. |
| Fencing | Scaffold only | Sabre/Foil/Epee metadata and manual referee console are present. Automatic two-fencer adjudication is roadmap work. |
| Paired Session | UX walkthrough + beta transport boundary | First-run/returning-user walkthrough is in Android; shared TCP/pairing core is in Shared-Libraries-asoc. Discovery, trust, reconnect and two-device acceptance remain. |
| Desktop file fallback | Local integrity-checked metadata viewer implemented | `web-viewer/` opens current `.crocbak` archives locally, verifies the declared SHA-256 payload checksum, and accepts `.croc` as a forward-compatible extension. Signed `.croc` verification and full report rendering remain. |
| Bocal interoperability | Beta compile integration | Bocal and Crocodyl consume the shared session boundary. No claim of production cross-device acceptance. |
| Production distribution | Not yet releasable | Workflow actions are now immutably pinned and guarded. Production keystore/CI identity, final versioning, clean install/update acceptance, physical-device matrix and range acceptance remain. |

## Current commits

- Crocodyl: `688491c` — Paired Session walkthrough, desktop viewer and documentation.
- Crocodyl: `bd84d72` — immutable GitHub Action pins and workflow policy guard.
- Crocodyl: `2e7cdbd` — fix Android compile import exposed by fresh replay.
- Shared-Libraries-asoc: `6b54b98` — bounded local-session transport frames.
- Bocal: `e57ef5e` — session frame guard documentation.

## Release decision

The current artifact may be used for QA and stakeholder review, but it must not be represented as a
production-signed release until the remaining external gates above have evidence attached to the
release checklist.
