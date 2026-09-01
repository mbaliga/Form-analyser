# Crocodyl — Athlete Performance, Starting with Archery

> **Product name: Crocodyl.** `form-analyser` is the historical repository/codename. The app ships as **Crocodyl**. See [`docs/naming.md`](docs/naming.md).

Crocodyl is a **local-first athlete performance app**. Its first complete sport/discipline is **Olympic Recurve archery**.

For Recurve, the product direction combines:

- phone-camera sagittal **form and shot-sequence analysis** using on-device pose estimation;
- fast manual scoring and target plotting;
- editable automatic **End Scan** target scoring;
- **Live Observer** tap/voice scoring while the athlete's phone remains positioned for form capture;
- rigs, arrows, equipment, tuning, sight marks, wear and equipment history;
- training plans, goals, body/recovery context and competition history;
- evidence-grounded local coaching plus optional BYOK/on-device models;
- portable athlete↔coach exchange without requiring a Crocodyl account or maintained backend.

The core product law is simple: **measurements and evidence come before advice**. Core athlete data is stored locally by default and sharing is explicit.

## Current implementation status

The reconciled implementation includes the Android app and pure-JVM/domain modules for the existing Crocodyl work: onboarding, Home/Train flows, camera/pose foundations, body and wellness, equipment/tuning, local/BYOK AI foundations, export/exchange foundations, Hyle UI work, and tests.

The implementation is **not equivalent to the full product blueprint yet**. In particular, production-grade manual round scoring, End Scan, Live Observer, complete `.croc` exchange, the paid multi-athlete Coach workspace, static web viewer, full equipment intelligence and the complete training/recovery system remain phased work.

See [`CROCODYL_STATUS.md`](CROCODYL_STATUS.md) for the implementation snapshot and [`docs/crocodyl/`](docs/crocodyl/) for the current product direction and execution plan.

## Repository layout

```text
form-analyser/
├── engine/              # sport-neutral Crocodyl analysis engine (pure Kotlin/JVM)
├── archery-module/      # Recurve pose/shot/form implementation over the SportModule seam
├── core-model/          # shared model types
├── core-equipment/      # equipment/tuning domain logic
├── core-wellness/       # wellness/readiness/privacy domain logic
├── core-body/           # 52-region body contract
├── core-coach/          # grounded AI/rule-coach domain and privacy redaction
├── core-exchange/       # consent/export/identity foundations
├── app-android/         # Android athlete app (Compose + Room + CameraX + MediaPipe)
└── docs/                # architecture, naming, Crocodyl blueprint and phased plan
```

## Architecture direction

The shared Crocodyl core is intended to remain sport-neutral. Archery supplies discipline-specific capture, scoring, equipment and coaching semantics through explicit contracts. Recurve proves those contracts first; later archery disciplines and then a second sport must not be implemented by leaking Recurve assumptions into the shared core.

Crocodyl itself does **not** require bow-mounted hardware. The athlete-facing Recurve form path is phone-camera based.

The separate **Baseline** product/integration is optional and outside the free Crocodyl athlete loop. EEG, bow-mounted IMU channels and cross-app advanced analytics belong to that separate path rather than being prerequisites for Crocodyl.

## Build and test

Pure-JVM modules can be checked with:

```bash
./gradlew test
```

Requires JDK 21. The Android app requires the Android toolchain and is built through the Android workflow / a development machine with the SDK. See [`app-android/README.md`](app-android/README.md).

## Product documents

- [`CROCODYL_STATUS.md`](CROCODYL_STATUS.md) — current implementation/status snapshot.
- [`CROCODYL_BUILD_NOTES.md`](CROCODYL_BUILD_NOTES.md) — accumulated engineering notes.
- [`docs/crocodyl/blueprint/01-product-direction.md`](docs/crocodyl/blueprint/01-product-direction.md) — product definition, scope, laws, roles and commercial boundaries.
- [`docs/crocodyl/blueprint/02-architecture-requirements-roadmap-ux.md`](docs/crocodyl/blueprint/02-architecture-requirements-roadmap-ux.md) — architecture, requirements, parity/release direction and UX architecture.
- [`docs/crocodyl/CROCODYL_PHASED_IMPLEMENTATION_PLAN.md`](docs/crocodyl/CROCODYL_PHASED_IMPLEMENTATION_PLAN.md) — phased execution plan from current build through Recurve v1 and later sports.

## Launch focus

The first product to prove is not “generic form analysis.” It is **Crocodyl for Recurve athletes**: a range-usable performance system where scoring, camera-based technique evidence, equipment context, training/recovery context and coach feedback can eventually be understood together without giving up local data ownership.
