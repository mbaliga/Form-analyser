# Form Analyser (codename) — Archery

> **Product name: Crocodyl.** `form-analyser` is the repo/codename; the app ships as
> **Crocodyl**. A full repo/package rebrand is deferred until just before the Play listing —
> see [`docs/naming.md`](docs/naming.md).

A **standalone, free** Android-first instrument that measures an archer's **form, stability,
and strength (as load/fatigue)**, establishes a **personal baseline**, scores **per-shot
deviation**, tracks **fatigue across a session**, and — the differentiator — **correlates those
signals with the actual arrow score**.

> Archery is the well-posed first case (handoff §1): discrete repeatable shots, a known
> external load (draw weight), a quasi-static hold, the draw arm in the camera's good
> (sagittal) plane, and a real arrow to validate against.

This repo is self-contained: the free app ships with its own copy of the sport-agnostic
**Crocodyl engine**. The separate [`baseline`](https://github.com/mbaliga/baseline) repo is the
**paid add-on** (the MW75 EEG mental-state channel + advanced analytics) that layers on top —
Form Analyser does not depend on it.

## Repo layout

```
form-analyser/
├── engine/              # the sport-agnostic Crocodyl engine (pure Kotlin/JVM)  ← shipped in the free app
│   └── src/main/kotlin/xyz/mdhv/baseline/engine/
│       ├── baseline/    # BaselineBuilder/Model — per-athlete "your good"
│       ├── deviation/   # DeviationScorer — signed z + 0–100 stability
│       ├── fatigue/     # FatigueTracker — session decay trajectory
│       ├── sport/       # SportModule seam + SignalScoreCorrelation
│       ├── model/ stats/
├── archery-module/      # the archery SportModule (pure Kotlin/JVM, depends on :engine)
│   └── src/main/kotlin/xyz/mdhv/formanalyser/archery/
│       ├── ArcheryShotSegmenter.kt   # set-up / hold / release from bow-IMU motion
│       ├── ArcheryFeatureExtractor.kt# steadiness, pin-drift, cant, release signature
│       ├── ArcheryModule.kt          # the SportModule the engine talks to
│       ├── signal/                   # FFT, Butterworth (no external DSP dep)
│       └── statics/InverseStatics.kt # §3.4 holding-moment / strength via known load
├── app-android/         # the capture app (CameraX + IMU + BLE + Hyle UI) — see its README
└── docs/architecture.md # how this maps to the build handoff
```

## Architecture in one line

`archery-module` implements the engine's `SportModule` seam (rep-segmenter, feature extractor,
scoring rubric); `:engine` owns the baseline, deviation, fatigue, and signal→score correlation.
The engine is a clean, sport-neutral module so a second sport (fencing / pistol / hangboard) can
reuse it later — extracted to its own package at that point if desired.

## Build & test

Self-contained — no external repo or artifact needed:

```bash
./gradlew test     # builds :engine + :archery-module and runs all unit tests
```

Requires JDK 21. The Android app (`app-android/`) needs the Android SDK, so it's **opt-in**: pass
`-PwithAndroid` (e.g. `./gradlew :app-android:assembleDebug -PwithAndroid`, which CI's `android`
job runs) — the default `./gradlew test` stays SDK-free. See [`app-android/README.md`](app-android/README.md).

**Crash recovery.** The app installs the shared `dev.aarso:crash-recovery` utility (from the
`hyle-design-system` submodule via a `-PwithAndroid`-gated `includeBuild` — no `:hyle`
dependency). A device-only crash is captured and shown on the next launch instead of the app
silently dying — Crocodyl is the constellation's real-world crasher, so this is where it earns
its keep. **Preview the recovery screen without a real crash:** on the home screen, long-press
the "Crocodyl" title (debug builds only) — calls `CrashRecovery.previewIntent(context, "Crocodyl")`.

## First build target (handoff §11)

> **Archery shot-stability tracker.** Bow-mounted phone IMU → segment each shot into
> set-up/hold/release → compute steadiness, pin-drift, cant, release signature → log the arrow
> score per shot (manual) → build a per-athlete baseline from good shots → show per-shot
> deviation + a stability score, plus a session trend (fatigue = steadiness decay) and a first
> signal-vs-score scatter. Validate the IMU derivations side-by-side against the Steady Aim A1
> Pro.

The **scoring math for all of this already exists and is tested** in `engine` + `archery-module`.
What remains for the MVP is the capture/UI layer in `app-android` (sensors, screens, persistence).
