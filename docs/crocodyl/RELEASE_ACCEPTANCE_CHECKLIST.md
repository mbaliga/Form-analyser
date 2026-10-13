# Crocodyl 0.6 release acceptance checklist

This checklist is the final gate for the current Recurve scope. It is intentionally separate from
the pure-JVM and packaging checks, which are already automated. Record device model, Android
version, app version, APK/AAB digest, tester, date, and evidence for every run.
Use DEVICE_ACCEPTANCE_RUN_TEMPLATE.md to capture each physical-device run consistently.
For the CI/signing sequence, use [PRODUCTION_RELEASE_RUNBOOK.md](PRODUCTION_RELEASE_RUNBOOK.md).

### Incremental evidence after the current QA artifact (2026-10-13)

- [x] Previous-source QA release `0.6.0-navbar-qa-20261013` / versionCode 696 APK and AAB passed
      `tools/release/verify_android_release.sh`: targetSdk 36, merged-manifest policy, matching
      QA/debug signer, APK ZIP alignment, AAB `PAGE_ALIGNMENT_16K`, 11 aligned 64-bit ELF
      libraries, pose-model hash and backup resources. APK SHA-256
      `88143b1fc001072cad1e26bf5788755cda638ea7a326138e4a0e510cdb0f724b`; AAB SHA-256
      `645089dbd26284cbdb438b84d1faad62b977584252449dd95ef686317bff1a91`.
- [x] The exact v696 minified APK passed full `ProductionSmokeTest` 11/11 on the API-36 ARM64
      16 KB AVD (`getconf PAGE_SIZE` = 16384), including the then-current two-arc swipe regression and all
      primary destinations. It then cold-launched with `Status: ok`, remained live, and emitted
      no fatal/class-loading match in the checked logcat. Tests were invoked directly on
      `emulator-5556` because Gradle's connected-device discovery timed out querying another
      attached emulator.
Distribution caveat: v696 used the committed QA/debug identity and is not production-distributable.
This evidence does not replace hosted release-workflow success, the physical-device/range matrix,
an update over the actual previous production APK, 30-real-session acceptance, or owner approval.

- [x] Current-source v697 QA release (2026-10-13): debug and minified release assembly, Android
      debug/release unit tests and `lintRelease` passed. Debug and minified-release
      `ProductionSmokeTest` each passed 11/11 on API-36 ARM64 16 KB with 1.3× font scale. The Home
      action labels adapt to large font and the smoke scrolls the feed in both directions. The
      minified APK/AAB passed `tools/release/verify_android_release.sh`, including QA signer match,
      targetSdk 36, manifest/version, APK alignment, AAB `PAGE_ALIGNMENT_16K`, 11 aligned ELF
      libraries, model hash and backup resources. APK SHA-256
      `9a7986fdbb4b6e7a53b8a1651d667d9553cd37e46cd2a2ad0100ec2388d2032a`; AAB SHA-256
      `3b77a708d596abf3d0f5498c14d181c4ab3f8127d919d277e50718b59d5622aa`. Visuals are
      checked in at `visuals/screenshots/home-hyle-two-arc-v696-api36-16kb.png` and
      `visuals/screenshots/home-large-font-1_3x-v697-api36-16kb.png`. These artifacts use the QA
      key and are not production-distributable.
      These v696/v697 navbar captures and test results predate later navbar corrections; they are
      historical evidence, not acceptance of current source.
- [x] Historical QA candidate `0.6.0-bocal-navbar-qa` / versionCode 698 compiled and passed
      Android debug and release unit tests, `lintRelease`, and debug/instrumentation APK assembly.
      Full debug and minified-release `ProductionSmokeTest` each passed 11/11 on API-36 ARM64
      16 KB; the new `bocalStyleArcNavigationSelectsCalendar` test passed in both runs. Fresh
      840×1920 screenshot: `visuals/screenshots/home-bocal-single-arc-v698-api36-16kb.png`.
      The minified test build used only the committed QA/debug key. This verifies runtime behavior
      on an emulator, not production signing, artifact preflight or physical-device acceptance.
      Owner review later established that the one-rail interaction still diverged from Bocal; v698
      is superseded and its screenshot is not evidence for current-source parity.
- [x] Three CI-discovered navbar contract tests now guard Bocal's two-rail geometry, curve-anchored
      context actions, upper-rail preview/commit, and direct destination tabs. Corrected-source QA
      v700 passed `testDebugUnitTest` and all 11 connected `ProductionSmokeTest` cases; QA v701 passed
      all 11 in the minified release variant on the API-36 ARM64 16 KB emulator. Both release-test
      installables used only the committed QA/debug signer, and `lintRelease` passed. Fresh
      checked-in capture and physical-device acceptance remain open.

- [x] Portable verification sweep passed: root JVM build/test task (50 tasks, up-to-date), 77 Python
      contract tests across release, version, manifest, ELF, shared checkout, community directory,
      Atelier/photo, arrow, arrow-reference and web-viewer contracts, and immutable Actions refs in
      all seven workflow files.

- [x] Fresh forced root/JVM run passed (50 Gradle tasks executed); combined root/JVM,
      Android debug/release unit tests, and `lintRelease` passed (113 actionable Gradle tasks).
- [x] Connected `ProductionSmokeTest` passed 10/10 on API-35 ARM64 for both debug and minified
      release, including Home/settings navigation, first-run flows, scoring and training paths.
- [x] Version `0.6.0-current-tree-qa-20261013` / versionCode 679 APK/AAB passed integrated
      preflight: targetSdk 36, merged-manifest allowlist, matching QA/debug signer, APK ZIP
      alignment, AAB `PAGE_ALIGNMENT_16K`, 11 aligned 64-bit ELF libraries, model hash and backup
      resources. APK SHA-256
      `3f4e96e6e2c48757e1d52ebc5e8a8dd6a3f26d591d9a682b0056d29b5cf2bdf1`; AAB SHA-256
      `45350ba20d416b9e51058a5ae3b9ed93fb5e2f204b077e8b80db3a56a2b25de8`.
- [x] The v679 minified QA build clean-installed and cold-launched on API-35 ARM64; the process
      remained live after four seconds with no fatal/class-loading error in the checked logcat.
- [x] Earlier v679 API-36 Google APIs ARM64 16 KB run passed full debug and minified-release
      `ProductionSmokeTest` 10/10 each, including score persistence across recreation and system
      Back; guest `getconf PAGE_SIZE` returned 16384. For the latest v681 Bocal-style nav source,
      focused debug primary-tab navigation passed 1/1 and full minified-release instrumentation
      passed 10/10 on the same 16 KB image. Latest APK/AAB preflight passed. APK SHA-256
      `3a44b861ba60676aea85882865a358fa6491ee768263167288c6eb8d597b7f7d`; AAB SHA-256
      `652d89ed33d6b90b9e19ed3429f747f4f1e2455a2a2e3bf6203ef6fbf25ccf7e`. The v681 signer is
      still the QA/debug key, not a production identity. A post-v681 current-source debug replay
      (QA versionCode 686) also produced a complete API-36 16 KB JUnit report, 10/10 with zero
      failures/errors/skips; the Gradle wrapper hung during teardown and was interrupted after the
      report completed, so this does not claim a clean Gradle task exit. A separate signed,
      instrumentation-only QA release replay (versionCode 689) passed all 10 tests on API-36 16 KB
      and exited successfully; it used the QA/debug keystore and was not run through artifact
      preflight or prepared for distribution.
- [x] On a disposable API-36 16 KB AVD, QA versionCode 678 installed and upgraded in place to v679;
      Package Manager reported 679, cold launch returned `Status: ok`, process remained alive after
      four seconds, and no fatal/class-loading error appeared in the checked logcat. This does not
      prove migration from the actual previous production APK or physical-device acceptance.
- [x] Repository contracts passed: release 39/39, shared checkout 4/4, viewer 10/10 plus
      ECDSA interoperability, community directory 4/4, Atelier/photo 9/9, arrow library 4/4,
      arrow-reference 7/7, and immutable action refs across 7 workflows.
- [ ] Production signer and hosted release-workflow pass, physical-device/range matrix, update over
      the actual previous production APK, 30-real-session acceptance and owner approval remain open.
      The corrected-source v700 QA build uses the committed QA/debug key and is not distributable;
      no v700 release APK/AAB preflight was produced. The Bocal two-rail navbar has connected debug
      smoke evidence above, but a fresh checked-in capture is still pending. The 1.3× large-font
      capture remains v697 and predates the current source. Physical-device visual acceptance remains
      open. Verify and set `tools/release/highest_issued_version_code.txt`
      to at least the actual previous production APK's code before triggering a release. The earlier
      lower-rail screenshot is explicitly superseded.

## 1. Required device matrix

- [ ] ARM64 phone, Android 12–15, rear camera, mid-range performance.
- [ ] ARM64 phone, Android 12–15, flagship performance and a different camera vendor.
- [ ] Camera-less or large-screen Android device (the app must still support scoring, logging,
      progress, body, coach, and export without capture).
- [ ] At least one clean install and one update over the previous production APK.
- [ ] If a supported x86 emulator is used, repeat the non-camera flows and record the emulator image.

### Previous v678 evidence (2026-10-11; historical baseline)

- [x] Complete connected debug and minified-release smoke suites pass 10/10 on API-35 ARM64 and
      API-36 ARM64 16 KB emulators, including the selected-round session persistence, pre-check-in
      skip, permission fallbacks and scorecard-recreation tests.
- [x] Root/JVM and Android debug/release unit tests pass; `lintRelease` passes (113 Gradle tasks).
- [x] Version `0.6.0-session-smoke-qa` / versionCode 678 APK/AAB pass integrated preflight,
      including signer agreement, targetSdk 36, manifest allowlist, APK ZIP alignment, AAB
      `PAGE_ALIGNMENT_16K`, 11 64-bit ELF libraries, model hash and backup resources.
      APK SHA-256 `6ee9bd509669dc07252333e335664b7b641597dca2c33a5a02be9ae5ab2193a5`; AAB SHA-256
      `9eed6e44790886d9274259b603cc662014f4e63c8e9d9ee31880e98ceb441999`.
- [ ] Production signer, hosted CI, physical-device/range matrix, 30-session gate and owner approval
      remain outstanding; these emulator artifacts are debug-key QA only.

### 1.1 Current local evidence (not release sign-off)

- Current-tree Recurve QA candidate (2026-10-11): `0.6.0-current-tree-qa` / versionCode 669.
  Root/JVM tests, Android debug/release unit tests, and release lint passed (113 Gradle tasks).
  Connected `ProductionSmokeTest` passed 9/9 with zero failures/errors/skips on API-35 ARM64 and
  API-36 ARM64 16 KB-page emulators for both debug and minified release. The QA-signed APK/AAB
  passed integrated preflight: matching QA signer, targetSdk 36, merged-manifest contract, APK ZIP
  alignment, AAB `PAGE_ALIGNMENT_16K`, 11 aligned 64-bit ELF libraries per artifact, model hash,
  and backup-resource checks. APK SHA-256
  `9d3bf449fe3f15459808c6348ff86f57f7bfd8577248a73cbe5778029ff0bba0`; AAB SHA-256
  `2c58df44bd8ca015c21bd22079957b060925fe8fe3ae8ae3411ac4a7c15fd780`. QA signing is not
  production signing or physical-device/range acceptance; hosted CI, the 30-session gate, and
  owner approval remain open. This older v669 evidence is superseded by the v678 10/10 matrix and
  artifact digests in the incremental evidence section above.

- API-36 Home/Atelier QA candidate (2026-10-11): `0.6.0-home-smoke-qa` / versionCode 668.
  Android debug/release unit tests, `lintRelease`, and connected `ProductionSmokeTest` each pass
  9/9 on API-35 ARM64 and API-36 ARM64 16 KB-page emulator (`getconf PAGE_SIZE` = 16384). This
  includes the Home Atelier component-entry assertion. The minified QA APK/AAB pass integrated
  release preflight: targetSdk 36, manifest contract, matching QA signer, APK ZIP alignment, AAB
  `BundleConfig.pb` `PAGE_ALIGNMENT_16K`, 11 64-bit ELF libraries per artifact with aligned
  PT_LOAD segments, model hash, and backup resources. The API-36/16 KB release suite completed
  without native-library load failure. Artifacts use the committed debug identity solely for QA,
  not production signing. APK SHA-256
  `13ac4c7b0ed12ce0350bfb86e8ac2d0ca6c0f54bf2f1749f1bc9735953e17f88`; AAB SHA-256
  `5259327800ed76e60852083161e5e6334b0ec77698b7e3982376a89fe10036a0`. Production signing,
  physical-device/range acceptance, the 30-session gate and owner approval remain open.

- API-36 Home/Atelier QA candidate (2026-10-11): `0.6.0-home-atelier-qa` / versionCode 667.
  Debug/release Android unit tests and `lintRelease` pass on the current tree. The minified
  QA-signed APK/AAB pass the integrated release preflight: targetSdk 36, merged-manifest contract,
  matching QA certificate, APK ZIP alignment, AAB `BundleConfig.pb` set to uncompressed
  `PAGE_ALIGNMENT_16K`, 11 64-bit ELF libraries per artifact with aligned PT_LOAD segments, model
  hash and backup-resource checks. The exact APK installed/launched successfully on the API-36
  16 KB-page ARM64 emulator (`getconf PAGE_SIZE` = 16384); process remained live after launch and
  no fatal app-process/native-load error appeared in the checked logcat window. Connected
  instrumentation was attempted but Gradle/ADB returned `java.io.IOException: Operation not
  permitted`, including retry with elevated local execution; no test result is claimed. This uses
  the committed debug identity solely for QA, not production signing. The Home smoke assertion for
  the Atelier component entry compiled at this point but did not yet have a connected run; this
  candidate is superseded by v668 below. APK SHA-256
  `7ed468bf3e8bc96cb01897755ff28cc1d19ecac31ed18811892b2a9f944c80f0`; AAB SHA-256
  `b3b688e104b29f23816b7cb3cc357e3d4b47e0940447a4605db56e0e907b6597`. Production signing,
  physical-device/range acceptance, the 30-session gate and owner approval remain open.

- API 36-target QA replay with connected smoke (2026-10-11): `0.6.0-api36-qa2` / versionCode 666
  supersedes the versionCode 665 artifact below. Root JVM, Android debug/release unit tests, and
  `lintRelease` passed. Connected debug and minified-release instrumentation each passed 9/9 on
  the API-35 ARM64 `alloy-api35-arm64` emulator, API-36 ARM64 Google APIs emulator (4 KB pages),
  and API-36 ARM64 Google APIs 16 KB-page emulator (`getconf PAGE_SIZE` = 16384). A test failure
  in the prior replay exposed that the round chooser lacked a visible cancel action; it now has
  “Cancel”, and the smoke test opens and dismisses the chooser through that UI. APK/AAB passed the
  full release preflight with targetSdk 36, merged-manifest contract, QA signature agreement,
  16 KB ZIP/ELF alignment and App Bundle `PAGE_ALIGNMENT_16K` configuration, model hash, and
  backup resources. A preflight gate checks every arm64-v8a and x86_64 shared library's PT_LOAD
  alignment in APK/AAB and fails if the bundle requests 4 KB alignment. MediaPipe Tasks
  Vision 0.10.26 has no 16 KB load-segment lint finding, and the 16 KB emulator completed the full
  connected suite without native-library load failure. Physical-device/range and 30-session gates
  remain open. This is debug-key QA, not production signing. APK SHA-256
  `5b52ff48aaf6b4f84f07558bce3f06a730526477cf35042c6e5ac156070e42dc`; AAB SHA-256
  `db6934358a19d387cd565e624edb71972428250e853830c68f13c17ee0a1147a`.

- API 36 target migration replay (2026-10-11): `0.6.0-api36-qa` / versionCode 665. Root JVM,
  Android debug/release unit tests, and `lintRelease` passed after upgrading the Android/Kotlin
  build plugins and SDK target; offline release APK/AAB packaging succeeded. The minified APK and
  AAB passed `tools/release/verify_android_release.sh`, including targetSdk 36, merged-manifest
  contract, APK/AAB signatures, ZIP alignment, model hash, and backup-resource checks. They are
  signed using the repository's debug-only key solely for QA; this is not production-signing proof.
  MediaPipe Tasks Vision is upgraded to 0.10.26; `lintRelease` no longer reports the prior
  16 KB load-segment alignment issue. Retain a final 16 KB page-size emulator/device run in the
  release matrix. No API-36 emulator/device test ran in this replay; the API-35 evidence below does
  not verify API-36 behavior. APK SHA-256
  `c8c5b436480801d34f55537138be6e3a1d8917dbc874b276c9ca1e4166906fac`; AAB SHA-256
  `52244a09f13775749086c0b9a091877280071813775ccb009db4af4a33a5fef9`.
  A follow-up connected-debug attempt compiled the updated instrumentation suite but the local
  ADB task stopped with `java.io.IOException: Operation not permitted` before reporting test
  results. The API-36 system-image installer also produced no package/download progress and was
  stopped; use the API-36 CI emulator job or a developer emulator for the connected run.

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
- [ ] Rig editing, handedness selection/override, draw-length and poundage validation behave correctly. Automated validation requires optional poundage to be blank or finite and greater than zero; physical UI acceptance remains open.
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
- [x] `:app-android:lintRelease` completed with zero app-source lint findings (2026-10-09); its
      GradleDependency messages are available-version suggestions, not a security/CVE scan. Review
      dependency updates with regression coverage before changing pinned versions.
- [x] All repository workflows use immutable third-party action commit SHAs; CI and release run
      `tools/release/verify_workflow_actions.py` to prevent mutable refs from returning.
- [x] The release workflow rejects the committed QA/debug signer before keystore materialization;
      release contract tests cover QA-certificate rejection, malformed fingerprints and acceptance
      of a well-formed non-QA fingerprint. This policy check does not substitute for a real
      production keystore or a signed production artifact.
- [x] Android CI runs connected debug smoke on API-36 `google_apis_ps16k`; release CI runs the
      signed minified release smoke on the same 16 KB image and asserts the live page size is
      16384. The newly-added GitHub Actions lanes still require a successful hosted run.
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
