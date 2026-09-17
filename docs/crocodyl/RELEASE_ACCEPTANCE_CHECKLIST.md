# Crocodyl 0.6 release acceptance checklist

This checklist is the final gate for the current Recurve scope. It is intentionally separate from
the pure-JVM and packaging checks, which are already automated. Record device model, Android
version, app version, APK/AAB digest, tester, date, and evidence for every run.

## 1. Required device matrix

- [ ] ARM64 phone, Android 12–15, rear camera, mid-range performance.
- [ ] ARM64 phone, Android 12–15, flagship performance and a different camera vendor.
- [ ] Camera-less or large-screen Android device (the app must still support scoring, logging,
      progress, body, coach, and export without capture).
- [ ] At least one clean install and one update over the previous production APK.
- [ ] If a supported x86 emulator is used, repeat the non-camera flows and record the emulator image.

### 1.1 Current local evidence (not release sign-off)

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
- [ ] Export a `.crocbak`, inspect the consent preview, share it, and verify PRIVATE data is absent.
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

- [ ] `./gradlew test` passes.
- [ ] `:app-android:testDebugUnitTest` and `:app-android:testReleaseUnitTest` pass with `-PwithAndroid`.
- [ ] `:app-android:lintRelease` has zero errors and zero warnings.
- [ ] Release APK/AAB are built by CI with the production keystore and requested version metadata.
- [ ] CI verifies APK signature, AAB signature, manifest security, bundled BlazePose hash, backup
      resources, and absence of staging files.
- [ ] APK is installed over the previous production build and launches on the acceptance matrix.
- [ ] Owner approves the release notes and selected distribution channel.

End Scan, Live Observer, signed `.croc` exchange, Coach workspace, web viewer, equipment catalog,
and the broader training/recovery roadmap are not acceptance criteria for 0.6; see
[`CROCODYL_STATUS.md`](../../CROCODYL_STATUS.md).
