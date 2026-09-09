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

## Phases 2–3 app layers (one tranche, CI-verified)

**Cores (locally green):** Readiness **v3** (additive `activeInjuries` param + severity clauses + tests);
`PrivacyRegistry` gains the Phase 3 tables (`document = MEDICAL`); **BodyAtlas** — the 52-region
geometry as rounded-rect regions in the 1000×2000 viewport, author-left/mirror-right, with the full
integrity suite (52 exactly once, exact mirrors, ≥60×60, exact pairwise no-overlap — stronger than
the brief's grid sample, centroid hit-tests). Hand-drawn SVG paths can replace rects later via the
override seam without touching IDs/consumers.

**Phase 2 app:** Room V2→V3 (checkin/soreness/rest_day/hiatus/mood_entry/life_event/cycle_entry/
medication_entry/event + session check-in/duration/arrows columns — table names match the
PrivacyRegistry contract); `WellnessDao`; `WellnessAssembler` (DB → load/ACWR/streak/readiness);
pre-check-in gate in TrainSetup (dials + soreness via chips **or mini-atlas**, skip recorded);
post-check-in sheet on capture stop (RPE CR10, feel, idle-trimmed auto duration + override, arrow
reconciliation); "+ Log" surface (check-in, rest, mood, life event → impact-3 hiatus offer, cycle
(gated), medication, event); **Calendar tab** (month grid + day marks + hiatus band + streak strip +
Load view with weekly bars/ACWR/warm-up state); Home readiness card (shape+luminance ●◐○, quiet
state) + streak line + "+ Log"; Settings: Wellness (chips toggle), Streak & Rest (planned-rest
pattern + hiatus), Cycle (enable + estimate + history), Medication (list).

**Phase 3 app:** Room V3→V4 (pain_log/injury/physio_plan/physio_exercise/physio_session/document);
`BodyAtlasCanvas` (one renderer: violet luminance ramp with exact anchor stops + numeral badges,
cyan 45° cross-hatch for physio, dashed outlines for injuries, selection, tap/long-press
hit-testing); **Body tab** (Today/History-8-weeks/Injuries/Physio views, pain dial 0–10 + quality
tags, region history, physio session ticking); injury CRUD (atlas multi-select, RESOLVED stamps
resolved date, pain auto-links to covering active injury); physio plan editor (targets, days,
exercise rows); **Tink streaming-AEAD vault** (AES256_GCM_HKDF_4KB, Keystore-wrapped, AD = row id,
25 MB guard, view-cache wiped on close/clear) + SAF import + image/PDF viewer; readiness v3 wired;
Body-tab injury badge; vault meter in Settings → Data.

**Deviations (R1/R2, logged):**
- Atlas geometry is schematic rounded-rects (v0), not hand-drawn SVG paths — integrity suite is the
  contract; override seam kept for hand-drawn art later.
- ~~Streak week-strip is a text summary line, not 7 glyph dots (visual polish deferred).~~ Done in a
  later pass: `CalendarScreen.WeekStrip` renders 7 dots off `WellnessAssembler.weekFacts` +
  `StreakEngine.qualifies`. CI-compiled only, not device-verified.
- ~~srpe lane is computed but the Load view shows shot-load bars only (secondary lane
  deferred).~~ Done in a later pass: `Acwr.computeSrpe` (same generic `compute(loadOf=...)` this
  file already had, just called with `it.srpeLoad`) feeds a second weekly-bars chart + ACWR line in
  the Load view. CI-compiled only, not device-verified.
- Physio session logging lives on the Body tab (plan row → Log), not in "+ Log" (fewer nav seams).
- ~~Document import is SAF-only for now (no in-app camera capture — TakePicture/FileProvider
  plumbing deferred); mime from ContentResolver.~~ Done in a later pass: a second FileProvider
  cache-path (`capture/`) + `BodyViewModel.newCaptureUri`/`importCapturedPhoto` add a "Photograph
  document" button next to "Attach document", using `ActivityResultContracts.TakePicture` and the
  same `Vault.encryptFrom` path as a SAF pick. CI-compiled only, not device-verified.
- Robolectric tests (migrations, registry reflection, VM suites) still deferred to a verified pass —
  no local Android SDK; pure-JVM suites cover the math.

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

## Hyle: approximation → real tokens

The Hyle Design System repo (dev.aarso hyle, `tokens/*.json` → `HyleTokens.kt`) is now the token
source. `ui/theme/Theme.kt` ports the generated values verbatim (warm ink #ECE8E4 at 92/42/18%
tiers, #121212-class surfaces per the halation rule, field.near #0A0809 window, radium #C7EF9E /
cold-cyan #35E0FF provenance, hairlines, calm 300ms cubic-bezier(0.4,0,0.2,1), token type scale).
Property names unchanged so all screens compile untouched. The body-map encoding hexes (violet
ramp anchors, #08FED5 physio hatch) stay as spec'd in the Crocodyl briefs — they are the body-map
law, not general theme tokens. Releases: `v0.4.0-hyle-approx` (before) vs `v0.4.1-hyle-real`
(after) for side-by-side comparison. Future step: consume `dev.aarso:hyle` as a real dependency
instead of ported constants (needs artifact publishing wiring).

## Hyle: real tokens → real dependency

`dev.aarso:hyle` doesn't publish to a Maven repo (no CI job in `mbaliga/Hyle-Design-System`
runs `publish`/`publishToMavenLocal` against a real registry) — "needs artifact publishing wiring"
above was never going to resolve itself. The actual mechanism already proven in this repo for
`dev.aarso:crash-recovery` (rebase commit `2725f80`, since dropped from `main` when that branch's
crash-recovery work was superseded — the wiring pattern is still sound, just not currently in the
tree) is a **git submodule + Gradle composite build**, not a publish step:

- `hyle-design-system` submodule → `mbaliga/Hyle-Design-System.git`, pinned at a commit (its own
  README dates the `:hyle` module to "the first single-sourced release, 0.2.0" — earlier commits
  shipped from three divergent copies of the token pipeline pre-single-sourcing).
- `settings.gradle.kts`: `includeBuild("hyle-design-system")` under the same `-PwithAndroid` gate
  as `:app-android` (its modules are Android libraries — including it unconditionally would break
  the SDK-free `./gradlew test`). Gradle's composite-build dependency substitution matches
  `dev.aarso:hyle` to the submodule's `:hyle` project by `group`/`name` automatically — no explicit
  `dependencySubstitution` block needed (same as the crash-recovery precedent).
- `app-android/build.gradle.kts`: `implementation("dev.aarso:hyle:0.2.0")`, AGP bumped 8.7.3 →
  8.9.1 to match the submodule's pinned AGP (composite builds require one AGP version across every
  participating build — Personal-Tracker DECISIONS.md D-Q, the same constraint the crash-recovery
  wiring hit).
- `android.yml` / `release.yml`: `submodules: recursive` on checkout, or the includeBuild sees an
  empty directory.

**The `:hyle` minSdk mismatch.** The submodule's `:hyle` module declares `minSdk = 31`; this app
is `minSdk = 26`. Left alone, that's a hard manifest-merger failure ("uses-sdk:minSdkVersion 26
cannot be smaller than version 31 declared in library"). Two ways out: bump this app's `minSdk` to
31 (a real, user-visible decision — drops API 26-30 devices — not something to do silently inside
a token-wiring change), or tell the merger to honor the app's real minSdk for this one library via
`<uses-sdk tools:overrideLibrary="dev.aarso.hyle" />`. Went with the override, because reading
`:hyle`'s entire Android surface (`Hyle.kt`, `HyleTokens.kt`, two generated `res/values/*.xml`
files) turns up zero platform-API calls above API 26 — it is, today, plain Kotlin constants,
sealed interfaces/data classes, and static color/dimen resources. That makes the override a safe
read of *today's* module, not a permanently safe assumption: if a future `:hyle` bump adds real
platform code gated above API 26 (the module's own README says Compose Modifiers/AGSL shaders are
coming), this needs re-checking, not just re-approving.

**What actually moved.** `ui/theme/Theme.kt`'s `object Hyle` — every `Color(0x....)` literal now
reads `dev.aarso.hyle.tokens.HyleTokens.Color.*` (an `Argb` = `Long` typealias in the exact
0xAARRGGBB packing Compose's `Color(Long)` already expects — no conversion helper needed), and the
radium/cold-cyan provenance pair now reads `dev.aarso.hyle.RadiantHues.RADIUM`/`COLD_CYAN` — the
hand-authored `Provenance` contract's canonical hue source, not just its generated token echo.
Every value is bit-identical to what was hand-copied (verified property-by-property against the
submodule's `HyleTokens.kt` before swapping), so this is a source change, not a value change.
`object Hyle`'s public shape is unchanged, so every consuming screen and `HyleAtoms.kt` (which
never duplicated token constants itself — it only ever consumed `Hyle.*`) compiles untouched.

**What did NOT move, on purpose — a partial migration, not a full swap:**
- `Hyle.Easing` (`CubicBezierEasing(0.4f, 0f, 0.2f, 1f)`) — `:hyle` compiles only token
  *durations* to Kotlin (`HyleTokens.Duration.*`, now wired); no bezier control points are exposed
  from `tokens/motion.json` yet, so this stays an app-side literal.
- The body-map encoding hexes in `ui/components/BodyAtlasCanvas.kt` (violet ramp anchors incl.
  `0xFF8E7BFF`, `#08FED5` physio hatch) — confirmed by grep to be the only other place these
  literals appear, and per the note above they are the Crocodyl briefs' body-map law, not general
  theme tokens; never part of this port.
- Any Compose-level `Finish`/`Pulse`/`Provenance` adoption (breathing radiant glow, colour-blind-
  safe glyph pairing) — `:hyle` itself doesn't have a Compose bridge yet (its own README: "The
  Compose Modifiers and AGSL shaders land next... this first cut is deliberately pure data"), so
  there is nothing on the other side to adopt beyond the token values already wired.

Not locally verified — no Android SDK in this environment; CI's `android` job (`-PwithAndroid`,
`submodules: recursive`) is the first real compile of this wiring, same caveat as the rest of the
Android layer.
