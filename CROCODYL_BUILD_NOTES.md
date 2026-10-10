# Crocodyl build notes (Phases 1–3)

Consolidated notes for the multi-phase build. (Deviation from the per-phase `PHASEn_NOTES.md`
convention: this is one autonomous build across Phases 1–3, so notes are consolidated here.)

## Product expansion addendum — 2026-10-09

Photo provenance refresh (2026-10-10): replaced the generated Fencing Home-card image and added
photographer-created Recurve/Fencing card photos. The bundled-photo contract validates source page,
photographer, license, dimensions, byte count and SHA-256 against `sports-manifest.json`; the old
generated range image and its manifest were removed. Atelier asset contracts pass 9/9. Android
debug/release unit suites pass 44/44 each; `lintRelease` passes. Asset contracts pass 9/9 and
release-workflow/version contracts pass 17/17. Both connected suites pass 7/7 on API-35 ARM64:
debug and QA-signed minified release, including Android decoding/dimension checks for all four
bundled sport photos. VersionCode 652 QA APK/AAB passed release preflight (APK SHA-256
`47a3f6f1ca320ecc7e9fd550e03e2ac12c98bc129b3edb98ea090949f2bf7649`; AAB SHA-256
`e23975aa3032c3817441986919a7da05088cd8436889af1a6c95d39cd3abfad4`). The APK was installed and
manually launched through first-run onboarding to Home; the Recurve/Fencing photo cards rendered in
the feed. These remain QA-key/emulator evidence, not production signing or physical-device/range
acceptance.

Full regression replay after the Android photo-decoding contract (2026-10-10): `./gradlew test
-PwithAndroid --offline --no-daemon` passed (104 actionable tasks), covering the root JVM modules
and Android debug/release unit suites. Release workflow/version contracts passed 17/17, immutable
workflow action references verified across 7 workflow files, and the refreshed APK/AAB release
preflight passed. Connected debug and minified-release instrumentation each passed 7/7 on API-35
ARM64 `emulator-5554`.

Capture-window concurrency hardening (2026-10-10): CameraX frame analysis and UI recording
controls previously touched the same mutable pose list across threads. Added a synchronized
`PoseFrameBuffer` with a per-recording generation token so pre-start frames and post-stop inference
completions cannot contaminate or mutate a capture snapshot. Four boundary tests now cover those
cases, restart, and calls outside recording. Android debug/release unit tests pass 34/34 each,
release lint passes, and connected debug plus QA-signed minified-release instrumentation pass 6/6
each on API-35 ARM64 `emulator-5554`. The connected suite walks the onboarded main shell through
Home, Train, Progress, Body and Calendar, then opens Score and the built-in round chooser to verify
the WA Recurve 70 m / 72-arrow option. It dismisses the chooser without creating or changing a
scorecard. A separate case creates an empty Quick Score card, records a 10 through the visible keypad,
checks the total/arrow count, undoes back to the empty state, closes/relaunches MainActivity, verifies
resume from Room, and discards only its own untouched card; it skips if an active card pre-exists.
Visible screen content is checked using Android's built-in accessibility tree. QA APK/AAB
`0.6.0-pose-capture-window-qa` / versionCode 646
passed artifact preflight. APK SHA-256 `a4c32676767e6fa2bac61cdabaa47ed8d9c18d3001c22c9aa33d47b0ecd4870d`;
AAB SHA-256 `89dac947a6f9234e7055e4ee60cd1702d64e74955b8b2e3f771d4b79144e3abb`. The verifier now
hashes with either GNU `sha256sum` or macOS `shasum`; the artifacts remain QA-key-signed, not
production-signed.

Camera permission recovery (2026-10-10): CaptureScreen now offers an App Settings deep link after a
permanent camera-permission denial instead of repeating a request that Android will not show, and
rechecks permission on Activity resume after the athlete returns from Settings. Debug/release unit
suites include four tests for initial request, retryable denial, permanent denial and granted state;
`lintRelease`, and connected debug plus QA-signed minified-release instrumentation pass on API-35
ARM64. The emulator suite does not automate the permission prompt/settings round trip; manual matrix
acceptance remains required.

Capture startup ordering (2026-10-10): Train navigates to Capture immediately after asynchronously
requesting session creation. Capture now keeps the record button disabled with a “Preparing
session…” label until the ViewModel confirms the session has been persisted, so a fast tap cannot
start a recorder without a session ID and silently discard the resulting frame window. The capture
readiness predicate is unit-tested along with the camera/model/permission requirements.

Session startup recovery (2026-10-10): starting a new session immediately closes the prior capture
gate, reports required-session creation failures with a route back to setup, and treats optional
context/refresh failures as warnings without making a persisted session unusable. Session and
pre-check-in/soreness rows are now inserted in one Room transaction; failure-injection tests abort
both during pre-check-in work and at the final session insert, proving rollback leaves no partial
check-in, soreness or session rows. The UI distinguishes preparing, ready, warning and failed states.

Duplicate session-start protection (2026-10-10): SessionViewModel now accepts a start request only
when neither another start nor a capture is in flight. The setup route navigates only when the request
was accepted, preventing repeated taps during slow persistence from creating duplicate sessions.
The acceptance policy has a four-state unit test.

Current source QA package (2026-10-10): `0.6.0-session-idempotency-qa`, versionCode 651, includes
camera-permission recovery, the persisted-session capture gate, transactional/recoverable session
startup and duplicate-start protection. Fresh minified QA APK/AAB passed
`tools/release/verify_android_release.sh`; APK SHA-256
`9938ff7038a0a1c21043423eb29e2dcae7f2feebeafdfa1e7bed96987c3fce73`, AAB SHA-256
`f0b4a05ce8e042b203f34610c7fd691f667c86f2f73a00a54fdfe28bb503edb8`. Android debug/release unit
tests pass 44/44 each, `lintRelease` passes, and connected debug plus QA-signed minified-release
instrumentation pass 6/6 each on API-35 ARM64. The QA key is not the production signer.

Scoring repository coverage (2026-10-10): `ScoringRepository` retains its public Context-based
construction and now has an internal Room-database constructor for isolated repository integration
tests. Five tests exercise close/reopen/resume of an active scorecard with an X, reject impossible
triple-face rings on numeric and observer paths, enforce finished-card immutability, retract/restore
historical arrows without losing PB/trend accounting, and ensure only empty untouched cards are
permanently discarded. Android debug and release unit suites pass 30/30 each and `lintRelease` passes.
Because the constructor seam changed app source, versionCode
644 did not include it; the versionCode 645 QA refresh is recorded below.

VersionCode 645 refresh (2026-10-10): after the scoring repository tests landed in `5c99a86`, built
fresh minified QA APK/AAB `0.6.0-scoring-repository-qa`. The release preflight passed; APK SHA-256
`97a9786b57ae58c6dc691d22e54859204a2db444aee52299b1dba8150faf1025`; AAB SHA-256
`a67a25de4eb4eaec24346d542e73cfae13abe10b7814667acdfddf3dded1a415`. Debug and minified-release
connected instrumentation each passed 4/4 on API-35 ARM64 (`emulator-5554`). Android debug/release
unit tests passed 30/30 and release lint passed. The bundle remains QA-key signed, not production-
signed.

Production-hardening continuation (2026-10-10): `PoseRecorder` now closes each synchronous
MediaPipe `MPImage` after detection, allowing its bitmap container to recycle the owned frame;
bitmap construction failure also cleans up the unowned bitmap. Android debug/release unit tests
passed 25/25 each and release lint passed. A fresh minified QA APK/AAB `0.6.0-mediapipe-image-qa`
(versionCode 644) passed artifact preflight (APK SHA-256
`2e71ad2eecb6f19d67bcdf1286e1c23af308da693b2494ccadf6044da49f2bef`; AAB SHA-256
`bc7ada1bf9ec3507e22da5b38756fcd479e7f1f523a9e134cc820b2efb8ded8f`). After adding a connected
ownership assertion that `MPImage.close()` recycles its bitmap, API-35 ARM64 debug instrumentation
passed 4/4 on `emulator-5554` and minified-release instrumentation passed 4/4 on `emulator-5556`.
Both artifacts use the committed QA/debug identity, not the production signer.

Release workflow input hardening (2026-10-10): extracted version resolution into
`tools/release/resolve_version.sh`; only bounded shell-safe version labels are accepted, version-code
arithmetic is range-checked, and workflow outputs are passed into commands through environment
variables. Plain semver derives a versionCode only for minor/patch values below 100, preventing
component collisions; prerelease/build suffixes require an explicit versionCode. An explicit
positive value overrides derivation and is range-checked. Runtime tests reject shell substitutions,
oversized values, component/suffix collisions and ignored invalid overrides. Release publication now
runs in a separate `contents: write` job; checkout/build/sign/test remain read-only and checkout
credentials are not persisted. The release workflow and resolver suites pass 17/17 (10 + 7); YAML
parsing, shell syntax and immutable action-pin checks pass. Version validation is in `01ce9b3`; the
write-permission split and version-code collision fix are recorded in their respective commits.

Dependency integrity follow-up (2026-10-10): added `gradle/verification-metadata.xml` with
SHA-256 pins for the current resolved build graph (556 components, 1,007 artifact hashes;
`verify-metadata=true`, signature verification disabled). An initial enforcement replay caught 23
missing hashes in the detached Android lint toolchain; those were captured and reviewed as part of
the same pinned graph. The final Gradle replay, with metadata writing disabled and Gradle offline,
passed root JVM tests, Android debug/release unit-test variants, and `lintRelease` (111 actionable
tasks; 4 executed, 107 up-to-date). This proves the exercised cached artifacts match the recorded
hashes and no unpinned download was needed. The pins record the locally resolved baseline; they do
not independently authenticate artifact publishers. Dependency changes must update and review the
lock metadata deliberately. Lint still reports its existing GradleDependency advisories; no
app-source findings were reported.

CI trigger audit (2026-10-10): the JVM and Android workflows did not watch the Muay Thai or
bouldering module directories, and JVM CI did not watch Gradle verification metadata. Added both
sport modules, `gradle/**`, and `gradle.properties` to push and pull-request filters in both
workflows. Extended the release contract suite to require checksum verification metadata with a
SHA-256 entry for every artifact and to protect those CI path filters. Release contracts now pass
8/8 and immutable action refs remain valid across all 7 workflow files.

Retained QA artifact preflight replay (2026-10-10): reran
`tools/release/verify_android_release.sh` against the existing Room 2.7.2 QA outputs
`0.6.0-room-272-qa` / versionCode 642. APK and AAB signatures matched the committed QA/debug
certificate (`36f33a33f9415fbe4f31556a3dde9bc010d03c502bb3dfe6b2bfaf0cbafdaebc`); the verifier
passed v2 APK signature, AAB signature, ZIP alignment, package/version, manifest policy, bundled
model digest, backup resources, and staging-file checks. APK SHA-256:
`14cbc022254193d39e927155cc44b41b394e054c14619c0827e15609ed7d0f02`; AAB SHA-256:
`c1ad9976b457d449ef3531be314b040245a238b7489036d04e1837fde798375e`. These are retained
QA artifacts, not freshly rebuilt production artifacts; their signer is not a production identity.

Capture lifecycle hardening (2026-10-10): `CaptureScreen` now serializes asynchronous CameraX provider
binding against composition disposal. A provider that completes after screen disposal is rejected;
an in-progress bind is released on close, and cleanup remains scoped to this screen's Preview and
ImageAnalysis use cases. Four deterministic tests cover late completion, one-time release, racing
close/bind, and release after a bind failure. Android debug and release unit-test variants each
passed 25/25; `lintRelease` passed with no app-source findings. A fresh minified QA build
`0.6.0-camera-lifecycle-qa` / versionCode 643 passed `tools/release/verify_android_release.sh`.
APK SHA-256: `33362f35c58d330e5fa503ff1b0501478d910d697f674f9284b0068d65d85cc8`; AAB SHA-256:
`1ee7e696dc1080b54382a3fc8c4c1ff538e4c88c28e15a1da222b481253a9024`. It uses the committed QA
debug key and is not production-signed. The temporary API-35 emulator image could not be completed,
so connected instrumentation for this particular fix was initially outstanding; the later
production-hardening continuation at the top of this addendum records the completed emulator replay.

Added Muay Thai and bouldering as distinct pure-JVM sport modules and local manual training
journals. The Home feed now opens cinematic, sport-specific experiences. Muay Thai captures focus,
round/work/rest structure and effort; bouldering preserves the athlete's grade system and label,
style, attempts and outcome. The Room V8 → V9 migration adds a shareable, retractable `sport_session`
table, and consent-filtered `.croc` export omits retracted rows. See
[`docs/crocodyl/MULTISPORT_JOURNAL.md`](docs/crocodyl/MULTISPORT_JOURNAL.md).

The host-independent `./gradlew test` replay passed with 50 actionable tasks, including the new
sport-module suites. Release (6/6), shared checkout (4/4), viewer (10/10), community directory
(4/4), and Atelier (19/19) contracts also passed; workflow action pins were verified across 7 files.
At the time of this first addendum, the new Android screens and Room migration had not yet been
compiled or exercised, and the V9 Room schema snapshot had not been generated. The follow-up replay
below closes the schema/unit/lint checks; connected instrumentation remains unverified.

Current replay after replacing the generated sport artwork with documented Unsplash photographs
(commit `0b5004f`): root Gradle tests again passed (50 actionable tasks); release, shared-checkout,
viewer, directory and Atelier contracts passed (6/6, 4/4, 10/10, 4/4 and 19/19). Photo checks
verified manifest dimensions, byte counts, SHA-256 values, photographer attribution and source/license
records.

Android follow-up replay (2026-10-09): after explicit approval, Android SDK licenses were accepted
and API 35, Build Tools 35.0.0 and platform-tools were installed in the temporary toolchain. Room
generated and checked in the V9 schema snapshot. Android debug and release unit suites passed 21/21
each, including the complete migration chain, sport-session table, privacy-registry schema check and
both model install/privacy tests. `lintRelease` completed with zero app-source lint issues and 20
GradleDependency update advisories. The first connected-test attempt eventually reached D8 and
exposed stale duplicate `.dex` entries in ignored generated output, including old files suffixed
` 2.dex`. After Gradle `clean`, app and instrumentation APK assembly succeeded. The first connected
retry could not resolve Android UTP's host-output artifact in offline mode; the online retry fetched
it and passed 3/3 on the API-35 ARM64 emulator (`emulator-5554`). This is connected debug evidence;
minified release was then signed only with the committed debug/QA keystore and also passed 3/3 on
the same emulator. The matching QA APK/AAB `0.6.0-qa-20261009` (versionCode 641) passed the release
preflight: APK v2 and AAB signatures agreed, version/package metadata matched, ZIP alignment and
manifest policy passed, the bundled model hash matched, and required backup resources and absence
of staging files were verified. This signer is not the production identity; production signing and
physical-device acceptance remain separate gates.

Post-clean consolidated replay (2026-10-09): root `test`, Android debug and release unit-test
variants, and `lintRelease` all passed together (111 actionable Gradle tasks; 52 executed). The
Android suites each passed 21/21. Lint reports 20 GradleDependency advisories and zero app-source
findings.

Persistence dependency follow-up (2026-10-09): upgraded Room runtime/KTX/compiler/testing as one
set to 2.7.2. Room KSP passed and produced no schema diff; root tests, both Android unit variants
(21/21 each), and `lintRelease` passed. The lint report lists 21 dependency-update advisories and
zero app-source findings. A minified QA APK/AAB, `0.6.0-room-272-qa` / versionCode 642, passed the
release preflight. After the prior emulator disconnected, an API-35 ARM64 emulator was installed
into the temporary SDK and started; current Room 2.7.2 connected debug and QA-signed minified-release
smoke suites both passed 3/3. Room 2.8.5 was tested but its processor graph resolves Kotlin 2.2.0
artifacts and KSP failed with `AbstractMethodError` under Kotlin/KSP 2.1.0; reverted to the compatible
2.7.2 stable release rather than widening the compiler/processor migration.

## Current addendum — 2026-10-08

The current `codex/production-hardening` checkout was clean at `30f4cb9`. This host currently has
no Java runtime, Android SDK (`ANDROID_HOME`), or ADB, and its production signing keystore and
certificate-fingerprint environment variables are unset. No build or test was run during this
recheck; the latest successful provisioned Android replay remains the QA-only 2026-10-02 run in
[`docs/crocodyl/PRODUCTION_STATUS.md`](docs/crocodyl/PRODUCTION_STATUS.md). Repository CI secrets
were not inspected from this local environment. Production signing, physical-device/range
acceptance, update verification, and owner distribution approval remain release gates.

The host-independent repository replay passed release workflow contracts (6/6), immutable action
pin validation (7 workflow files), shared-checkout contracts (4/4), desktop viewer contracts
(10/10), Community Directory contracts (4/4), and Atelier asset suites (19/19). The tests did not
modify generated assets. Android/JVM build tests were not rerun because this host has no Java
runtime or Android SDK.

The earlier addendum below records the previous implementation snapshot.

## Historical addendum — 2026-10-02

The CI path filter includes `tools/release/**` and `tools/community_directory/**`, so changes to
release preflight or directory provenance validation cannot silently bypass the ordinary repository
signal. The release workflow runs the directory unit tests and validates the empty-or-curated
dataset before packaging.

The notes below are the historical implementation log and contain superseded “pending” entries from
before the Android layers landed. The authoritative current snapshot is [`CROCODYL_STATUS.md`](CROCODYL_STATUS.md).

The latest recorded checkout is `codex/production-hardening` at `30f4cb9`; older commit references
and pending items below are historical implementation notes, not the current release ledger.
The Android artifacts remain QA-only and unreleased. Since the historical entries below:

- the manual Recurve scoring wedge is implemented (numeric, plotted and observer input, WA/custom
  rounds, resume, grouping, PBs and scorecard retraction);
- DeepSeek is present as a BYOK provider alongside OpenAI, Anthropic and Google;
- Room recovery is non-destructive: failed opens are preserved and surfaced for explicit reset;
- Room schema export is enabled and release builds now require explicit release signing;
- Android CI watches core-module and Gradle changes as well as `app-android` changes.
- The current body layer now supports in-app document-photo capture alongside SAF import; camera
  staging is scoped through FileProvider and deleted after atomic vault encryption.
- The bundled BlazePose asset is content-pinned: clean builds verify the SHA-256 of the downloaded
  model and publish it atomically, and Android CI runs both the root JVM tests and full release lint.
- The current verification pass reports 178 primary JVM/Android test cases with zero failures or
  errors; release lint has zero errors, warnings, or informational notices, and the QA-key APK and
  AAB pass signature verification.
- App networking is explicitly cleartext-disabled in the manifest, and CI now triggers when the Gradle settings
  file changes so module inclusion cannot drift without an Android build.
- The final manifest-only hardening was rebuilt and inspected in the current debug APK and in a
  corrected minified QA-key APK/AAB. The R8 configuration retains shrinking, obfuscation, and
  resource shrinking while disabling aggressive optimization, bringing the release APK build down
  to 1m27s and the AAB build to 12s. CI must still rebuild with the real production key before
  distribution.
- The release workflow now inspects the packaged APK manifest and AAB contents, failing on a
  cleartext-enabled or backup-enabled manifest, a missing or tampered BlazePose asset/backup
  resource, or leaked staging files.
- An API-35 ARM64 emulator smoke pass installed the minified QA APK, completed onboarding, rendered
  Progress/Train/Body/Calendar/Settings, verified force-stop/relaunch profile persistence, initialized
  the bundled pose model, and toggled Start recording → Stop & analyze against an empty camera frame
  without an app fatal exception. Full physical-device, camera/range, and 30-session acceptance remains.
- The first minified capture run exposed MediaPipe protobuf reflection breakage from R8 field
  obfuscation (`typeUrl_`); retaining `com.google.protobuf.**` in the release rules fixed it and was
  reverified on the same emulator path.
- SAF export now advertises Crocodyl's vendor MIME type so DocumentsUI preserves the requested
  `.crocbak` extension; the final emulator export opened successfully and excluded private tables.
- The shared segmented control now uses responsive equal-width, single-line labels; the final
  emulator screenshot confirms all five Body views are legible instead of wrapping `Physio` into a
  vertical column.

The remaining production gate is evidence, not another blind feature pass: Android device/range QA,
camera/permission/interruption behavior, release artifact verification with the real production key,
and the blueprint’s later phases
(validated form calibration, End Scan, signed `.croc`, Coach workspace and web viewer).

## Reliability addendum — 2026-09-21

- Share-for-sharing exports now write to a hidden same-directory `.partial` file and publish with a
  same-directory rename only after the archive has completed. If archive generation or publication
  fails, both staging and final paths are cleaned up, preventing an interrupted export from leaving a
  misleading `.crocbak` cache artifact. SAF exports remain caller-owned URI writes and are unchanged.
  `ShareExportPublisherTest` now covers both successful publication and failure cleanup; Android
  debug and release unit suites passed after this change (`21d52bd`).

- Post-hardening verification on 2026-09-21 passed `./gradlew test`,
  `:app-android:testDebugUnitTest`, `:app-android:testReleaseUnitTest`, and
  `:app-android:lintRelease` in one offline run after `21d52bd`.
- Release preflight now requires `CROCODYL_RELEASE_CERT_SHA256` and compares the normalized
  certificate fingerprint independently in the signed APK and AAB. A valid signature with the wrong
  keystore is therefore rejected; the production workflow must configure this secret before it can
  publish. QA-only local checks may supply the QA certificate fingerprint explicitly.
- The release workflow now opts into `connectedReleaseAndroidTest` after signing, so the on-device
  smoke contract runs against the minified release variant as well as the ordinary debug variant.
- Local QA validation of that path passed 3/3 on the API-35 ARM64 emulator on 2026-09-21 using the
  committed debug keystore only as a QA signing substitute; this is minified-runtime evidence, not
  production-key evidence.

## Hardening addendum — 2026-09-18

- Closed the current-scope scoring context gap: sight mark, venue, conditions and training intent are
  now editable from the scorecard and retained in the existing Room fields. Narrow-screen scoring
  mode labels scroll safely.
- Scorecard linking now enforces same-athlete ownership and rejects retracted training sessions.
  End Scan candidate proposals validate scores/plots/confidence, cap to the remaining arrows, and
  replace only unresolved proposals; confirmed/rejected provenance is retained. ViewModel actions
  preserve coroutine cancellation instead of converting it into a user-facing failure.
- Document vault views now decrypt to a unique private staging file and publish atomically only after
  full authenticated completion. Viewer path components are constrained, preventing partial plaintext
  from being treated as a valid view. On-device model removal is canonical-path scoped to the private
  model directory, with a regression test for outside-path deletion.
- Latest verification: Android debug/release unit-test variants and `lintRelease` pass with zero
  failures/errors and no lint findings; a rebuilt debug APK passes `apksigner verify`. Including the
  root JVM run and the added model-path regression, the current root-plus-two-Android-variants
  matrix totals 180 test cases, all passing. Release APK/AAB generation still fails closed without
  the real production signing inputs; the `verifyReleaseSigning` guard is working as intended. For
  final local validation on 2026-09-18, a throwaway QA-only keystore was supplied outside the
  repository; the minified APK/AAB then passed R8, resource shrinking, APK v2 verification, and AAB
  `jarsigner` verification; the APK also passed ZIP alignment verification. This proves the release
  path, not the identity of the production key. Release CI now enforces the same alignment check.
- An equipment continuity audit confirmed that the existing arrow tuning model/editor and FOC/GPP
  analysis remain present. The validated `feat/immersive-visuals` handoff is now integrated as a
  separate generic arrow-reference studio with five modular GLB families, eight selectors, local-only
  WebGL2 controls, semantic part IDs, provenance metadata, deterministic generator/contract tests and
  committed Chromium evidence. It is not an owner-approved arrow, compatibility claim or full catalog.
- The rebuilt debug APK was installed on the API-35 ARM64 emulator and exercised through Home → the
  Atelier card. The non-exported host reached a resumed local WebView; after binding a 1×1 fallback
  texture for untextured meshes, the isolated log had no fatal exception or WebGL texture warning.
  Final QA artifact SHA-256 values are recorded in `CROCODYL_STATUS.md` and the acceptance checklist.
- The Atelier GLB exporter now canonicalizes float serialization, and both generators were rerun
  successfully with no resulting worktree drift. `atelier-quality.yml` and `release.yml` compare
  regenerated models against the checked-in outputs, closing the stale-generated-asset gap found in
  the final production audit.
- The remaining generic point families are now present as separate semantic components: hardened
  416 stainless glue-in, adjustable glue-in bullet, and removable 10/20/50-grain module studies.
  The Lancaster ledger and browser contract preserve their explicit product-family boundaries; the
  next visual dependency is the owner-arrow reference, followed by owner approval.
- Fletching taxonomy is now present as a separate fifth GLB and eighth studio selector. It keeps
  material/type, mounting, vane count/clocking, wrap presence and nock-relative orientation as
  independent generic studies, with Lancaster source references and contract coverage. The next
  asset dependency is the owner-selected arrow reference, not further speculative equipment.
- The corrected fletching build was installed on the API-35 ARM64 emulator on 2026-09-20. Home →
  Equipment Atelier → horizontal selector → Fletching rendered the five-family taxonomy at mobile
  scale; the isolated log showed no fatal, uncaught-JavaScript or WebGL error. Debug APK SHA-256:
  `a25c5ec863be8cbe731c508b94e2c9986c104b3574b849682b37e53729124461`.

## Release validation addendum — 2026-09-20

- The QA release candidate `v0.6.0-rc.2` (versionCode 602) was rebuilt with
  `assembleRelease`, `bundleRelease`, `testReleaseUnitTest` and `lintRelease`. The build passed
  R8/resource shrinking, release lint, and packaging with the throwaway QA keystore.
- The resulting APK SHA-256 is
  `3aefac4f4138de33313c10cc8b0dd9efe02370b619c5eb74e0c5b23525a66e85`; the AAB SHA-256 is
  `2b43deecf044d7ec2f8b4c430c4390a59fb72d643b4602a65c94e4adc7d1eeb7`. `apksigner verify`,
  `zipalign -c -P 16 -v 4`, and `jarsigner -verify` each exited successfully. The signatures are
  QA-only evidence, not a production signing identity.
- The minified QA APK was installed on the API-35 ARM64 emulator and cold-started through
  `xyz.mdhv.formanalyser/.app.MainActivity`; `am start -W` returned `Status: ok`, a live process
  remained after three seconds, and the isolated log contained no fatal app-process or class-loading
  error. The signed release workflow now repeats this direct launch smoke on a fresh API-35 emulator;
  the richer launcher/offline-Atelier contract remains in the debug instrumentation workflow.
- The remaining release gates are external: production keystore/CI identity and final versioning,
  owner approval, physical-device/accessibility/thermal/battery checks, real camera/range tests,
  and the required acceptance matrix. The current generic arrow-reference studio is not an exact
  owner-arrow catalog.
- The legacy arrow-taxonomy baker was converted from a branch-specific write-capable workflow into
  a read-only validation workflow on pull requests, `main`, and `release/**`. It now rejects
  generated GLB drift before browser QA and cannot auto-commit or push generated assets.
- The release verifier now also fails if the packaged APK's `versionCode` or `versionName` differs
  from the release branch/input metadata; the local `0.6.0-rc.2` APK passed this check.
- A current-tree API-35 ARM64 emulator replay installed the debug APK (`0.6.0-fletching`,
  versionCode 601), exercised Home → Train → Progress → Body → Calendar, opened Home → Equipment
  Atelier, selected Fletching after horizontal navigation, and observed every fletching component
  label in the accessibility tree. After clearing logcat, the launch and Atelier path produced no
  fatal exception, uncaught JavaScript error, WebGL error or Chromium error.
- The Android module now has two real-device instrumentation contracts: launcher startup and the
  offline Atelier WebView origin/security settings. Both passed 2/2 on the API-35 emulator. The
  Android workflow runs these contracts through a fresh API-35 emulator; the signed-release workflow
  installs the minified artifact and runs the separate direct launch smoke before publishing.

## Fencing and ecosystem scaffold validation addendum — 2026-09-20

- The Sabre/Foil/Epee module, referee-call domain model, Android weapon-card shell, and community
  directory shell are included without changing the production Recurve capture path. The scaffold
  is intentionally non-live: referee vision, web scraping, user submissions, advanced-search
  adoption, Baseline adapters, and Nooz news ingestion remain future work.
- Pure-JVM tests, Android debug unit tests, connected instrumentation, release unit tests and
  release lint passed for the scaffold build. Connected smoke passed 3/3 on the API-35 ARM64
  emulator; the QA audit used `0.6.0-fencing-scaffold` (versionCode 603). APK SHA-256:
  `64cacf12109913f07f9b7db86d134badbfb6d0bdf800b44fb469e9d0af242d8`.
- Matching AAB SHA-256:
  `8fc21f816cc9ea38ea6b53c7bd2765e4609b6500aff9cfb0ef7fe3d6ae81a1fd`. APK v2 signing, zip
  alignment, AAB verification, and direct minified cold-start smoke passed. These are QA-only
  artifacts and do not replace the public `0.6.0-rc.2` release candidate or production-device gates.

The release artifact checks are now maintained in the version-controlled
tools/release/verify_android_release.sh preflight and called by release.yml, covering expected
version metadata, APK/AAB signatures, alignment, cleartext/backup/FileProvider policy, model hash,
backup-rule resources and staging-file rejection. This keeps local replay and CI on the same
release-boundary contract.
- The Android debug and release unit suites passed after adding
  app-android/src/test/.../ArcheryAnalyzerTest.kt. The tests cover feature JSON persistence through
  the Android JSON runtime and the app bridge from good-shot samples to a ready baseline and
  deviation result; the Robolectric runner prevents false failures from the unmocked SDK JSON stub.
- The same debug/release suites now include ManifestSecurityTest, which locks the committed
  cleartext/backup/FileProvider policy and both backup-rule XML sources. Packaged manifest and
  resource checks remain enforced independently by the release preflight.
- AppDatabaseMigrationTest now also verifies that a reset notice and preserved recovery path remain
  durable across reads until the athlete explicitly dismisses the notice; debug and release suites
  pass with this recovery contract.
- VaultTest now covers Tink-backed encrypt/decrypt round trips, plaintext exclusion, SHA-256/size
  metadata, associated-data rejection and cleanup of failed decrypted views in both Android
  variants.
- ModelInstallTest now also verifies that model inventory ignores stale staging and undersized files
  and that path-like display names are sanitized into the private model directory; both Android
  variants pass.
- The ordinary Android and JVM test workflows now declare contents-read-only GitHub permissions.
  The release workflow retains the narrower contents-write permission required only to publish
  signed release artifacts.
- Full current-tree verification on 2026-09-21 completed the root JVM suite, Android debug/release
  unit suites and release lint. The connected smoke was then rerun with verification version
  0.6.0-verification (versionCode 604) after clearing the emulator's stale downgrade state and
  passed 3/3 tests on API-35 ARM64 emulator-5554.
- The release workflow now publishes a SHA-256 manifest alongside each APK/AAB, so a downloaded
  artifact can be checked against the exact bytes produced by the signed build.

## Historical recon findings

These notes capture the initial blind-build investigation. They are retained for provenance; the
2026-09-15 addendum above supersedes any later “pending” or “deferred” claim that it contradicts.

- **Repo/branch:** app lives on `claude/form-analyser-baseline-split-e9s6lr` (PR #1, draft). Building
  continues on that branch (not per-phase branches) so progress is continuous without merge gates.
  `main` is not yet the default branch; merging/default-branch changes are left to Madhav.
- **Modules (before):** `engine` (pure JVM), `archery-module` (pure JVM), `app-android` (gated behind
  `-PwithAndroid`; needs Android SDK). Kotlin 2.1.0, AGP 8.7.3, Gradle 8.14.3, JDK 21.
- **Room (app):** `AppDatabase` v1, entities `athletes`/`sessions`/`shots` with **String (UUID) PKs**
  (not the integer PKs the briefs' SQL assumes) and `session.drawWeightLbs`/`distanceMeters` columns.
- **Pose space:** MediaPipe **normalized image landmarks** ([0,1], y-down) — so the handedness mirror
  is `x' = 1 − x` (§4 table row 1).
- **DI:** none present. Nav: `navigation-compose` present (single activity, NavHost).
- **Verification limit:** no Android SDK in the build container → pure-JVM modules are tested locally
  (`./gradlew test`); the Android app + Room migrations + UI compile only in CI (`android` workflow).

## Done this pass — pure-JVM engine cores (all unit-tested, CI-green)

These are the headless, correctness-critical foundations of Phases 1–3.

**Phase 1 core**
- `core-model`: `Handedness {RH,LH}`, `BowType`. (Canonical app-wide handedness; the archery pipeline
  keeps its internal `pose.Handedness {RIGHT,LEFT}` — deviation logged below.)
- `archery-module`: `HandednessNormalizer` (single mirror point, `x'=1−x` + L/R swap table) +
  `EffectiveHandedness` resolver. Tests: swap-table completeness, involution, non-identity,
  resolver precedence, and the **feature-invariance exit test** (LH twin ⇒ identical features + phases).
- `core-equipment`: `PoundageEstimator` (ILF convention, DECISION-21 constants, measured>estimated>marked
  precedence, compound never estimates) + tests.

**Phase 2 core**
- `core-wellness`: `LoadModel` (shot/srpe load, poundage fallback chain), `Acwr` (EWMA + warm-up gate +
  chronic guard + zones), `StreakEngine` (grace/break/hiatus/provisional-today), `DurationModel`
  (idle-trim gap cap), `Readiness` (v2 cascade + reason accumulation, life-event context not content),
  `CycleEstimator` (median/MAD, gated), `PrivacyRegistry` (SHAREABLE/MEDICAL/PRIVATE). Full test suite.
- `core-body`: 52-region `RegionIds` contract + handedness-aware `SorenessChipResolver` + tests.

**Phase 3 core**
- `core-body` already carries the 52-region contract (Phase 3's appendix list). Atlas *geometry*
  (SVG paths, hit-testing) uses `android.graphics` → lands in the app module (pending, CI-verified).

## Deviations from the briefs (R1/R2)

1. **Handedness enum:** briefs specify `Handedness {RH,LH}`; the incumbent archery pose code already
   has `pose.Handedness {RIGHT,LEFT}`. To honor the "don't modify existing archery src" guardrail, the
   canonical `{RH,LH}` lives in `core-model`; the pose enum stays as an internal segmenter detail.
   Post-normalization the segmenter always runs right-handed, so the two never conflict.
2. **PoundageEstimator location:** brief puts it in `app-android/domain` for Phase 1; placed in the new
   pure `core-equipment` module instead so it's unit-testable headlessly and matches the Phase 4 target.
3. **PrivacyRegistry table names:** registered with the spec's canonical (singular) logical names; the
   app-layer Robolectric reflection test will reconcile these with actual Room `tableName`s (some are
   historically plural: `athletes`/`sessions`/`shots`).
4. **Integer vs String PKs:** the briefs' migration SQL assumes integer PKs; the actual schema uses
   String UUID PKs. The Android migration (pending) will adapt (nearest-faithful) — rig/session FKs
   become String to match.

## Phase 1 app — increment 1 (data foundation, CI-verified)

Landed (additive, incumbent manual-DI kept to de-risk blind compilation):
- Room **V1→V2 migration**: rig table + athlete columns (handedness, drawLengthMm, avatarSeed, club,
  pubkey) + session columns (rigId, handednessOverride), with a per-athlete default-rig backfill
  (tuning seeded from the latest session's draw weight) and sessions repointed. `RigEntity` + `RigDao`
  (transactional single-active) + repository rig methods.
- `TuningV0` (kotlinx.serialization) + `Tuning.effectivePoundage()` bridging to `core-equipment`.
- `AppPrefs` (DataStore) for the Appendix-A keys.
- **Handedness normalization wired** into the analysis path (`SessionViewModel` normalizes the captured
  pose by the athlete's handedness before segment/extract).

Deviations logged: **Hilt deferred** (kept manual DI — a blind Hilt refactor is high-risk without a
local SDK; will adopt in a verified pass); **exportSchema stays false** for now (enable with the ksp
schema dir when verifiable); Robolectric migration/VM tests deferred to the same verified pass.

## Phase 1 app — increment 2 (UI, CI-verified)

Landed: Hyle atoms (`HyleSegmented`/`HyleStepper`/`HyleListRow`/`HyleSectionHeader`/`HyleEmptyState`)
+ deterministic `HyleAvatar`; **onboarding** flow (role → name/avatar → handedness → draw length →
rig v0 with live OTF estimate → permissions primer → commit); **nav restructure** (onboarding gate
via DataStore, bottom-bar shell Home/Train/Progress/Body/Calendar with ComingSoon stubs, settings
graph); **Home v1** (header/avatar, active-rig row, Start, recent sessions → reopen review);
**TrainSetup** (rig chip replaces the draw-weight field + per-session handedness override);
**Settings** (profile, rigs list + RigEdit, capture, appearance, data-wipe, about). Session start now
resolves poundage from the active rig; handedness override flows into normalization.

Deviations (logged): flat `ui` package kept (no feature-package split — less blind-move risk); no
Hilt (manual DI); Home **StabilitySpark** card omitted for now (needs per-session baseline compute);
settings sub-screens use system back (no custom back affordance yet); Robolectric onboarding/VM tests
deferred to a verified pass.

## Phases 2–3 app layers (one tranche, CI-verified)

**Cores (locally green):** Readiness **v3** (additive `activeInjuries` param + severity clauses + tests);
`PrivacyRegistry` gains the Phase 3 tables (`document = MEDICAL`); **BodyAtlas** — the 52-region
geometry as rounded-rect regions in the 1000×2000 viewport, author-left/mirror-right, with the full
integrity suite (52 exactly once, exact mirrors, ≥60×60, exact pairwise no-overlap — stronger than
the brief's grid sample, centroid hit-tests). Hand-drawn SVG paths can replace rects later via the
override seam without touching IDs/consumers.

**Phase 2 app:** Room V2→V3 (checkin/soreness/rest_day/hiatus/mood_entry/life_event/cycle_entry/
medication_entry/event + session check-in/duration/arrows columns — table names match the
PrivacyRegistry contract); `WellnessDao`; `WellnessAssembler` (DB → load/ACWR/streak/readiness);
pre-check-in gate in TrainSetup (dials + soreness via chips **or mini-atlas**, skip recorded);
post-check-in sheet on capture stop (RPE CR10, feel, idle-trimmed auto duration + override, arrow
reconciliation); "+ Log" surface (check-in, rest, mood, life event → impact-3 hiatus offer, cycle
(gated), medication, event); **Calendar tab** (month grid + day marks + hiatus band + streak strip +
Load view with weekly bars/ACWR/warm-up state); Home readiness card (shape+luminance ●◐○, quiet
state) + streak line + "+ Log"; Settings: Wellness (chips toggle), Streak & Rest (planned-rest
pattern + hiatus), Cycle (enable + estimate + history), Medication (list).

**Phase 3 app:** Room V3→V4 (pain_log/injury/physio_plan/physio_exercise/physio_session/document);
`BodyAtlasCanvas` (one renderer: violet luminance ramp with exact anchor stops + numeral badges,
cyan 45° cross-hatch for physio, dashed outlines for injuries, selection, tap/long-press
hit-testing); **Body tab** (Today/History-8-weeks/Injuries/Physio views, pain dial 0–10 + quality
tags, region history, physio session ticking); injury CRUD (atlas multi-select, RESOLVED stamps
resolved date, pain auto-links to covering active injury); physio plan editor (targets, days,
exercise rows); **Tink streaming-AEAD vault** (AES256_GCM_HKDF_4KB, Keystore-wrapped, AD = row id,
25 MB guard, view-cache wiped on close/clear) + SAF import + image/PDF viewer; readiness v3 wired;
Body-tab injury badge; vault meter in Settings → Data.

**Deviations (R1/R2, logged):**
- Atlas geometry is schematic rounded-rects (v0), not hand-drawn SVG paths — integrity suite is the
  contract; override seam kept for hand-drawn art later.
- Streak week-strip is a text summary line, not 7 glyph dots (visual polish deferred).
- srpe lane is computed but the Load view shows shot-load bars only (secondary lane deferred).
- Physio session logging lives on the Body tab (plan row → Log), not in "+ Log" (fewer nav seams).
- At the initial blind pass, document import was SAF-only; the current branch adds in-app camera
  capture through a scoped FileProvider path and retains the ContentResolver MIME path for SAF.
- Robolectric tests (migrations, registry reflection, VM suites) still deferred to a verified pass —
  no local Android SDK; pure-JVM suites cover the math.

## Historical pending list (superseded)

Not yet built (require the Android SDK / only compile in CI):
- **Phase 1 app:** Hilt DI, Room migration (rig table + athlete columns + backfill), onboarding flow,
  nav restructure into feature packages, Home v1 cards, Settings skeleton, rig v0 UI, Hyle atoms +
  `HyleAvatar`, contextual camera permission, wiring the normalizer into the live capture path.
- **Phase 2 app:** check-in flows, calendar tab, life layer UI, schema V→V+1 for wellness tables,
  `PrivacyRegistry` reflection test.
- **Phase 3 app:** atlas geometry + hit-testing, body tab UI + encodings, Tink document vault,
  injuries/physio CRUD, readiness v3 (injuries).

## Open questions for Madhav

1. **DECISION-21** OTF constants (2.0 lbs/in draw, 1.0 lb/in riser) — shipped as named defaults in
   `PoundageEstimator`; confirm/adjust from coaching ground truth.
2. **PR strategy** — everything is stacking on the current branch/PR. Happy to keep it as one growing
   PR, or split per-phase once you're merging.
3. Android app layers will be built + verified via CI iteration (no local SDK); flagging that the pace
   there is CI-round-trip-bound, unlike these locally-verified cores.

## Hyle: approximation → real tokens

The Hyle Design System repo (dev.aarso hyle, `tokens/*.json` → `HyleTokens.kt`) is now the token
source. `ui/theme/Theme.kt` ports the generated values verbatim (warm ink #ECE8E4 at 92/42/18%
tiers, #121212-class surfaces per the halation rule, field.near #0A0809 window, radium #C7EF9E /
cold-cyan #35E0FF provenance, hairlines, calm 300ms cubic-bezier(0.4,0,0.2,1), token type scale).
Property names unchanged so all screens compile untouched. The body-map encoding hexes (violet
ramp anchors, #08FED5 physio hatch) stay as spec'd in the Crocodyl briefs — they are the body-map
law, not general theme tokens. Releases: `v0.4.0-hyle-approx` (before) vs `v0.4.1-hyle-real`
(after) for side-by-side comparison. Future step: consume `dev.aarso:hyle` as a real dependency
instead of ported constants (needs artifact publishing wiring).
