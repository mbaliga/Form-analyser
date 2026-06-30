# app-android — the capture instrument

The Android app that turns the phone into the measurement instrument (handoff §5: "Phone =
the instrument"). It captures sensor data, runs the archery module + engine **on-device**, and
shows live + post-session feedback in the Hyle design language.

> **Status: IMU-first MVP implemented; not wired into the default Gradle build.** The module
> exists (`build.gradle.kts` + sources below), but the root `settings.gradle.kts` deliberately
> omits it so the headless engine/archery CI stays green without an Android SDK. To build it:
>
> 1. add `include(":app-android")` to the root `settings.gradle.kts`
> 2. provide an Android SDK via a `local.properties` (`sdk.dir=/path/to/Android/sdk`) or `ANDROID_HOME`
> 3. `./gradlew :app-android:assembleDebug`
>
> This code was written without an on-device compile, so expect a few small build fixes on first
> run. The hardware-independent logic it sits on (`:engine`, `:archery-module`) is unit-tested.

## What's implemented (MVP, handoff §11)

The full IMU-first loop, end to end:
- `capture/ImuRecorder` — `SensorManager` gyro+accel → fused `TimeSeries` (deg/s, g) + a live steadiness readout.
- `data/` — Room persistence (athlete / session / shots-as-features + score + baseline flag).
- `domain/ArcheryAnalyzer` + `SessionViewModel` — segment → extract → build baseline → score deviation → fatigue → signal↔score correlation.
- `ui/` — Hyle-themed Compose: Home (session setup) · Capture (live steadiness gauge w/ radium-green provenance glow) · Review (per-shot cards, manual score entry, baseline toggles, steadiness-trend + pin-drift-vs-score charts, fatigue + correlation summaries).

Still to come (later layers): real-time per-shot detection during capture, CameraX/BlazePose form, A1 Pro BLE, target-face CV scoring, raw-window file storage.

## Dependencies (pin at build time, handoff §6)

| Concern | Library |
|---|---|
| UI | Jetpack Compose (native Kotlin), Hyle design tokens |
| Camera/pose | CameraX + MediaPipe Pose / BlazePose (GHUM) |
| Phone IMU | `SensorManager` (`TYPE_GYROSCOPE`, `TYPE_ACCELEROMETER`, `TYPE_ROTATION_VECTOR`), `SENSOR_DELAY_FASTEST` |
| BLE (A1 Pro / external IMU) | Nordic Android-BLE-Library; nRF Connect for discovery |
| Storage | Room/SQLite (raw windows + reps + sessions) |
| Engine | this repo's `:engine` + `:archery-module` modules (no external dependency) |

## MVP screen flow (handoff §11 first build target)

1. **Session setup** — pick athlete, enter bow/draw-weight (the known load for inverse statics).
2. **Live capture** — bow-mounted phone IMU streams; live steadiness + cant readout during the
   hold; provenance-glow = radium-green (on-device inference).
3. **Per-shot card** — after the release spike, show segmented phases + the shot's features and
   per-shot deviation vs baseline; prompt for **manual arrow score**.
4. **Baseline build** — mark good shots; the engine's `BaselineBuilder` firms up "your good"
   (shows N/8 until ready).
5. **Session review** — stability trend across shots, **fatigue** (steadiness decay), and a
   first **signal-vs-score scatter** from `SignalScoreCorrelation`.

## Capture → module → engine wiring (illustrative)

The hardware-touching code is thin; everything below the `TimeSeries` boundary is already
implemented and tested in `:archery-module` and `baseline-engine`.

```kotlin
// 1. Collect a bow-IMU window from SensorManager into the engine's TimeSeries.
//    channels MUST be ArcheryChannels.{GYRO_*, ACC_*}; gyro in deg/s, accel in g.
val window: TimeSeries = imuRecorder.toTimeSeries()   // app-owned (SensorManager)

// 2. Segment + extract — pure module code, no Android types.
val shots = ArcheryModule.segmenter.segment(window)
val features = shots.map { ArcheryModule.extractor.extract(it) }

// 3. (optional) add strength features from a sagittal pose frame at full draw.
val moments = InverseStatics.analyze(pose, drawForceN, stringDir, athleteBodyMassKg)
val shotFeatures = features.last() + moments.asFeatures()

// 4. Score against the athlete's baseline (engine).
val deviation = DeviationScorer(baseline, ArcheryModule.deviationWeights).score(shotFeatures)
//    deviation.stability -> the 0..100 the archer watches; deviation.topDeviation -> the diagnosis

// 5. After logging arrow scores, surface the differentiator.
val relations = SignalScoreCorrelation.correlate(session.reps)  // "this drift costs you ~X points"
```

## On-device validation plan (handoff §4b)

Run the Steady Aim A1 Pro's own app side-by-side and compare our `steadiness` / `pinDriftDeg` /
phase segmentation against its USA-Archery-grade numbers. Tune `SegmenterConfig` and
`STEADINESS_SCALE_DEG_PER_S` until they track. Only then pursue reverse-engineering the A1 Pro
GATT profile to fold it in as a first-class sensor (§4a).
