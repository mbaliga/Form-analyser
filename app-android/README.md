# app-android — the capture instrument

The Android app that turns the phone into a **vision** instrument (handoff §5: "Phone = the
instrument"). The phone sits on a tripod, lateral/sagittal to the archer, and the camera +
on-device pose estimation analyse **form and the shot sequence**. It runs the archery module +
engine on-device and shows live + post-session feedback in the Hyle design language.

> This is the **free** app and is camera-based — there is no bow-mounted hardware. The
> sensor-based shot-to-shot signals (drift / release / cant from a bow IMU like the Steady Aim
> A1 Pro) are the **paid** Baseline add-on, parked in the `baseline` repo.

> **Not wired into the default Gradle build by default.** The root `settings.gradle.kts` gates
> this module behind `-PwithAndroid` so the SDK-free engine/archery build runs anywhere. CI's
> `android` workflow builds it with `./gradlew :app-android:assembleDebug -PwithAndroid` on a
> runner that ships the Android SDK. To build locally, add an Android SDK (`local.properties`
> `sdk.dir=…` or `ANDROID_HOME`) and run the same command.

> **BlazePose model: auto-bundled at build time.** The `downloadPoseModel` Gradle task fetches
> `pose_landmarker_lite.task` into `assets/` before packaging, so the installed APK works with
> zero setup — no manual file drop, no first-run download, works offline. The binary is
> gitignored (not committed). Build needs network access the first time (CI has it).

## What's implemented (MVP, handoff §11)

The full vision loop, end to end:
- `capture/PoseRecorder` — CameraX frames → MediaPipe Pose (BlazePose) → a `PoseSequence`, plus a live form readout (bow-arm angle, tracking state).
- `data/` — Room persistence (athlete / session / shots-as-features + score + baseline flag).
- `domain/ArcheryAnalyzer` + `SessionViewModel` — segment → extract form features → build baseline → score deviation → fatigue → signal↔score correlation.
- `ui/` — Hyle-themed Compose: Home (session setup) · Capture (CameraX preview + live form readout, radium-green provenance glow) · Review (per-shot cards, manual score entry, baseline toggles, bow-arm trend + form-vs-score charts, fatigue + correlation summaries).

Still to come (later layers): real-time per-shot detection during capture, pose-overlay rendering, flexibility/ROM tests, target-face CV scoring, the web review companion. Sensor signals come via the paid add-on.

## Dependencies (pin at build time, handoff §6)

| Concern | Library |
|---|---|
| UI | Jetpack Compose (native Kotlin), Hyle design tokens |
| Camera | CameraX (core/camera2/lifecycle/view) |
| Pose | MediaPipe Tasks Vision — Pose Landmarker (BlazePose GHUM) |
| Storage | Room/SQLite (sessions / shots-as-features) |
| Engine | this repo's `:engine` + `:archery-module` modules (no external dependency) |

## MVP screen flow

1. **Session setup** — athlete, draw weight (kept for future inverse-statics calibration), distance.
2. **Live capture** — CameraX preview, side-on; live "tracking ✓ / bow-arm angle" readout; provenance-glow = radium-green (on-device inference). Record an end.
3. **Analyse** — on stop, the pose sequence is segmented into shots (draw → anchor → release) and form features are extracted and persisted.
4. **Baseline build** — mark good shots; the engine's `BaselineBuilder` firms up "your good" (shows N/8 until ready).
5. **Session review** — per-shot form + deviation, bow-arm **trend** (fatigue = downward slope), **form-vs-score** scatter, and `SignalScoreCorrelation` summaries.

## Capture → module → engine wiring (illustrative)

The camera-touching code is thin; everything below the `PoseSequence` boundary is implemented
and unit-tested in `:archery-module` and `:engine`.

```kotlin
// 1. CameraX ImageAnalysis frames -> PoseRecorder (MediaPipe) -> a PoseSequence while recording.
poseRecorder.process(imageProxy)              // per frame; app-owned (CameraX)
val sequence: PoseSequence = poseRecorder.stop()!!

// 2. Segment + extract form features — pure module code, no Android types.
val shots = ArcheryModule.segmenter.segment(sequence)
val features = shots.map { ArcheryModule.extractor.extract(it) }

// 3. Score against the athlete's baseline (engine).
val deviation = DeviationScorer(baseline, ArcheryModule.deviationWeights).score(features.last())
//    deviation.stability -> the 0..100 the archer watches; deviation.topDeviation -> the diagnosis

// 4. After logging arrow scores, surface the differentiator.
val relations = SignalScoreCorrelation.correlate(session.reps)  // "this form drift costs you ~X points"
```

## Validation plan

Record an archer side-on and confirm the pose-based phase segmentation (draw / anchor / release)
and the form features (bow-arm angle, draw-elbow, spine lean, shoulder tilt) match coach
judgement; tune `PoseSegmenterConfig` and the feature definitions against real footage. The axis
conventions and "good" ranges are first-pass and need that validation before they're trusted.
