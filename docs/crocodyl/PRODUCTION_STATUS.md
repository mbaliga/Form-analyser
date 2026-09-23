# Crocodyl production status

Last reviewed: 2026-09-24

Latest verification replay: root JVM `test` passed on the current tree with Temurin JDK 21 and
Gradle 8.14.3 (42 actionable tasks). The Android compile was provisioned with API 35/build-tools
35.0.0 and caught a missing `PairedSessionCard` import; that defect is fixed in `2e7cdbd`. An
unrestricted replay completed `:app-android:testDebugUnitTest`, `:app-android:testReleaseUnitTest`,
and `:app-android:lintRelease` successfully on 2026-09-24 using Temurin JDK 21 and Gradle 8.14.3.
Connected instrumentation and physical-device acceptance remain separate gates. The follow-up
replay after deprecation cleanup also passed; no app-source Kotlin deprecation warnings were
reported.

This is the current release boundary for the Recurve Android app. It separates evidence-backed
completion from gates that require a production signing environment, a connected device, or range
participants.

| Area | Current status | Evidence / remaining gate |
|---|---|---|
| Recurve Android build | QA production candidate | Root JVM tests and the unrestricted Android debug/release unit-test plus release-lint replay pass. Re-run the same gates in final CI before distribution. |
| Data safety and recovery | Implemented and tested | Room recovery, atomic exports, vault staging cleanup, manifest/checksum and privacy filtering are covered by tests and release checks. |
| Equipment Atelier | Implemented for current arrow-first visual scope | Component-separated arrow library, variants and mobile/desktop previews are committed. Owner/device visual review remains a release-quality gate. |
| Fencing | Scaffold only | Sabre/Foil/Epee metadata and manual referee console are present. Automatic two-fencer adjudication is roadmap work. |
| Paired Session | UX walkthrough + beta transport boundary | First-run/returning-user walkthrough is in Android; shared TCP/pairing core is in Shared-Libraries-asoc. Discovery, trust, reconnect and two-device acceptance remain. |
| Desktop file fallback | Local integrity-checked metadata viewer implemented | `web-viewer/` opens current `.crocbak` archives locally, verifies the declared SHA-256 payload checksum, and accepts `.croc` as a forward-compatible extension. Its offline-only contract is now covered by CI tests. Signed `.croc` verification and full report rendering remain. |
| Bocal interoperability | Beta compile integration | Bocal and Crocodyl consume the shared session boundary. No claim of production cross-device acceptance. |
| Production distribution | Not yet releasable | Workflow actions are now immutably pinned and guarded. Production keystore/CI identity, final versioning, clean install/update acceptance, physical-device matrix and range acceptance remain. |

## Current commits

- Crocodyl: `dbe5856` — current connected replay evidence, including the minified-release device-offline result.
- Crocodyl: `9061f89` — paired-session walkthrough persistence test and replay evidence.
- Crocodyl: `ce95805` — current-tree QA APK/AAB package audit and hashes.
- Crocodyl: `e8e34bf` — remove app-source deprecation warnings and record the passing Android gate.
- Crocodyl: `688491c` — Paired Session walkthrough, desktop viewer and documentation.
- Crocodyl: `bd84d72` — immutable GitHub Action pins and workflow policy guard.
- Crocodyl: `2e7cdbd` — fix Android compile import exposed by fresh replay.
- Crocodyl verification: 2026-09-24 — Android debug/release unit tests and `lintRelease` passed with
  the isolated Temurin JDK 21/API-35 toolchain; connected instrumentation was not included.
- Shared-Libraries-asoc: `6b54b98` — bounded local-session transport frames.
- Bocal: `e57ef5e` — session frame guard documentation.

## Release decision

The current artifact may be used for QA and stakeholder review, but it must not be represented as a
production-signed release until the remaining external gates above have evidence attached to the
release checklist.

## Verification replay after warning cleanup

On 2026-09-24, the current tree passed root JVM `test` (42 actionable tasks), Android debug/release
unit tests, Android release lint, offline viewer tests (4/4), community-directory tests (4/4), the
immutable workflow-action guard, and `git diff --check`. The Android app-source deprecation warnings
were removed in `e8e34bf`; the remaining Gradle notice concerns tooling compatibility, not Crocodyl
source. A fresh minified QA-signed APK/AAB package also passed the release preflight with version
`0.6.0-qa-current` / versionCode `620`; no connected-device or physical-device evidence was
available for that package in this replay. A current-tree debug connected replay did pass 3/3 on
the API-35 ARM64 `alloy-api35-arm64` emulator; the subsequent minified-release connected attempt
was rejected because the emulator went offline before test execution.

The paired-session walkthrough persistence flag now has a dedicated Robolectric test covering its
unseen default and completed state in both debug and release unit-test variants.
