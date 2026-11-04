# Home-screen widgets

_Added 2026-11-04. Compiled and unit-tested here only; nothing below has run on a phone or a
launcher yet (see [Owner verify](#owner-verify))._

Crocodyl ships six standard Android App Widgets (`AppWidgetProvider` + `appwidget-provider` XML +
`RemoteViews`), so any launcher that hosts widgets can place them: Pixel Launcher, One UI, Nova, and
the owner's own launcher once it hosts App Widgets. Each one is resizable and has a small, a medium
and a large layout, plus a one-line compact form for a widget too short for the small one. They read
the app's own Room database on the device; there is no snapshot cache, no network, no new permission
and no new dependency.

The house rules hold on the home screen, which is a public surface:

- **Measurements, never advice.** No coaching text, no "you should". The readiness widget shows the
  level and the measurements behind it, not the cascade's sentences.
- **No streak** on any widget, although the app keeps `StreakState`. No badges, no unread counts, no
  manufactured urgency.
- **Nothing from the `MEDICAL` or `PRIVATE` privacy classes** (`PrivacyClass`, core-wellness) is
  shown. The content of physio plans (names, target regions, notes), injuries, life events, mood,
  cycle, medication and documents never reaches a widget. **Their effect on the readiness level
  does**, exactly as in the app: an active injury or a high-impact life event can lower the level
  to `◐ Caution` or below. The medium and large Readiness layouts also print the other inputs
  (energy, sleep, soreness count, load ratio, check-in time), so when those all look normal and
  the level still drops, someone looking at the home screen can infer that an injury or a private
  life event is active, without learning which. This is an **owner decision** (see
  [Owner decisions](#owner-decisions)); the alternative is a level-only Readiness widget.
- **Readiness only as the app draws it**: a shape plus a word, `● Ready`, `◐ Caution`,
  `○ Rest advised`, `‖ On pause`. No colour carries the level.
- **Provenance is a shape plus the word `native`/`cloud`, never the radium-green colour alone.**
  This overrides the app's own rule ("provenance is a colour, not a label", `CROCODYL_STATUS.md` §1)
  for widgets only, per the widget strategy: D-BD (PROPOSED; renumbered from D-AD), v1.1 §2 rule 6
  and §6, `personal-tracker/snapshots/widget-strategy-2026-10-14.md` in the owner's
  `personal-tracker` repository. No widget value comes from a cloud
  model, so the widgets only ever print `◆ native · computed on this phone`; radium green is not
  used on any widget.
- Red and green never carry meaning. Widget colours are the Hyle palette from `ui/theme/Theme.kt`
  (accent violet, surfaces, ink), with a `values-night` pair for the dark palette.
- Baseline-specific code (EEG, bow IMU, paid analytics) is not involved and must not be.

## The six widgets

| Widget | Template | Default size | Shows | Tap opens |
|---|---|---|---|---|
| Last scorecard | `stat` | 2x2 | Most recent finished scorecard: total against maximum, arrows | Progress |
| This week | `progress` | 4x2 | Arrows this week against the active plan's weekly target | Progress |
| Next physio day | `next` | 2x2 | Next scheduled physio day, its exercise and plan counts | Body |
| Readiness | `stat` | 2x2 | Readiness level as shape + word, with the check-in behind it | Home (Log from the large layout) |
| Active rig | `card` | 4x2 | Active rig, bow type, poundage and recorded tuning | Settings → Rigs |
| Start training | `pads` | 4x1 | Score (or Resume), Train, Log; Progress, Body, Calendar when large | Each pad its own screen |

All six resize from 2x1 upwards (`minResizeWidth` 110dp, `minResizeHeight` 40dp,
`resizeMode="horizontal|vertical"`).

**Which layout a widget gets** (`WidgetSizing`). Every layout declares what it needs: a minimum
width (200dp for the two-column and multi-pad layouts) and a height made of fixed dp (padding,
margins, the progress bar, the 48dp touch targets) plus its stacked text in sp, which grows with the
font scale (`LayoutNeed`, 1.2dp of line per sp). The widget gets the largest layout whose need fits
its size **at the current font scale**; when even the small one does not fit (a 1-row widget in
landscape, about 51dp tall, or a large font on a 1-row widget) it gets the **compact** form: one
tappable line, the value and a caption, no label. So the size class depends on the template and the
font: at 1x, for example, the stat widgets are small at 2x1, medium at 4x1 and 4x2 and large at 4x3;
the card (five stacked lines) stays small at 4x1; This week shows its day strip from 4x2; at 1.3x a
4x1 stat widget is compact, and at 2x a 4x3 one is medium.

On API 31+ the app hands the launcher all four layouts at once (`RemoteViews(Map<SizeF,
RemoteViews>)`), keyed by each layout's need at the current font scale; the keys grow in both
directions, so the launcher's choice (the fitting key nearest the widget's size) is always the
largest layout that fits, without waking the app. Below API 31 the provider reads the reported size
range and hands over one layout for portrait (minimum width x maximum height) and one for
landscape (maximum width x minimum height) as `RemoteViews(landscape, portrait)`, and redraws on
`onAppWidgetOptionsChanged`. A change of the system font size redraws every widget while the app
process is alive; otherwise the next redraw (a write, the app opening, midnight) picks it up.

`WidgetLayoutFitTest` measures every layout, filled with long content, with real font metrics
(Robolectric native graphics, xxhdpi) at 1x, 1.3x and 2x on SDK 31 (linear font scaling) and 35
(non-linear), at the narrowest width it is used at, and fails if any layout is taller than its
declared need. That is what makes "the layout fits the size it is chosen for" a tested fact rather
than an estimate, for Robolectric's Roboto; a device font with taller metrics is an owner-verify
item.

### Last scorecard (`stat`)

- **Compact:** `612 /720` and the arrows.
- **Small:** total, `/maximum`, and arrows shot (`48 of 72 arrows` when the round was not completed).
  A set match shows the set score (`6–4 sets`) instead of a total.
- **Medium:** adds the round name and distance, the date it finished, and X count with points per
  arrow.
- **Large:** adds rows for X count, points per arrow, round best (the same basis as the scorer's PB
  and Progress: complete, finished, not retracted, set matches excluded) and the rig, and the
  provenance footer.
- **Data:** `ScoringDao.completedForAthlete`, `activeArrowCount`, `bestPerRound`, `RigDao` (rig name).
  Retracted cards never appear; an unfinished card is not a "last" result.
- **Empty:** "No finished scorecard yet".

### This week (`progress`)

- **Compact:** the arrows and `/ target arrows · % of weekly target`.
- **Small:** arrows this week, `/ target arrows`, a progress bar, and `% of weekly target`
  (or `14 arrows over weekly target`, or "No weekly target in an active plan").
- **Medium:** adds the week's first day and the session count.
- **Large:** adds a day-by-day strip (today marked with `▸`, a shape, not a colour), the plan's title
  and the provenance footer.
- **Data:** the week is the locale's week (`WeekFields.of(Locale.getDefault())`, as the Calendar's
  weekly bars). Arrows per session use `domain/TrainingVolume`, the rule extracted from
  `ProgressViewModel` so the widget and Progress share one implementation: a capture session's
  reconciled arrows (or recorded shots), with a scorecard linked to it folded in (larger count wins),
  plus every unlinked finished scorecard (the same 250 most recent cards Progress reads). The active
  plan is the latest-started `training_plan` in state `ACTIVE` that has started and not ended; its
  `weeklyArrowTarget` is the target. Recovery notes are never read.

### Next physio day (`next`)

- **Compact:** the day and `Physio · Sat, 8 Nov` (no label fits, so the caption says what it is).
- **Small:** the day (`Today`, `Tomorrow` or the weekday) and the date (`In 3 days · Sat, 8 Nov`).
- **Medium:** adds what the day holds (`5 exercises · 2 plans`), "Today's session logged" when today
  was scheduled and every plan due today has a session, and scheduled days attended over the last 28.
- **Large:** adds the next scheduled days as rows, the 28-day adherence row
  (`6 of 8 days (75%)`, computed by `PhysioAdherence` exactly as Progress does) and the footer
  "Plan names stay in the app".
- **Data:** `BodyDao.activePlans`, `exercisesFor`, `physioSessionsFor`. The widget model
  (`PhysioPlanRow`) has no field for a plan's title, target regions or notes, so they cannot reach a
  home screen even by mistake; a test pins that.
- **Empty:** "No physio day scheduled" (no active plan with a schedule in the next 14 days).

### Readiness (`stat`)

- **Compact and small:** the level as shape + word (`◐ Caution`) and `As of 14:05`. With no
  check-in ever logged the cascade has nothing to lower the level with and returns Ready, so every
  size that shows the level also says so: `● Ready` with `No check-in logged · As of 14:05` (small,
  compact), or the "No check-in logged" line (medium, large). Missing data never reads as readiness.
- **Medium:** adds the latest check-in's date and time, energy and sleep (`n/5`), and the load ratio
  (ACWR, only once the app's own warm-up is complete; "Baseline building" before that).
- **Large:** the same as rows plus soreness (a count of regions), the provenance footer and a
  **Log check-in** action that opens Log.
- **Data:** the level is `WellnessAssembler.readiness`, the same call Home makes, so the widget never
  disagrees with the app. The cascade's reason strings are dropped: they can name an injured region
  ("Active injury: rotator_cuff_r") or stand in for a private life event ("high-stress window").
  The level itself can be lowered by those inputs, as it is in the app, and the widget never says
  why; see the house rules above for what can be inferred. Measurements come from
  `WellnessDao.latestCheckin`/`sorenessFor` and `WellnessAssembler.acwr`, computed once and passed
  to `WellnessAssembler.readiness` (an optional parameter; Home and the coach still let it compute
  its own), so the full load history is read once per redraw, not twice.
- **Stale:** readiness can change with no new data (a check-in older than 36 hours stops counting),
  so it always prints the time it was read, and the next redraw is scheduled for that boundary.

### Active rig (`card`)

- **Compact:** the rig's name and its bow type with poundage.
- **Small:** the rig's name and bow type with effective poundage (`≈ 36 lbs estimated`,
  `36 lbs measured` or `40 lbs marked`, the app's measured > estimated > marked precedence).
- **Medium:** adds the first two recorded tuning facts.
- **Large:** up to six rows: brace height, tiller (only when both limbs are recorded), arrow spine,
  arrow length, point, arrow mass, FOC and GPP. Only what the athlete recorded, or what follows from
  it through `Tuning.focPercent`/`Tuning.gpp`; nothing is estimated for the widget.
- **Data:** `RigDao.activeForAthlete`, `Tuning.parseFull`, the athlete's draw length.
- **Empty:** "No rig yet".

### Start training (`pads`)

- **Compact:** the whole widget is one target, Score or Resume, icon beside the word.
- **Small (below the pads template's 2x2 minimum):** one target, Score, or **Resume** when a scorecard
  is still open.
- **Medium:** Score/Resume, Train (form capture), Log.
- **Large:** adds Progress, Body and Calendar, and a line naming the open scorecard
  (`Open scorecard · WA 70 m · 36 of 72 arrows`).
- **Data:** `ScoringDao.activeForAthlete`, `activeArrowCount`.

### States every widget can show

All in the app's words, from `res/values/strings.xml`:

- **First run** (no profile yet, or after a full data wipe): "Set up Crocodyl".
- **Empty**, per widget, as above.
- **Needs the app:** "Open Crocodyl. Your saved data needs attention in the app." when the database
  could not be opened safely (see below).
- **Error:** "Couldn't read your data".
- **Stale:** readiness prints its "As of" time; dated widgets print absolute dates next to any
  relative one, so they stay true even if a midnight redraw is late.
- **Withheld:** there is no all-or-nothing withheld state; the privacy reductions above (counts
  instead of physio plan names, the readiness level without its reasons) are always on.

States are sized like everything else: one line (title and body) when the widget is too short,
otherwise the label, the title and two to four lines of body as the height allows.

## Refresh: pushed, never polled

`updatePeriodMillis` is 0. `widget/WidgetRefresh.kt`:

- **Data changes:** while any widget is placed, a Room `InvalidationTracker` observer in the app
  process watches the tables the widgets read (`WidgetTables`); a write redraws only the widgets that
  read the written tables, debounced by 1.5 s so a burst of arrow taps is one redraw. No call site in
  the repositories had to change.
- **The app opens, onboarding, a full wipe:** `MainShell` and the onboarding branch of `AppRoot` call
  `WidgetRefresh.onAppReady`, which redraws everything (after a wipe, widgets fall back to "Set up
  Crocodyl" instead of keeping deleted data on the home screen).
- **Time:** one inexact, non-waking `AlarmManager.setWindow(RTC, at, 10 min, …)` alarm at one minute
  past local midnight (the day, the week and every "today" move then), or earlier when the latest
  check-in goes stale. A window rather than `set()`: on API 26–30 `set()` may defer an alarm by up
  to 75% of the time until it is due (one set at 09:00 for midnight could fire mid-morning the next
  day); the window bounds the delay on every API level, still with no exact-alarm permission and no
  WorkManager. Doze and OEM battery managers can still defer it. The providers also redraw on
  `TIME_SET`, `TIMEZONE_CHANGED` and `LOCALE_CHANGED`.
- **The next alarm is always set.** It is scheduled after every redraw in a `finally` block that
  survives cancellation, with its own 1.5 s budget, and the midnight alarm (which needs no database)
  is set before the readiness read that may move it earlier. So a redraw that runs out of time or
  fails still leaves the next boundary in place.
- **Order:** redraws of one widget kind run one at a time, in the order they were asked for (a fair
  `Mutex` per kind), and each reads the database and the clock only once it holds the lock, so an
  older, slower read can never overwrite a newer one.
- **Work budget:** every update runs off the main thread under `goAsync()`: seven seconds for the
  redraws, then up to one and a half for the next alarm. A redraw that fails keeps the widget's last
  views and cannot crash the app (one widget failing does not stop the others); nothing is reported
  anywhere.

**A database that will not open.** Opening may migrate; if it fails, `AppDatabase` preserves a
recovery copy and the app shows its blocking recovery screen, which is the only place that decision
is made. A widget must not keep retrying from the background (each failed open writes another
copy), so the first failure on an app version is remembered in
`noBackupFilesDir/widgets/database-open-failed` (one line: the version code) and widgets show
"Open Crocodyl" until the app has opened the database again. That file is the only thing the widgets
write; it holds no athlete data and is outside every backup path.

## Taps

Every tap is `PendingIntent.getActivity` for the app's own explicit `WidgetTapActivity` with the
fixed action `xyz.mdhv.formanalyser.app.action.OPEN_FROM_WIDGET` and a destination name in an extra
(no URL scheme), `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`, and a request code unique per widget and
destination.

**A tap never discards work.** `WidgetTapActivity` is not exported, has no window
(`Theme.NoDisplay`), runs in its own empty task (`taskAffinity=""`, excluded from Recents) and
finishes in `onCreate`. It looks at Crocodyl's task (`ActivityManager.getAppTasks`) and applies
`WidgetLaunch.tapAction`:

- no task, or `MainActivity` on top: it starts `MainActivity` with the destination
  (`NEW_TASK | SINGLE_TOP`, so an open main screen receives it in `onNewIntent`);
- anything else on top (a Fencing bout, whose live score is kept only in memory; a Muay Thai or
  Bouldering entry, whose draft is kept only in `rememberSaveable`; the Atelier; the Community
  Directory; the camera app taking a document photo; app settings): it brings the task to the front
  exactly as it was (`AppTask.moveToFront`, or the launcher's own intent if that fails) and does not
  navigate.

No widget intent carries `CLEAR_TOP`, which would have finished those screens and lost their
unsaved state. `WidgetLaunch` accepts only known destination names (an unknown one falls back to
Home), ignores a relaunch from Recents, and removes the extra once read; `MainShell` navigates when
its graph exists, and never pulls the athlete out of a running capture (on the Capture screen the
app just comes to the front).

## Security and the release manifest contract

Widget providers must be exported so a launcher can bind them through the system's widget service.
Each is protected by `android:permission="android.permission.BIND_APPWIDGET"`, a signature
permission only the system holds, so only the system can deliver to them. The release preflight
(`tools/release/verify_manifest_contract.py`) previously allowed no exported component except the
launcher activity and AndroidX's profile receiver; it now also allows exactly this shape (a receiver
in `xyz.mdhv.formanalyser.app.widget`, behind `BIND_APPWIDGET`, with the `APPWIDGET_UPDATE` filter
and the `android.appwidget.provider` metadata), with four new contract tests. The boundary-alarm
receiver and the tap activity are not exported. No permission was added.

## Files

- `app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/widget/`: `WidgetModel.kt` (pure rules),
  `WidgetSource.kt` (Room reads, `WidgetDatabase`), `WidgetRenderer.kt` (RemoteViews),
  `WidgetRefresh.kt` (push refresh, alarm, `WidgetRefreshReceiver`), `WidgetLaunch.kt` (taps),
  `WidgetTapActivity.kt` (where every tap lands first), `WidgetProviders.kt` (the six providers).
- `domain/WellnessAssembler.kt`: `readiness` takes an optional, already computed ACWR series.
- `domain/TrainingVolume.kt`: the volume rule shared with `ProgressViewModel`.
- `res/layout/widget_*.xml` (`widget_compact` and `widget_pads_compact` are the one-line forms;
  only `LinearLayout`, `TextView`, `ImageView` and `ProgressBar`, which
  RemoteViews allows; two lint suggestions are declined in place with the reason: pads keep their
  icon centred over the word rather than as a compound drawable, and the large pads' two rows share
  the height with nested weights), `res/xml/widget_info_*.xml`,
  `res/values*/widget_*.xml`, `res/drawable/widget_*.xml` and `ic_widget_*.xml` (icons drawn for
  these widgets, no third-party art).
- `MainActivity.kt`: accepts widget taps and calls the refresh hooks. Fully qualified names keep
  the import block unchanged, so line 108, which the privacy policy cites, did not move; the
  manifest receivers and the contract change were likewise placed below every cited line.
- `app-android/build.gradle.kts`: unit tests now include Android resources
  (`unitTests.isIncludeAndroidResources = true`), appended at the end of the file, because the
  widget tests inflate the real layouts.

## Tests

- `widget/WidgetModelTest` (plain JUnit): sizing per template and font scale (the keys form a chain
  that agrees with the fallback rule and never undercut a layout's need; portrait and landscape
  below API 31), tap actions, destinations, readiness shapes and the absence of
  reason text, refresh boundaries (including a daylight-saving night), last-scorecard selection and
  set matches, the week and active-plan rules, next physio day and adherence, rig facts, pads, and
  which tables each widget reads (none medical; only readiness touches a private one, through the
  app's own cascade).
- `domain/TrainingVolumeTest`: the shared volume rule.
- `widget/WidgetSourceTest` (Robolectric, real Room schema): first-run state for every widget; each
  widget's data; readiness with an active injury and a bereavement life event shows `◐ Caution`
  and none of their words; next physio day shows "Today" and "2 exercises" but no plan name,
  region, note or exercise name; every layout at every size inflates under RemoteViews' class filter
  (checked by temporarily adding a plain `<View>`, which made the test fail as intended); tap intents
  are explicit, immutable, scheme-free, go through `WidgetTapActivity` and carry no `CLEAR_TOP`;
  readiness with no check-in says so at every size; Room accepts the observed table names; the
  open-failure flag stops retries.
- `widget/WidgetLayoutFitTest` (Robolectric native graphics, SDK 31 and 35, xxhdpi): every layout,
  every size, at 1x, 1.3x and 2x, is no taller than its declared need.
- `widget/WidgetRuntimeTest` (Robolectric, SDK 35): the platform's own pick from the sized
  `RemoteViews` at 4x1 landscape, 2x1, 4x1, 4x3 and a 230dp-tall widget (stat and card), states
  sized too; a placed widget is drawn on app start, the boundary alarm is set with its 10-minute
  window, and a write to the rig table redraws it through Room's invalidation tracker; the alarm is
  set even when the database cannot be read; a tap with the app closed or the main screen on top
  opens the destination (no `CLEAR_TOP`), a tap with another screen on top only brings the task to
  the front, an unknown destination falls back to Home.
- `widget/WidgetBelowApi31Test` (Robolectric, SDK 26): from the reported size range, a 4x1 gets the
  medium layout in portrait and the compact one in landscape, a 4x3 large and medium, and a resize
  redraws with the new size.
- `tools/release/test_manifest_contract.py`: the widget-provider allowance and its three rejections.

## Not done (and why)

- **Goal progress.** It was a seed, but goal evaluation lives inside `ProgressViewModel.buildState`
  (six metrics, including form stability over every captured shot, and it writes `ACHIEVED` as a side
  effect). A widget would need that extracted into a process-scoped assembler first; that is a
  refactor of its own, so it was left out rather than duplicated.
- **A "counts only" configure step** (strategy §6). Not built; the reductions above are always on.
- **A level-only Readiness widget** (see [Owner decisions](#owner-decisions)). Not built.
- **Store documents.** `docs/store/privacy-policy.md` now has a short, cited "Home-screen widgets"
  section and `docs/store/play-console.md` a matching line; both are marked for owner review.
- **Fencing, Muay Thai and Bouldering** journals have no widget yet.
- **Lock screen** is not targeted. No Glance, Compose-in-widget, WorkManager or new library.

## Owner verify

Only a device can confirm these:

1. Each widget appears in the picker of Pixel Launcher, One UI, Nova and Orrery Home (when it hosts
   App Widgets), with its preview (API 31+) or preview image (API 26–30).
2. Placing, resizing through 2x1, 2x2, 4x1, 4x2 and 4x3, in portrait and landscape, switches
   compact/small/medium/large as described, and nothing is clipped at 1x, 1.3x and 2x font scale
   with the device's own font (the fit test uses Roboto; an OEM font with taller metrics could
   still clip by a few dp). After changing the font size with the app closed, the widgets may keep
   the old choice until the next redraw.
3. Dark and light system themes give the dark and light Hyle palettes; corners follow the launcher
   radius on Android 12+.
4. Scoring an arrow, finishing a card, logging a check-in or a physio session, changing the active
   rig or a plan's target redraws the right widgets within a couple of seconds while the app is
   running.
5. After midnight (and at the start of a new week) dated widgets roll over; the readiness level
   changes when a check-in passes 36 hours. OEM battery managers may delay the inexact alarm; the
   printed dates and "As of" time are there for that case.
6. Taps land on the right screen from a cold start and with the app already open, and a tap while
   capturing does not leave the Capture screen. With a Fencing bout, a Muay Thai or Bouldering
   entry, the Atelier or the Community Directory open, a tap brings that screen back unchanged (no
   navigation, nothing lost); the same with the camera open for a document photo. No flash of an
   empty window and no extra Recents entry from `WidgetTapActivity`. Check that
   `AppTask.moveToFront` is allowed from it on Android 14 and 15 (background-start rules); if not,
   the fallback (the launcher's intent) still resumes the task as it was. After a cold start from a
   widget, opening Crocodyl from its icon should bring that task back, not start a second copy of
   the main screen (the task's root intent is then the widget's; this was already so before the
   tap activity existed).
7. TalkBack reads each widget's summary and each pad's description; the shapes (`●◐○‖▸◆`) render in
   the device font.
8. A full data wipe (Settings → Data) returns placed widgets to "Set up Crocodyl".
9. After an app update whose migration fails, widgets say "Open Crocodyl" and do not keep creating
   recovery copies.
10. A widget left alone over midnight on API 26–30 (and 31+) redraws within about ten minutes of
    00:01 when the phone is awake.

## Owner decisions

1. **Readiness on a public surface.** The level reacts to injuries and life events, as in the app,
   and the medium and large layouts show the other inputs beside it, so a drop with normal inputs
   hints that an injury or a private life event is active (never which). Keep this, or ask for a
   level-only Readiness widget (level and "As of" only, at every size).
2. **Store documents.** Review the new "Home-screen widgets" section of the privacy policy and the
   matching Play Console line before the hosted copy changes.
3. **Release contract.** Approve the `verify_manifest_contract.py` change (exported widget receivers
   behind `BIND_APPWIDGET`).
