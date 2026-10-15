# Crocodyl production status

Last reviewed: 2026-10-16

Current-source test/lint replay (2026-10-16): from the committed checkout, forced all root/module
JVM tests plus Android debug and release unit tests and `lintRelease` with the isolated JDK 21/API 36
toolchain. `BUILD SUCCESSFUL`; all 113 Gradle tasks executed. XML evidence totals 378 tests, 0
failures, 0 errors and 0 skipped. This validates source and lint only; it does not change or promote
the QA-signed v711 APK/AAB, and does not clear the separate emulator UI, production-signing, hosted
CI or physical-device gates below.

Current-source Android replay (2026-10-15): removed an unnecessary safe call in session soreness
creation and redundant non-null assertions in the model-import test. With JDK 21, temporary API 35/36
SDK packages, build-tools 35/36, and Kotlin compilation in-process, the forced Android debug and
release unit suites passed 50/50 each and `lintRelease` passed. QA v711 (`0.6.4-bocal-arc-qa`)
debug/release APK and release AAB assembled; `tools/release/verify_android_release.sh` passed on
the exact QA APK/AAB. The APK signer is the committed debug/QA certificate, never a production key.
APK SHA-256 `cb7196cbb7ffe13b3a8776c68fd8444656458cb38d0a089725c74e4bbb6380bd` (76,017,131 bytes),
AAB SHA-256 `d929d981ce23072d106b40888b1d4aac7ebc7b2d7c8b2626afb8294426feccc6` (42,256,133 bytes),
and R8 mapping SHA-256 `6cf8ed314eb300b02874a3b9ac1a3813889943577c51f473f69eeef7ff49b080` (34,935,496
bytes). Preflight confirms target/compile SDK 36, version metadata, signer agreement, manifest
security, bundled model hash, backup resources, App Bundle 16 KB configuration, and 11 aligned
64-bit libraries in both APK and AAB. A temporary API-36 ARM64/16 KB AVD subsequently booted and
reported `PAGE_SIZE` = 16384. Its current-source debug connected suite ran 11 tests: five
non-UI/resource checks passed; six UI smoke tests failed because the emulator displayed
“Pixel Launcher isn't responding” throughout app launch/navigation assertions. The AVD was then
reconfigured in temporary storage for 4 GB RAM and SwiftShader graphics; after resetting only this
task-created AVD's generated user data, it still did not register with ADB after more than three
minutes. QEMU was observed sleeping at 0% CPU before it was stopped. This is an emulator stability
failure, not evidence that those six app
flows pass or fail on a stable device. Earlier passing v711 runtime evidence remains separately
recorded. Production signing, current-source hosted CI, physical-device/range acceptance and
30-session evidence remain.

Current-source revalidation after the desktop-report work (2026-10-14): provisioned a temporary
JDK 21 from Eclipse Temurin and verified the vendor-published SHA-256 before use. A forced
`./gradlew test --rerun-tasks` completed successfully (50/50 Gradle tasks executed). Python
contracts passed: release 54/54, shared checkout 4/4, Community Directory 4/4, Atelier 20/20, and
desktop viewer 11/11; the seven-workflow immutable-action verifier passed. The Node report test
passed with hostile labels rendered escaped and set-match points kept out of target-score rates;
the `.croc` signature interoperability test passed. This revalidation did not rerun Android
unit/lint/connected tests: the current temporary toolchain has no Android SDK/emulator, and
`app-android` is intentionally included in Gradle only with `-PwithAndroid`. Earlier v711 Android
evidence remains the last Android-source validation; production signing, hosted CI and physical
acceptance remain open. A read-only GitHub Actions audit found the newest push-triggered `main`
CI and Android runs on 2026-09-18 at `d0987ce8` ([CI](https://github.com/mbaliga/Form-analyser/actions/runs/35334586525),
[Android](https://github.com/mbaliga/Form-analyser/actions/runs/35334586097)); the latest visible
push-triggered `feat/immersive-visuals` CI run is 2026-09-16. Current local commits have not been
pushed and therefore have no hosted-run evidence.

## Current-turn evidence and commit trace

- Crocodyl: `44f9aa4` — remove unnecessary nullability warnings and record current-source 50/50 Android debug/release suites, lint, QA v711 artifact preflight, hashes and emulator `SIGILL` boundary.
- Crocodyl: `dfd36f4` — index report-renderer escaping and match-display safeguards.
- Crocodyl: `d92047f` — verify report HTML escaping for hostile round/feature labels and verify set-match display semantics.
- Crocodyl: `34b78b9` — index offline desktop coach analysis and remaining visual/coach acceptance gate.
- Crocodyl: `ef0ba57` — implement the offline coach report and CI/release regression execution.

Current web-source arc parity update (2026-10-14): following the owner's confirmation to match
Bocal's current web-source arc (not its older flat Android screenshot), the mobile dock now draws
its Recurve/destination readout along the upper Bézier, angles the 44 dp context controls to the
rail guides, and supports lower-rail drag preview/release navigation while preserving direct taps
and the five accessible destinations. This supersedes the flat-pill/context-row details in the
historical v708 entry below. QA candidate v710 (`0.6.4-bocal-arc-qa`) now includes this source;
debug/release JVM unit tests, `lintRelease`, and integrated APK/AAB release preflight pass. APK
SHA-256 `ee8923c440a28624f0340f2a30d641403a98c4f4f3d15d789783a89c514b09c0`; AAB SHA-256
`6907043de51846f2e9071a747fe563f1851fe42bc3df4ef7283eb0ec4a73e727`; R8 mapping SHA-256
`663de5f9e74c08820353e71afe1a9c9ac067abfd8563619bb1a14b3ef5902894` (34,926,684 bytes). These
are QA/debug-signed, not distributable. The focused API-35 connected smoke attempt did not reach
its Home assertion (`Find your rhythm.`; accessibility tree empty), so runtime interaction and
visual acceptance are still unverified. Hosted production workflow, production signer and
physical-device gates remain open.

Camera-permission instrumentation hardening (2026-10-14): the training-start smoke now captures the
permission state before launch, explicitly checks the denied or granted capture UI, and asserts the
flow did not mutate the permission state. It leaves permission setup to the device harness because
revoking a live app's permission can terminate the instrumentation process. A fresh UTP install
exercises denial; host-configured API-35 replays verified both pre-granted and pre-revoked states.
The minified QA release suite passed 11/11 in each permission state, with unchanged permission
postconditions asserted. The app APK/AAB were not changed by this instrumentation-only update;
hosted CI and physical-device acceptance remain open.

Artifact-output correction (2026-10-14): connected-test builds without explicit version properties
left release output metadata at `0.6.0-spec-dev` / versionCode 6. Those outputs were not used as
release evidence. Reassembled the exact QA candidate `0.6.4-bocal-arc-qa` / versionCode 709 and
reran integrated APK/AAB preflight successfully. Current QA APK SHA-256
`0b1a967194a5a8b4a4385cd9e1c97374edd5e8b94e90fa1a335094b54983ba29`; AAB SHA-256
`2da58084fb7208b2698e8cecb09375ee87fe3c58a81336642e3774eabe939b7f`. This is QA/debug signing,
not a production-distributable artifact.

QA v709 final-source validation (2026-10-14): local QA candidate `0.6.4-bocal-arc-qa`,
versionCode 709, was built after the current navigation and equipment-validator sources. Debug and
release Android unit tests, `lintRelease`, APK/AAB assembly, and the cross-module JVM test suite
passed; the JVM suite was forced to rerun all 50 tasks. `core-equipment:test` was rerun after
removing a redundant nullable access in FOC calculation, and the compiler warning is gone. The
exact minified QA release passed 11/11 instrumentation tests on API 35 (4 KB pages) and 11/11 on
API 36 ARM64/16 KB at 1.3× font. The API-36 page size and font scale were asserted and restored.
Integrated artifact preflight passed. Current fresh QA APK SHA-256
`0b1a967194a5a8b4a4385cd9e1c97374edd5e8b94e90fa1a335094b54983ba29`; AAB SHA-256
`2da58084fb7208b2698e8cecb09375ee87fe3c58a81336642e3774eabe939b7f`; R8 mapping SHA-256
`5b46ed049593b4dbeeb49be24159fc06846b0bba557cb034c6cdff23bfa2409c` (34,833,386 bytes).
These artifacts use the QA/debug signing identity and are not distributable. The Android 15 and
16 KB evidence is local emulator evidence, not the required hosted workflow or physical-device/
range acceptance.

Bocal navigation correction / prior QA v708 (2026-10-13): Crocodyl now follows Bocal's curved,
two-level mobile dock interaction: a Recurve context control, Quick Add and Settings side actions,
and five direct-select destinations on a curved lower rail. The selected destination expands to a
labelled keycap; inactive destinations stay icon-forward while retaining full accessible names.
The duplicate Home-hero Settings action was removed. Debug and minified-release unit tests, release
lint, APK/AAB assembly, and integrated release preflight pass. All 11 debug and all 11 minified
release `ProductionSmokeTest` cases pass on the API-36 ARM64 16 KB emulator; the focused navigation
case also passes at 1.3× font scale and verifies Calendar↔Home, Quick Add open/dismiss, and Settings
navigation. Emulator restored to 840×1920 and font scale 1.0. Screenshots are in
`visuals/screenshots/home-bocal-arc-dock-v708-api36-16kb.png` and
`visuals/screenshots/home-bocal-arc-dock-large-font-1_3x-v708-api36-16kb.png`. QA APK SHA-256
`479f3035d58484aab0be76de81c65a1bf40cd250758d36c977bbc27f9a27715f`; AAB SHA-256
`714d3eaa3caca309f308330767799a953327a0737772268665942527edb9552e`. These use the committed
QA/debug signer, not the production signer; physical-device/range acceptance and hosted CI remain
open.

Release-manifest hardening (2026-10-13): artifact preflight now requires exactly one merged
`application` element and rejects explicit `android:debuggable="true"` or
`android:testOnly="true"`. Seven focused manifest regressions and all 51 release-tool tests pass;
immutable action references still verify across seven workflows. The actual v708 QA APK/AAB passed
the updated integrated preflight, including signer, manifest, package/version/target SDK, 16 KB
alignment, model and backup-resource checks. This is a verifier safeguard, not a new production
artifact or production-signing/device-acceptance result.

Crash-diagnostics hardening (2026-10-13): the production release workflow now fails if the minified
build does not produce a non-empty, version-matched R8 mapping. It uploads that mapping and a
SHA-256 digest as a 90-day Actions artifact, deliberately separate from public release assets.
Actions artifacts are downloadable by signed-in users with repository read access; do not treat
this artifact as confidential. The runbook/checklist require verifying and archiving it durably.
The 52-test release contract suite, immutable action-ref check and workflow YAML parse pass locally;
this workflow change has not yet run in hosted CI. The task-local JDK/SDK were found outside the
default shell PATH, enabling the local Gradle/JVM/Android replay recorded above.

Large-font visual QA follow-up (2026-10-13): the exact v696 Home screen is now captured at normal
scale in `visuals/screenshots/home-hyle-two-arc-v696-api36-16kb.png`. Review at 1.3× exposed Home
action-label wrapping; the current source fixes it with an icon-above-label action layout at larger
font scales. QA versionCode 697 debug and minified-release assembly passed. Android debug/release
unit tests and `lintRelease` passed; all 11 `ProductionSmokeTest` cases passed in both debug and
minified release on the API-36 ARM64 16 KB emulator at font scale 1.3. The corresponding 840×1920 capture is
`visuals/screenshots/home-large-font-1_3x-v697-api36-16kb.png`. The full test now scrolls Home in both
directions to reach the Atelier and return to the hero. Emulator font scale was restored to 1.0.
v697 APK/AAB passed `tools/release/verify_android_release.sh`: matching QA signer, target SDK 36,
manifest/package/version policy, APK ZIP alignment, AAB `PAGE_ALIGNMENT_16K`, 11 aligned native
libraries per artifact, pose-model hash and backup resources. APK SHA-256
`9a7986fdbb4b6e7a53b8a1651d667d9553cd37e46cd2a2ad0100ec2388d2032a`; AAB SHA-256
`3b77a708d596abf3d0f5498c14d181c4ab3f8127d919d277e50718b59d5622aa`. These are QA/debug-key
artifacts, not production-signed or physically accepted. Physical-device review remains open.
The debug and signed minified-release workflows now include Android 15 (API 35) smoke lanes in
addition to API 36 and API-36/16 KB; the 16 KB lanes explicitly assert 16,384-byte pages and 1.3×
font scale. Eighteen release-workflow contract tests, YAML parsing and immutable action-ref checks
pass locally. The local API-35 ARM64 AVD exits 132 during emulator startup, so no v707 API-35
runtime result is claimed; hosted CI has not run for this checkout.
Release version resolution now also enforces a checked-in high-water mark of 697: semver such as
0.6.1 (601) is rejected, and the next candidate must use a higher explicit code or a version whose
derived code is higher. Before production release, reconcile the ledger with the actual previous
production APK and advance it after each successful release.
Previous-production APK/update replay (2026-10-13): fetched the public `v0.5.1-reconciled`
release asset and matched its SHA-256 `84e21831c085905f2beedea704c3998b32e76c4ed84966c1e797ab006c2a440b`
to GitHub's release metadata. The APK is `xyz.mdhv.formanalyser`, versionCode 4, targetSdk 35, and
uses certificate `36f33a33f9415fbe4f31556a3dde9bc010d03c502bb3dfe6b2bfaf0cbafdaebc`, matching the
committed QA/debug key. The release workflow's signer guard rejects that certificate for a new
production release; the update below is compatibility/migration evidence under the same signer,
not proof of production-key continuity. The checked-in high-water mark is 697, safely above the
actual published code 4. On the disposable API-36 ARM64
16 KB AVD (`PAGE_SIZE=16384`), clean-installed and cold-launched v0.5.1, completed onboarding as
synthetic athlete `Test` with active rig `My bow`, then updated it in place to the exact v701
minified QA APK using the same certificate. Package Manager reported versionCode 701; the updated
app cold-launched with `Status: ok`, remained live after four seconds, and emitted no fatal/class-
loading errors in the checked log. After update, Home still showed `My bow` and Settings → Profile &
Identity still showed `Test`. This verifies the onboarding profile and active-rig data survived the
published-APK upgrade on this emulator. A later data-bearing replay seeded three synthetic shot
scores and a training session in the actual V4 schema, then updated in place to v702; Room reached
V9 and preserved one session plus all three scored shots. V4 has no standalone scorecard table, so
no legacy scorecard migration could be exercised. Real athlete database and physical-device update
acceptance remain open. Android displayed its 16 KB compatibility warning for the old v0.5.1 APK
(`ELF alignment check failed`) and ran it in compatible mode; v701's current artifact preflight
confirmed all packaged 64-bit native libraries are 16 KB aligned. Production signing remains open.

Navbar owner correction (2026-10-13): the owner clarified Crocodyl should follow Bocal's current
curved, two-level interaction, not the flat bar inferred from an older screenshot. The earlier v707
five-tab dock is historical. Current source is QA v708 (`0.6.4-bocal-arc-qa`): Recurve context,
Quick Add and Settings actions above five direct-select curved destinations. The selected destination
expands into a labelled keycap; inactive items remain icon-forward with full accessibility names.
The duplicate Home-hero Settings action was removed. Debug/release unit tests, `lintRelease`, APK/AAB
assembly and artifact preflight pass. Full 11/11 debug and minified-release instrumentation suites
pass on API-36 ARM64 16 KB; focused dock navigation/action smoke passes at 1.3× font. Evidence:
`visuals/screenshots/home-bocal-arc-dock-v708-api36-16kb.png` and
`visuals/screenshots/home-bocal-arc-dock-large-font-1_3x-v708-api36-16kb.png`. QA APK SHA256
`479f3035d58484aab0be76de81c65a1bf40cd250758d36c977bbc27f9a27715f`; AAB SHA256
`714d3eaa3caca309f308330767799a953327a0737772268665942527edb9552e`. Production signing, hosted
CI, API-35 runtime, physical-device/range acceptance, 30-real-session evidence and owner approval
remain open.

Prior arc-source QA release v705 (`0.6.4-curved-dock-qa`) passed artifact preflight: APK SHA-256
`392551f44fccc1a72b7ba9bb96766c588e270a27c26af10279abcbce5639239e`; AAB SHA-256
`73eabb62ff5e83d5af23f6fca32cd646c389abf53ddaeb2b665840efd80f5d7a`. The artifacts are QA-signed,
not distributable, and did not advance the ledger. Production signing/hosted CI and physical-device
review remain open. The older flat-dock screenshots are historical only.

Earlier two-rail implementation QA (2026-10-13): QA v695/v696 builds passed debug/release unit
tests, `lintRelease`, and upper-rail swipe instrumentation on API-36 16 KB AVDs. Their screenshots
are historical revisions rather than proof of the current source render; the current v700 source
has separately passed connected behavior tests above.

Previous signed minified artifact (2026-10-13): version `0.6.0-navbar-qa-20261013`, versionCode 696.
It is superseded by current-source v697 below.
The APK and AAB were signed with the committed QA/debug certificate, then passed
`tools/release/verify_android_release.sh`: signer agreement, targetSdk 36, merged manifest policy,
version metadata, APK ZIP alignment, App Bundle `PAGE_ALIGNMENT_16K`, 11 aligned 64-bit native
libraries in each artifact, pose-model hash, and backup resources. The complete minified-release
`ProductionSmokeTest` passed 11/11 when invoked directly on `emulator-5556` (API 36, `PAGE_SIZE` =
16384), including `upperArcDragPreviewsAndSelectsCalendar` and the primary-tab flow. The exact APK
then cold-launched (`Status: ok`), its process remained live, and the checked logcat had no fatal or
class-loading error. APK SHA-256
`88143b1fc001072cad1e26bf5788755cda638ea7a326138e4a0e510cdb0f724b`; AAB SHA-256
`645089dbd26284cbdb438b84d1faad62b977584252449dd95ef686317bff1a91`. This is a complete QA
artifact replay only: the key is not a production identity, and a passing local preflight cannot
substitute for hosted workflow execution, physical-device/range acceptance, or the separate
v0.5.1-to-v701 update test documented above.

Portable verification sweep (2026-10-13): root `./gradlew test` completed successfully (50 tasks,
all up-to-date). Seventy-seven Python contract tests passed across release workflow (16), version
resolution (7), merged manifest (5), ELF alignment (11), shared checkout (4), community directory
(4), Atelier/photo assets (9), arrow library (4), arrow-reference library (7), and web viewer (10).
The immutable GitHub Actions reference check also passed for all seven workflow files.

Historical Recurve QA replay after the first, lower-rail-only navigation correction (2026-10-13):
version `0.6.0-nav-qa-20261013`, versionCode 681. Android debug/release unit tests and `lintRelease` passed
(89 actionable tasks). The API-36 Google APIs ARM64 emulator reported 16384-byte pages. The focused
debug primary-tab navigation smoke passed 1/1; the complete current-source debug and minified-release
`ProductionSmokeTest` suites subsequently produced 10/10 reports with zero skips or failures. The
At that time, a signed QA release replay also exited successfully: instrumentation-only versionCode 689
passed all 10 tests on the API-36 16 KB AVD. QA APK/AAB v681 passed
`tools/release/verify_android_release.sh`: matching committed QA/debug signer
`36f33a33f9415fbe4f31556a3dde9bc010d03c502bb3dfe6b2bfaf0cbafdaebc`, targetSdk 36, manifest
allowlist, APK ZIP alignment, AAB `PAGE_ALIGNMENT_16K`, 11 aligned 64-bit ELF libraries, model hash
and backup resources. APK SHA-256
`3a44b861ba60676aea85882865a358fa6491ee768263167288c6eb8d597b7f7d`; AAB SHA-256
`652d89ed33d6b90b9e19ed3429f747f4f1e2455a2a2e3bf6203ef6fbf25ccf7e`. The 1080×1920 Home render
is checked in at `visuals/screenshots/home-bocal-arc-navigation-api36.png`. These are QA/debug-key
artifacts and emulator results, not production signing, physical-device acceptance, or owner
release approval. Production credentials/hosted CI, physical-device/range acceptance, previous
production APK update, 30 real sessions and owner approval remain open.

Previous v679 evidence (2026-10-13): the forced root/JVM run passed (50 tasks); the combined root/JVM,
Android debug/release unit-test and `lintRelease` replay passed (113 tasks). Connected API-36 debug
and minified-release suites passed 10/10 each, and the v678 QA APK upgraded to v679 in place. v679
APK/AAB preflight and cold launch also passed. Source contracts passed release 39/39, shared checkout
4/4, web viewer 10/10 plus ECDSA interoperability, community directory 4/4, Atelier/photo 9/9,
arrow library 4/4, arrow-reference 7/7, and immutable action refs across 7 workflows. A read-only
`git ls-remote` found no matching branch on origin, so hosted CI has not run for the newest commits.

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
| Recurve Android build | QA-only, not production releasable | Current-source QA v711 has passing debug and minified-release instrumentation (11/11 each on API 35) and minified-release instrumentation 11/11 on API 36 ARM64/16 KB at 1.3× font (page size 16,384), plus debug/release unit tests, release lint, APK/AAB assembly and artifact preflight. The camera-permission test now inspects all interactive windows while requiring denial UI from the active app window, closing an API-36 false positive. Dock screenshot is archived. Production signer/hosted CI, physical/range matrix, real-athlete data and 30-session evidence remain. |
| Data safety and recovery | Implemented and tested | Room recovery, checksum-manifested database backups restricted to the app-private recovery root, atomic exports, vault staging cleanup, manifest/checksum and privacy filtering are covered by tests and release checks. |
| Equipment Atelier | Implemented for current arrow-first visual scope | Component-separated arrow library, variants and mobile/desktop previews are committed. Owner/device visual review remains a release-quality gate. |
| Fencing | Scaffold only | Sabre/Foil/Epee metadata and manual referee console are present. Automatic two-fencer adjudication is roadmap work. |
| Local Multiplayer | UX walkthrough + beta transport boundary | The first-run/returning-user walkthrough is user-facing; the shared TCP/pairing core is published on Shared-Libraries-asoc `codex/evidence-schema-v1` at `da50c62`. Crocodyl locks that revision, and both CI and release workflows now fetch and verify the actual published checkout. Discovery, trust, reconnect, product event mapping and two-device acceptance remain. |
| Desktop file fallback | Offline coach report implemented; visual/device acceptance remains | `web-viewer/` opens `.crocbak` recovery archives and signed `.croc` sharing archives locally, verifies the declared SHA-256 payload checksum and manifest/table contract, and verifies the `.croc` P-256/ECDSA producer signature over the payload plus new consent-envelope fields. It now summarizes form sessions/shots, cumulative scoring trends, and latest non-baseline measurements against available baseline samples while keeping set-match scoring distinct and filtering retracted rows. Bounded escaped row previews remain available. Numeric SQLite booleans are handled as exported `0/1`. Viewer data, renderer HTML-escaping, and source-contract tests pass; visual browser review, representative real `.croc`/`.crocbak` files, accessibility and coach workflow acceptance remain. |
| Bocal interoperability | Beta compile integration | Bocal and Crocodyl consume the shared session boundary. No claim of production cross-device acceptance. |
| Production distribution | Not yet releasable | Workflow actions are now immutably pinned and guarded. Production keystore/CI identity, final versioning, clean install/update acceptance, physical-device matrix and range acceptance remain. |

## Current commits

- Crocodyl: `d92047f` — verify the actual report renderer escapes untrusted archive labels and never displays set-match points as a cumulative score percentage.
- Crocodyl: `ef0ba57` — add offline desktop coach analysis for form, scoring and baseline data; handle SQLite-exported boolean values; run report regression tests in CI/release and document the remaining visual/coach acceptance gate.

- Crocodyl: `e15c557` — replace the blank database-open frame with an accessible branded loading surface; exercise the curved dock swipe and correct the Home-header test assumption.
- Crocodyl: `33841c9` — index QA v711 artifact digests, API-35 debug/minified instrumentation evidence and current-source screenshot.
- Crocodyl: `7cfacf1` — make camera-permission instrumentation robust to API-36 permission-controller windows; API-35 and API-36/16 KB minified release suites pass 11/11.
- Crocodyl: `7ac4ec9` — index completed API-36/16 KB v711 smoke evidence and the permission-window test fix.
- Crocodyl: `4ee9757` — refresh v711's APK digest after the final integrated preflight pass.
- Crocodyl: `55b8490` — record current-source QA v710 hashes, lint/unit/preflight evidence, and open connected-smoke gate.
- Crocodyl: `b537196` — index the current web-source arc parity implementation and its v710 validation boundary.
- Crocodyl: `5ff0d53` — match Bocal's current web-source arc with curved contextual text, angled controls, and rail scrub preview/release navigation; record validation boundary.
- Crocodyl: `3781a86` — correct QA v709 artifact hashes after restoring the explicit versioned release outputs.
- Crocodyl: `d81ac3f` — validate granted and denied camera UI without mutating permission state; record 11/11 API-35 release runs for each state.
- Crocodyl: `84ebb2e` — record API-35 release-test evidence and add explicit camera-permission state handling to the training smoke.
- Crocodyl: `994ed7a` — clear the FOC validator compiler warning and record exact-source v709 API-35/16 KB QA evidence.
- Crocodyl: `596bc1b` — correct R8 artifact accessibility claims for the public repository.
- Crocodyl: `2169d26` — require and preserve the versioned R8 mapping and digest for crash diagnostics.
- Crocodyl: `a1d61ae` — align the mobile navigation interaction with current Bocal source; add v708 screenshots and runtime coverage.
- Crocodyl: `a4fd94e` — reject debuggable and test-only Android release artifacts.

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

Navigation correction history (2026-10-13): Crocodyl iterated from a curved lower-only rail, to a
single-arc v698 design, then a two-arc Hyle experiment in v700/v701. Owner review of the actual
Bocal mobile reference clarified that the desired pattern is the floating five-destination dock,
not either arc variation. Current source now matches that dock; new 11/11 connected debug smoke
results are recorded in the current navbar correction entry above. The v700/v701 screenshots and
artifact checks are historical for those exact builds, not current-source visual evidence.

Debug instrumentation follow-up (2026-10-13): the complete current-source API-36 16 KB debug
`ProductionSmokeTest` generated a JUnit report with 10 tests, 0 failures, 0 errors and 0 skips
(QA versionCode 686). The first replay exposed a test-helper issue: exact accessible-text matching
was given a truncated camera-denial message, and API-36 accessibility click could leave the system
permission prompt foregrounded. The helper now waits for Crocodyl's full safe-state label and uses
Back only if the permission-controller dialog remains; the focused camera/session test then passed
1/1, followed by the full 10/10 report. Gradle remained attached after UTP finished the suite and
did not return a normal task exit; the process was interrupted after verifying the complete XML
report. Do not count this as a clean Gradle task exit, and retain the teardown issue for CI/local
tooling follow-up. Android debug/release unit tests and `lintRelease` subsequently exited
successfully (89 actionable tasks).

Minified-release instrumentation follow-up (2026-10-13): with release signing explicitly configured
to the committed QA/debug keystore, the v689 instrumentation-only build passed all 10
`ProductionSmokeTest` cases on `crocodyl-api36-16k-qa` (API 36 ARM64, `getconf PAGE_SIZE` = 16384).
The JUnit XML records 10 tests, 0 failures, 0 errors and 0 skips; the Gradle task exited successfully
(`BUILD SUCCESSFUL`, 108 actionable tasks). The artifact was used only for connected QA and was not
run through standalone release preflight or prepared for distribution; the production signer remains
absent.

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
