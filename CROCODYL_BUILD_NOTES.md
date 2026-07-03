# Crocodyl build notes (Phases 1–3)

Consolidated notes for the multi-phase build. (Deviation from the per-phase `PHASEn_NOTES.md`
convention: this is one autonomous build across Phases 1–3, so notes are consolidated here.)

## Recon findings

- **Repo/branch:** app lives on `claude/form-analyser-baseline-split-e9s6lr` (PR #1, draft). Building
  continues on that branch (not per-phase branches) so progress is continuous without merge gates.
  `main` is not yet the default branch; merging/default-branch changes are left to Madhav.
- **Modules (before):** `engine` (pure JVM), `archery-module` (pure JVM), `app-android` (gated behind
  `-PwithAndroid`; needs Android SDK). Kotlin 2.1.0, AGP 8.7.3, Gradle 8.14.3, JDK 21.
- **Room (app):** `AppDatabase` v1, entities `athletes`/`sessions`/`shots` with **String (UUID) PKs**
  (not the integer PKs the briefs' SQL assumes) and `session.drawWeightLbs`/`distanceMeters` columns.
- **Pose space:** MediaPipe **normalized image landmarks** ([0,1], y-down) — so the handedness mirror
  is `x' = 1 − x` (§4 table row 1).
- **DI:** none present. Nav: `navigation-compose` present (single activity, NavHost).
- **Verification limit:** no Android SDK in the build container → pure-JVM modules are tested locally
  (`./gradlew test`); the Android app + Room migrations + UI compile only in CI (`android` workflow).

## Done this pass — pure-JVM engine cores (all unit-tested, CI-green)

These are the headless, correctness-critical foundations of Phases 1–3.

**Phase 1 core**
- `core-model`: `Handedness {RH,LH}`, `BowType`. (Canonical app-wide handedness; the archery pipeline
  keeps its internal `pose.Handedness {RIGHT,LEFT}` — deviation logged below.)
- `archery-module`: `HandednessNormalizer` (single mirror point, `x'=1−x` + L/R swap table) +
  `EffectiveHandedness` resolver. Tests: swap-table completeness, involution, non-identity,
  resolver precedence, and the **feature-invariance exit test** (LH twin ⇒ identical features + phases).
- `core-equipment`: `PoundageEstimator` (ILF convention, DECISION-21 constants, measured>estimated>marked
  precedence, compound never estimates) + tests.

**Phase 2 core**
- `core-wellness`: `LoadModel` (shot/srpe load, poundage fallback chain), `Acwr` (EWMA + warm-up gate +
  chronic guard + zones), `StreakEngine` (grace/break/hiatus/provisional-today), `DurationModel`
  (idle-trim gap cap), `Readiness` (v2 cascade + reason accumulation, life-event context not content),
  `CycleEstimator` (median/MAD, gated), `PrivacyRegistry` (SHAREABLE/MEDICAL/PRIVATE). Full test suite.
- `core-body`: 52-region `RegionIds` contract + handedness-aware `SorenessChipResolver` + tests.

**Phase 3 core**
- `core-body` already carries the 52-region contract (Phase 3's appendix list). Atlas *geometry*
  (SVG paths, hit-testing) uses `android.graphics` → lands in the app module (pending, CI-verified).

## Deviations from the briefs (R1/R2)

1. **Handedness enum:** briefs specify `Handedness {RH,LH}`; the incumbent archery pose code already
   has `pose.Handedness {RIGHT,LEFT}`. To honor the "don't modify existing archery src" guardrail, the
   canonical `{RH,LH}` lives in `core-model`; the pose enum stays as an internal segmenter detail.
   Post-normalization the segmenter always runs right-handed, so the two never conflict.
2. **PoundageEstimator location:** brief puts it in `app-android/domain` for Phase 1; placed in the new
   pure `core-equipment` module instead so it's unit-testable headlessly and matches the Phase 4 target.
3. **PrivacyRegistry table names:** registered with the spec's canonical (singular) logical names; the
   app-layer Robolectric reflection test will reconcile these with actual Room `tableName`s (some are
   historically plural: `athletes`/`sessions`/`shots`).
4. **Integer vs String PKs:** the briefs' migration SQL assumes integer PKs; the actual schema uses
   String UUID PKs. The Android migration (pending) will adapt (nearest-faithful) — rig/session FKs
   become String to match.

## Phase 1 app — increment 1 (data foundation, CI-verified)

Landed (additive, incumbent manual-DI kept to de-risk blind compilation):
- Room **V1→V2 migration**: rig table + athlete columns (handedness, drawLengthMm, avatarSeed, club,
  pubkey) + session columns (rigId, handednessOverride), with a per-athlete default-rig backfill
  (tuning seeded from the latest session's draw weight) and sessions repointed. `RigEntity` + `RigDao`
  (transactional single-active) + repository rig methods.
- `TuningV0` (kotlinx.serialization) + `Tuning.effectivePoundage()` bridging to `core-equipment`.
- `AppPrefs` (DataStore) for the Appendix-A keys.
- **Handedness normalization wired** into the analysis path (`SessionViewModel` normalizes the captured
  pose by the athlete's handedness before segment/extract).

Deviations logged: **Hilt deferred** (kept manual DI — a blind Hilt refactor is high-risk without a
local SDK; will adopt in a verified pass); **exportSchema stays false** for now (enable with the ksp
schema dir when verifiable); Robolectric migration/VM tests deferred to the same verified pass.

## Phase 1 app — increment 2 (UI, CI-verified)

Landed: Hyle atoms (`HyleSegmented`/`HyleStepper`/`HyleListRow`/`HyleSectionHeader`/`HyleEmptyState`)
+ deterministic `HyleAvatar`; **onboarding** flow (role → name/avatar → handedness → draw length →
rig v0 with live OTF estimate → permissions primer → commit); **nav restructure** (onboarding gate
via DataStore, bottom-bar shell Home/Train/Progress/Body/Calendar with ComingSoon stubs, settings
graph); **Home v1** (header/avatar, active-rig row, Start, recent sessions → reopen review);
**TrainSetup** (rig chip replaces the draw-weight field + per-session handedness override);
**Settings** (profile, rigs list + RigEdit, capture, appearance, data-wipe, about). Session start now
resolves poundage from the active rig; handedness override flows into normalization.

Deviations (logged): flat `ui` package kept (no feature-package split — less blind-move risk); no
Hilt (manual DI); Home **StabilitySpark** card omitted for now (needs per-session baseline compute);
settings sub-screens use system back (no custom back affordance yet); Robolectric onboarding/VM tests
deferred to a verified pass.

## Then: Phases 2–3 app layers

## Pending — the later Android app layers (CI-verified, next tranche)

Not yet built (require the Android SDK / only compile in CI):
- **Phase 1 app:** Hilt DI, Room migration (rig table + athlete columns + backfill), onboarding flow,
  nav restructure into feature packages, Home v1 cards, Settings skeleton, rig v0 UI, Hyle atoms +
  `HyleAvatar`, contextual camera permission, wiring the normalizer into the live capture path.
- **Phase 2 app:** check-in flows, calendar tab, life layer UI, schema V→V+1 for wellness tables,
  `PrivacyRegistry` reflection test.
- **Phase 3 app:** atlas geometry + hit-testing, body tab UI + encodings, Tink document vault,
  injuries/physio CRUD, readiness v3 (injuries).

## Open questions for Madhav

1. **DECISION-21** OTF constants (2.0 lbs/in draw, 1.0 lb/in riser) — shipped as named defaults in
   `PoundageEstimator`; confirm/adjust from coaching ground truth.
2. **PR strategy** — everything is stacking on the current branch/PR. Happy to keep it as one growing
   PR, or split per-phase once you're merging.
3. Android app layers will be built + verified via CI iteration (no local SDK); flagging that the pace
   there is CI-round-trip-bound, unlike these locally-verified cores.
