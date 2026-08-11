# Crocodyl — Status & Vision

_Last updated: 2026-08-12 · current build: **v0.5.1** (versionCode 4)_

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

---

## 4. Pending

### 4.1 Verification (the honest gap)
CI proves the Android app **compiles and packages** — not that it **behaves**. The pure-JVM cores are
unit-tested; the Android UI/wiring has **not been exercised on a device yet**. First real-run pass is
the immediate next step (onboarding → Home → all tabs → Body atlas → coach → export → tuning).

### 4.2 AI coach polish
- **On-device model UX:** today the user must supply a Gemma `.task`/`.bin` model file; add a guided
  download/import flow (and consider bundling a small default).
- Streaming responses, token/cost display, per-provider model-list refresh.

### 4.3 Design system
- Wire **`dev.aarso:hyle`** as a real published dependency instead of ported token constants.
- Hand-drawn body-atlas SVG art to replace the schematic rounded-rects (the integrity suite is the
  contract; the override seam is ready).

### 4.4 Deferred within Phases 1–3 (logged)
- Hilt DI (kept manual to de-risk blind compilation).
- Robolectric tests (migrations, PrivacyRegistry reflection, VM suites) — need the Android SDK.
- Streak week-strip glyphs, sRPE secondary load lane, in-app document camera capture.

### 4.5 Future phases (roadmap, not yet built)
The full spec runs to ~Phase 12. Notable future work: deeper tuning history, richer export/import &
coach↔athlete sharing (Phase 5 continuation), a Progress/stability-trends surface, and a Wear OS
companion (Phase 12). These were always beyond the 1–3 + AI-coach + Phase-4/5 scope built so far.

### 4.6 Delta vs. the new blueprint (2026-08-12)

On 2026-08-12, Madhav pushed a Product Blueprint v2.1 + Phased Implementation Plan into this repo via
a ChatGPT-driven agent (`agent/crocodyl-chatgpt-sync` / `agent/crocodyl-package-export`, reconciled
into PR #4). It's a genuine strategic document, not mechanical — it repositions Crocodyl as a general
"sovereign athlete performance system" (Olympic Recurve first, more sports later) and specifies a much
larger surface than what's built so far. Concretely, **not yet built**, per that blueprint:

- **Manual/plotted scoring** — WA Recurve round packs, cold-thumb numeric scoring, target-face
  plotting, totals/set points/PBs. (Today's app has no manual scoring surface at all.)
- **End Scan** — editable automatic post-end target-photo scoring.
- **Live Observer** — tap/constrained-voice scoring during form capture; **Paired Target Cam** later.
- **Coach workspace** — a paid, multi-athlete surface (roster, report review, notes, assignments).
- **Static local-only web viewer** for exported files.
- **Signed `.croc` exchange** — a person-to-person envelope, distinct from the device-backup
  `.crocbak` this repo already has (Phase 5 built the backup half only).
- **DeepSeek** as a BYOK provider (not in `core-coach`'s `ModelRegistry` yet).
- **Equipment catalog/commerce** — provenance-rich catalog, wear forecasts, upgrade evidence,
  affiliate-link laws (commerce must never steer evidence/ranking).
- **Fuller training/recovery system** — plans, goals, habits, injury-risk context, conservative
  recovery guidance, alongside the existing wellness/body layers.
- **A formal longitudinal observation-resolution contract**
  (`SHOT_CONFIRMED`/`SHOT_INFERRED`/`END_ONLY`/`SESSION_ONLY`/`DAY_WINDOW`/`PERIOD_WINDOW`) — stricter
  than what the current wellness/coach cores implicitly assume. A design constraint for future work,
  not retrofitted onto Phases 1–3 now.

None of the above required changing any existing code — the blueprint is purely additive documentation
and merges clean on top of `v0.5.1`.

### 4.7 Owner to-dos (Madhav)
- Review/merge **PR #4** (`claude/form-analyser-baseline-split-e9s6lr → main`) — the reconciled,
  CI-green `v0.5.1` build, now including the Blueprint v2.1 docs. **PR #5** (the ChatGPT-agent branch
  merged into #4) can be closed as superseded. **PR #3** (a stale ~v0.4.2 snapshot) remains superseded
  from the earlier reconciliation pass and can also be closed.
- Approve/edit the blueprint's "governing decisions" (§4.6 above; full list in
  `docs/crocodyl/blueprint/01-product-direction.md`) — the blueprint itself calls these "proposed,"
  governing only after owner approval.
- Confirm the DECISION-21 OTF constants (shipped as defaults 2.0 lbs/in draw, 1.0 lb/in riser).
- Baseline repo: the Crocodyl spec + phase briefs are committed there (PR #5 on `baseline`, draft) —
  separate from this repo's own PR #5.

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
