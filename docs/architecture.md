# Form Analyser — architecture

## The governing principle (handoff §3.3)

The product is built around a deliberate split of *which signal owns which truth*:

- **The sensor (bow IMU) owns the shot-to-shot, score-critical signals** — hold steadiness /
  pin drift, release execution, cant. These decide *this* arrow.
- **Vision (camera + pose) owns the slow postural foundation** — T-form, alignment,
  draw-elbow line. This explains *why* the groups sit where they do.
- **Strength is a fatigue story, not a per-shot one** — holding-moment via inverse statics,
  and its decay across a session.

The code mirrors this. `ArcheryModule.deviationWeights` weights release/drift/cant above
posture; `ArcheryModule.fatigueMetrics` marks steadiness (higher = fresher) and drift
(higher = more fatigued) as the session-decay signals.

## Layers

```
            ┌──────────────────────── app-android (the instrument) ───────────────────────┐
 capture →  │ CameraX/BlazePose pose · phone IMU (SensorManager) · A1 Pro BLE (validation) │
            │ Hyle Compose UI · Room/SQLite (raw windows) · manual score entry             │
            └───────────────┬─────────────────────────────────────────────────────────────┘
                            │ TimeSeries (gyro/accel channels), pose, draw weight, score
            ┌───────────────▼──────────── archery-module (this, pure Kotlin) ──────────────┐
            │ ArcheryShotSegmenter → ArcheryFeatureExtractor → features                     │
            │ InverseStatics → holding-moment features (when pose available)                │
            └───────────────┬──────────────────────────────────────────────────────────────┘
                            │ FeatureVector + score
            ┌───────────────▼──────────── baseline-engine (separate repo) ─────────────────┐
            │ BaselineBuilder/Model · DeviationScorer · FatigueTracker · SignalScore…       │
            └───────────────────────────────────────────────────────────────────────────────┘
```

## Per-shot feature set (what the extractor produces)

| Feature | Phase | Source | Ties to score (handoff §3.2) |
|---|---|---|---|
| `steadiness` (0–100) | hold | gyro RMS | strong → group spread |
| `pinDriftDeg` | hold | integrated gyro (pitch+yaw) | strong → group spread |
| `cantDeg` | hold | accel gravity vector | medium → directional bias |
| `releasePeakDegPerSec` | release | gyro magnitude peak | strong → L-R fliers |
| `releaseDominantHz` | release | FFT of release window | release-quality |
| `tremorHz` | hold | FFT of signed gyro axes | consistency / fatigue |
| `holdDurationS` | hold | phase length | consistency, rhythm |
| `momentWrist/Elbow/ShoulderNm` | hold | inverse statics (pose + draw wt) | weak per-shot, strong on session decay |

## Boundaries stated honestly (handoff §3.6)

- **Well-posed and implemented:** steadiness, drift, cant, release signature (IMU);
  holding-moment + fatigue (inverse statics, known load, quasi-static hold).
- **Deliberately out of scope:** per-muscle activation (non-unique without EMG),
  release-phase *dynamics* (too fast to be quasi-static), absolute force from video alone.
- **Axis convention** in `ArcheryFeatureExtractor` (X=right, Y=forward, Z=up) and the
  segmentation thresholds in `SegmenterConfig` are starting points to be **validated
  side-by-side against the Steady Aim A1 Pro** (handoff §4b) before they're trusted.

## Roadmap layering (handoff §11)

1. ✅ Archery scoring math (segment / features / inverse statics / engine wiring) — done & tested.
2. ⬜ Android capture app: IMU + manual score + baseline + deviation + session trend + scatter.
3. ⬜ Vision form/alignment + flexibility tests (sagittal BlazePose).
4. ⬜ Target-face CV scoring (auto score capture).
5. ⬜ Web review companion (trends, shot-vs-shot replay overlays, coach sharing).
6. ⬜ A1 Pro as a first-class sensor (reverse-engineer GATT once the math is trusted).
7. ⬜ Optional MW75 EEG mental-state channel via the engine.
