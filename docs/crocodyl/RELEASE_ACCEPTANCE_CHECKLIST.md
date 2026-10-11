# Crocodyl 0.6 release acceptance checklist

This checklist is the final gate for the current Recurve scope. It is intentionally separate from
the pure-JVM and packaging checks, which are already automated. Record device model, Android
version, app version, APK/AAB digest, tester, date, and evidence for every run.
Use DEVICE_ACCEPTANCE_RUN_TEMPLATE.md to capture each physical-device run consistently.
For the CI/signing sequence, use [PRODUCTION_RELEASE_RUNBOOK.md](PRODUCTION_RELEASE_RUNBOOK.md).

## 1. Required device matrix

- [ ] ARM64 phone, Android 12–15, rear camera, mid-range performance.
- [ ] ARM64 phone, Android 12–15, flagship performance and a different camera vendor.
- [ ] Camera-less or large-screen Android device (the app must still support scoring, logging,
      progress, body, coach, and export without capture).
- [ ] At least one clean install and one update over the previous production APK.
- [ ] If a supported x86 emulator is used, repeat the non-camera flows and record the emulator image.

### 1.1 Current local evidence (not release sign-off)

- Current-tree QA release replay (2026-10-11): `0.6.0-home-immersive-qa` / versionCode 664.
  Minified APK/AAB passed `tools/release/verify_android_release.sh`, including APK v2/AAB
  signatures, matching QA/debug certificate, package/version, manifest security, ZIP alignment,
  BlazePose hash, backup resources, and staging-file checks. APK SHA-256
  `2ee1b5d1bc5c91dacef5194b33ca1acb0996074014ee018c86e05c9bb0dfef74`; AAB SHA-256
  `bd18106eb9f182712aeddc0b3dd1f80d14f8433bf9ac89268ac5abd74ff9f938`. Root JVM and Android
  debug/release unit tasks passed, release lint passed, and connected debug plus minified-release
  instrumentation passed 9/9 each on API-35 ARM64 `emulator-5554`; the tab smoke asserts Home's
  editorial headline, active-rig label and Train/Score actions. Authored-asset/photo contracts
  passed 9/9. Release artifact verification now additionally audits the merged manifest's complete
  permission allowlist and exported-component surface, with five passing focused regression tests.
  This is QA/debug-signed evidence, not a production signing or physical-device/range release sign-off.
  It does not close the required phone/device matrix, 30-session volume or owner approval.

- Historical current-tree replay: `0.6.0-cinematic-home-qa` / versionCode 662. Superseded by the
  versionCode 664 artifact above; retain its test/output record below as historical evidence only.

- Run record: `sdk_gphone64_arm64` / API 35 / ARM64 emulator, app `0.6.0-test` (versionCode 600),
  2026-09-16, tester: Codex. QA APK SHA-256:
  `4736b0cc799790c26aafcbf481789b807c28c87d4ead5c09f4f1fc287036be44`; QA AAB SHA-256:
  `9cc27ba8b2409a2727e63ba5d1c89c15355aaa616ecbe80531be3e4ff5337324`.
- API-35 ARM64 emulator: the minified QA APK installed and launched; onboarding completed; Home,
  Train, Progress, Body, Calendar, and Settings rendered; force-stop/relaunch retained the profile.
- The bundled BlazePose model initialized in the minified APK. With no subject in frame, the capture
  screen reported “No archer detected” safely; Start recording → Stop & analyze returned with zero
  shots and no app fatal exception.
- A shareable-only export saved through SAF as `crocodyl-export.crocbak`, opened as a valid archive,
  and contained no mood, life-event, or cycle table entries. This evidence does not cover the
  required physical-device matrix, real subject/range behavior, or 30-session volume.
- API-35 ARM64 emulator permission recovery (2026-10-10): camera denied twice, Capture displayed
  the blocked state and App Settings action; after granting camera in Android Settings and returning,
  the preview resumed and recording became enabled. This manual emulator check is not physical-device
  permission or range acceptance.
- Final release-candidate run: API-35 ARM64 emulator, 2026-09-20, tester: Codex. The rebuilt
  debug APK (`a25c5ec863be8cbe731c508b94e2c9986c104b3574b849682b37e53729124461`) installed
  successfully; Home → Equipment Atelier → horizontal selector → Fletching rendered the complete
  scaled taxonomy with no fatal, uncaught-JavaScript or WebGL error in the isolated log. The
  minified QA release candidate is `0.6.0-rc.2` (versionCode 602): APK
  `3aefac4f4138de33313c10cc8b0dd9efe02370b619c5eb74e0c5b23525a66e85`; AAB
  `2b43deecf044d7ec2f8b4c430c4390a59fb72d643b4602a65c94e4adc7d1eeb7`. APK signature,
  ZIP alignment and AAB verification passed. These artifacts use a throwaway QA keystore, not the
  production release identity, and this remains evidence rather than physical-device/range sign-off.
- On-device instrumentation replay: API-35 ARM64 emulator, 2026-09-20, tester: Codex. The
  launcher and offline Equipment Atelier WebView contract passed 2/2 tests. The contract verified
  the bundled `https://crocodyl.local/atelier/` origin, JavaScript availability, disabled file and
  content access, and blocked network loads. This is emulator evidence, not the required physical
  device matrix.
- Minified release launch replay: API-35 ARM64 emulator, 2026-09-20, tester: Codex. The QA-signed
  release APK returned `Status: ok` from `am start -W`, retained a live process after three seconds,
  and emitted no fatal app-process or class-loading error. The signed release workflow repeats this
  startup smoke on a fresh API-35 emulator.
- Fencing/ecosystem scaffold audit: QA build `0.6.0-fencing-scaffold` (versionCode 603), 2026-09-20,
  tester: Codex. Pure-JVM tests, Android unit tests, connected instrumentation and release checks
  passed; connected instrumentation was 3/3. APK SHA-256:
  `64cacf12109913f07f9b7db86d134badbfb6d0bdf800b44fb469e9d0af242d8`; AAB SHA-256:
  `8fc21f816cc9ea38ea6b53c7bd2765e4609b6500aff9cfb0ef7fe3d6ae81a1fd`. This validates only the
  non-live scaffold and does not count as physical-device, camera, referee-vision, directory, or
  production-signing acceptance.

Full current-tree verification: 2026-09-21, tester: Codex. Root JVM tests, Android debug/release
unit tests and release lint completed successfully. Connected instrumentation was rerun as
verification build 0.6.0-verification (versionCode 604) and passed 3/3 on API-35 ARM64
emulator-5554. The separate release artifact audit remains recorded for the QA-signed scaffold
build; this verification build is not a production-signed release.

Fresh QA artifact preflight: 2026-09-22, tester: Codex. Version `0.6.0-qa-verification`,
versionCode `610`, built with the isolated JDK 21/Android SDK. The reusable release preflight passed
APK/AAB signing, version metadata, manifest security, ZIP alignment, model hash, backup resources
and staging-file checks. APK SHA-256:
`66fcda10ab31a730db2fd6d9ceac7cc61d06e96c7cca09ad068b93518813e9eb`; AAB SHA-256:
`b9fa07f5b40e9c787bc1cfb7f716b9b357a68c6ab5a976aa337af73420f04987`. QA signing is not production
signing and this remains artifact evidence, not physical-device acceptance.

Fresh QA artifact preflight: 2026-09-27, tester: Codex. Version `0.6.0-qa-preflight`, versionCode
`630`, built with the isolated JDK 21/Android SDK and disposable QA/debug keystore. The reusable
preflight passed APK/AAB certificate matching, package identity, version metadata, manifest
security, ZIP alignment, bundled BlazePose hash, backup resources, and staging-file checks. This
is QA artifact evidence only and is not production signing or release approval.

Repository-side contract replay: 2026-09-22, tester: Codex. Community Directory validation passed
4/4 tests; Atelier asset contracts passed 8/8; original arrow-library contracts passed 4/4; and
arrow-reference contracts passed 7/7. ADB connected-device verification was attempted with the
isolated platform-tools package, but this sandbox rejects the local ADB daemon socket; no connected
device or emulator result is claimed from this run.

Unrestricted Android verification replay: 2026-09-24, tester: Codex. With the isolated Temurin JDK
21, Gradle 8.14.3, Android API 35 and build-tools 35.0.0 toolchain, `:app-android:testDebugUnitTest`,
`:app-android:testReleaseUnitTest`, and `:app-android:lintRelease` completed successfully (83
actionable tasks; 21 executed, 62 up-to-date). This verifies source/unit/lint health only. No
connected instrumentation, production signing, physical-device, or range acceptance is implied.
The same gate was rerun after replacing deprecated app-source APIs and completed successfully in
55 seconds; the remaining deprecation notice is from Gradle tooling rather than Crocodyl source.
The subsequent repository replay also passed root JVM tests, viewer and community-directory
contracts, immutable workflow-action verification, and `git diff --check`.

Fresh current-tree QA package: 2026-09-24, tester: Codex. Minified `bundleRelease` and
`assembleRelease` completed with version `0.6.0-qa-current`, versionCode `620`, using the committed
QA/debug signing identity. The reusable Android release preflight passed APK v2 verification, AAB
verification, version metadata, manifest security, ZIP alignment, bundled BlazePose hash, backup
resources, and staging-file checks. APK SHA-256:
`35a2332aaaf4ff16664a5aa477059baf1b66677afdccfd241bf5d7593dc035bf`; AAB SHA-256:
`41db10c25edb0ecfffc6b4a2c16e11ad3c8276a05efed88c82000e843b2b545e`. This is QA artifact
evidence only and is not production signing or device acceptance.

Current-tree connected replay: 2026-09-24, tester: Codex. `connectedDebugAndroidTest` passed 3/3 on
the API-35 ARM64 `alloy-api35-arm64` emulator. The subsequent `connectedReleaseAndroidTest` build
completed its minified test APK preparation, but the emulator went offline before test execution
(`AdbCommandRejectedException: device offline`); no minified-release connected pass is claimed.

Current-tree connected debug replay: 2026-09-27, tester: Codex. `:app-android:connectedDebugAndroidTest`
passed 3/3 on API-35 ARM64 `alloy-api35-arm64` (`emulator-5554`) using the current
`codex/production-hardening` tree. This strengthens emulator evidence only; it does not satisfy the
physical-device matrix, production-signing, release-connected, or range-acceptance gates.

Provisioned current-tree replay: 2026-10-02, tester: Codex. With isolated Temurin JDK 21, Gradle
8.14.3, API 35 and build-tools 35.0.0, root JVM tests, Android debug/release unit tests and
`lintRelease` passed. QA APK/AAB `0.6.0-qa-provisioned` / versionCode 640 passed the complete
artifact preflight. Connected debug instrumentation passed 3/3 and connected minified release
instrumentation passed 3/3 on `alloy-api35-arm64` / `emulator-5554`. The artifact uses the
committed QA/debug key; this does not satisfy production-signing or physical-device acceptance.

Current-tree Android replay: 2026-10-09, tester: Codex. After removing stale generated duplicate
dex archives with Gradle `clean`, connected debug instrumentation passed 3/3 and QA-signed minified
release instrumentation passed 3/3 on API-35 ARM64 `emulator-5554`. QA APK/AAB
`0.6.0-qa-20261009` (versionCode 641) passed `tools/release/verify_android_release.sh`, including
signer agreement and model/security/package checks. The release was signed with the committed
debug/QA key, not a production key. Physical-device matrix, clean install/update, camera/range use,
and production signing remain unchecked.

Room upgrade follow-up: Room 2.7.2 tree passed root tests, Android debug/release unit tests (21/21
each), `lintRelease`, and release preflight for QA APK/AAB `0.6.0-room-272-qa` (versionCode 642).
After the previous emulator disconnected, a temporary API-35 ARM64 emulator was provisioned.
Current Room 2.7.2 connected debug and QA-signed minified-release smoke each passed 3/3 on
`emulator-5554`. Production signer and physical-device acceptance remain separate.

Retained artifact preflight replay: 2026-10-10. Rechecked the existing QA versionCode 642 APK/AAB;
the verifier passed. APK SHA-256 `14cbc022254193d39e927155cc44b41b394e054c14619c0827e15609ed7d0f02`,
AAB SHA-256 `c1ad9976b457d449ef3531be314b040245a238b7489036d04e1837fde798375e`. These retain the
QA/debug signing identity and do not count as a production-signed build or new device run.

Capture lifecycle follow-up: 2026-10-10. The late CameraX provider/disposal race fix passed debug
and release Android unit-test suites (25/25 each), release lint, and a fresh minified QA APK/AAB
preflight (`0.6.0-camera-lifecycle-qa`, versionCode 643). APK SHA-256
`33362f35c58d330e5fa503ff1b0501478d910d697f674f9284b0068d65d85cc8`; AAB SHA-256
`1ee7e696dc1080b54382a3fc8c4c1ff538e4c88c28e15a1da222b481253a9024`. This does not satisfy
connected instrumentation for the changed code, production signing, or physical-device acceptance.

MediaPipe frame ownership follow-up: 2026-10-10. `PoseRecorder.process` now closes each synchronous
`MPImage` after detection; MediaPipe's bitmap container recycles its owned bitmap on close. If MPImage
construction fails, the still-unowned bitmap is recycled directly. Debug/release unit tests and
`lintRelease` pass offline with Gradle dependency verification enabled. Fresh minified QA APK/AAB
`0.6.0-mediapipe-image-qa` / versionCode 644 passed release preflight: APK SHA-256
`2e71ad2eecb6f19d67bcdf1286e1c23af308da693b2494ccadf6044da49f2bef`; AAB SHA-256
`bc7ada1bf9ec3507e22da5b38756fcd479e7f1f523a9e134cc820b2efb8ded8f`. Minified release
debug instrumentation passed 4/4 on API-35 ARM64 `emulator-5554` and minified-release instrumentation
passed 4/4 on API-35 ARM64 `emulator-5556`, including MPImage bitmap-release behavior. These are
QA-key artifacts and emulator evidence only; production signing and physical-device/range acceptance
remain open.

Scoring persistence follow-up: 2026-10-10. Five Room-backed repository tests cover active-card
reopen/resume with a retained X, invalid low rings on numeric and observer inputs for the vertical
triple, finished-card immutability, and retract/restore behavior with history/PB preservation.
The permanent-delete path is also confirmed to apply only to an untouched empty card. Android
debug/release unit tests pass 30/30 each and
`lintRelease` passes. Fresh QA APK/AAB `0.6.0-scoring-repository-qa` / versionCode 645 passed release
preflight and connected debug + minified-release instrumentation each passed 4/4 on API-35 ARM64
`emulator-5554`. APK SHA-256 `97a9786b57ae58c6dc691d22e54859204a2db444aee52299b1dba8150faf1025`; AAB
SHA-256 `a67a25de4eb4eaec24346d542e73cfae13abe10b7814667acdfddf3dded1a415`. QA signing only.

Capture-window concurrency follow-up: 2026-10-10. Four synchronized-buffer tests cover frames
already in flight at Start, late inference after Stop, starting a fresh generation, and frames
outside a recording. Android debug/release unit tests pass 34/34 each, `lintRelease` passes, and
connected debug plus QA-signed minified-release instrumentation pass 6/6 each on API-35 ARM64
`emulator-5554`; the instrumentation also traverses all five primary tabs and checks visible screen
content, opens the Score landing screen and WA Recurve round chooser, then exercises empty Quick Score
numeric 10 entry, total/arrow-count update, undo back to empty, Activity-recreation resume, and
cleanup of only its own untouched card (or skips if an active card pre-exists).
The capture screen now directs permanently denied camera permission to App Settings and rechecks it
on resume; the automated suite does not simulate the permission-dialog/settings round trip, so
verify that behavior manually on the required device matrix. Unit tests cover the policy that
distinguishes first request, retryable denial, permanent denial and granted state.
Capture also waits for session persistence before enabling recording; the unit gate covers missing
session, camera hardware, permission, and pose model. Session creation clears any previous capture
target, persists session and pre-check-in/soreness rows atomically, presents a setup recovery route on
required-write failure, and shows optional-context failures as warnings. A database-trigger rollback
tests verify no partial rows remain, including when the final session insert fails after check-in and
soreness have already been written inside the transaction.
Repeated start requests are ignored while session creation or capture is active; setup navigates only
when a start request is accepted, with the gate covered by four policy cases.
Current-tree QA APK/AAB `0.6.0-session-idempotency-qa` / versionCode 651 passed artifact preflight.
APK SHA-256 `9938ff7038a0a1c21043423eb29e2dcae7f2feebeafdfa1e7bed96987c3fce73`; AAB SHA-256
`f0b4a05ce8e042b203f34610c7fd691f667c86f2f73a00a54fdfe28bb503edb8`. Android debug/release unit
tests pass 44/44 each, release lint passes, and connected debug/QA-signed minified-release
instrumentation pass 6/6 each. This is not production signing or physical-device/range acceptance.

Photo refresh QA build (2026-10-10): `0.6.0-photo-assets-qa` / versionCode 652 passed
`tools/release/verify_android_release.sh`. APK SHA-256
`47a3f6f1ca320ecc7e9fd550e03e2ac12c98bc129b3edb98ea090949f2bf7649`; AAB SHA-256
`e23975aa3032c3817441986919a7da05088cd8436889af1a6c95d39cd3abfad4`. Debug/release unit tests
pass 44/44 each, release lint passes, Atelier asset contracts pass 9/9, and release workflow/version
contracts pass 17/17. Connected debug and QA-signed minified-release instrumentation each pass 7/7
on API-35 ARM64 `emulator-5554`, including Android decoding checks for the four bundled sport photos.
The QA APK also installs, completes onboarding and opens Home; visual inspection confirms
Recurve/Fencing photo cards render in the feed. QA signer only; physical device/range acceptance
remains.

Current visual-refresh QA build (2026-10-11): `0.6.0-saf-orphan-startup-qa` / versionCode 661.
The minified APK/AAB passed `tools/release/verify_android_release.sh` with QA/debug certificate
SHA-256 `36f33a33f9415fbe4f31556a3dde9bc010d03c502bb3dfe6b2bfaf0cbafdaebc`. APK SHA-256
`22ed20a0ccdde1eb409f4114b356114057d9e3aad6638186d34de3a1e3ae2ed4`; AAB SHA-256
`2de813e95705b5cee35f8070a55cf3179467606aec0d5e4c80ec723fea7369ac`. Connected debug and
QA/debug-signed minified-release instrumentation pass 9/9 each, including startup cleanup of an
orphan staging file. The QA key is not the production signer; physical-device/range acceptance
remains open. SAF archives are generated in private cache before opening the selected document;
copy failures trigger a document-delete attempt. A force-stop during provider copy still requires
real-provider acceptance.

Paired-session walkthrough coverage: 2026-09-24, tester: Codex. Added a Robolectric test for the
DataStore flag that controls first-run versus returning-user walkthrough state. Debug and release
Android unit-test suites plus release lint passed with this test included.

Fencing next-target validation: 2026-09-21, tester: Codex. The manual referee console state core
(weapon metadata, Prêt/Allez/Halte, score, undo and reset) passed pure-JVM tests. Debug connected
smoke passed 3/3 with verification versionCode 607; minified release connected smoke passed 3/3
with QA-only version `0.6.0-fencing-manual` (versionCode 606). This validates the manual console
slice only; it is not camera adjudication, physical-device, or production-signing acceptance. The
Android unit suite also covers lifecycle-scoped ViewModel retention, weapon switching and bounded
fencer labels.

Lifecycle-safe Fencing replay: 2026-09-21, tester: Codex. Debug connected instrumentation passed
3/3 on API-35 ARM64 with versionCode 608; minified release connected instrumentation passed 3/3
with QA-only signing as `0.6.0-fencing-vm` (versionCode 609). This is emulator/QA evidence only.

## 2. P0 functional flows

Run on each applicable device. A failure is release-blocking if it crashes, loses persisted data,
leaks private data, or leaves the user unable to recover without clearing app storage.

- [ ] Install, launch, onboarding, restart, and update; profile and active rig persist.
- [ ] Home and every bottom tab open; system back does not silently discard a recording or filled form.
- [ ] Rig editing, handedness selection/override, draw-length and poundage validation behave correctly.
- [ ] Camera permission grant, denial, “don’t ask again”, rotation, background/foreground, interruption,
      and camera-unavailable paths are safe and explain the next action.
- [ ] Start and stop a form session; post-session duration/RPE/feel/arrows sheet persists its values.
- [ ] Quick Score, WA round, custom practice, numeric entry, target plot, set points, PB, grouping,
      observer input, resume-after-interruption, correction, and retraction all persist correctly.
- [ ] Body atlas hit-testing, pain/soreness, injuries, resolved injury, physio plan/session, history,
      and injury-linked readiness behave correctly.
- [ ] Import a document through SAF and in-app camera; preview it; verify encrypted vault storage;
      cancel and retry imports; verify temporary camera files are removed.
- [ ] Export a signed `.croc`, inspect the consent preview, share it, verify PRIVATE data is absent,
      and open it in the offline viewer with local producer-signature verification.
- [ ] Offline rule-coach works without a key; BYOK cloud paths are opt-in; invalid keys/models fail
      visibly without corrupting existing settings or installed models.
- [ ] Data wipe, database recovery notice, explicit reset-after-backup, and post-reset disclosure work.

## 3. Resilience and privacy checks

- [ ] Kill/restart during capture, scoring, export, vault import, and model import; no committed data
      is lost and no `.partial` file is exposed as installed or exported content.
- [ ] Exercise low storage, denied camera, missing camera, offline network, rotated display, and
      process death; the app remains usable for non-camera features.
- [ ] Confirm cloud requests contain only consent-allowed fields; PRIVATE records never leave the app.
- [ ] Confirm app backup/restore does not silently reintroduce an incompatible Room database.
- [ ] Confirm the release APK has cleartext disabled, backups disabled, and FileProvider is not exported.

## 4. Acceptance volume

- [ ] Complete at least 30 real sessions across the three device classes.
- [ ] The final 10 sessions complete without a crash, data loss, or unrecoverable state.
- [ ] Any defect is logged with device, Android version, reproduction steps, logs, and affected data.
- [ ] All P0 defects are closed or explicitly accepted by the owner before release.

## 5. Release sign-off

- [x] Gradle dependency verification is enabled and every locked artifact has a SHA-256 entry;
      the offline JVM/Android unit/lint replay passed with verification active (2026-10-10).
- [x] JVM and Android CI trigger on Gradle verification and all current sport-module changes;
      release contract tests guard both path-filter coverage and checksum metadata (2026-10-10).
- [x] Release publication runs with isolated `contents: write`; checkout/build/sign/test run
      read-only and checkout credentials are not persisted. Workflow contracts verify the boundary
      and the artifact handoff (2026-10-10).
- [x] `./gradlew test` passes (local verification recorded in the handoff/build notes).
- [x] `:app-android:testDebugUnitTest` and `:app-android:testReleaseUnitTest` pass with `-PwithAndroid` (2026-09-20; includes analyzer persistence/baseline, manifest privacy, durable recovery-notice, encrypted-vault, and model-inventory tests).
- [x] `:app-android:lintRelease` completed with zero app-source lint findings (2026-10-09); 21
      GradleDependency advisory warnings remain for dependency-version review.
- [x] All repository workflows use immutable third-party action commit SHAs; CI and release run
      `tools/release/verify_workflow_actions.py` to prevent mutable refs from returning.
- [x] Current-tree QA APK/AAB (`0.6.0-saf-export-staging-qa`, versionCode 659) passed release
      artifact preflight; connected debug and QA/debug-signed minified-release instrumentation
      each passed 8/8 on API-35 ARM64 `emulator-5554` (2026-10-11). Includes first-run onboarding,
      Body region selection, and current primary-tab smoke. SAF staging unit tests cover archive
      writer failure, staged success and destination-copy cleanup. APK/AAB digests and test evidence
      are recorded above. QA signing is not production signing or physical-device acceptance.
- [ ] Release APK/AAB are built by CI with the production keystore and requested version metadata.
- [ ] CI verifies APK signature, AAB signature, manifest security, bundled BlazePose hash, backup
      resources, APK ZIP alignment, absence of staging files, and the expected production
      certificate fingerprint (`CROCODYL_RELEASE_CERT_SHA256`) in both APK and AAB.
- [ ] APK is installed over the previous production build and launches on the acceptance matrix.
- [ ] Owner approves the release notes and selected distribution channel.

End Scan, Live Observer, complete `.croc` import/merge exchange, Coach workspace, full web report
rendering, equipment catalog, and the broader training/recovery roadmap are not acceptance criteria for 0.6; see
[`CROCODYL_STATUS.md`](../../CROCODYL_STATUS.md).
