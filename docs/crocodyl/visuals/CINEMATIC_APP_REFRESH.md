# Crocodyl cinematic app refresh

**Owner direction:** make the Android product immersive and cinematic in the spirit of Bocal, after
the owner saw the October 10 emulator screenshots and said the app looked poor. **Use actual,
properly licensed photography only. No AI-generated imagery.** Preserve Crocodyl's Hyle token rules,
privacy/local-first content, accessibility and all current functional behavior.

## Evidence and diagnosis

Reviewed the October 10 QA Home screenshot at `/private/tmp/crocodyl-photo-assets-home.png` and the
Bocal Android reference `.../Bocal/screenshots/145483.jpg`, alongside Bocal's
`android/docs/Bocal_0.5_UI_System.md` and shared-Hyle adoption directive.

Post-refresh emulator evidence is checked in at
[`screenshots/home-cinematic-refresh-api35.png`](screenshots/home-cinematic-refresh-api35.png):
API-35 ARM64 emulator, QA profile `QA`, 1080×1920. It shows the Recurve hero, primary actions,
non-photographic Atelier motif, and inset dock in one viewport.

Home problems visible in Crocodyl's screenshot:

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
This does not yet refresh every screen; continue from real emulator captures.

2026-10-10 validation: Android debug and release unit-test tasks, `lintRelease`, and connected debug
instrumentation passed (7/7) after the Home and Train Compose changes. The connected suite covers all
five primary tabs and Quick Score. `tools/atelier/test_assets.py` passes 9/9, including the
source/license and checksum contract for the Home hero photograph. These are emulator and
local-build results, not physical-device visual acceptance. The emulator's ADB server could not be
restarted in the current sandbox to capture a second Train screenshot, so only the existing Home
after-shot is currently checked in.

## Screen-by-screen target direction

| Surface | Direction | Functional constraint |
|---|---|---|
| Home | Editorial athlete greeting, cinematic Recurve image, compact readiness pill, unmistakable Train/Score, active setup and recent session, then secondary sport and directory features. | Preserve fast-start and all current destinations; do not conceal the primary action below secondary cards. |
| Train setup | “At the range” session panel, selected round as a strong horizontal selector/card, compact fields, collapsible optional metadata, persistent high-contrast start action. | Values remain editable; check-in and transaction failure recovery unchanged. |
| Capture | Camera feed should own the screen, with one translucent compact capture HUD, framing guidance, discreet haptic/audio; controls legible outdoors. | Permission, pause/stop, session persistence and tracking state remain explicit. |
| Review | Make one session narrative: score/context, shot timeline, validated form cues and trends; differentiate observation from inference. | No unsupported detector or causal claims; correction/retraction remains easy. |
| Score | Warm paper/target-face moment against dark shell; dominant numeric score and a tactile keypad; minimize chrome. | Input accuracy, undo/correction, autosave/resume and accessibility remain paramount. |
| Progress | A small number of evidence-rich trends with date range, sample count and baseline quality; avoid a wall of charts. | Sparse evidence stays visibly sparse; no invented interpolations. |
| Body/recovery | Calm editorial atlas and actionable recovery context, with privacy and non-diagnostic wording. | Never turn readiness into diagnosis or a gate against training. |
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

1. Validate the updated Home hero and condensed actions on emulator; tune gradient/crop using actual
   screenshot evidence.
2. Replace the standard bottom navigation with a compact inset/floating Crocodyl dock; preserve all
   existing destinations and accessibility.
3. Apply the same card, eyebrow, type and spacing grammar to Train setup and Progress.
4. Refresh Score/Review as separate range and reflection modes, with visual continuity but different
   interaction density.
5. Take before/after screenshots for the remaining screens and iterate from actual devices.

The active screenless motion-session product spec is separate:
[`../MOTION_SESSIONS_HANDOFF.md`](../MOTION_SESSIONS_HANDOFF.md). Do not use its future sport cards
to widen current Recurve release acceptance.
