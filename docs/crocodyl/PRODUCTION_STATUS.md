# Crocodyl production status

Last reviewed: 2026-10-10

Dependency-integrity follow-up (2026-10-10): added Gradle SHA-256 dependency verification for the
resolved build graph (`gradle/verification-metadata.xml`: 556 components, 1,007 artifact hashes;
metadata verification enabled, signature verification disabled). An initial enforcement replay
found 23 missing hashes in Android's detached lint configuration; after completing the baseline,
the full Gradle test + Android debug/release unit-test + `lintRelease` replay passed with metadata
writing disabled and `--offline` (111 actionable tasks, 4 executed, 107 up-to-date). This confirms
the exercised dependencies match their lock entries and that Gradle required no unpinned artifact
downloads. The baseline hashes bytes obtained from configured repositories; they are not independent
publisher authentication. Treat future metadata changes as reviewed dependency updates, not routine
generated churn. Existing GradleDependency update advisories remain; lint reported no app-source
findings.

Product continuation (2026-10-09): Muay Thai and bouldering manual journals, sport-module seams,
Home cards and local Room records are present on this branch; see
[`MULTISPORT_JOURNAL.md`](MULTISPORT_JOURNAL.md). They are not part of the Recurve 0.6 release
candidate. Their API-35 compile, debug/release unit tests and release lint have now passed, and the
Room V9 schema snapshot has been generated. Connected instrumentation for the updated tree is now
verified separately below.

Current-checkout replay (2026-10-09, follow-up to `0b5004f`): `./gradlew test` passed (50
actionable tasks). Android debug and release unit tests passed 42/42; the migration chain, V8 → V9
table creation and V9 privacy-schema test are included. `lintRelease` completed with no source-code
lint findings but 20 GradleDependency update advisories. Release workflow contracts passed 6/6,
immutable workflow pins were verified across 7 files, shared checkout contracts passed 4/4, desktop
viewer contracts 10/10, Community Directory contracts 4/4, and Atelier assets 19/19. Both cinematic
sport photo assets match manifest byte counts, dimensions and hashes, with photographer/source/license
records present. The local sandbox initially blocked Gradle's worker socket; the Android verification
was rerun with temporary state redirected and build-only escalation.

Connected-test follow-up (2026-10-09): `:app-android:connectedDebugAndroidTest` passed 3/3 on
`emulator-5554` (API 35 ARM64). The first attempt exposed stale duplicate dex archives in ignored
generated build output (original `.dex` files plus macOS-style ` 2.dex` copies); Gradle `clean`
removed only generated outputs, after which the clean app and test APKs built. The first clean
connected attempt lacked the Android UTP host-output artifact in offline cache; the online retry
resolved it and passed all 3 tests. This closes current-tree debug emulator instrumentation only;
production signing, physical-device and range acceptance remain open.

Minified-release follow-up (2026-10-09): a QA-only release build signed with the committed debug
keystore passed `:app-android:connectedReleaseAndroidTest` 3/3 on the same emulator. APK/AAB
`0.6.0-qa-20261009` (versionCode 641) passed `tools/release/verify_android_release.sh`, including
APK/AAB signature agreement, package/version, ZIP alignment, manifest security, model hash, backup
resources and staging-file checks. The certificate is explicitly QA/debug, not a production signer;
production keystore and fingerprint are still required before distribution.

Persistence dependency hardening (2026-10-09): Room runtime/KTX/compiler/testing were upgraded
together from 2.6.1 to 2.7.2. Root tests, Android debug/release unit suites (21/21 each), and
`lintRelease` passed; Room KSP generated no schema changes. QA APK/AAB `0.6.0-room-272-qa`
(versionCode 642) built and passed release preflight. After the original emulator disconnected, an
API-35 ARM64 emulator was provisioned in the temporary SDK; current Room 2.7.2 connected debug and
QA-signed minified-release instrumentation then both passed 3/3. Room 2.8.5 was evaluated but its
compiler classpath requires Kotlin stdlib 2.2.0 and failed with an `AbstractMethodError` under this
checkout's Kotlin/KSP 2.1 toolchain; the broader toolchain upgrade was deferred rather than
weakening verification.

Release-environment recheck (2026-10-08): the `codex/production-hardening` checkout was clean at
`30f4cb9` (`docs(release): record provisioned Android replay`). At that time the host had a Java
launcher but no installed Java runtime, `ANDROID_HOME` was unset, and `adb` was unavailable. This
historical recheck did not claim a new Android build or test run. Production CI secrets are not
observable from this local environment and must be confirmed in repository release settings before
dispatch.

Repository-side replay (2026-10-08): release workflow contracts passed 6/6, immutable action refs
were verified across 7 workflow files, shared-checkout contracts passed 4/4, desktop viewer
contracts passed 10/10, Community Directory contracts passed 4/4, and the Atelier asset suites
passed 19/19 (authored assets 8/8, arrow library 4/4, arrow-reference library 7/7). The asset
checks left the checkout unchanged. These checks do not substitute for Android build/test or device
acceptance.

Latest verification replay: on 2026-10-02, root JVM `test`, Android debug/release unit tests,
and `lintRelease` passed with Temurin JDK 21, Gradle 8.14.3, API 35 and build-tools 35.0.0. The
same replay built QA APK/AAB `0.6.0-qa-provisioned` (versionCode 640), passed the complete local
artifact preflight, and passed both connected debug and minified release instrumentation 3/3 on
the `alloy-api35-arm64` emulator (`emulator-5554`). Physical-device and production-signing
acceptance remain separate gates. No app-source Kotlin deprecation warnings were reported; the
environment emitted only its known Gradle/tooling notices.

The 2026-10-02 local contract replay passed shared-library lock verification (4/4), release
contracts (6/6), and desktop viewer contracts (10/10). The locked Shared-Libraries-asoc
checkout is `codex/evidence-schema-v1` at `da50c62dd93bb67d590584a788eb794a6b7b55ec`; the
negative verifier test also confirms that a wrong checkout remote is rejected. The current
the Android replay used an isolated JDK/SDK under `/private/tmp`; CI must still reproduce the
gates with the production signing identity before distribution.

The CI and release workflows now clone the locked ref before their contract suites and invoke the
same verifier against that checkout; this is guarded by the shared lock contract test.
The checkout shape was replayed locally as a shallow branch clone with the canonical GitHub origin,
and the verifier accepted the exact locked revision.

The same replay passed the combined Atelier asset suite (19/19), which includes the arrow
library, arrow-reference library, and authored GLB asset checks under `tools/atelier`.

This is the current release boundary for the Recurve Android app. It separates evidence-backed
completion from gates that require a production signing environment, a connected device, or range
participants.

| Area | Current status | Evidence / remaining gate |
|---|---|---|
| Recurve Android build | QA production candidate | Current Room 2.7.2 tree passes root JVM tests, Android debug/release unit tests, release lint, connected debug and QA-signed minified-release instrumentation (3/3 each), and QA APK/AAB preflight. Production signer and physical acceptance remain open. |
| Data safety and recovery | Implemented and tested | Room recovery, checksum-manifested database backups restricted to the app-private recovery root, atomic exports, vault staging cleanup, manifest/checksum and privacy filtering are covered by tests and release checks. |
| Equipment Atelier | Implemented for current arrow-first visual scope | Component-separated arrow library, variants and mobile/desktop previews are committed. Owner/device visual review remains a release-quality gate. |
| Fencing | Scaffold only | Sabre/Foil/Epee metadata and manual referee console are present. Automatic two-fencer adjudication is roadmap work. |
| Local Multiplayer | UX walkthrough + beta transport boundary | The first-run/returning-user walkthrough is user-facing; the shared TCP/pairing core is published on Shared-Libraries-asoc `codex/evidence-schema-v1` at `da50c62`. Crocodyl locks that revision, and both CI and release workflows now fetch and verify the actual published checkout. Discovery, trust, reconnect, product event mapping and two-device acceptance remain. |
| Desktop file fallback | Local integrity-checked read-only preview implemented; signed `.croc` verification added | `web-viewer/` opens `.crocbak` recovery archives and signed `.croc` sharing archives locally, verifies the declared SHA-256 payload checksum and manifest/table contract, and verifies the `.croc` P-256/ECDSA producer signature over the payload plus new consent-envelope fields before exposing bounded escaped row previews. Legacy checksum-only signatures remain readable. Full report/analysis rendering remains. |
| Bocal interoperability | Beta compile integration | Bocal and Crocodyl consume the shared session boundary. No claim of production cross-device acceptance. |
| Production distribution | Not yet releasable | Workflow actions are now immutably pinned and guarded. Production keystore/CI identity, final versioning, clean install/update acceptance, physical-device matrix and range acceptance remain. |

## Current commits

- Crocodyl: current-tree API-35 ARM64 connected debug instrumentation passed 3/3 on 2026-10-09 after clearing stale generated duplicate dex archives.
- Crocodyl: QA-signed minified release instrumentation passed 3/3 and versionCode 641 APK/AAB preflight passed on 2026-10-09; QA key only.
- Crocodyl: `30f4cb9` — records the provisioned Android replay and current QA-only release boundary.
- Shared-Libraries-asoc: `da50c62` — reconciled and published evidence-schema plus local-session transport history.
- Crocodyl: `2063e84` — shared-library lock verifier negative-path coverage.
- Crocodyl: `1f0626c` — require the locked shared-library revision to be published on the locked ref.
- Crocodyl: `7cf0324` — record the shared-checkout replay evidence.
- Crocodyl: `014b12e` — CI/release workflows fetch and verify the locked shared checkout.
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

The 2026-10-02 dependency audit confirmed that the reconciled local transport history is now
published on Shared-Libraries-asoc `codex/evidence-schema-v1` at `da50c62`. Crocodyl still does
not claim production multiplayer: discovery, trust, reconnect, product event mapping and physical
two-device acceptance remain open.

Recovery hardening replay (2026-09-27): both Android unit-test variants, release lint, and
connected debug instrumentation passed after checksum-manifested backups and the app-private
recovery-root restriction were added. Connected instrumentation passed 3/3 on
`alloy-api35-arm64` / `emulator-5554`.

QA release preflight replay (2026-09-27): APK/AAB `0.6.0-qa-preflight` / versionCode `630`
passed the complete local artifact verifier, including APK/AAB certificate matching, package
identity, version metadata, manifest privacy flags, ZIP alignment, model hash, backup resources,
and staging-file checks. It uses the disposable QA/debug signing identity and is not a production
release.

Provisioned current-tree replay (2026-10-02): with isolated Temurin JDK 21, Gradle 8.14.3,
Android API 35 and build-tools 35.0.0, root JVM tests, Android debug/release unit tests and
`lintRelease` passed. QA APK/AAB `0.6.0-qa-provisioned` / versionCode `640` passed the reusable
artifact preflight, including APK v2/AAB signing, certificate consistency, package/version
metadata, manifest security, ZIP alignment, BlazePose hash, backup resources and staging-file
checks. Connected debug instrumentation passed 3/3 and connected minified release instrumentation
passed 3/3 on `alloy-api35-arm64` / `emulator-5554`. The signing identity was the committed QA
debug key; this is current emulator and artifact evidence, not production signing or physical
device/range acceptance.

Consolidated repository audit (2026-09-27): root JVM tests passed (42 actionable tasks), release
contracts passed 6/6, viewer contracts passed 10/10, ECDSA interoperability passed, Community
Directory passed 4/4, Atelier assets passed 8/8, original arrow library passed 4/4,
arrow-reference library passed 7/7, immutable workflow refs passed, and `git diff --check` passed.
The open checklist items remain physical-device/range acceptance, production signing/CI evidence,
clean-install/update acceptance, real-session volume, owner approval, and the explicitly documented
multiplayer transport publication and cross-device acceptance gates.
