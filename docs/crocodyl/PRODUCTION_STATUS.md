# Crocodyl production status

Last reviewed: 2026-09-27

Latest verification replay: root JVM `test` and the Android debug/release unit-test plus
`lintRelease` gates passed on the current tree with Temurin JDK 21, Gradle 8.14.3, API 35 and
build-tools 35.0.0. The connected debug suite also passed 3/3 on the `alloy-api35-arm64`
emulator (`emulator-5554`) on 2026-09-27. An earlier Android compile caught a missing
`PairedSessionCard` import; that defect is fixed in `2e7cdbd`. Physical-device and production
signing acceptance remain separate gates. The follow-up replay after deprecation cleanup also
passed; no app-source Kotlin deprecation warnings were reported.

This is the current release boundary for the Recurve Android app. It separates evidence-backed
completion from gates that require a production signing environment, a connected device, or range
participants.

| Area | Current status | Evidence / remaining gate |
|---|---|---|
| Recurve Android build | QA production candidate | Root JVM tests and the unrestricted Android debug/release unit-test plus release-lint replay pass. Re-run the same gates in final CI before distribution. |
| Data safety and recovery | Implemented and tested | Room recovery, checksum-manifested database backups restricted to the app-private recovery root, atomic exports, vault staging cleanup, manifest/checksum and privacy filtering are covered by tests and release checks. |
| Equipment Atelier | Implemented for current arrow-first visual scope | Component-separated arrow library, variants and mobile/desktop previews are committed. Owner/device visual review remains a release-quality gate. |
| Fencing | Scaffold only | Sabre/Foil/Epee metadata and manual referee console are present. Automatic two-fencer adjudication is roadmap work. |
| Local Multiplayer | UX walkthrough + beta transport boundary | The first-run/returning-user walkthrough is user-facing; the shared TCP/pairing core is available in the local Shared-Libraries-asoc checkout. Its transport revision `6b54b98` is not yet present on the published branch used for reproducible checkout; discovery, trust, reconnect and two-device acceptance remain. |
| Desktop file fallback | Local integrity-checked read-only preview implemented; signed `.croc` verification added | `web-viewer/` opens `.crocbak` recovery archives and signed `.croc` sharing archives locally, verifies the declared SHA-256 payload checksum and manifest/table contract, and verifies the `.croc` P-256/ECDSA producer signature over the payload plus new consent-envelope fields before exposing bounded escaped row previews. Legacy checksum-only signatures remain readable. Full report/analysis rendering remains. |
| Bocal interoperability | Beta compile integration | Bocal and Crocodyl consume the shared session boundary. No claim of production cross-device acceptance. |
| Production distribution | Not yet releasable | Workflow actions are now immutably pinned and guarded. Production keystore/CI identity, final versioning, clean install/update acceptance, physical-device matrix and range acceptance remain. |

## Current commits

- Crocodyl: `481d3b3` — fail-fast release signing-input validation.
- Crocodyl verification: 2026-09-27 — connected debug instrumentation passed 3/3 on API-35
  ARM64 emulator `alloy-api35-arm64` / `emulator-5554`.
- Crocodyl: `dbe5856` — earlier connected replay evidence, including the minified-release device-offline result.
- Crocodyl: `9061f89` — paired-session walkthrough persistence test and replay evidence.
- Crocodyl: `bbe938c` — release-workflow security contract tests and CI/release integration.
- Crocodyl: `1c4424c` — fresh shared search/session beta performance report.
- Crocodyl: `f445ed7` — bounded local table-row previews in the desktop viewer.
- Crocodyl: `ce95805` — current-tree QA APK/AAB package audit and hashes.
- Crocodyl: `e8e34bf` — remove app-source deprecation warnings and record the passing Android gate.
- Crocodyl: `688491c` — Paired Session walkthrough, desktop viewer and documentation.
- Crocodyl: `bd84d72` — immutable GitHub Action pins and workflow policy guard.
- Crocodyl: `2e7cdbd` — fix Android compile import exposed by fresh replay.
- Crocodyl verification: 2026-09-24 — Android debug/release unit tests and `lintRelease` passed with
  the isolated Temurin JDK 21/API-35 toolchain; connected instrumentation was not included.
- Shared-Libraries-asoc: `6b54b98` — bounded local-session transport frames.
- Bocal: `e57ef5e` — session frame guard documentation.
- Crocodyl: `777a35a` — Android sharing exports now use `.croc` with a P-256/ECDSA
  producer signature; the offline viewer verifies it locally and keeps `.crocbak` checksum-only.
- Crocodyl: `a9e3da3` — browser signature verification is isolated and covered by executable
  WebCrypto interoperability coverage in CI and release validation.

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

On 2026-09-27, the current tree replayed `:app-android:connectedDebugAndroidTest` successfully:
3/3 tests passed on `alloy-api35-arm64` (`emulator-5554`). This is connected debug evidence only;
production signing, release-connected execution and physical-device acceptance remain separate
gates.

The offline desktop viewer now fails closed on oversized input, ZIP expansion bombs, duplicate
entries, path-traversal names, entries that exceed the bounded local payload contract, missing
checksums, malformed table JSON, row-count mismatches and manifest/table mismatches; six viewer
contract tests pass.
Unexpected non-directory ZIP entries are also rejected rather than ignored, preserving the
manifest-plus-table exchange boundary; this hardening is covered by the viewer contract suite.
The picker drop zone is also keyboard-operable with accessible button semantics; seven viewer
contract tests pass. Unknown manifest schemas are rejected before rendering; eight viewer contract
tests pass. Verified exports now expose bounded read-only table-row previews for desktop coaches;
signed `.croc` envelopes additionally require a valid P-256/ECDSA signature over the declared
payload checksum and, for new envelopes, the sorted table list and consent tier; ten viewer
contract tests pass.
The executable interoperability check also passes against a generated P-256 key and a
Java-compatible DER ECDSA signature, including tamper rejection and unsigned `.crocbak` fallback.

Post-viewer-hardening replay (2026-09-27): root JVM `test`, Android debug/release unit tests and
`lintRelease` passed again. The viewer contract suite remained 10/10 and the executable ECDSA
interoperability check passed after unexpected ZIP-entry rejection was added.

The paired-session walkthrough persistence flag now has a dedicated Robolectric test covering its
unseen default and completed state in both debug and release unit-test variants.

The shared beta performance report was rerun on 2026-09-24 against Shared-Libraries-asoc revision
`6b54b980…`; the recorded search/session measurements are in
`PERFORMANCE_REPORT_2026-09-24.md`.

The 2026-09-27 dependency audit confirmed that the local transport revision `6b54b98` exists in
the available development checkout, while the published `codex/evidence-schema-v1` branch points
to a later history without `local-session-core`. Crocodyl therefore does not pin a submodule or
claim a reproducible production multiplayer build until the transport commit is published in the
shared repository's supported history.

Recovery hardening replay (2026-09-27): both Android unit-test variants, release lint, and
connected debug instrumentation passed after checksum-manifested backups and the app-private
recovery-root restriction were added. Connected instrumentation passed 3/3 on
`alloy-api35-arm64` / `emulator-5554`.

QA release preflight replay (2026-09-27): APK/AAB `0.6.0-qa-preflight` / versionCode `630`
passed the complete local artifact verifier, including APK/AAB certificate matching, package
identity, version metadata, manifest privacy flags, ZIP alignment, model hash, backup resources,
and staging-file checks. It uses the disposable QA/debug signing identity and is not a production
release.

Consolidated repository audit (2026-09-27): root JVM tests passed (42 actionable tasks), release
contracts passed 6/6, viewer contracts passed 10/10, ECDSA interoperability passed, Community
Directory passed 4/4, Atelier assets passed 8/8, original arrow library passed 4/4,
arrow-reference library passed 7/7, immutable workflow refs passed, and `git diff --check` passed.
The open checklist items remain physical-device/range acceptance, production signing/CI evidence,
clean-install/update acceptance, real-session volume, owner approval, and the explicitly documented
multiplayer transport publication and cross-device acceptance gates.
