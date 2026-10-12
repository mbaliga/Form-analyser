# Crocodyl production status

Last reviewed: 2026-10-13

Current-tree Recurve QA replay after cinematic Home/onboarding refinement (2026-10-13): version
`0.6.0-current-tree-qa-20261013`, versionCode 679. A fresh forced root/JVM test run passed (50
Gradle tasks executed); the complete Gradle root + Android debug/release unit-test + `lintRelease`
replay passed (113 actionable tasks). The prior connected `ProductionSmokeTest` passed 10/10 on
API-35 ARM64 for debug and minified release. Release APK/AAB passed `tools/release/verify_android_release.sh`:
matching committed QA/debug signer `36f33a33f9415fbe4f31556a3dde9bc010d03c502bb3dfe6b2bfaf0cbafdaebc`,
targetSdk 36, merged manifest allowlist, APK ZIP alignment, AAB `PAGE_ALIGNMENT_16K`, 11 aligned
64-bit ELF libraries, pose-model hash and backup resources. APK SHA-256
`affab55015bdaa75bbb98c9492908f35a0d8e2cbcd3d1f889479701bf380c2c3`; AAB SHA-256
`45350ba20d416b9e51058a5ae3b9ed93fb5e2f204b077e8b80db3a56a2b25de8`. The v679 minified QA build
was clean-installed on API-35 ARM64 and cold-launched with `Status: ok`; its process remained
live after four seconds and the checked logcat window contained no fatal/class-loading error. This
is a QA emulator clean-install smoke only, not an update over a prior production APK. Follow-up
API-36 16 KB verification (2026-10-13): the approved Google APIs ARM64 16 KB system image was
installed after redirecting sdkmanager's cache to a writable temporary `ANDROID_USER_HOME`.
`getconf PAGE_SIZE` returned 16384. The latest-tree debug and minified-release
`ProductionSmokeTest` suites each passed 10/10 (zero failures/skips) on the disposable API-36
ARM64 emulator. The Quick Score flow now enters a 10, recreates the Activity, verifies the scored
arrow still appears from persisted state, presses system Back to Home, reopens Score to verify the
same arrow remains, then undoes it and verifies cleanup. The QA install path also passed:
versionCode 678 (`0.6.0-session-smoke-qa`, built from `042de10`) installed, then `adb install -r`
upgraded to versionCode 679. A direct cold launch returned `Status: ok`, process remained live after
four seconds, and no fatal/class-loading error appeared in the checked logcat. The upgraded v679
APK/AAB passed the integrated release preflight again; current APK SHA-256 is recorded above and
AAB SHA-256 remains unchanged. This is QA-only upgrade evidence, not an update over the prior
production APK or physical-device acceptance. Source-tree
contracts also passed: release 39/39, shared checkout 4/4, web viewer 10/10 plus ECDSA
interoperability, community directory 4/4, Atelier/photo 9/9, arrow library 4/4, arrow-reference
library 7/7, and immutable action refs across 7 workflows. A read-only `git ls-remote` found no
matching branch on origin, so hosted CI has not run for the newest commits. These artifacts use the
QA/debug identity and are not production candidates. Production signing/hosted CI,
physical-device/range acceptance, 30 real sessions, the physical
clean-install/update matrix, and owner approval remain open.

Previous current-tree Recurve QA replay after session-start smoke and Home visual correction (2026-10-11):
version `0.6.0-session-smoke-qa`, versionCode 678. Root/JVM tests, Android debug/release unit tests,
and `lintRelease` pass (113 actionable Gradle tasks). Connected `ProductionSmokeTest` passes 10/10
with zero failures/errors/skips on API-35 ARM64 and API-36 ARM64 16 KB-page emulators for both
debug and minified release. The new Train → selected round → skipped pre-check-in → persisted
session test covers camera-denied, permanently blocked, and camera-less states; the scorecard
activity-recreation smoke also passes. APK/AAB pass integrated preflight: QA signer fingerprint
`36f33a33f9415fbe4f31556a3dde9bc010d03c502bb3dfe6b2bfaf0cbafdaebc`, targetSdk 36, merged manifest
allowlist, APK ZIP alignment, AAB `PAGE_ALIGNMENT_16K`, 11 aligned 64-bit ELF libraries per
artifact, pose-model hash and backup resources. APK SHA-256
`6ee9bd509669dc07252333e335664b7b641597dca2c33a5a02be9ae5ab2193a5`; AAB SHA-256
`9eed6e44790886d9274259b603cc662014f4e63c8e9d9ee31880e98ceb441999`. These are QA/debug-signed
artifacts, not production release candidates. Production credentials, hosted CI, physical-device/
range acceptance, the 30-session gate and owner approval remain open.

Additional source-tree contracts (2026-10-11): release 39/39, shared-checkout 4/4, web-viewer
10/10, community-directory 4/4, and Atelier plus arrow libraries 20/20 pass. The release workflow
now rejects the committed QA/debug signer fingerprint
before it materializes signing material; tests cover QA rejection, malformed fingerprints and a
well-formed non-QA value. This prevents an accidental QA-key release but does not establish the
missing production signing identity. The production
signing properties and required environment inputs are absent from this checkout. A read-only
`git ls-remote` check found no `codex/production-hardening` branch on `origin`, so hosted CI has not
run for these commits; no remote write was performed.

Previous current-tree QA replay (2026-10-11): version `0.6.0-current-tree-qa`, versionCode 669.
Its earlier 9/9 instrumentation and APK/AAB digests are historical; use v679 above for the current
source tree.

16 KB emulator CI lane (2026-10-11): Android CI now includes a Google APIs 16 KB page-size
emulator run for connected debug smoke tests; the release workflow adds the same check for the
signed, minified release APK. Both assert `getconf PAGE_SIZE` is exactly 16384 before testing.
The emulator action is pinned to reviewed upstream v2.38.0, which supports the `google_apis_ps16k`
image target. Local v668 connected runs already pass on an API-36 16 KB emulator; the new GitHub
Actions lanes have not yet run and must pass in CI before they count as release evidence.

Post-CI safeguard verification (2026-10-11): release workflow contract suite passes 38/38;
shared checkout 4/4, offline viewer 10/10, community directory 4/4, Atelier asset/photo contracts
9/9, arrow library 4/4, arrow reference library 7/7, and immutable action refs across all 7
workflows pass. The retained local connected-test XML reports show 9/9 for each debug and
minified-release suite on API-35 ARM64 and API-36 16 KB ARM64. The temporary JDK/Android toolchain
was located and used for a fresh full Gradle replay; root/JVM tests, Android debug/release unit tests,
and `lintRelease` passed (113 actionable tasks), followed by connected debug and minified-release
smoke passes on both emulators. The current branch is not present on GitHub, so the new hosted 16 KB
lanes cannot be run until the branch is pushed; no hosted pass is claimed. No product/app source
changed in the CI-safeguard commit.

Current-tree QA release replay (2026-10-11): Home/Atelier visual changes are packaged in QA
candidate `0.6.0-home-smoke-qa` / versionCode 668. Android debug/release unit tests, `lintRelease`,
and connected `ProductionSmokeTest` each pass 9/9 on API-35 ARM64 and API-36 ARM64 16 KB-page
emulators. The suite includes the Home Atelier component-entry assertion. The minified APK/AAB
pass integrated release preflight: APK/AAB signer agreement (committed debug identity), manifest
contract, APK ZIP alignment, AAB `PAGE_ALIGNMENT_16K`, 11 aligned 64-bit ELF libraries in each
artifact, model hash and backup resources. The minified APK was exercised on the 16 KB emulator
without native-load failure. APK SHA-256
`13ac4c7b0ed12ce0350bfb86e8ac2d0ca6c0f54bf2f1749f1bc9735953e17f88`; AAB SHA-256
`5259327800ed76e60852083161e5e6334b0ec77698b7e3982376a89fe10036a0`. Signing is QA-only;
production signing, physical-device/range acceptance, 30-session and owner-approval gates remain
open. The initial connected-test socket failure was resolved by using a temporary writable
`ANDROID_USER_HOME`; an initial API-36 install attempt also required a QA versionCode higher than
the already installed v667 app.

16 KB App Bundle safeguard (2026-10-11): release preflight now parses `BundleConfig.pb` and
requires uncompressed native libraries with `PAGE_ALIGNMENT_16K`, in addition to checking every
arm64-v8a/x86_64 ELF `PT_LOAD` segment in both APK and AAB and the APK ZIP alignment. The v666 QA
APK/AAB passed the integrated release verifier; release contract/regression tests pass 37/37. The
parser is schema-grounded in Google's bundletool `config.proto`, and its purpose matches Android's
[16 KB page-size guidance](https://developer.android.com/guide/practices/page-sizes). This closes a
packaging-verification gap only; it does not replace Play-generated APK testing, physical-device
acceptance, production signing or owner release approval.

Release security gate update (2026-10-11): release artifact preflight now parses the merged binary
manifest and fails on unreviewed permissions or exported components; the sole non-launcher export
allowed today is AndroidX ProfileInstaller behind `android.permission.DUMP`. Five regression tests
cover the accepted surface and fail-closed cases. The versionCode 664 QA APK/AAB at that point
passed the strengthened verifier. This is additional packaging evidence; production signing,
physical-device/range acceptance and owner release approval remain open.

CI failure diagnostics update (2026-10-11): Android CI and release workflows upload connected
instrumentation XML/HTML reports and emulator logcat with `always()` semantics for debug and
minified-release smoke runs. Static contracts verify report/log retention steps. This is a CI
triage safeguard, not extra device-matrix or physical acceptance evidence.

Home + Fencing visual update (2026-10-11): Home now integrates the active rig and Train/Score
actions into the licensed Recurve photo hero, and Fencing has the sport-specific photo, weapon
reference and manual referee console documented below. Both connected debug and QA/debug-signed
minified-release suites pass 9/9 on API-35 ARM64; release lint, root tests and asset provenance
checks pass. QA artifact `0.6.0-home-immersive-qa`, versionCode 664, passed APK/AAB preflight.
APK SHA-256 `2ee1b5d1bc5c91dacef5194b33ca1acb0996074014ee018c86e05c9bb0dfef74`; AAB SHA-256
`bd18106eb9f182712aeddc0b3dd1f80d14f8433bf9ac89268ac5abd74ff9f938`. The artifacts use the
committed QA/debug signer, not the production identity. See
[`visuals/screenshots/home-immersive-api35.png`](visuals/screenshots/home-immersive-api35.png)
and [`visuals/CINEMATIC_APP_REFRESH.md`](visuals/CINEMATIC_APP_REFRESH.md). The refresh is still
partial; physical-device/range acceptance and production signing remain open.

Fencing visual update (2026-10-11): Fencing now uses the existing provenance-checked Unsplash photo
as atmosphere, a weapon selector/reference, bout scoreboard and manual referee command deck. This
UI-only change preserves manual referee behavior; camera adjudication is still not connected. The
connected debug and QA/debug-signed minified-release tests each pass 9/9; root tests (104 actionable
tasks), debug/release unit tests, `lintRelease` and Atelier photo/asset checks (9/9) pass. QA artifact
`0.6.0-fencing-cinematic-qa`, versionCode 663, passed APK/AAB preflight. APK SHA-256
`279907f08c1ec972d931f2ee1861b59d81df156a8ea9b79e7238d09a5970bf2c`; AAB SHA-256
`17685bdfcb7f717146b717af6a6b94b45d9b1f0c9fa343b8181b701eacbf2ce9`. The certificate is the
committed QA/debug signer, not the production identity. Screenshots and design limits are in
[`visuals/CINEMATIC_APP_REFRESH.md`](visuals/CINEMATIC_APP_REFRESH.md). Physical-device/range
acceptance remains separate.

Current-tree release replay (2026-10-11): the latest visual Home and SAF diagnostic commits are
`2efe6e7` and `ceef0a0`. Root `./gradlew test -PwithAndroid --offline --no-daemon` passed (104
actionable tasks); Android debug/release unit tests passed 96/96 combined, `lintRelease` passed,
and connected debug plus minified-release instrumentation passed 9/9 each on API-35 ARM64
`emulator-5554`; the primary-tab smoke asserts Home's new editorial headline, active-rig label,
and Train/Score actions. Release/version contracts 17/17, shared checkout 4/4, offline viewer 10/10,
Community Directory 4/4, authored assets 9/9, arrow library 4/4, arrow-reference library 7/7, and
immutable workflow refs across 7 workflows also passed. QA artifact `0.6.0-cinematic-home-qa`,
versionCode 662, passed APK/AAB preflight. APK SHA-256
`c4b686be1661a27cce1c96b9baa3458a7bf0b5dabc562c1b3febea4c268d1f67`; AAB SHA-256
`4d155c00cdeba56b67f7d7e236a849640a87eb516365abc63d338c77309fea3f`. Signer is the committed
QA/debug identity, not a production key. Production signing inputs are absent from this local
environment; physical-device/range acceptance, the 30-session volume run and owner sign-off remain
open. See the release runbook and acceptance checklist before any distribution.

Repository-side gate replay (2026-10-11): release workflow/resolver tests passed 17/17, shared
checkout 4/4, desktop viewer 10/10, Community Directory 4/4, Atelier authored assets/photo
provenance 9/9, arrow library 4/4, arrow reference library 7/7, and immutable action references
verified across all 7 workflow files. These are local contract checks; they do not close the
physical-device, production-signing or owner-approval gates.

Visual-system follow-up (2026-10-10): Home now uses the bundled, provenance-checked Recurve photo
with an editorial scrim; action cards are more compact, repeated readiness copy is removed, the
Atelier motif avoids repeating the same photograph, and primary navigation is an inset dock. Train
setup now has a range-specific setup panel, horizontal round picker, grouped editable parameters,
and persistent start action while retaining existing session setup behavior. Progress now has a
training-ledger hierarchy and explicit evidence-aware empty states while preserving metric semantics.
API-35 screenshot evidence and the screen-by-screen plan are in
[`visuals/CINEMATIC_APP_REFRESH.md`](visuals/CINEMATIC_APP_REFRESH.md). Android debug/release unit
tests, release lint, connected debug and QA/debug-signed minified-release smoke (7/7 each), and
Atelier photo/asset contracts (9/9) pass. Home, Train, Progress and Score emulator screenshots are checked
in. A later full-tree replay passed `./gradlew test -PwithAndroid` (104 actionable tasks),
`:app-android:lintRelease` (54 actionable tasks), release 17/17, shared checkout 4/4, offline viewer
10/10 plus ECDSA interoperability, Community Directory 4/4, Atelier 9/9, arrow library 4/4 and
arrow-reference 7/7. The QA/debug key is not the production signer. This refresh is partial; other
surfaces and physical-device acceptance remain open.

Dependency-integrity follow-up (2026-10-10): added Gradle SHA-256 dependency verification for the
resolved build graph (`gradle/verification-metadata.xml`: 556 components, 1,007 artifact hashes;
metadata verification enabled, signature verification disabled). An initial enforcement replay
found 23 missing hashes in Android's detached lint configuration; after completing the baseline,
the full Gradle test + Android debug/release unit-test + `lintRelease` replay passed with metadata
writing disabled and `--offline` (111 actionable tasks, 4 executed, 107 up-to-date). This confirms
the exercised dependencies match their lock entries and that Gradle required no unpinned artifact
downloads. The baseline hashes bytes obtained from configured repositories; they are not independent
publisher authentication. Treat future metadata changes as reviewed dependency updates, not routine
generated churn. GradleDependency messages are available-version suggestions, not security/CVE
advisories; lint reported no app-source
findings. A CI trigger audit also found that Muay Thai, bouldering, and Gradle verification-file
changes could miss some existing JVM/Android workflows; both push and pull-request filters now watch
those paths. Contract tests lock the trigger coverage and require every pinned artifact to have a
SHA-256 value; the release workflow contract suite initially passed 8/8, before the version-input
hardening and expanded coverage recorded below. All 7 workflow files retain immutable action refs.

Release-input hardening (2026-10-10): the release workflow rejects names outside a 1–64 character
shell-safe allowlist, bounds derived and user-supplied version codes using checked integer
validation, and passes version outputs via environment variables instead of interpolating them into
shell command source. Plain semver derives a code only when minor and patch are each below 100,
keeping the encoding injective; prerelease/build labels require an explicit code. An explicit code
takes precedence for any label. This prevents component and prerelease/stable collisions and lets
operators maintain monotonic numbering. The resolver is isolated in
`tools/release/resolve_version.sh`, with static workflow contracts and runtime tests for these rules,
custom labels, injection strings and integer overflow. Release publication is isolated into a `contents: write` job while the
signing/build/test job is `contents: read` and checkout credentials are not persisted. The combined
release suite passes 17/17 (10 workflow contracts + 7 resolver tests). YAML parsing, shell syntax, and
immutable action refs across all 7 workflow files also pass. This protects the release workflow from
command injection through manually dispatched version inputs and keeps release-write authority out
of build/test steps; it does not change the production-secret or human-approval gates.

Repository regression replay (2026-10-10): root JVM tests passed (`50` actionable tasks; all
up-to-date). At that replay, release contracts/resolver passed 14/14; a subsequent publication
permission-isolation contract raised the release suite to 15/15, and prerelease/component collision
tests raised it to 17/17. Shared checkout passed 4/4;
offline viewer passed 10/10 plus ECDSA interoperability; Community Directory passed 4/4 and its
entries validated; Atelier assets passed 8/8, arrow-library tests 4/4, and arrow-reference tests
7/7. These checks replay existing scope and generated asset integrity; they do not close
physical-device or production signing acceptance.

Scoring persistence coverage (2026-10-10): introduced an internal `AppDatabase` constructor seam for
`ScoringRepository` while retaining its existing Context constructor, enabling isolated Room-backed
tests. Three Robolectric integration tests verify an active scorecard and X persist across closing
and reopening the database, impossible 1–5 rings are rejected on both numeric and observer paths for
the vertical-triple face, and finished cards reject further scoring/undo without changing history.
Android debug/release unit suites pass 30/30 each; `lintRelease` passes. These app-source changes are
included in fresh QA package `0.6.0-scoring-repository-qa` / versionCode 645. Its APK/AAB passed the
release preflight and connected debug and minified-release instrumentation each passed 4/4 on API-35
ARM64 (`emulator-5554`). APK SHA-256 is
`97a9786b57ae58c6dc691d22e54859204a2db444aee52299b1dba8150faf1025`; AAB SHA-256 is
`a67a25de4eb4eaec24346d542e73cfae13abe10b7814667acdfddf3dded1a415`. Artifacts are QA-key signed,
not production-signed; physical-device and range acceptance remain open.

Capture-window concurrency follow-up (2026-10-10): CameraX inference runs off the UI thread, while
the UI can start/stop recording during an in-flight frame. `PoseFrameBuffer` now serializes frame
collection and snapshots, tags frames with the capture generation at analysis start, rejects frames
that began outside the active window, and prevents late inference completions from changing a
stopped capture. Four deterministic boundary tests cover start, stop, restart and no-recording
behavior. Android debug/release unit suites pass 34/34 each; `lintRelease` passes. Connected debug
and QA-signed minified-release instrumentation now pass 6/6 each on API-35 ARM64 `emulator-5554`,
including Home → Train → Progress → Body → Calendar → Home navigation, the read-only Score/round
chooser entry, and an empty Quick Score card that is closed and resumed in a new Activity. The test
discards only the empty card it created and skips if a pre-existing active card is present. Fresh QA
APK/AAB `0.6.0-pose-capture-window-qa` / versionCode 646 passed artifact
preflight. APK SHA-256
`a4c32676767e6fa2bac61cdabaa47ed8d9c18d3001c22c9aa33d47b0ecd4870d`; AAB SHA-256
`89dac947a6f9234e7055e4ee60cd1702d64e74955b8b2e3f771d4b79144e3abb`. The preflight checksum
helper now supports both Linux `sha256sum` and macOS `shasum`. QA identity only; no production
signing or physical-device/range acceptance is implied.

Retained artifact preflight replay (2026-10-10): revalidated the existing Room 2.7.2 QA APK/AAB
`0.6.0-room-272-qa` / versionCode 642 with `tools/release/verify_android_release.sh`. The APK and
AAB signatures matched the committed QA/debug certificate
`36f33a33f9415fbe4f31556a3dde9bc010d03c502bb3dfe6b2bfaf0cbafdaebc`; signature, ZIP alignment,
package/version, manifest policy, model digest, backup resources, and staging-file checks passed.
APK SHA-256 is `14cbc022254193d39e927155cc44b41b394e054c14619c0827e15609ed7d0f02`; AAB SHA-256 is
`c1ad9976b457d449ef3531be314b040245a238b7489036d04e1837fde798375e`. This was a preflight replay
of retained QA outputs, not a new production build; the signer is not production-approved.

Capture lifecycle follow-up (2026-10-10): hardened the asynchronous CameraX provider callback so
it cannot bind use-cases after `CaptureScreen` is disposed. The binding owner serializes late
completion, in-progress binding and disposal; its four unit tests pass in both debug and release
variants (25/25 each), and release lint passes with no app-source findings. Fresh minified QA APK/AAB
`0.6.0-camera-lifecycle-qa` / versionCode 643 passed release preflight. APK SHA-256 is
`33362f35c58d330e5fa503ff1b0501478d910d697f674f9284b0068d65d85cc8`; AAB SHA-256 is
`1ee7e696dc1080b54382a3fc8c4c1ff538e4c88c28e15a1da222b481253a9024`. This artifact uses the
committed QA/debug key. No connected run was completed for the fix because the temporary API-35
system image download stalled; the October 9 emulator result predates this code change.

MediaPipe frame ownership follow-up (2026-10-10): `PoseRecorder.process` now closes each synchronous
`MPImage` after detection; MediaPipe's bitmap container recycles its owned bitmap on close. If MPImage
construction itself fails, the still-unowned bitmap is recycled directly. This closes a per-frame
native bitmap retention path while preserving CameraX `ImageProxy` cleanup. Android debug/release
unit tests and `lintRelease` pass on this checkout with Gradle dependency verification enabled and
offline. Fresh minified QA APK/AAB `0.6.0-mediapipe-image-qa` / versionCode 644 then passed release
preflight. APK SHA-256 is
`2e71ad2eecb6f19d67bcdf1286e1c23af308da693b2494ccadf6044da49f2bef`; AAB SHA-256 is
`bc7ada1bf9ec3507e22da5b38756fcd479e7f1f523a9e134cc820b2efb8ded8f`. The minified release
debug and minified-release instrumentation both passed 4/4 on API-35 ARM64 emulators (debug
`emulator-5554`, release `emulator-5556`), including a runtime assertion that closing the MediaPipe
image recycles its owned bitmap. Both artifacts use the committed QA/debug signing identity; none of
this substitutes for production signing or physical device/range acceptance.

Product continuation (2026-10-09): Muay Thai and bouldering manual journals, sport-module seams,
Home cards and local Room records are present on this branch; see
[`MULTISPORT_JOURNAL.md`](MULTISPORT_JOURNAL.md). They are not part of the Recurve 0.6 release
candidate. Their API-35 compile, debug/release unit tests and release lint have now passed, and the
Room V9 schema snapshot has been generated. Connected instrumentation for the updated tree is now
verified separately below.

Current-checkout replay (2026-10-09, follow-up to `0b5004f`): `./gradlew test` passed (50
actionable tasks). Android debug and release unit tests passed 42/42; the migration chain, V8 → V9
table creation and V9 privacy-schema test are included. `lintRelease` completed with no source-code
lint findings but 20 GradleDependency available-version suggestions (not security advisories).
Release workflow contracts passed 6/6,
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

Quick Score keypad/undo follow-up (2026-10-10): extended the connected smoke to add a 10 through
the visible numeric keypad, assert `10 · 0 X · 1/72`, undo it to `0 · 0 X · 0/72`, then recreate
the Activity and verify the empty card resumes before cleaning up only that test-created card. The
current tree passes connected debug and minified-release instrumentation 6/6 each on API-35 ARM64
`emulator-5554`; the minified run uses the committed QA/debug signing key, not a production identity.
This is scoring-flow emulator evidence, not physical-device, range, or production-signing acceptance.

Camera permission recovery follow-up (2026-10-10): CaptureScreen now offers an App Settings deep
link after permanent denial and refreshes camera permission on Activity resume. Android debug and
release unit suites include four explicit permission-policy cases; `lintRelease`, and connected
debug plus QA-signed minified-release instrumentation pass on API-35 ARM64. Manual API-35 emulator
round trip (2026-10-10): denied camera twice, verified the capture screen's blocked-state message
and Open app settings action, granted camera in Android Settings, then returned to Capture and
verified the preview resumed and Start recording became enabled. This emulator exercise does not
replace permission/camera acceptance on the required physical-device matrix.

Capture startup ordering follow-up (2026-10-10): the record control now remains disabled and says
“Preparing session…” until the asynchronous session insert completes and the ViewModel publishes its
ready state. This closes the fast-tap window in which CameraX could record before a session ID
existed. The readiness predicate is covered for missing session, camera, permission and model cases;
full recording/range acceptance remains on the device checklist.

Failure-safe session startup (2026-10-10): a new session clears the old session capture gate before
asynchronous creation. Required persistence errors are shown with a back-to-setup action; optional
context and list-refresh errors are surfaced as warnings while preserving the successfully created
session. Session, pre-check-in and soreness records are persisted within one Room transaction. A
SQLite trigger failure-injection tests prove rollback for failures during check-in work and at the
final session insert, including removal of already-inserted soreness rows. Debug/release unit tests
pass 44/44 each. Session startup also rejects duplicate requests while a start or capture is in
flight, and setup navigation occurs only for an accepted start.

Current-tree QA artifact (2026-10-11): minified APK/AAB `0.6.0-saf-orphan-startup-qa`,
versionCode 661, includes SAF export staging, provider-failure cleanup, and startup pruning of
orphaned private staging files. Release preflight passed. APK SHA-256
`22ed20a0ccdde1eb409f4114b356114057d9e3aad6638186d34de3a1e3ae2ed4`; AAB SHA-256
`2de813e95705b5cee35f8070a55cf3179467606aec0d5e4c80ec723fea7369ac`. APK and AAB share the
committed QA/debug signing certificate `36f33a33f9415fbe4f31556a3dde9bc010d03c502bb3dfe6b2bfaf0cbafdaebc`.
Connected debug and QA-signed minified-release instrumentation each pass 9/9 on API-35 ARM64
`emulator-5554`, including first-run onboarding, Body region selection, and app-start removal of an
orphaned export staging file while retaining an unrelated cache file. Root/Android tests and release
lint pass (160 actionable tasks in the combined replay). Four SAF publisher tests cover writer
failure, successful staged copy, copy-failure cleanup, and orphan-versus-active staging behavior.
The installed 658 QA APK rendered first-run onboarding at 1080×1920; screenshot evidence is checked
in under `visuals/screenshots/onboarding-cinematic-refresh-api35.png`. Provider behavior after
force-stop during the final SAF copy remains a physical/provider acceptance item.
These are emulator/build results, not physical-device/range acceptance. QA signing only; production
signing remains open.

| Area | Current status | Evidence / remaining gate |
|---|---|---|
| Recurve Android build | QA-only, not production releasable | Current-tree v679 QA APK/AAB preflight passed; connected debug and QA-signed minified-release instrumentation pass 10/10 on API-35 ARM64. Prior v678 covered API-36 16 KB; v679 still needs the API-36 16 KB rerun. Production signer/hosted CI, physical acceptance, clean install/update and 30-session evidence remain open. |
| Data safety and recovery | Implemented and tested | Room recovery, checksum-manifested database backups restricted to the app-private recovery root, atomic exports, vault staging cleanup, manifest/checksum and privacy filtering are covered by tests and release checks. |
| Equipment Atelier | Implemented for current arrow-first visual scope | Component-separated arrow library, variants and mobile/desktop previews are committed. Owner/device visual review remains a release-quality gate. |
| Fencing | Scaffold only | Sabre/Foil/Epee metadata and manual referee console are present. Automatic two-fencer adjudication is roadmap work. |
| Local Multiplayer | UX walkthrough + beta transport boundary | The first-run/returning-user walkthrough is user-facing; the shared TCP/pairing core is published on Shared-Libraries-asoc `codex/evidence-schema-v1` at `da50c62`. Crocodyl locks that revision, and both CI and release workflows now fetch and verify the actual published checkout. Discovery, trust, reconnect, product event mapping and two-device acceptance remain. |
| Desktop file fallback | Local integrity-checked read-only preview implemented; signed `.croc` verification added | `web-viewer/` opens `.crocbak` recovery archives and signed `.croc` sharing archives locally, verifies the declared SHA-256 payload checksum and manifest/table contract, and verifies the `.croc` P-256/ECDSA producer signature over the payload plus new consent-envelope fields before exposing bounded escaped row previews. Legacy checksum-only signatures remain readable. Full report/analysis rendering remains. |
| Bocal interoperability | Beta compile integration | Bocal and Crocodyl consume the shared session boundary. No claim of production cross-device acceptance. |
| Production distribution | Not yet releasable | Workflow actions are now immutably pinned and guarded. Production keystore/CI identity, final versioning, clean install/update acceptance, physical-device matrix and range acceptance remain. |

## Current commits

- Crocodyl: `18ea047` — refine cinematic Home/onboarding; current-tree v679 QA artifact preflight and API-35 debug/minified-release 10/10 instrumentation pass.
- Crocodyl: `5623cfe` — reject the committed QA/debug signer for production and execute fingerprint-policy negative tests.
- Crocodyl: `15f686b` — record the final QA gate status and 76 Python contract tests after v678.
- Crocodyl: `b8501f2` — capture v678 (10/10 debug and minified-release suites across API 35 and 16 KB API 36, unit tests, lint, preflight and artifact digests).
- Crocodyl: `ff4a0a5` — refine the cinematic Home/Atelier composition and add the Train-session persistence smoke.
- Crocodyl: `7fddd4f` — primary-tab accessibility navigation smoke, verified 5/5 in debug and QA-signed minified release.
- Crocodyl: Quick Score keypad 10 → total update → undo → Activity recreation/resume/cleanup emulator smoke passed in debug and minified release, 6/6 each; only the newly created empty card is discarded.
- Crocodyl: `c00d4f7` — synchronized CameraX capture windows; Android unit tests 34/34 each, lint and versionCode 646 QA artifact preflight passed.
- Crocodyl: `712f388` and `5afb897` — reject colliding semver/versionCode inputs and require explicit codes for suffixed labels.
- Crocodyl: current versionCode 651 QA APK/AAB preflight passed; API-35 ARM64 connected debug and QA-signed minified-release instrumentation passed 6/6 each; unit suites pass 44/44 and `lintRelease` passes.
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

Poundage validation hardening (2026-10-13): optional marked and measured poundage is now accepted
only when blank or finite and greater than zero. Onboarding retains the raw text, so incomplete
numeric input cannot silently become a blank value; it blocks advancing from the equipment step.
Rig editing shows a validation message and disables Save. JVM coverage includes blank, positive,
zero, negative, malformed, NaN and infinity cases. The broader rig and handedness acceptance item
remains open for physical-device verification.

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
