# Crocodyl — Status & Vision

_Last updated: 2026-09-11 · current development build: **v0.6.0-spec-dev** (versionCode 6)_

This is the single source of truth for **what Crocodyl is**, **what's built**, and **what's left**.
It complements `CROCODYL_BUILD_NOTES.md` (the engineering log) with the product-level picture.

> **Product direction is now governed by `docs/crocodyl/blueprint/`** (Product Blueprint v2.1: 
> [`01-product-direction.md`](docs/crocodyl/blueprint/01-product-direction.md),
> [`02-architecture-requirements-roadmap-ux.md`](docs/crocodyl/blueprint/02-architecture-requirements-roadmap-ux.md))
> **and [`docs/crocodyl/CROCODYL_PHASED_IMPLEMENTATION_PLAN.md`](docs/crocodyl/CROCODYL_PHASED_IMPLEMENTATION_PLAN.md)**
> (the phased execution plan). Where those documents and this one differ, they govern the *direction*;
> this file remains the **engineering-implementation snapshot** underneath that direction — what's
> actually built, versus what's planned. See §4.6 below for the concrete delta.

---

## 1. Vision

**Crocodyl is a free, standalone, vision-based archery training app.** You mount nothing on the bow
and wear no sensors — the phone camera films you (sagittal view), and on-device pose estimation
(MediaPipe BlazePose) analyses your **form and shot sequence**. Everything runs locally; your data is
yours and stays on the device by default.

Three principles drive the whole product:

1. **Local-first & private.** Analysis, storage, and the default coach all run on-device. Data leaves
   the phone only through an explicit, per-item ceremony you control.
2. **Show, don't tell.** Adopted from the Hyle Design System: _state is shown by material behaviour,
   never said by language._ Readiness is a shape and a luminance, not a paragraph. Provenance is a
   colour (radium-green = on-device/native, cold-cyan = cloud/from-elsewhere), not a label.
3. **Honest coaching.** The coach reasons over **your own data**. It never invents a "free chat"
   surface; it grounds every insight in facts and shows you exactly what it did and didn't send.

### The two-product split

| | **Crocodyl** (this repo, `form-analyser`) | **Baseline** (`baseline`) |
|---|---|---|
| Price | Free, public, open-source | Paid add-on, **open-source** — gated by entitlement in the official binary only |
| Modality | Phone-camera vision (BlazePose) | EEG mental-state channel + bow-mounted IMU sensor channel |
| Analytics | The shared engine ships **inside** Crocodyl | Advanced/paid analytics engines |
| Repo rule | — | **What belongs to Baseline must never enter Crocodyl's git history.** |

The engine/sport-module seam lives in Crocodyl so the free app is fully functional on its own;
Baseline plugs additional channels and engines on top for subscribers.

> **Licensing correction (2026-08-12):** Baseline was previously described here as a private, closed
> repo. Per the Product Blueprint v2.1 governing decisions, paid Baseline features stay
> **open-source** — the wall is a commercial entitlement check in the official binary, not a hidden
> source tree. This is a licensing/visibility stance on the *Baseline repo itself* and does **not**
> change the separate, still-standing repo-content-separation rule directly above: Baseline-specific
> content (EEG, bow-IMU code, paid analytics) must never enter Crocodyl's git history, regardless of
> either repo's license.

---

## 2. Architecture

Gradle multi-module. **Pure-JVM cores are headless-testable** (`./gradlew test` anywhere); the Android
app needs the SDK and only builds in CI (`-PwithAndroid`).

```
engine/            sport-agnostic analysis engine (SportModule seam)
archery-module/    archery implementation: handedness normalise, pose→shot segmentation, features
core-model/        shared enums (Handedness, BowType)
core-equipment/    poundage estimator + Phase-4 tuning math (FOC/GPP/KE, validation)
core-wellness/     load, ACWR, streak, readiness, cycle, PrivacyRegistry
core-body/         52-region body atlas contract + soreness resolver
core-coach/        AI coach domain: model registry, grounding, redaction, prompt, rule-coach
core-exchange/     Phase-5 export/exchange: consent filter, .crocbak manifest, pubkey identity
app-android/       the app: Compose UI, Room, CameraX, MediaPipe, providers, vault (CI-only)
```

- **Stack:** Kotlin 2.1.0, AGP 8.7.3, Gradle 8.14.3, JDK 21, Jetpack Compose (Material3),
  Room 2.6.1 + KSP, DataStore, kotlinx.serialization, CameraX 1.4.1, MediaPipe tasks-vision +
  tasks-genai, Tink 1.15.0.
- **DI:** manual (Hilt deferred). **ViewModels:** `AndroidViewModel` + manual `load()`.
- **CI:** `ci.yml` runs `./gradlew test` (pure-JVM cores); `android.yml` builds `assembleDebug`;
  `release.yml` builds + attaches an APK to a GitHub Release when a `release/<version>` branch is pushed.
- **Signing/versioning:** a committed debug keystore signs every build identically so updates install
  in place; versionCode is bumped per release.

---

## 3. Done

### 3.1 Stability (shipped & install-verified path)
- **Onboarding DB crash** → self-healing DB open: if a legacy schema can't migrate, the DB is rebuilt
  instead of crashing. `allowBackup=false` so stale DBs can't be restored onto a fresh install.
- **`MainShell` `NoSuchFieldError`** → the nav object `R` collided with the generated resources `R`;
  renamed to `Routes`.
- **"App not installed"** → committed debug keystore (stable signature) + versionCode bumping.
- **DB-recovery safety (2026-08-12):** the Phased Implementation Plan flagged the self-healing rebuild
  above as unsafe for production ("must never silently delete athlete history"). Hardened:
  `fallbackToDestructiveMigration()` removed (Room now throws instead of silently wiping on a missing
  migration path, surfacing the failure to the same catch below rather than hiding it two ways); on a
  genuine open/migration failure the raw `.db` file is best-effort backed up to `filesDir/db-recovery/`
  *before* any reset, and a `lastDbResetAtMs` flag is recorded so the UI can tell the athlete, after
  the fact, that a reset happened and a backup was kept. A blocking pre-open confirmation isn't
  possible (Room's open is synchronous, before any Activity/Compose context exists), so "back up the
  bytes, then tell the user" is the practical version of "never silently delete" on Android. The full
  Phase-0/1 contract system from the blueprint (stable IDs, the observation-resolution model) is *not*
  retrofitted here — see §4.6.

### 3.2 Phases 1–3 — the core app (code-complete, CI-green)
- **Phase 1:** onboarding flow, profile/identity, rigs + poundage estimator, Home, Train setup,
  Settings, Hyle UI atoms, handedness normalisation wired into capture.
- **Phase 2:** wellness + life layer (check-ins, mood, life events, cycle, medication, events),
  load/ACWR/streak/readiness, Calendar tab, the "+ Log" surface, pre/post-session check-ins.
- **Phase 3:** 52-region body atlas, pain logging, injuries (CRUD, auto-link), physio plans/sessions,
  the Tink streaming-AEAD **document vault** (encrypted at rest), readiness v3 (injuries).

### 3.3 Wave 1 — new pure-JVM cores (unit-tested, adversarially reviewed)
- **`core-coach`** — model registry (Anthropic/OpenAI/Google cloud + on-device Gemma; **every cloud
  model is BYOK, no free hosted tier**), fact grounding, **privacy-class redaction** (PRIVATE never
  reaches cloud/export; MEDICAL only with an explicit grant; destination bound to the model kind),
  prompt builder (fixed intents, no open chat), deterministic offline **rule-coach**.
- **`core-exchange`** — Phase-5 **consent filter** enforcing the PrivacyRegistry across export tiers,
  the **`.crocbak`** archive manifest (clock injected, round-trips), pubkey identity + `KeyProvider` seam.
- **`core-equipment` Phase-4 tuning** — `TuningSpec` (versioned, all-optional), FOC/GPP/KE math,
  range-validation warnings. `PoundageEstimator` untouched.
- ✅ The privacy invariant was independently verified: PRIVATE data cannot reach a cloud model or an
  export, and MEDICAL is grant-gated.

### 3.4 Wave 2 — Android layers over the cores (CI-compiled & packaged)
- **AI coach:** BYOK key vault (Tink Aead + Keystore); `AiSettings` (model choice, medical-grant &
  keep-private toggles, on-device model path); cloud clients (Anthropic Messages / OpenAI chat /
  Gemini generateContent); on-device client via MediaPipe LLM Inference; `CoachViewModel` +
  `CoachScreen` (offline insights always; grounded "Ask"; provenance-coloured model picker; a
  "what wasn't sent" report).
- **Phase 5 export:** `AndroidKeyProvider` (Keystore identity), `ExportViewModel` (builds the
  `.crocbak` zip from only the consent-included tables), `ExportScreen` (live consent preview + SAF write).
- **Phase 4 tuning UI:** `AdvancedTuningSection` wired into the rig editor — brace/tiller/plunger/
  stabilizer/arrow, with live FOC/GPP and validation warnings.
- Wiring: nav routes, Home "Coach" card, Settings rows, INTERNET permission (BYOK cloud calls only).

### 3.5 Releases
`v0.4.2` (DB fix) · `v0.4.3` (nav fix) · `v0.4.4` (install/signing fix) · `v0.5.0-everything`
(all of the above in one APK) · **`v0.5.1`** (reconciled onto `main`'s `crocodyl.engine` package
rename; current).

Going forward, releases track the Phased Implementation Plan's ladder (§5.1 of that doc): `0.6.0`
manual-scoring wedge → `0.7.x` form/End-Scan/Live-Observer → `0.8.0` exchange → `0.9.0` Coach →
`0.10.0` web viewer → `0.11.0` model UX → `0.12.x` equipment/training → `1.0.0` club release, rather
than continuing ad hoc `0.4.x`/`0.5.x` numbering in isolation.

### 3.6 Current development branch — immersive athlete experience

PR #11 (`feat/immersive-visuals`) now contains the current product surface:

- a complete System/Light/Dark theme switch with warm, high-contrast light surfaces;
- an illustrated athlete Home hero, graphical capture placement guide and live framing overlay;
- an anatomical, procedurally drawn front/back muscle atlas replacing rectangular body regions;
- graphical improvement-area cards and richer review/body presentation;
- WA/manual numeric scoring, target plotting, set matches, PBs, grouping, score history and Progress;
- constrained observer voice declarations such as “eight bottom left”, plus repeat/undo, using
  Android's on-device recognizer only; repeat, undo, skip, finish-end and audited last-arrow
  correction are supported, and declarations/observation resolution are preserved;
- photo-assisted End Scan: import a target photograph, calibrate centre/edge, mark arrows, calculate
  provisional rings, then confirm or reject each result before it affects a scorecard;
- opt-in raw MP4 capture alongside pose analysis, stored in app-private media storage;
- DeepSeek BYOK, on-device model file import, and deterministic offline coaching fallback;
- checksum-validated `.crocbak` preview/import that adds missing rows transactionally without
  overwriting local history; and
- ECDSA-signed `.croc` sharing using the Android Keystore identity, with signature, fingerprint and
  payload verification before import preview;
- visual Pairing Cards, explicit trust-on-first-use identity pinning, manifest/envelope identity
  binding, and two-step quarantine/replacement when a known athlete's signing key changes; and
- durable raw-video/session links, per-shot draw/release timestamps, phase-aligned replay from each
  shot card, and explicit raw-video deletion from Review.

---

## 4. Pending

### 4.1 Verification gates (intentionally not claimed by CI)

- On-device behavioural, rotation/foldable, camera, microphone, thermal, battery and storage QA.
- Range validation of pose metrics, shot segmentation and all target-photo scoring behavior.
- Athlete/coach comprehension, accessibility and sunlight/glove testing.
- Clinical/content review of recovery, pain, injury and return-to-training guidance.

### 4.2 Remaining product implementation

- **Automatic End Scan vision:** the current flow is calibrated photo marking, not an arrow detector.
  Perspective correction, automatic impact candidates, drag/add/remove correction and a measured
  device/target/lighting envelope remain.
- **Voice command depth:** embedded Android on-device recognition, scoring, repeat, undo, skip,
  finish-end and audited correct-last are implemented. Range false-acceptance validation remains.
- **Synchronized media review:** durable shot-to-video timing and phase-aligned replay are built.
  A user-selectable automatic retention schedule remains; Review currently provides explicit raw
  video deletion.
- **Human Coach product:** roster, athlete inbox/detail, notes, assignments, acknowledgement,
  coach-local score book, entitlement and retention controls remain. The existing “Coach” is the
  athlete's AI/rule coach, not this paid human workspace.
- **Organiser/referee and spectator display:** competition control, casting/external-display layout,
  automatic form filling and offline tournament workflows remain.
- **Static local web viewer:** local `.croc`/`.crocbak` inspection and report rendering in a PWA is
  not implemented.
- **Exchange trust UX:** `.croc` signing/verification, Pairing Cards, TOFU pinning and key-change
  quarantine now exist. Duplicate/conflict inspection and explicit athlete switching remain.
- **Device ecosystem:** Garmin, Wear OS, Health Connect, Bluetooth sensors and Steady Aim A1 Pro are
  not integrated. These require their respective SDK/protocol work and hardware testing.
- **Baseline seam:** a versioned, consent-filtered Crocodyl-to-Baseline factor/observation adapter is
  still required; Baseline-specific engines remain outside this repository.
- **Equipment depth:** individual arrows, catalog provenance, lifecycle/wear, tuning history and
  neutral upgrade evidence remain beyond the current rig/tuning calculator.
- **Training depth:** competition planning, plan-vs-completed reconciliation, pressure games and the
  reviewed recovery action library remain beyond current goals/check-ins/calendar/body/physio.
- **Model UX:** guided local-model download, streaming, cancellation, token/cost display and live
  provider model discovery remain. Manual local-model import and all configured BYOK providers work.
- **Additional disciplines/platforms:** Compound, Korean/traditional archery, Bouldering, Fencing,
  Swimming, Rock climbing and iOS have not been implemented; only the sport-module seam exists.
- **Release engineering:** localization, automated accessibility checks, migration fixtures,
  malicious exchange fixtures, security review, store declarations and club-pilot evidence remain.

### 4.3 Repository decisions

- Merge PR #11 after review; it supersedes the stale implementation claims and visual branch work.
- Confirm DECISION-21 OTF constants (current defaults: 2.0 lbs/in draw, 1.0 lb/in riser).
- Keep EEG/bow-IMU and paid Baseline analytics out of this repository; exchange only typed,
  consent-filtered observations across the future adapter.

---

## 5. Known caveats
- **Blind-compiled Android layer:** authored against verified core APIs and green in CI, but this is
  its first packaging — treat v0.5.1 as a thorough first-pass test build, not a shipped release.
- **BYOK required for cloud "Ask":** the offline rule-coach needs nothing; cloud insights need a key,
  on-device insights need an installed model.
- **No local Android SDK** in the build environment — the app is only ever verified in CI, so the
  round-trip for Android fixes is CI-bound.

---

## 6. TL;DR
Phases 1–3 + the AI coach + Phase-4 tuning + Phase-5 export are **built and compiling green**, packaged
in **v0.5.1**, reconciled onto `main`'s `crocodyl.engine` rename in PR #4. What remains before calling
it done is **on-device behavioural verification**, on-device-model UX, the real Hyle dependency, and
the deferred/future-phase work above. The paid Baseline channels (EEG, bow-IMU, advanced analytics)
stay out of Crocodyl's git history — that separation is unchanged — though Baseline itself is now
understood to be open-source, gated by entitlement rather than by a closed repo (§1).

A much larger product blueprint (Product Blueprint v2.1 + Phased Implementation Plan, in
`docs/crocodyl/`) now governs where Crocodyl is headed — manual scoring, End Scan, Live Observer, a
paid Coach workspace, a static web viewer, signed `.croc` exchange, and more (§4.6). Everything in
this file is the *implementation* snapshot underneath that direction, not a competing plan.
