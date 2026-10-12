# Crocodyl cinematic app refresh

**Owner direction:** make the Android product immersive and cinematic in the spirit of Bocal, after
the owner saw the October 10 emulator screenshots and said the app looked poor. **Use actual,
properly licensed photography only. No AI-generated imagery.** Preserve Crocodyl's Hyle token rules,
privacy/local-first content, accessibility and all current functional behavior.

The owner has since said the first visual pass still falls short. Treat this as an active, phased
redesign—not a finished app-wide art pass. Home, Train, Progress and Score have initial treatments;
Body has an editorial header/region-picker pass; Fencing now has its first bespoke cinematic screen.
The remaining destinations still need coherent, screenshot-reviewed work, and the screen-by-screen
table below is the next design backlog. Do not call a polished Fencing screen or a checked-in
screenshot “production-grade visual QA.”

The owner has now explicitly rejected the current overall look as falling short of the Bocal
standard. The October 11 capture confirms why: the app still has a tall generic title bar, Home is a
vertical inventory of similarly weighted cards, the first-useful continuation is below feature
promos, and the lower navigation visually competes with the imagery. The next pass is therefore a
composition redesign, not a claim that the previous polished individual screens are sufficient.
The current implementation moves recent practice directly after the Recurve hero, introduces quiet
editorial section markers, and turns the top row into a compact Crocodyl range identity with a
contained settings control. It reuses existing licensed sports photography and does not generate or
download imagery. Treat this as the first renewed slice: capture and review it on emulator/device,
then continue the same art direction across all primary and secondary destinations.

2026-10-11 renewed Home composition pass: recent practice (up to two sessions) or the honest empty
state now follows the Recurve hero before secondary destinations; editorial labels group the
Atelier, other disciplines and training-together/community entries. The primary order and all
existing routes/actions are preserved. The tab header is now a compact range/training identity with
a contained settings affordance. Static diff validation passed. Android compile, lint,
instrumentation and a refreshed screenshot could not be run in this environment because no Java
runtime, Android SDK `adb`, or connected emulator is installed/available; do not treat this pass as
visually accepted until a fresh emulator/device screenshot is reviewed. The previously checked-in
screenshots show the pre-pass state.

Follow-up visual QA fixed two issues in that first render: the compact header applies the system
status-bar insets explicitly, and the no-session state is a compact, neutral row rather than a tall
outlined emoji panel. The October 13 validation and onboarding continuity screenshot are recorded
at the end of this document. Existing Home screenshots still predate the latest compact-header
adjustment; they are not evidence of the current Home render.

## Evidence and diagnosis

Reviewed the October 10 QA Home screenshot at `/private/tmp/crocodyl-photo-assets-home.png` and the
Bocal Android reference `.../Bocal/screenshots/145483.jpg`, alongside Bocal's
`android/docs/Bocal_0.5_UI_System.md` and shared-Hyle adoption directive.

Post-refresh emulator evidence is checked in at
[`screenshots/home-cinematic-refresh-api35.png`](screenshots/home-cinematic-refresh-api35.png):
API-35 ARM64 emulator, QA profile `QA`, 1080×1920. It shows the Recurve hero, primary actions,
non-photographic Atelier motif, and inset dock in one viewport.

Additional real-render evidence is checked in at
[`screenshots/train-setup-cinematic-refresh-api35.png`](screenshots/train-setup-cinematic-refresh-api35.png)
and [`screenshots/progress-cinematic-refresh-api35.png`](screenshots/progress-cinematic-refresh-api35.png).
Both are API-35 ARM64 emulator captures at 1080×1920 with a temporary `QA` profile. The Train image
shows WA Recurve 70 m selected and the primary action enabled; it is a visual review aid, not proof
that a session was recorded. The Progress image shows the evidence-led empty state. No AI-generated
visual assets were used.

The Score landing visual is captured at
[`screenshots/score-cinematic-refresh-api35.png`](screenshots/score-cinematic-refresh-api35.png),
also at 1080×1920 on API 35 with the QA profile. It uses the real target-face renderer in noninteractive
example mode and does not claim a round has been selected or scored.

The first-run entry is now captured at
[`screenshots/onboarding-cinematic-refresh-api35.png`](screenshots/onboarding-cinematic-refresh-api35.png),
1080×1920 on API-35 ARM64 using QA build 658. It uses the already-bundled, licensed Recurve training
photograph with a dark Hyle scrim; the photo is atmospheric and is not presented as form guidance.

The Fencing visual pass is captured in two states:
[`screenshots/fencing-cinematic-api35.png`](screenshots/fencing-cinematic-api35.png) shows the real
photo hero, Sabre/Foil/Epee selector, weapon reference and bout setup;
[`screenshots/fencing-referee-console-api35.png`](screenshots/fencing-referee-console-api35.png)
shows the live score, manual referee calls, point controls and call-history area. Both are 1080×1920
API-35 ARM64 emulator captures from the QA/debug-signed minified release. The photograph is
`fencing-training.jpg`, by Nathanaël Desmeules (`@nathanael240606`), under the Unsplash License; its
source, checksum and restrictions are recorded in
`app-android/src/main/assets/atelier/cinematic/sports-manifest.json`. It is atmosphere only, not a
technique or adjudication reference.

The October 11 Home refinement is captured at
[`screenshots/home-immersive-api35.png`](screenshots/home-immersive-api35.png), a 1080×1920 render
from the QA/debug-signed minified release on API-35 ARM64. The licensed Recurve image now carries
the editorial headline, readiness, active bow and Train/Score controls in one deliberate hero;
the duplicated standalone rig strip and oversized action row have been removed. The equipment,
Fencing and subsequent facet cards remain in the scroll feed. This is an incremental visual
improvement, not a claim that the whole app now matches Bocal or has passed device acceptance.

The October 11 Home/Atelier follow-up is captured at
[`screenshots/home-atelier-refresh-api35.png`](screenshots/home-atelier-refresh-api35.png), a
1080×1920 API-35 ARM64 emulator render. It replaces the abstract concentric-ring equipment tile
with the already provenance-checked dark arrow render, and changes the promise to the real arrow
library capability: independently explore components and variants. The Atelier tile and sport
facet cards are shorter, while the hero is modestly more compact, allowing more of the fencing
entry to appear before the floating dock. No new imagery or generated media was introduced. The
capture is visual review evidence only; it is not a device-acceptance result. This is one focused
Home composition iteration, not completion of the app-wide cinematic refresh.

Initial Home problems visible in the October 10 pre-refresh screenshot (historical baseline; some
were addressed by the subsequent Home/Train/Progress/Score passes):

- The hero occupies about one-third of the usable screen but is only a blank gradient with target
  line art; it wastes the real photography already bundled and approved for Recurve.
- “Ready to train” is repeated as status and its supporting detail.
- Train and Score are oversized, text-heavy slabs with excessive dead space.
- Repeated dark rectangles make the vertical feed look like a settings dashboard rather than an
  athlete's training space.
- The standard full-width Material bottom bar and plain top title do not establish a distinctive
  product frame.
- The home feed presents too many features at equal visual weight. Primary action, current state,
  equipment, and secondary sport facets need editorial hierarchy.

Bocal references that transfer well: immersive but restrained image fields, crisp uppercase
eyebrows, strong short headlines, a small number of large-radius visual cards, elevated/floating
navigation, compact utility controls, one active accent, and real interactive 3D/equipment visuals
where they add information. Crocodyl should translate these into an archery field/atelier language;
do not copy Bocal instrument metaphors or its cyan accent into Hyle's violet/radium provenance laws.

## Visual laws

1. **Photography is provenance-bearing.** Use the curated assets under
   `app-android/src/main/assets/atelier/cinematic/` and the adjacent
   `sports-manifest.json`. Keep source, photographer, license, checksums and restrictions current.
   Do not use assets marked as generated, uncertain or editorial-only without explicit owner review.
2. **Photo is atmosphere, not coaching evidence.** Never use decorative photography to imply a
   technique, safe setup, identity, venue, level or sport result.
3. **One hero per destination, not one hero on every screen.** Keep secondary pages efficient for
   range use; let score entry and live capture stay high-contrast and task-focused.
4. **Hyle owns state.** Violet is the action accent; radium green marks on-device provenance, not
   score or health; cyan stays reserved for external/cloud provenance. Warm off-white type on near-
   black surfaces. Avoid uncontrolled gradients and decorative colored status lights.
5. **Prioritize the athlete's next useful action.** Home hierarchy: greeting/current training state,
   primary Train and Score actions, active rig/session context, recent progress; secondary facets
   and catalogs follow below.
6. **Density should feel intentional.** Replace oversized blank tiles with compact, tactile actions;
   use whitespace between sections instead of empty interior card space. Do not hide full features in
   a horizontal carousel simply to shorten the page.
7. **Accessible first.** 48dp minimum touch targets, adequate contrast over photography, no state
   encoded by color alone, semantic labels for clickable cards, scalable text, reduced-motion
   support, and test at small screens and large font/display scaling.
8. **Cinematic motion is quiet.** Use Hyle's weighted ease and limited transitions; no parallax that
   impairs scroll, spring/bounce effects, flashing, or animation required to understand state.

## Work shipped in this refresh

The first implementation pass replaces the empty Home hero with the photographer-created Recurve
training image already bundled in the app, preserves a strong left-side text scrim, compacts Train /
Score cards, suppresses duplicate readiness copy and hides a zero-day streak. The Equipment Atelier
card now uses a code-drawn target motif instead of repeating the same photo; the motif is decoration,
not a target/equipment reference. The standard bottom bar is replaced with an inset floating dock.
The Train setup screen now carries that visual grammar into a quieter range workflow: a compact,
code-drawn range panel (no repeated photo), horizontally browsable round choices, grouped editable
session parameters, clearer practice-detail sections, and a persistent bottom start action. Rig
management, autofill, pinning, handedness, pre-check-in, and session-start behavior remain intact.
Progress now reads as a local training ledger: an editorial code-drawn header, consistent evidence
cards and rows, and explicit explanations when score, pose-repeatability, or equipment comparisons
do not yet have sufficient data. It preserves current calculations, normalized-score labeling,
sample counts, and non-causal interpretation. The Progress hero heading is distinct from its tab
title. The Score landing now has a compact editorial panel, an explicitly labelled code-drawn
example target face, and a differentiated Quick score primary action; score creation and inputs are
unchanged.
This does not yet refresh every screen; continue from real emulator captures.

2026-10-11 validation: Android debug/release unit tests, `lintRelease`, and connected debug
instrumentation passed (8/8). The suite covers first-run onboarding, all five primary tabs, Quick Score, accessible
Progress empty-state/title copy, Train's disabled-action guidance, and the exact multiline Score
headline. QA/debug-signed minified-release instrumentation also passed 7/7; its signer is not the
production signer. `tools/atelier/test_assets.py` passes 9/9, including the source/license and
checksum contract for the Home hero photograph. Version 658 QA APK/AAB passed release preflight.
These are emulator and local-build results, not physical-device visual acceptance. The checked-in
screenshots now cover Home, Train, Progress, Score entry, onboarding and Fencing; other primary
destinations remain visually unfinished and are still part of the refresh.

2026-10-11 Body slice: added a restrained editorial “Body & recovery” header in the Hyle palette,
explicitly framing the journal as non-diagnostic. The Today atlas now offers a named, scrollable
region chooser as an accessible alternative to map hit-testing; switching Body subviews resets the
shared scroll position so the selected view's controls return above the fold. The existing atlas,
pain dial, logging and history behavior are preserved. Connected debug and QA/debug-signed
minified-release instrumentation pass 7/7 each on API-35 ARM64, including named region selection.
Version 657 QA APK/AAB passed release preflight. No Body screenshot is checked in yet; the API-35
emulator result is interaction evidence, not a completed visual review or physical-device acceptance.

2026-10-11 onboarding visual pass: the first setup step now uses the bundled licensed Recurve photo
as an immersive full-screen field with a dark scrim and a small radium/Hyle eyebrow. Later setup
steps retain the plain dark task-focused surface. Role choices, progression, validation and commit
behavior are unchanged. Debug and QA minified-release connected suites pass 8/8 each; the QA APK
starts successfully and the checked-in 1080×1920 screenshot confirms the first-run render. Version
658 APK/AAB passed release preflight. This screenshot is emulator evidence, not device acceptance.

2026-10-11 Home refinement: retained the same licensed Recurve hero photograph and Hyle dark scrim,
replaced the temporary profile name as the oversized headline with “Find your rhythm.”, and moved
active-rig editing and Train/Score into the photo hero as a compact action shelf. The previous
standalone rig strip and oversized action row were removed. Rig editing, Train, Score, readiness,
and navigation are unchanged. The fresh 1080×1920 API-35 ARM64 render is
[`screenshots/home-immersive-api35.png`](screenshots/home-immersive-api35.png). No generated imagery
was introduced. Debug and minified-release connected instrumentation pass 9/9 each; unit tests and
release lint passed. QA APK/AAB version `0.6.0-home-immersive-qa` (versionCode 664) passed release
preflight. The artifacts use the QA/debug signing identity, not the production signer. Screenshot
and emulator results are not physical-device acceptance.

2026-10-11 Fencing visual pass: replaced the live referee screen's plain, text-first layout with a
licensed-photo piste hero, a horizontal Sabre/Foil/Epee selector, a weapon-specific target/priority/
equipment reference, named-bout setup, high-contrast scoreboard and dedicated manual-referee command
deck. Small-label/supporting-text contrast was increased without changing Hyle token values. The
referee state machine, calls, point gating, undo/reset and history are unchanged; camera adjudication
remains explicitly not connected. Instrumentation checks the hero and visible manual call controls.
API-35 debug and QA/debug-signed minified-release connected suites passed 9/9 each; debug/release
unit tests and `lintRelease` passed. No new or generated image was added. This is one focused Fencing
slice, not a completed visual refresh of every Crocodyl destination. The emulator captures are not
physical-device/range acceptance.

2026-10-11 Home/Atelier follow-up: reduced hero height from 310dp to 284dp; the arrow tile now uses
the existing checksum-verified `arrow-dark.jpg` render and a direct, capability-accurate headline
instead of decorative target rings. The arrow library itself remains separately navigable, with
its existing component and variant inspection. Atelier and sport cards were shortened to improve
the first-screen rhythm without concealing their full copy or changing feature routing. Home was
rebuilt and visually inspected on API-35 ARM64 at 1080×1920; the screenshot is linked above. Debug
unit tests and release lint passed. Production signing, compact-screen/large-font checks, remaining
destination refreshes and physical-device visual acceptance remain open. The follow-up v668
minified QA build and debug build each passed the 9-test connected Home smoke on API-35 ARM64 and
API-36 ARM64 16 KB-page emulators; this includes the visible Atelier entry assertion. The artifacts
are QA/debug-signed, not production-signed. The shared
[`../MOTION_SESSIONS_HANDOFF.md`](../MOTION_SESSIONS_HANDOFF.md) was refreshed to use current QA
baseline v666; screenless motion capture remains a separate proposal pending owner reconciliation.

## Screen-by-screen target direction

| Surface | Direction | Functional constraint |
|---|---|---|
| Home | Editorial athlete greeting, cinematic Recurve image, compact readiness pill, unmistakable Train/Score, active setup and recent session, then secondary sport and directory features. | Preserve fast-start and all current destinations; do not conceal the primary action below secondary cards. |
| Train setup | “At the range” session panel, selected round as a strong horizontal selector/card, compact fields, collapsible optional metadata, persistent high-contrast start action. | Values remain editable; check-in and transaction failure recovery unchanged. |
| Capture | Camera feed should own the screen, with one translucent compact capture HUD, framing guidance, discreet haptic/audio; controls legible outdoors. | Permission, pause/stop, session persistence and tracking state remain explicit. |
| Review | Make one session narrative: score/context, shot timeline, validated form cues and trends; differentiate observation from inference. | No unsupported detector or causal claims; correction/retraction remains easy. |
| Score | Warm paper/target-face moment against dark shell; dominant numeric score and a tactile keypad; minimize chrome. | Input accuracy, undo/correction, autosave/resume and accessibility remain paramount. |
| Progress | A small number of evidence-rich trends with date range, sample count and baseline quality; avoid a wall of charts. | Sparse evidence stays visibly sparse; no invented interpolations. |
| Body/recovery | Editorial non-diagnostic header and named atlas-region selection are in place; next refine atlas framing, history/injury/physio hierarchy and compact-screen presentation from captures. | Never turn readiness into diagnosis or a gate against training. |
| Calendar/log | A clean training ledger with visual rhythm and compact event chips; retain humane empty states. | Local events, time zones, editing and deletion behavior unchanged. |
| Atelier | Keep the cinematic reference-led 3D/equipment experience; improve entry preview/framing only from the approved geometry/asset workflow. | Arrow components and variants stay separately inspectable; no fabricated reference accuracy. |
| Fencing/Muay Thai/Bouldering | Sport-specific real/licensed image cards with clear beta/scaffold status; mature screens get a bespoke hero only as the feature merits it. | A beautiful card must not imply unimplemented analysis/adjudication. |

## Navigation direction

Owner correction (2026-10-13): Crocodyl's primary navigation should visibly and behaviorally follow
Bocal's arc dock, not the generic horizontal capsule that shipped in the first pass. The current
implementation carries Bocal's five-seat curved rail, tangent-rotated controls and raised active
keycap with its label; Crocodyl's existing Quick actions entry remains available as a separate
context button. Route ownership, state restoration and accessibility semantics remain Compose-native.
This is the lower destination rail only; do not invent a second contextual arc or claim shared Hyle
component parity until that canonical reusable API is available to both apps. Device screenshots,
large-font/compact viewport behavior, gesture insets and touch-target acceptance remain required.

## Acceptance checks for each visual slice

- Capture the same emulator at 1080×1920 and a compact viewport; compare Home above/below the fold,
  large-font behavior, tablet/landscape where supported, and dark contrast.
- Verify scrolling, card taps, talkback names, touch size, system bar/gesture insets and keyboard
  avoidance. Confirm no new badge/action occludes content.
- Run Android debug/release unit tests, release lint, connected debug and QA minified-release smoke
  after meaningful Compose/navigation changes.
- Record screenshots and the exact source asset IDs/licensing in the docs. Do not claim device QA
  from a render or preview.
- Keep scoring/capture screens readable in outdoor glare and during one-handed operation; visual
  flourish must not add an unsafe mid-session interaction.

## Suggested next design/implementation sequence

1. Capture and review Train and Progress after-shots, including keyboard-visible Train setup and
   small-screen/large-font layouts; current checked-in visual evidence is Home-only.
2. Refresh Score and Review as separate range-entry and reflection modes, with visual continuity but
   different interaction density and strict numeric/accessibility validation.
3. Revisit Capture with range ergonomics first: outdoor contrast, one-handed pause/stop, session
   persistence and permission recovery must remain explicit.
4. Apply the visual system to Body/recovery, Calendar/log, Coach and settings without obscuring
   privacy, medical caveats, empty states, or the existing local-first data boundaries.
5. Run before/after screenshots across compact and standard screens, large font scaling, landscape
   where supported, and physical devices; iterate from those captures rather than previews.

The active screenless motion-session product spec is separate:
[`../MOTION_SESSIONS_HANDOFF.md`](../MOTION_SESSIONS_HANDOFF.md). Do not use its future sport cards
to widen current Recurve release acceptance.

## 2026-10-11 owner-review correction pass

After the owner said the screenshots still looked poor, the Home composition received a focused
correction: the Home wordmark is smaller, the Recurve hero is shorter, and its horizontal scrim is
less opaque so more of the licensed athlete/range photograph reads. The Atelier card's scrim was
also reduced to reveal the existing arrow study render more clearly. The arrow itself, its
separately explorable components/variants, routing and all feature behavior are unchanged. No new
imagery was introduced. The updated render is
[`screenshots/home-cinematic-october-api35.png`](screenshots/home-cinematic-october-api35.png),
captured on API-35 ARM64 at 1080×1920 using a disposable QA profile. The Recurve photograph remains
the already-attributed, licensed asset documented in `sports-manifest.json`.

Verification for this correction: release lint passed; the session-start/check-in/permission-fallback
instrumentation test passed in isolation on API 35 and API 36 16 KB; the scorecard-resume test passed
in isolation on both devices. The first full debug rerun exposed platform-dialog wording variation
and a downstream scorecard timeout on API 35; after making the permission assertions cover both
first-denial and permanently-denied copy, the two affected tests passed individually. A full suite
rerun after that harness correction is still outstanding, so this capture is visual evidence, not
full-suite or physical-device acceptance. This is a first correction slice only: the wider screen-by-
screen redesign and compact/large-font/device reviews below remain open.

## 2026-10-13 Home and onboarding continuity pass

Home no longer spends vertical space on the generic title bar; the licensed Recurve hero now owns
the top of the destination, with Settings contained in the image card, a compact no-session row,
and recent practice directly after the primary actions. This preserves the Home destinations,
settings route, rig editing, Train and Score behavior. The arrow Atelier remains its own card and
continues to open the previously implemented component-by-component and variant inspection.

The same bundled, provenance-checked Recurve photograph now carries through all first-run setup
steps under a stronger Hyle-black scrim. The form fields and actions remain legible; onboarding
state, copy, progression and commit behavior are unchanged. This is a continuity treatment, not a
claim that the photo teaches safe or correct technique. No new image was added and no generated
media was used. A current 1080×1920 API-35 ARM64 profile-step render is checked in at
[`screenshots/onboarding-cinematic-continuity-api35.png`](screenshots/onboarding-cinematic-continuity-api35.png).

Verification on October 13: debug assembly, Android debug and release unit tests, and `lintRelease`
passed. The connected debug `ProductionSmokeTest` passed 10/10 on one API-35 ARM64 emulator after
the onboarding update; the suite checks Home, settings, core navigation and primary flows. The
previous Home/navigation run also passed 10/10. The initial offline root `test` attempt lacked
cached Kotlin-test artifacts; after allowing Gradle to fetch the declared, checksum-verified
dependencies, a forced root/JVM test run passed (50 tasks executed), and the combined root/JVM,
Android debug/release unit-test and release-lint replay passed (113 actionable tasks). The minified
release connected rerun also passed 10/10 on API-35 ARM64. A fresh Home screenshot after the
title-bar change is still outstanding, as are API-36 16 KB connected tests for the new v679 build,
compact/large-font visual review and physical-device acceptance. The refreshed onboarding screenshot
is visual evidence only; remaining destinations still need the full Bocal-inspired, screen-by-screen
review described above.

## 2026-10-13 Bocal navigation correction

The owner correctly noted that the first correction still did not use Bocal's full interaction
pattern: it implemented only the lower destination rail. That first build and its screenshot
([`home-bocal-arc-navigation-api36.png`](screenshots/home-bocal-arc-navigation-api36.png)) are
superseded; they are not evidence for the current navbar.

The follow-up implements the shared Hyle two-arc stage described in Bocal's
`docs/HYLE_ARC_NAVIGATION_DIRECTIVE.md`: both rails scale as one 400×150 stage; the upper Bézier
hosts the active destination label and accepts horizontal scrub gestures, previewing destinations
while held and selecting on release. The left contextual control opens Train, the right opens the
existing Add sheet, and the five lower destinations sit on the lower Bézier with tangent rotations
and the selected raised keycap. Routes, tab semantics, state restoration and the existing Add
action remain intact. The selection interaction is tested independently by
`upperArcDragPreviewsAndSelectsCalendar`.

Verification (2026-10-13): debug and debug-test APK assembly passed (QA versionCode 695), and the
focused instrumentation test passed 1/1 directly on the API-36 ARM64 16 KB AVD. Android
debug/release unit tests and `lintRelease` passed after the final source edits (89 actionable
tasks). The full primary-tab flow also passed 1/1 directly on that AVD, including the visible
Progress checks. An unrelated off-screen lazy-row assertion was removed from this navigation smoke;
the test no longer treats a not-yet-composed section as a route failure. A manual 420dp compact-width
swipe changed Home to Calendar. Gradle's connected task timed out while querying a second attached
emulator, so both target-AVD test runs used the Android instrumentation runner directly. At the
time, the final seat-aligned capture and large-font review were outstanding; both emulator captures
are now documented in the Large-font Home follow-up below. Physical-device review remains open. Do
not mistake the earlier lower-rail screenshot or release test results for evidence about this latest
change.

## 2026-10-13 Large-font Home follow-up

The final visual review caught a genuine 1.3× font-scale defect: Home's Train and Score action
labels wrapped mid-word beside their icons. `CrocodylHero` now switches those actions to a compact
icon-above-label arrangement at font scales of 1.2× and higher, gives the hero enough height for
the larger controls, and keeps both labels single-line. At normal font scale the established
side-by-side icon-and-label composition is unchanged.

Visual evidence: [`screenshots/home-hyle-two-arc-v696-api36-16kb.png`](screenshots/home-hyle-two-arc-v696-api36-16kb.png)
shows the normal-scale two-arc Home on the API-36 ARM64 16 KB emulator; the final
[`screenshots/home-large-font-1_3x-v697-api36-16kb.png`](screenshots/home-large-font-1_3x-v697-api36-16kb.png)
shows the updated v697 debug build at 1.3× system font scale. Both are 840×1920 emulator captures,
not physical-device or production-signing evidence. The large-font screenshot uses the synthetic
QA profile created only on the disposable emulator. The lower Atelier card remains part of the
scrollable Home feed; large text naturally places it below the first viewport.

Verification: debug and minified QA release assembly succeeded for versionCode 697. The full debug
and minified-release `ProductionSmokeTest` suites each passed 11/11 on the API-36 ARM64 16 KB
emulator with system font scale set to 1.3×. Android debug/release unit tests and `lintRelease`
also passed. The Home primary-tab smoke now performs deliberate short feed swipes in both directions,
so it verifies reaching the below-fold Atelier and returning to the hero instead of assuming the
feed resets on tab selection. The emulator font scale was restored to 1.0 after testing. The v697
APK/AAB passed the release preflight, but are still signed with the QA/debug key and are not
production-distributable. The Home large-font screenshot itself is from v697 debug; the visual
source is the same as the minified artifact.
