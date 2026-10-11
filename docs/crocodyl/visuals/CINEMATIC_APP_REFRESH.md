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
destination refreshes and physical-device visual acceptance remain open. Connected instrumentation
was attempted but Gradle/ADB returned `java.io.IOException: Operation not permitted`, including on
retry with elevated local execution; therefore this slice has no fresh connected-test pass. The shared
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

Study Bocal's floating dock, but design for Crocodyl's five current primary destinations. Target an
inset, elevated surface with a compact active capsule, quiet inactive icons, readable labels, and
optional secondary quick action attached to the active context. It must respect Android gesture
navigation, edge-to-edge insets, keyboard, accessibility semantics, test tags, display scaling and
the Compose navigation back stack. Do not implement Bocal's two-level arc geometry locally until the
canonical reusable Hyle component/API is available to both apps; an approximation is worse than a
simple accurate dock.

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
