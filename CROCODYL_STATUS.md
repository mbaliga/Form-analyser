# Crocodyl — Status & Vision

_Last updated: 2026-09-09 · current build: **v0.6.0-spec-dev** (versionCode 6)_

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

- **Stack:** Kotlin 2.1.0, AGP 8.9.1 (bumped from 8.7.3 this pass to match the `dev.aarso:hyle`
  composite build — see §4.3), Gradle 8.14.3, JDK 21, Jetpack Compose (Material3), Room 2.6.1 + KSP,
  DataStore, kotlinx.serialization, CameraX 1.4.1, MediaPipe tasks-vision + tasks-genai, Tink 1.15.0,
  Hilt 2.52 (DI — see §4.4), Robolectric 4.16.1 (local unit tests — see §4.4).
- **DI:** Hilt (this pass — see §4.4). **ViewModels:** `@HiltViewModel` + `@Inject constructor`, still
  manual `load()` (converting the refresh pattern to reactive Room `Flow`s was out of scope for a DI
  pass and is a separate, larger change).
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
  the **`.crocbak`** archive manifest (clock injected, round-trips), pubkey identity + `KeyProvider` seam,
  and the signed **`.croc`** exchange envelope: `Signer`/`Verifier` seams (ECDSA P-256 / SHA-256 over
  plain `java.security`, so a recipient needs no Android and no third-party crypto), `CrocManifest`
  (clock injected, sender pubkey bound inside the signature), a name-binding `PayloadChecksum`,
  TOFU trust evaluation, and a fail-closed `CrocVerifier` that re-runs the PrivacyRegistry law on the
  *recipient's* side. 26 unit tests cover seal/verify, tamper, forged key, downgrade, and the refusal
  of a validly-signed envelope that claims a PRIVATE table.
- **`core-equipment` Phase-4 tuning** — `TuningSpec` (versioned, all-optional), FOC/GPP/KE math,
  range-validation warnings. `PoundageEstimator` untouched.
- ✅ The privacy invariant was independently verified: PRIVATE data cannot reach a cloud model or an
  export, and MEDICAL is grant-gated.

### 3.4 Wave 2 — Android layers over the cores (CI-compiled & packaged)
- **AI coach:** BYOK key vault (Tink Aead + Keystore); `AiSettings` (model choice, medical-grant &
  keep-private toggles, on-device model path, a token-usage ledger); cloud clients (Anthropic
  Messages / OpenAI chat / Gemini generateContent / **DeepSeek chat**, OpenAI-shaped); on-device
  client via MediaPipe LLM Inference; `CoachViewModel` + `CoachScreen` (offline insights always;
  grounded "Ask"; provenance-coloured model picker; a "what wasn't sent" report). See §4.2 for the
  streaming/cost/discovery/on-device-install layer added on top of this in the current pass.
- **Phase 5 export:** `AndroidKeyProvider` (Keystore identity, and now a real `sign()` over the
  existing non-exportable P-256 key — no migration, the key always had `PURPOSE_SIGN`),
  `ExportViewModel` (builds the `.crocbak` zip **or** a signed `.croc` envelope from one shared
  consent decision), `ExportScreen` (live consent preview + SAF write + share, both formats),
  `ImportViewModel`/`ImportScreen` (**verify-only** `.croc` preview — checks the signature and lists
  the contents; writes nothing, because merge/dedupe rules do not exist yet). Not device-verified:
  CI compiles it, nothing here can run it.
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

### 4.2 AI coach polish — streaming, cost, discovery, on-device install (this pass)

All four items this section used to list as pending are now implemented; CI-compiled only, **not
device-verified** (no Android SDK / socket in this environment — same caveat as the rest of the
Android layer, §5).

- **Streaming.** `LlmClient` gained a defaulted `supportsStreaming(model)` / `stream(request, sink)`
  seam (a push-based `StreamSink`/`StreamDirective`, not `Flow` — `core-coach` stays coroutine-free
  by design) plus a shared `StreamAssembler` (buffer + "last-known-usage-wins" + cancel latch) and an
  `SseDecoder` line-format decoder, both unit-tested in `core-coach`. Anthropic, OpenAI, DeepSeek
  (sharing one `OpenAiCompatStream` helper — DeepSeek's API is OpenAI-shaped) and Google now override
  `stream()`; a provider that doesn't (on-device, for now — see below) falls back to the interface's
  one-shot bridge automatically. `CoachViewModel` publishes a throttled `CoachAskState.Streaming`,
  supports cancel-in-place (`cancelAsk()`, a `@Volatile` flag — not coroutine `Job` cancellation,
  which would kill the code that publishes the partial result), and retries once via `complete()` if
  a provider answers "invalid request" naming `stream` before any text arrived (some OpenAI orgs are
  gated out of streaming). On-device streaming is deliberately deferred (MediaPipe's async listener
  API and delta-vs-cumulative partial semantics could not be verified without `tasks-genai`/an SDK).
- **Token/cost display.** `ModelPricing`/`CostEstimator`/`UsageLedger` (pure, tested in `core-coach`):
  ON_DEVICE is always `NoCharge`; a model with no citable published rate is `Unknown`, never a
  guessed price — which is the honest state for **most of today's registry** (no rate this codebase
  can currently cite for `claude-opus-4-8`, `gpt-5`, `deepseek-v4-*`, etc.; they show "tokens only"
  until real, sourced rates are filled in). Running totals persist as JSON in `AiSettings`
  (`usage_ledger_json`) — deliberately **not** a Room table (no PrivacyRegistry entry needed, holds
  only model ids/counters/a start date, structurally unreachable by `ConsentFilter`/`.crocbak`); if it
  ever becomes one, the call recorded in its KDoc is PRIVATE with the nullable-timestamp reset idiom,
  not `DELETE`. Surfaced on `CoachScreen`'s response footer and an AiSettings "Usage" section
  (per-model totals, an aggregate that reports unpriced asks rather than hiding them, Reset).
- **Per-provider model-list refresh**, scoped honestly as an **entitlement check**, not a catalog
  import (`ModelDiscovery`, AiSettings): badges each curated model AVAILABLE / NOT_LISTED_BY_PROVIDER
  / UNKNOWN against what the athlete's key can currently reach, never adds an id to the picker on its
  own, fetches no price (no provider exposes one), and is manual-only — the athlete presses "Check
  available models," it never runs on launch or on a schedule. Doubles as the honest API-key test the
  app otherwise lacked.
- **Guided on-device model install.** `ModelInstall` is now a staged installer — copies to `<name>.part`
  through a `DigestInputStream` (SHA-256), throttled progress, a free-space precheck, a ZIP-magic sniff
  for `.task` bundles, a minimum-size check, an optional athlete-pasted expected-SHA-256 check, an
  atomic `renameTo` onto the final name, and then a real `LlmInference` load **probe** before reporting
  success — fixing a real bug in the prior version, where a copy that failed partway left a truncated
  file at the *final* path that `isInstalled()` reported as installed. Install/remove now also close
  the app's one running on-device engine first (`OnDeviceEngineRegistry`), fixing a second bug where a
  removed/replaced model file could be left open by the already-built `OnDeviceLlmClient`. No bundled
  default model and no in-app download (both deliberately rejected — APK size and Gemma redistribution
  obligations); the guided part is AiSettingsScreen text pointing at the official licensed listing, an
  optional expected-SHA-256 field (no vendored hash list — one would go stale), and live progress
  through verify/probe. Model-file removal stays a hard delete, not the nullable-`deletedAt` idiom —
  a downloaded weights file is a reproducible artifact, not athlete history.

### 4.3 Design system
- **`dev.aarso:hyle` is now wired as a real dependency** (this pass) — CI-compiled only, **not
  device-verified**: `hyle-design-system` is a git submodule (`mbaliga/Hyle-Design-System`, pinned
  commit) brought in via Gradle `includeBuild`, gated behind the same `-PwithAndroid` flag as
  `:app-android` (mirrors how `:crash-recovery` was once wired the same way — see
  `CROCODYL_BUILD_NOTES.md`), with AGP bumped 8.7.3 → 8.9.1 to match the submodule's pin (composite
  builds need identical AGP everywhere). `ui/theme/Theme.kt`'s `object Hyle` now sources every
  color/duration constant from `dev.aarso.hyle.tokens.HyleTokens` / `dev.aarso.hyle.RadiantHues`
  instead of hand-copied hex literals — same values, real source. Two things this pass did **not**
  do, on purpose: (1) `:hyle`'s own `minSdk` is 31, above this app's 26; a
  `tools:overrideLibrary="dev.aarso.hyle"` in `AndroidManifest.xml` papers over the manifest-merge
  failure, justified because `:hyle`'s Android surface is plain Kotlin constants/sealed
  types + two resource files with no platform-API calls — but this is a judgment call from reading
  the module's source, not a device-verified one, and is worth someone double-checking; (2) `:hyle`
  itself is still "deliberately pure data" upstream (its own README: "the Compose Modifiers and
  AGSL shaders land next") — there is no real `Finish`/`Pulse`/`Provenance` Compose bridge to adopt
  yet, so `HyleAtoms.kt`'s composables and the body-map encoding hexes in `BodyAtlasCanvas.kt`
  (the Crocodyl briefs' body-map law, never part of this port) are untouched.
- Hand-drawn body-atlas SVG art to replace the procedural placeholder (the integrity suite is the
  contract; the override seam is ready) — **still pending, needs a human illustrator**. This pass
  upgraded `BodyAtlasCanvas.kt`'s placeholder from plain rounded-rects to parameterized `Path`
  outlines (tapered limb bands, teardrop shoulder/pec/lat caps, flat-edged glute/hip/trap domes,
  oblique/rhomboid lenses, joint blobs) that read as rough muscle silhouettes — still procedurally
  generated from a handful of Bezier archetypes, not commissioned art, and CI-compiled only, **not
  device-rendered/verified** here (no Android SDK in this environment). `BodyAtlas.kt`'s rectangles
  (position, mirroring, no-overlap, finger-sized targets, 52-region coverage) and
  `AtlasIntegrityTest` are untouched and still green — hit-testing resolves to the rectangle, not the
  drawn silhouette.

### 4.4 Deferred within Phases 1–3 (logged)
- ~~Hilt DI (kept manual to de-risk blind compilation).~~ Wired this pass, **CI-compiled only, not
  device-verified** (no Android SDK in this environment): the Hilt Gradle plugin + KSP compiler (no
  kapt — Hilt has supported a kapt-free KSP compiler since 2.48), an `@HiltAndroidApp CrocodylApp`
  (`AndroidManifest.xml`'s `android:name`), and `MainActivity` as `@AndroidEntryPoint`. All 15
  `AndroidViewModel`s (every screen's, plus the End Scan/Live Observer state on `ScoringViewModel`
  and the croc-exchange `Export`/`ImportViewModel`s added earlier this pass) are now `@HiltViewModel`
  with `@Inject constructor`s, resolved via `hiltViewModel()` in place of the old `viewModel()` /
  `viewModel(factory = ...)` calls. The shared repositories/services they used to construct inline
  per-VM (`Repository`, `AppPrefs`, `AthleteFeatureRepository`, `ScoringRepository`, `KeyVault`,
  `AiSettings`, `Vault`, `AndroidKeyProvider`) are now `@Inject`-constructible themselves (most
  `@Singleton`, shared once across every ViewModel rather than one instance per screen — no
  behaviour change, since the Room DB / DataStore / Keystore-backed keysets underneath were already
  process-wide singletons). `CoachViewModel`'s old hand-rolled `ViewModelProvider.Factory` — needed
  because it built a model→client resolver that plain `viewModel()` couldn't parametrize — is gone;
  the object graph it built is now a plain injectable `CoachLlmRouter` class (`app/ai/`) plus one
  `@Provides` method for the on-device runtime (`app/di/CoachModule.kt`, the one piece needing a
  `modelPath` lambda + a registration side effect rather than a bare constructor). The manual
  `load()`-after-mutation refresh pattern is untouched — that is a separate, larger change (reactive
  Room `Flow`s throughout), not part of a DI pass. Nothing here compiles Android code locally (no
  SDK); CI (`android.yml`'s `assembleDebug -PwithAndroid`) is the actual judge, same as every other
  Android-layer change in this project.
- Robolectric tests (migrations, PrivacyRegistry reflection, VM suites) — need the Android SDK.
- ~~Streak week-strip glyphs, sRPE secondary load lane, in-app document camera capture.~~ Built
  this pass, **CI-compiled only, not device-verified** (no Android SDK in this environment): the
  Calendar tab's streak strip now renders 7 glyph dots (`CalendarScreen.WeekStrip`, driven by
  `WellnessAssembler.weekFacts` + the same `StreakEngine.qualifies` rule the streak count uses)
  instead of a text-only summary; the Load view now has a second chart for the sRPE lane
  (`Acwr.computeSrpe` — the EWMA/warm-up/zone math was already generic over which `DailyLoad`
  field it reads, so this is a thin, tested wrapper, not new modelling) alongside the existing
  shot-load one; and the injury editor's "Attach document" now sits next to a "Photograph
  document" button (`ActivityResultContracts.TakePicture` + a second `FileProvider` cache path,
  `BodyViewModel.newCaptureUri`/`importCapturedPhoto`) that feeds the same Tink-vault
  encrypt-then-record path as a SAF pick — same authority, same `Vault.encryptFrom` call, no
  separate lesser code path for a photographed document.

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

- ~~**Manual/plotted scoring**~~ — **built** (predates this pass): `ScoringScreen`/`ScoringViewModel`/
  `ScoringRepository` cover WA Recurve round packs, numeric and plot-to-score input, set matches with
  shoot-offs, totals/set points/PBs. This bullet was stale when written (2026-08-12, before PR #4/#6
  landed) — left here only so the correction is visible rather than silently deleting the record.
- ~~**End Scan**~~ — **built this pass**: camera capture, an on-device target-photo detector
  (`core-scoring`'s `TargetCalibration`/`EndScanDetector`, pure-JVM and unit-tested — reuses the
  existing `PlotPoint`/`scoreFromPlot` geometry rather than new scoring math), and the human-confirm
  propose/confirm/reject review UI that already existed but was never fed. **Not range-validated or
  device-verified** — every detector threshold is a conservative geometric default, not tuned against
  a real target photo; see the code's own KDoc for the documented behavioural limits (off-axis
  capture, low-contrast arrows in the black band, rotational symmetry).
- ~~**Live Observer**~~ — tap scoring already existed; **voice input built this pass** (on-device
  `SpeechRecognizer`, English-only, rejects rather than guesses on low confidence, never bypasses the
  same human-confirm path as everything else). **Paired Target Cam** remains unbuilt/future work.
- **Coach workspace** — a paid, multi-athlete surface (roster, report review, notes, assignments).
- **Static local-only web viewer** for exported files.
- **The rest of `.croc` exchange** — the envelope, signing/verification and a verify-only import
  preview now exist (§3.3/§3.4). Still missing: a durable **TOFU pin store** (every sender reads as
  first contact, so the app cannot yet say "this key changed"), the **Pairing Card / QR identity
  exchange**, **merge, dedupe and conflict resolution** on import, and the coach-side return legs
  (`COACH_ASSIGNMENT`/`COACH_OBSERVATION` are vocabulary in the schema, nothing produces them).
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
- ~~Review/merge PR #4~~ — merged 2026-09-01 (as was the follow-up trust-foundation PR #9). PR #3/#5
  were closed as superseded, as this section previously anticipated.
- Approve/edit the blueprint's "governing decisions" (§4.6 above; full list in
  `docs/crocodyl/blueprint/01-product-direction.md`) — the blueprint itself calls these "proposed,"
  governing only after owner approval. **Still open.**
- Confirm the DECISION-21 OTF constants (shipped as defaults 2.0 lbs/in draw, 1.0 lb/in riser).
  **Still open.**
- Baseline repo: the Crocodyl spec + phase briefs are committed there (PR #5 on `baseline`, draft) —
  separate from this repo's own history. **Still open** — not touched by this pass.
- Range/device validation for End Scan and Live Observer voice (§4.6) — both ship this pass as
  conservative, unvalidated defaults; a real range session against a real target photo/voice is the
  next gate before either is trusted for an authoritative score without heavy manual correction.
- Real, sourced per-model USD/million-token rates for `ModelPricing` (§4.2) — most of today's model
  registry currently shows "tokens only" because no citable rate exists in this codebase yet.

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
in **v0.6.0-spec-dev**, reconciled onto `main` in PR #4/#9. This pass (2026-09-09) added: a signed
`.croc` exchange (§3.3/§4.6), AI coach streaming/cost-display/model-availability-check/guided
on-device install (§4.2), Live Observer voice input (§4.6), an End Scan target-photo detector feeding
the pre-existing human-confirm review UI (§4.6), a real `dev.aarso:hyle` dependency (§4.3), Hilt DI
and Robolectric tests (§4.4), procedural body-atlas silhouettes (§4.3), and the streak/sRPE/document-
camera items (§4.4). None of it is device-verified — CI (`android.yml`) is the first real judge, and a
real range session is the gate before End Scan/Live Observer are trusted for an authoritative score.
What remains before calling the product done: on-device behavioural verification generally, the
Coach workspace, the static web viewer, equipment catalog/commerce, a TOFU pin store and pairing card
for `.croc`, and the rest of §4.5/§4.6/§4.7. The paid Baseline channels (EEG, bow-IMU, advanced
analytics) stay out of Crocodyl's git history — that separation is unchanged — though Baseline itself
is now understood to be open-source, gated by entitlement rather than by a closed repo (§1).

A much larger product blueprint (Product Blueprint v2.1 + Phased Implementation Plan, in
`docs/crocodyl/`) now governs where Crocodyl is headed — a paid Coach workspace, a static web viewer,
and more (§4.6). Everything in this file is the *implementation* snapshot underneath that direction,
not a competing plan.
