# Crocodyl — Status & Vision

_Last reviewed: 2026-10-11 · current branch: **codex/production-hardening** · latest QA artifact: `0.6.0-home-immersive-qa` (versionCode 664), APK/AAB preflight passed with QA/debug signer · production release: **not approved**_

This is the single source of truth for **what Crocodyl is**, **what's built**, and **what's left**.
It complements `CROCODYL_BUILD_NOTES.md` (the engineering log) with the product-level picture.

## Sport expansion — Fencing, Muay Thai and Bouldering

The near-term follow-on is underway in [`docs/crocodyl/FENCING_AND_ECOSYSTEM_ROADMAP.md`](docs/crocodyl/FENCING_AND_ECOSYSTEM_ROADMAP.md): Sabre, Foil and Epee, a live manual two-fencer referee console for **Prêt**, **Allez**, **Halte** and point calls, visible equipment metadata before live capture, a provenance-aware directory, and a shared local-session contract for Bocal/Crocodyl beta builds. The sport module and Android entry points are present; the Fencing activity now has a licensed-photo piste hero and manual referee command deck (API-35 screenshots are linked from the cinematic refresh doc). Referee vision, web ingestion, advanced-search adoption, Nooz news adapters and production local transport are not live yet.

Muay Thai and bouldering now have separate manual training journals, pure sport modules, local session records and cinematic Home entry cards. This first slice records user-entered sessions only; it does not make camera-coaching, safety, grade-conversion or cross-sport performance claims. Details and release limitations are in [`docs/crocodyl/MULTISPORT_JOURNAL.md`](docs/crocodyl/MULTISPORT_JOURNAL.md).

## Screenless Motion Sessions — concept handoff

No IMU/screen-off capture has been implemented. The existing
[`docs/crocodyl/MOTION_SESSIONS_HANDOFF.md`](docs/crocodyl/MOTION_SESSIONS_HANDOFF.md) is the
canonical product/engineering brief and includes copy-ready prompts for the owner's ChatGPT Work
and Claude sessions, a repository map, scope boundaries, validation contract, safety/privacy guidance
and a proposal-reconciliation template. Parallel sessions should return independent proposals only;
choose one sport, exact mount and measurement contract before implementation.

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
- **CI:** `ci.yml` runs `./gradlew test` (pure-JVM cores); `android.yml` runs Android unit tests and
  builds `assembleDebug`; `release.yml` builds + attaches signed APK/AAB artifacts to a GitHub Release
  when a `release/<version>` branch is pushed.
- **Signing/versioning:** debug builds use a committed debug keystore for repeatable internal updates;
  release builds require a separate uncommitted keystore and fail closed when it is not configured.

---

## 3. Done

### 3.1 Stability (shipped & install-verified path)
- **Onboarding DB crash** → resilient DB open: if a legacy schema can't migrate, the app blocks at a
  recovery screen instead of crashing or starting over. `allowBackup=false` so stale DBs can't be
  restored onto a fresh install.
- **`MainShell` `NoSuchFieldError`** → the nav object `R` collided with the generated resources `R`;
  renamed to `Routes`.
- **"App not installed"** → committed debug keystore (stable signature) + versionCode bumping.
- **DB-recovery safety (2026-09-15):** the Phased Implementation Plan flagged the former
  self-healing rebuild as unsafe for production ("must never silently delete athlete history").
  `fallbackToDestructiveMigration()` is removed; a genuine open/migration failure is copied to a
  timestamped private recovery directory before the app shows a blocking recovery screen. The app
  never deletes or replaces the live database automatically. Reset is available only after a verified
  backup and an explicit athlete confirmation; the preserved backup remains on-device and the reset is
  disclosed once on the next usable launch. The full Phase-0/1 contract system from the blueprint
  (stable IDs, the observation-resolution model) is *not* retrofitted here — see §4.6.

### 3.2 Phases 1–3 — the core app (code-complete, CI-green)
- **Phase 1:** onboarding flow, profile/identity, rigs + poundage estimator, Home, Train setup,
  Settings, Hyle UI atoms, handedness normalisation wired into capture.
- **Phase 2:** wellness + life layer (check-ins, mood, life events, cycle, medication, events),
  load/ACWR/streak/readiness, Calendar tab, the "+ Log" surface, pre/post-session check-ins.
- **Phase 3:** 52-region body atlas, pain logging, injuries (CRUD, auto-link), physio plans/sessions,
  the Tink streaming-AEAD **document vault** (encrypted at rest, with SAF and in-app camera import),
  readiness v3 (injuries).

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
- **Manual Recurve scoring wedge:** Quick Score, WA round packs, custom practice, numeric keypad,
  synchronized target plotting, totals/set points/PBs, basic grouping, observer input, interruption-safe
  resume, scorecard correction/retraction, and form-session linking.
- **AI coach:** BYOK key vault (Tink Aead + Keystore); `AiSettings` (model choice, medical-grant &
  keep-private toggles, on-device model path); cloud clients (Anthropic Messages / OpenAI chat /
  Gemini generateContent / DeepSeek); on-device client via MediaPipe LLM Inference; `CoachViewModel` +
  `CoachScreen` (offline insights always; grounded "Ask"; provenance-coloured model picker; a
  "what wasn't sent" report).
- **Phase 5 export:** `AndroidKeyProvider` (Keystore identity/signing), `ExportViewModel` (builds a
  signed `.croc` sharing archive from only the consent-included tables), `ExportScreen` (live consent
  preview + SAF/share write). Existing `.crocbak` recovery archives remain checksum-compatible.
- **Phase 4 tuning UI:** `AdvancedTuningSection` wired into the rig editor — brace/tiller/plunger/
  stabilizer/arrow, with live FOC/GPP and validation warnings.
- Wiring: nav routes, Home "Coach" card, Settings rows, INTERNET permission (BYOK cloud calls only).
- **Android verification:** Robolectric covers the complete V1→V8 migration DDL and recovery backup
  preservation; local debug and minified signed release APK/AAB builds pass.
- **Release-candidate verification (2026-09-15):** the root JVM suite plus Android debug/release
  reports contain 178 primary test cases with zero failures/errors; `lintRelease` has zero errors
  and zero warnings or informational notices; the QA-key APK
  passes APK Signature Scheme v2 verification, the AAB passes `jarsigner`, and the package metadata
  is `targetSdk 35` / `minSdk 26`. The bundled
  BlazePose asset is SHA-256 pinned and verified before packaging. These are artifact gates, not a
  substitute for physical-device or range acceptance.
- **Current-checkout refresh (2026-09-16):** the root suite and Android debug/release unit-test
  variants pass with 178 tests and zero failures/errors. A rebuilt debug APK confirms the final
  manifest hardening (`allowBackup=false`, cleartext disabled, explicit backup rules, and restricted
  FileProvider). The corrected minified QA APK/AAB now also build reproducibly and pass APK v2/
  `jarsigner` verification; the release workflow must still rebuild them with the real production
  key before publishing.
- **API-35 ARM64 smoke (2026-09-16):** the current minified QA APK installed and launched on an
  API-35 ARM64 emulator. Onboarding completed to Home; Progress, Train, Body, Calendar, and Settings
  rendered; force-stop/relaunch returned to Home with the profile retained; the bundled pose model
  initialized; and Start recording → Stop & analyze handled an empty camera frame without a crash.
  This is a wiring smoke test, not a substitute for the physical-device, camera, range, interruption,
  and 30-session acceptance matrix.
- **Hardening continuation (2026-09-18):** scoring now exposes the persisted session context fields
  (sight mark, venue, conditions and training intent), keeps its mode strip usable on narrow screens,
  validates scorecard links against athlete ownership/deleted sessions, and validates/caps/replaces
  only unresolved End Scan proposals. Lifecycle cancellation is no longer surfaced as an app error.
  Vault viewer decryption is staged and atomically published, and model removal is constrained to the
  app-private model directory. Android debug/release unit tests and `lintRelease` pass; the rebuilt
  debug APK is APK-signature verified. The added model-path regression is included in the current
  matrix, bringing the root-plus-two-Android-variants total to 180 test cases, all passing.
- **Equipment continuity audit and handoff integration (2026-09-18):** the existing Recurve equipment
  work remains intact in `RigTuning`/`ArrowDto` and the advanced rig editor: arrow spine, length, point
  mass, total mass, balance point, vane count, FOC/GPP derivation and tuning warnings are represented
  and tested. The validated `feat/immersive-visuals` handoff is now carried into this checkout as the
  separate generic arrow-reference studio: five modular GLB families, eight selectors, named-part
  focus/separation, local-only WebGL2 loading, semantic IDs, provenance metadata, deterministic
  generator/contract tests and committed Chromium evidence. It remains generic reference material,
  not an owner-approved arrow or a compatibility/catalog claim.
- **Final release-candidate gate (2026-09-20):** `assembleRelease`, `bundleRelease`,
  `testReleaseUnitTest` and `lintRelease` pass for `v0.6.0-rc.2` with the temporary QA-only
  keystore. The real-device instrumentation smoke contract passes 2/2 on API-35; the minified
  release APK also cold-starts directly with a live process and a clean isolated fatal/class-loading
  log. The APK passes APK Signature Scheme v2 and ZIP alignment verification, and the AAB passes
  `jarsigner`. Final QA artifact digests are APK
  `3aefac4f4138de33313c10cc8b0dd9efe02370b619c5eb74e0c5b23525a66e85` and AAB
  `2b43deecf044d7ec2f8b4c430c4390a59fb72d643b4602a65c94e4adc7d1eeb7`. The production key remains
  a CI/release-owner input; these artifacts must not be distributed.
- **Release-variant instrumentation and signer gate (2026-09-21):** the minified release
  instrumentation task passed 3/3 on the API-35 ARM64 emulator with an explicitly QA-only signing
  substitute. Release CI now runs that task after production signing, and its preflight compares the
  expected certificate fingerprint in both APK and AAB. This is stronger runtime evidence, but does
  not replace the production-key or physical-device gates.
- **Asset reproducibility gate (2026-09-18):** the Atelier exporter now canonicalizes float
  attributes and accessor bounds before GLB serialization. Regenerating both the original study
  models and all five arrow-reference families produces a clean worktree; CI and the release job
  fail if checked-in generated assets drift from their generators.
- **Point taxonomy completion (2026-09-18):** the active points family now includes a separately
  named one-piece hardened 416 stainless glue-in study plus an adjustable glue-in bullet and
  individually selectable 10-, 20- and 50-grain removable module studies. Lancaster source URLs,
  explicit Altra/Easton fitment boundaries, generic-topology wording, deterministic output tests,
  and browser-contract requirements are recorded; no compatibility or tuning claim is implied.
- **Fletching taxonomy (2026-09-18):** the active studio now includes a fifth generic family covering
  Mylar/spin-wing, low-profile polymer, shield-cut plastic and turkey feather studies; straight,
  left/right offset and left/right helical mounting; three/four-fletch clocking; wrap presence; and
  a nock-relative reference. Source URLs, independent semantic IDs, deterministic contracts and
  future Chromium checks are recorded. The exact owner arrow still requires reference intake and
  approval.
- **Fletching Android smoke (2026-09-20):** the rebuilt debug APK (`a25c5ec863be8cbe731c508b94e2c9986c104b3574b849682b37e53729124461`)
  installed on the API-35 ARM64 emulator, opened Home → Equipment Atelier through the intended
  card, reached the horizontally scrolled Fletching selector, and rendered the corrected scaled
  taxonomy. The isolated log showed no fatal exception, uncaught JavaScript error, or WebGL error.
  This is emulator smoke evidence; physical-device, accessibility, thermal, battery and range QA
  remain open.

### 3.5 Releases
`v0.4.2` (DB fix) · `v0.4.3` (nav fix) · `v0.4.4` (install/signing fix) · `v0.5.0-everything`
(all of the above in one APK) · `v0.5.1` (reconciled onto `main`'s `crocodyl.engine` package rename).
The current hardening branch keeps the development default **`0.6.0-spec-dev`** metadata. Its latest
QA-only minified package is **`0.6.0-session-idempotency-qa`** (versionCode 651): APK/AAB preflight
passed, debug/release unit tests passed 44/44 each, `lintRelease` passed, and connected debug plus
minified-release instrumentation each passed 6/6 on API-35 ARM64, including visible-content checks
while traversing every primary tab and the Score/round chooser; Quick Score enters a 10, updates the
total/arrow count, undoes it, and resumes the empty card after Activity recreation. Capture waits
for persisted-session readiness before enabling recording, and permanently denied camera permission
offers Settings with a resume-time permission refresh. Recent hardening closes MediaPipe frame-image
resources, serializes capture-window start/stop against in-flight inference, tests scoring
repository persistence/invariants, and validates release version inputs before they reach shell
commands. The package is signed with the QA/debug key, not the production identity.
Session creation now commits the session and pre-check-in/soreness rows atomically; creation failures
return the user to setup, while optional-context failures leave the session available with a warning.
Failure-injection tests cover rollback both before and during final session insertion, including
check-in and soreness rows. Duplicate start requests are rejected while a start/capture is active.
The QA artifact SHA-256 is APK
`9938ff7038a0a1c21043423eb29e2dcae7f2feebeafdfa1e7bed96987c3fce73`,
AAB `f0b4a05ce8e042b203f34610c7fd691f667c86f2f73a00a54fdfe28bb503edb8`. Public publishing still
requires production signing and full physical-device/range acceptance.

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

### 4.1 Verification (the remaining device/range gate)
The manual scoring wedge is implemented in the current branch, and its persistence/migration paths
are covered by Robolectric. It has not yet passed the physical-device and range acceptance pass.
CI proves the Android app **compiles and packages** — not that it **behaves**. The pure-JVM cores are
unit-tested; an API-35 ARM64 emulator smoke pass now covers onboarding → Home → all main tabs and
relaunch persistence. Full acceptance remains open for physical devices, camera/permissions,
interruption behavior, and the complete Body atlas → coach → export → tuning paths.
Range ergonomics and the executable [release acceptance checklist](docs/crocodyl/RELEASE_ACCEPTANCE_CHECKLIST.md)
remain open; the checklist records the device matrix, resilience cases, 30-session volume, and
sign-off criteria.

### 4.2 Post-RC roadmap — AI coach polish (out of scope for 0.6 release acceptance)
- **On-device model UX:** today the user must supply a Gemma `.task`/`.bin` model file through the
  system picker; import now validates extension/size, hashes and atomically publishes a private
  copy, but a guided licensed download/import flow (and possibly bundling a small default) remains.
- Streaming responses, token/cost display, per-provider model-list refresh.

### 4.3 Post-RC roadmap — design system (out of scope for 0.6 release acceptance)
- Wire **`dev.aarso:hyle`** as a real published dependency instead of ported token constants.
- Hand-drawn body-atlas SVG art to replace the schematic rounded-rects (the integrity suite is the
  contract; the override seam is ready).

### 4.4 Deferred within Phases 1–3 (logged; out of scope for 0.6 release acceptance)
- Hilt DI (kept manual to de-risk blind compilation).
- Broader ViewModel/UI suites remain open. Room-backed scoring now has focused repository tests for
  reopen/resume persistence, triple-face validation and immutable finished scorecards; the generated
  Room-schema/privacy boundary is covered by `PrivacyRegistrySchemaTest`, and migration/recovery by
  `AppDatabaseMigrationTest`.
- Accessibility and visual polish beyond the current streak glyph strip and sRPE secondary load lane.

### 4.5 Future phases (roadmap, not yet built)
The full spec runs to ~Phase 12. Notable future work: validated form capture, End Scan, richer tuning
history, richer export/import & coach↔athlete sharing (Phase 5 continuation), the full Progress/
stability-trends surface, and a Wear OS companion (Phase 12). These are beyond the current manual
scoring + AI-coach + Phase-4/5 foundation.

### 4.6 Delta vs. the new blueprint (2026-08-12)

On 2026-08-12, Madhav pushed a Product Blueprint v2.1 + Phased Implementation Plan into this repo via
a ChatGPT-driven agent (`agent/crocodyl-chatgpt-sync` / `agent/crocodyl-package-export`, reconciled
into PR #4). It's a genuine strategic document, not mechanical — it repositions Crocodyl as a general
"sovereign athlete performance system" (Olympic Recurve first, more sports later) and specifies a much
larger surface than what's built so far. Concretely, **not yet built**, per that blueprint:

- **End Scan** — editable automatic post-end target-photo scoring.
- **Live Observer** — tap/constrained-voice scoring during form capture; **Paired Target Cam** later.
- **Coach workspace** — a paid, multi-athlete surface (roster, report review, notes, assignments).
- **Full static web report viewer** — the current bounded local viewer reads manifests and table-row
  previews, but full report/analysis rendering is still future work.
- **Complete `.croc` exchange** — signing and local verification are implemented; public-key
  identity pinning/TOFU, import/merge, deduplication/conflict handling and coach round-trips remain.
- **Equipment catalog/commerce** — provenance-rich catalog, wear forecasts, upgrade evidence,
  affiliate-link laws (commerce must never steer evidence/ranking).
- **Fuller training/recovery system** — richer plans, goals, habits, injury-risk context, and
  conservative recovery guidance beyond the current foundation.
- **A formal longitudinal observation-resolution contract**
  (`SHOT_CONFIRMED`/`SHOT_INFERRED`/`END_ONLY`/`SESSION_ONLY`/`DAY_WINDOW`/`PERIOD_WINDOW`) — stricter
  than what the current wellness/coach cores implicitly assume. A design constraint for future work,
  not retrofitted onto Phases 1–3 now.

The remaining blueprint items are still additive roadmap work on top of the current scoring and
recovery foundation; they are not implied by the current `0.6.0-spec-dev` build.

### 4.7 Owner to-dos (Madhav)
- Review the `codex/production-hardening` changes after Android CI/device QA, then merge them into the
  chosen default branch. The source branch remains `claude/form-analyser-baseline-split-e9s6lr`; the
  older PR #3/#5 snapshots are historical and should remain closed/superseded.
- Approve/edit the blueprint's "governing decisions" (§4.6 above; full list in
  `docs/crocodyl/blueprint/01-product-direction.md`) — the blueprint itself calls these "proposed,"
  governing only after owner approval.
- Confirm the DECISION-21 OTF constants (shipped as defaults 2.0 lbs/in draw, 1.0 lb/in riser).
- Baseline repo: the Crocodyl spec + phase briefs are committed there (PR #5 on `baseline`, draft) —
  separate from this repo's own PR #5.

### 4.8 Ledger carried over from `feat/immersive-visuals` (merged 2026-10-22)

The September visual/feature branch kept its own remaining-work ledger. It is retained here
verbatim apart from the voice-scoring note above, because most items are still open and none of it
is superseded by the hardening branch's list.

#### Verification gates (intentionally not claimed by CI)

- On-device behavioural, rotation/foldable, camera, microphone, thermal, battery and storage QA.
- Range validation of pose metrics, shot segmentation and all target-photo scoring behavior.
- Athlete/coach comprehension, accessibility and sunlight/glove testing.
- Clinical/content review of recovery, pain, injury and return-to-training guidance.

#### Remaining product implementation

- **Automatic End Scan vision:** the current flow is calibrated photo marking, not an arrow detector.
  Perspective correction, automatic impact candidates, drag/add/remove correction and a measured
  device/target/lighting envelope remain.
- **Voice command depth:** the command grammar, repeat, undo, skip, finish-end and audited
  correct-last are implemented and unit-tested, along with an on-device-only recogniser wrapper.
  The push-to-talk control is **not wired into the scoring screen** because it needs
  `RECORD_AUDIO`, which the release manifest contract does not allow; enabling it is a deliberate
  change to that contract. Range false-acceptance validation remains.
- **Synchronized media review:** durable shot-to-video timing, multi-clip phase-aligned replay,
  explicit per-clip deletion and opt-in 7-day/30-day/forever retention are built.
- **Human Coach product:** roster, athlete inbox/detail, notes, assignments, acknowledgement,
  coach-local score book, entitlement and retention controls remain. The existing “Coach” is the
  athlete's AI/rule coach, not this paid human workspace.
- **Organiser/referee and spectator display:** competition control, casting/external-display layout,
  automatic form filling and offline tournament workflows remain.
- **Static local web viewer:** local `.croc`/`.crocbak` inspection and report rendering in a PWA is
  not implemented.
- **Exchange trust UX:** `.croc` signing/verification, Pairing Cards, TOFU pinning, key-change
  quarantine and pre-import new/identical/conflict inspection now exist. Explicit athlete
  switching remains.
- **Device ecosystem:** Garmin, Wear OS, Health Connect, Bluetooth sensors and Steady Aim A1 Pro are
  not integrated. These require their respective SDK/protocol work and hardware testing.
- **Baseline seam:** Crocodyl now owns a versioned, typed, per-observation consent-filtered
  `InsightInput` adapter and constrained `Insight` presentation contract. Baseline-specific engines,
  estimators and paid analytics remain outside this repository.
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

#### Repository decisions (as recorded on the branch)

- Merge PR #11 after review; it supersedes the stale implementation claims and visual branch work.
- Confirm DECISION-21 OTF constants (current defaults: 2.0 lbs/in draw, 1.0 lb/in riser).
- Keep EEG/bow-IMU and paid Baseline analytics out of this repository; exchange only typed,
  consent-filtered observations across the future adapter.

### 4.8 Multi-platform porting (plan only)
See [`docs/crocodyl/CROCODYL_PORTING_PLAN.md`](docs/crocodyl/CROCODYL_PORTING_PLAN.md): nothing is built; its proposals await the owner.

---

## 5. Known caveats
- **Android release boundary:** the current tree has passed Android debug/release unit tests, release
  lint, QA artifact preflight and API-35 ARM64 debug/minified-release connected smoke. Treat the
  QA-signed `0.6.0-scoring-repository-qa` package as validation evidence, not a shipped release, until
  production signing and physical-device gates are complete.
- **Dependency pins:** the Android/MediaPipe stack is intentionally held for this release; upgrade
  it as a compatibility-tested maintenance change, not opportunistically during release hardening.
- **BYOK required for cloud "Ask":** the offline rule-coach needs nothing; cloud insights need a key,
  on-device insights need an installed model.
- **No full device/range acceptance yet:** the API-35 ARM64 emulator smoke pass is clean, while
  camera, permissions, interruption behavior, and range ergonomics still need evidence across the
  supported device floor and the 30-session acceptance volume.
- **R8 optimization is intentionally disabled:** release shrinking, obfuscation, and resource
  shrinking remain enabled, but aggressive code optimization is disabled because the current
  MediaPipe/Compose graph exceeded a practical local build bound. Re-enable it only as a measured,
  compatibility-tested R8/AGP maintenance change.

---

## 6. TL;DR
Phases 1–3 + the AI coach + Phase-4 tuning + Phase-5 export + the manual scoring wedge are **built and
pure-JVM/Android-test green**, with a fresh QA-validated **0.6.0-scoring-repository-qa** artifact. What
remains before public release is **production-key signing, physical-device/accessibility/thermal/
battery verification, camera/interruption/range acceptance, and owner sign-off**. Guided on-device-model distribution, the
real Hyle dependency, and the deferred/future-phase work above are explicitly post-RC roadmap items,
not hidden release blockers. The paid Baseline channels (EEG, bow-IMU, advanced analytics)
stay out of Crocodyl's git history — that separation is unchanged — though Baseline itself is now
understood to be open-source, gated by entitlement rather than by a closed repo (§1).

A much larger product blueprint (Product Blueprint v2.1 + Phased Implementation Plan, in
`docs/crocodyl/`) now governs where Crocodyl is headed — manual scoring, End Scan, Live Observer, a
paid Coach workspace, full report rendering, complete `.croc` exchange, and more (§4.6). Everything in
this file is the *implementation* snapshot underneath that direction, not a competing plan.
