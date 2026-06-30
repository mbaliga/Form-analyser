# Form Analyser (codename) — Archery

The first sport built on the [Baseline engine](https://github.com/mbaliga/baseline). An
Android-first instrument that measures an archer's **form, stability, and strength (as
load/fatigue)**, establishes a **personal baseline**, scores **per-shot deviation**, tracks
**fatigue across a session**, and — the differentiator — **correlates those signals with the
actual arrow score**.

> Archery is the well-posed first case (handoff §1): discrete repeatable shots, a known
> external load (draw weight), a quasi-static hold, the draw arm in the camera's good
> (sagittal) plane, and a real arrow to validate against.

## Repo layout

```
form-analyser/
├── archery-module/      # the archery SportModule (pure Kotlin/JVM, headless-testable)  ← built & tested here
│   └── src/main/kotlin/xyz/mdhv/formanalyser/archery/
│       ├── ArcheryShotSegmenter.kt   # set-up / hold / release from bow-IMU motion
│       ├── ArcheryFeatureExtractor.kt# steadiness, pin-drift, cant, release signature
│       ├── ArcheryModule.kt          # the SportModule the engine talks to
│       ├── signal/                   # FFT, Butterworth (no external DSP dep)
│       └── statics/InverseStatics.kt # §3.4 holding-moment / strength via known load
├── app-android/         # the capture app (CameraX + IMU + BLE + Hyle UI) — see its README
└── docs/architecture.md # how this maps to the build handoff
```

## How it relates to Baseline

`archery-module` implements the engine's `SportModule` seam — it supplies the
rep-segmenter, feature extractor, and scoring rubric; the engine owns the baseline,
deviation, fatigue, and signal→score correlation. The dependency is one-way: this repo
depends on `xyz.mdhv.baseline:baseline-engine`, never the reverse.

## Build & test

The archery module depends on the Baseline engine, resolved from `mavenLocal()`. Publish the
engine first:

```bash
# in the baseline repo
cd engine && ./gradlew publishToMavenLocal

# here
./gradlew :archery-module:test
```

Requires JDK 21. The Android app (`app-android/`) is **not** part of this Gradle build yet —
it needs the Android SDK and is built on a dev machine / in CI with the Android toolchain.
See [`app-android/README.md`](app-android/README.md).

## First build target (handoff §11)

> **Archery shot-stability tracker.** Bow-mounted phone IMU → segment each shot into
> set-up/hold/release → compute steadiness, pin-drift, cant, release signature → log the arrow
> score per shot (manual) → build a per-athlete baseline from good shots → show per-shot
> deviation + a stability score, plus a session trend (fatigue = steadiness decay) and a first
> signal-vs-score scatter. Validate the IMU derivations side-by-side against the Steady Aim A1
> Pro.

The **scoring math for all of this already exists and is tested** in `archery-module`. What
remains for the MVP is the capture/UI layer in `app-android` (sensors, screens, persistence).
