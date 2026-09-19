# Crocodyl build notes (Phases 1–3)

Consolidated notes for the multi-phase build. (Deviation from the per-phase `PHASEn_NOTES.md`
convention: this is one autonomous build across Phases 1–3, so notes are consolidated here.)

## Current addendum — 2026-09-16

The notes below are the historical implementation log and contain superseded “pending” entries from
before the Android layers landed. The authoritative current snapshot is [`CROCODYL_STATUS.md`](CROCODYL_STATUS.md).

Current work is on `codex/production-hardening`, based on `4c15c72`, with an unreleased
`0.6.0-spec-dev` build. Since the historical entries below:

- the manual Recurve scoring wedge is implemented (numeric, plotted and observer input, WA/custom
  rounds, resume, grouping, PBs and scorecard retraction);
- DeepSeek is present as a BYOK provider alongside OpenAI, Anthropic and Google;
- Room recovery is non-destructive: failed opens are preserved and surfaced for explicit reset;
- Room schema export is enabled and release builds now require explicit release signing;
- Android CI watches core-module and Gradle changes as well as `app-android` changes.
- The current body layer now supports in-app document-photo capture alongside SAF import; camera
  staging is scoped through FileProvider and deleted after atomic vault encryption.
- The bundled BlazePose asset is content-pinned: clean builds verify the SHA-256 of the downloaded
  model and publish it atomically, and Android CI runs both the root JVM tests and full release lint.
- The current verification pass reports 178 primary JVM/Android test cases with zero failures or
  errors; release lint has zero errors, warnings, or informational notices, and the QA-key APK and
  AAB pass signature verification.
- App networking is explicitly cleartext-disabled in the manifest, and CI now triggers when the Gradle settings
  file changes so module inclusion cannot drift without an Android build.
- The final manifest-only hardening was rebuilt and inspected in the current debug APK and in a
  corrected minified QA-key APK/AAB. The R8 configuration retains shrinking, obfuscation, and
  resource shrinking while disabling aggressive optimization, bringing the release APK build down
  to 1m27s and the AAB build to 12s. CI must still rebuild with the real production key before
  distribution.
- The release workflow now inspects the packaged APK manifest and AAB contents, failing on a
  cleartext-enabled or backup-enabled manifest, a missing or tampered BlazePose asset/backup
  resource, or leaked staging files.
- An API-35 ARM64 emulator smoke pass installed the minified QA APK, completed onboarding, rendered
  Progress/Train/Body/Calendar/Settings, verified force-stop/relaunch profile persistence, initialized
  the bundled pose model, and toggled Start recording → Stop & analyze against an empty camera frame
  without an app fatal exception. Full physical-device, camera/range, and 30-session acceptance remains.
- The first minified capture run exposed MediaPipe protobuf reflection breakage from R8 field
  obfuscation (`typeUrl_`); retaining `com.google.protobuf.**` in the release rules fixed it and was
  reverified on the same emulator path.
- SAF export now advertises Crocodyl's vendor MIME type so DocumentsUI preserves the requested
  `.crocbak` extension; the final emulator export opened successfully and excluded private tables.
- The shared segmented control now uses responsive equal-width, single-line labels; the final
  emulator screenshot confirms all five Body views are legible instead of wrapping `Physio` into a
  vertical column.

The remaining production gate is evidence, not another blind feature pass: Android device/range QA,
camera/permission/interruption behavior, release artifact verification with the real production key,
and the blueprint’s later phases
(validated form calibration, End Scan, signed `.croc`, Coach workspace and web viewer).

## Hardening addendum — 2026-09-18

- Closed the current-scope scoring context gap: sight mark, venue, conditions and training intent are
  now editable from the scorecard and retained in the existing Room fields. Narrow-screen scoring
  mode labels scroll safely.
- Scorecard linking now enforces same-athlete ownership and rejects retracted training sessions.
  End Scan candidate proposals validate scores/plots/confidence, cap to the remaining arrows, and
  replace only unresolved proposals; confirmed/rejected provenance is retained. ViewModel actions
  preserve coroutine cancellation instead of converting it into a user-facing failure.
- Document vault views now decrypt to a unique private staging file and publish atomically only after
  full authenticated completion. Viewer path components are constrained, preventing partial plaintext
  from being treated as a valid view. On-device model removal is canonical-path scoped to the private
  model directory, with a regression test for outside-path deletion.
- Latest verification: Android debug/release unit-test variants and `lintRelease` pass with zero
  failures/errors and no lint findings; a rebuilt debug APK passes `apksigner verify`. Including the
  root JVM run and the added model-path regression, the current root-plus-two-Android-variants
  matrix totals 180 test cases, all passing. Release APK/AAB generation still fails closed without
  the real production signing inputs; the `verifyReleaseSigning` guard is working as intended. For
  final local validation on 2026-09-18, a throwaway QA-only keystore was supplied outside the
  repository; the minified APK/AAB then passed R8, resource shrinking, APK v2 verification, and AAB
  `jarsigner` verification; the APK also passed ZIP alignment verification. This proves the release
  path, not the identity of the production key. Release CI now enforces the same alignment check.
- An equipment continuity audit confirmed that the existing arrow tuning model/editor and FOC/GPP
  analysis remain present. The validated `feat/immersive-visuals` handoff is now integrated as a
  separate generic arrow-reference studio with five modular GLB families, eight selectors, local-only
  WebGL2 controls, semantic part IDs, provenance metadata, deterministic generator/contract tests and
  committed Chromium evidence. It is not an owner-approved arrow, compatibility claim or full catalog.
- The rebuilt debug APK was installed on the API-35 ARM64 emulator and exercised through Home → the
  Atelier card. The non-exported host reached a resumed local WebView; after binding a 1×1 fallback
  texture for untextured meshes, the isolated log had no fatal exception or WebGL texture warning.
  Final QA artifact SHA-256 values are recorded in `CROCODYL_STATUS.md` and the acceptance checklist.
- The Atelier GLB exporter now canonicalizes float serialization, and both generators were rerun
  successfully with no resulting worktree drift. `atelier-quality.yml` and `release.yml` compare
  regenerated models against the checked-in outputs, closing the stale-generated-asset gap found in
  the final production audit.
- The remaining generic point families are now present as separate semantic components: hardened
  416 stainless glue-in, adjustable glue-in bullet, and removable 10/20/50-grain module studies.
  The Lancaster ledger and browser contract preserve their explicit product-family boundaries; the
  next visual dependency is the owner-arrow reference, followed by owner approval.
- Fletching taxonomy is now present as a separate fifth GLB and eighth studio selector. It keeps
  material/type, mounting, vane count/clocking, wrap presence and nock-relative orientation as
  independent generic studies, with Lancaster source references and contract coverage. The next
  asset dependency is the owner-selected arrow reference, not further speculative equipment.
- The corrected fletching build was installed on the API-35 ARM64 emulator on 2026-09-20. Home →
  Equipment Atelier → horizontal selector → Fletching rendered the five-family taxonomy at mobile
  scale; the isolated log showed no fatal, uncaught-JavaScript or WebGL error. Debug APK SHA-256:
  `a25c5ec863be8cbe731c508b94e2c9986c104b3574b849682b37e53729124461`.

## Release validation addendum — 2026-09-20

- The QA release candidate `v0.6.0-rc.2` (versionCode 602) was rebuilt with
  `assembleRelease`, `bundleRelease`, `testReleaseUnitTest` and `lintRelease`. The build passed
  R8/resource shrinking, release lint, and packaging with the throwaway QA keystore.
- The resulting APK SHA-256 is
  `494b33c802230b0651310592fa8629d63fe0d55cef7a6b36cdbb7c319bbd268d`; the AAB SHA-256 is
  `8964f27a5d2577a17f5ee65510c958384d02fc3e7e3aaae0f49d99d1e0b3dc45`. `apksigner verify`,
  `zipalign -c -P 16 -v 4`, and `jarsigner -verify` each exited successfully. The signatures are
  QA-only evidence, not a production signing identity.
- The remaining release gates are external: production keystore/CI identity and final versioning,
  owner approval, physical-device/accessibility/thermal/battery checks, real camera/range tests,
  and the required acceptance matrix. The current generic arrow-reference studio is not an exact
  owner-arrow catalog.

## Historical recon findings

These notes capture the initial blind-build investigation. They are retained for provenance; the
2026-09-15 addendum above supersedes any later “pending” or “deferred” claim that it contradicts.

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
- Streak week-strip is a text summary line, not 7 glyph dots (visual polish deferred).
- srpe lane is computed but the Load view shows shot-load bars only (secondary lane deferred).
- Physio session logging lives on the Body tab (plan row → Log), not in "+ Log" (fewer nav seams).
- At the initial blind pass, document import was SAF-only; the current branch adds in-app camera
  capture through a scoped FileProvider path and retains the ContentResolver MIME path for SAF.
- Robolectric tests (migrations, registry reflection, VM suites) still deferred to a verified pass —
  no local Android SDK; pure-JVM suites cover the math.

## Historical pending list (superseded)

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
