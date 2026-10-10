# Crocodyl hands-free motion sessions — cross-work-session handoff

**Purpose:** give ChatGPT Work, Codex, Claude Code and human contributors one shared product and
engineering brief for designing Crocodyl's proposed screenless / pocket-mode motion sessions.
**Status:** concept and requirements handoff only. No motion-session implementation is implied by
this document. The owner said they will develop the concept in parallel work sessions; reconcile
those proposals here before committing to architecture or user-facing measurement claims.
**Scope boundary:** this is a future cross-sport capability. It is not a prerequisite to the current
Recurve Android production release and must not destabilize or silently widen that release.

## 1. Product idea

Let an athlete prepare the phone, place it in a supported position (pocket, armband, belt, or another
sport-specific mount), lock the screen or let it go dark, train without watching the display, and
receive a clear post-session account of what the phone could and could not measure.

The product is **hands-free capture**, not generic step counting and not a promise that one phone
placement can see the whole athlete. Every sport and mounting position needs a capture recipe, a
measurement contract, and validation evidence. Sensor traces are observations of the phone, not
direct measurements of joints, force, intent, injury risk, or performance causality.

### Intended experiences

- **Running:** the phone is secured in a tested pocket, belt, or arm mount. A run can explore rhythm,
  cadence proxies, impact patterns, and repeatability. Placement-specific accuracy and left/right
  inference must be demonstrated before those are presented as facts.
- **Archery:** a secured upper-arm/armband or other explicitly tested mount records the athlete's
  movement through setup, draw, anchor, expansion, release and follow-through. The candidate analysis
  is shot-cycle timing and phone-orientation/motion stability. It must not claim bow stability or
  precise body mechanics merely because the phone is on an arm. The existing tripod-camera form path
  remains distinct and may later be paired for validation, not silently conflated with the phone IMU.
- **Bouldering:** a phone carried securely off the climber's hands can record approach/rest intervals
  and broad movement/load signatures only if a validated mount exists. The phone must never be
  loose, held, placed where it can fall onto a climber, or required to touch the wall. Safer first
  concepts include a secured waist/chest carry or a stationary phone between attempts; these are
  separate protocols and produce different evidence.
- **Fencing:** a device must be secured and explicitly approved for the training context. Do not
  suggest a phone in a pocket or on an arm while sparring unless equipment rules, impact risk,
  coach approval and safety testing support it. Start with footwork drills or a secured coach-side
  device; keep this separate from the referee-console/priority adjudication project.

These are design hypotheses, not validated promises. Each sport card should say which placement is
supported, what event it can observe, and what confidence/data are missing.

## 2. Outcomes and non-goals

### Athlete outcomes

1. A first-time athlete receives a short, visual setup walkthrough tailored to sport and placement.
2. The athlete can start, pause, resume and end without looking at the screen during a set.
3. Screen-off capture has an unmistakable start cue, discreet state cues, and recoverable stop paths.
4. The athlete can understand a post-session timeline, measurements, confidence, gaps and placement
   limitations without interpreting raw sensor jargon.
5. Raw sensor data stays local by default and is exportable/deletable under Crocodyl's existing
   explicit sharing rules.

### Non-goals

- Always-on/background surveillance, automatic upload, social comparison, or account sync.
- A universal “form score” that combines incompatible sports and body locations.
- Medical diagnosis, injury-risk forecasts, coaching certainty without evidence, or causal claims.
- Replacing Recurve camera form analysis, manual scoring, the Fencing referee console, or a coach.
- Claiming an ordinary phone observes a bow, wall, opponent, weapon contact, foot pressure, or joint
  angles when it does not.
- Silently keeping sensors/camera/microphone running after the athlete ends the session.

## 3. Proposed product vocabulary

Working name: **Motion Session** (preferred in navigation and user help). “Pocket mode” is an
accessible explanation for compatible placements, but is too narrow as the feature name because
archery and other protocols may use a securely attached mount. “Screenless” describes the interaction
state, not a separate sport or analytics engine.

Sport + protocol examples:

```text
Running / Pocket or belt / Easy run
Archery / Upper-arm armband / Shot-cycle study
Bouldering / Secured waist carry / Between-attempt session
Fencing / Coach-side device / Footwork drill
```

Do not show unsupported combinations as selectable merely because they are easy to implement.

## 4. Session lifecycle and interaction requirements

### Before capture

- Select sport, drill/session intent, placement, dominant side where relevant, and audio/haptic cue
  preference.
- Show a placement illustration and a short fit check: phone secure, orientation known, case/mount
  stable, no obstruction or unsafe equipment conflict.
- Offer a 5–10 second stationary calibration only where it is meaningful. Record calibration status;
  never silently re-zero a moving signal.
- Preview what will be recorded, why, retention policy, estimated battery use, and any permission.
- Tell the athlete which metrics are supported for this precise sport + placement + device/sensor
  combination. If none are supported, allow a raw research session only behind an explicit beta
  label and export controls.

### During capture

- Start after explicit user action; confirm via configurable haptic and/or audio. Screen dim/lock may
  follow only after the app's capture state is durable.
- Keep a foreground service only if needed to continue sampling with the display off; provide the
  required persistent notification with a clear Stop action. Do not rely on background sensor
  callbacks without validating Android platform behavior.
- Support pause/resume and interruption recovery without stitching across an unknown gap.
- Provide discreet, configurable cues. Never require the athlete to watch a visual dashboard while
  running, climbing, shooting or sparring.
- Stop sensor work immediately on stop, cancel, failure, permission loss or service teardown; flush
  bounded buffers and commit session data atomically.
- Handle unavailable sensors, sensor accuracy changes, timestamp gaps, thermal/battery limits,
  app/process death, screen lock, notification denial, and incoming calls visibly and safely.

### After capture

- First show the sequence of captured activity/rest intervals and sensor coverage, not a single
  opaque score.
- Show each metric with units, validity/confidence, placement, sensor source, missing-data warning,
  and minimum sample requirements.
- Distinguish **observed** (e.g. acceleration peaks), **derived** (algorithmic cadence/event), and
  **interpreted** (possible technique cue) information.
- Allow edit/correction of detected event boundaries before they become training history.
- Save a session summary when optional analytics fail; a processor failure must not lose raw capture.
- Use existing Crocodyl local-first database and export/privacy boundaries; agree a schema migration
  before storing production data.

## 5. Measurement contracts by sport (hypotheses to validate)

| Sport/protocol | Candidate signals | Candidate outputs (only after validation) | Must not claim |
|---|---|---|---|
| Running: pocket/belt | accelerometer, gyroscope, timestamps; optional user-entered distance/route | step-event cadence estimate, repeated-interval timing, impact proxy with device/placement qualification | ground-reaction force, true stride length, injury risk, reliable left/right balance from arbitrary pocket orientation |
| Running: armband | accelerometer, gyroscope, orientation | cadence/event rhythm and stability of phone motion | direct footstrike or joint kinematics without correlated validation |
| Archery: upper arm | accelerometer/gyro, orientation, explicit user button or optional voice marker | shot-cycle event timing, within-athlete phone-motion variability around labelled phases | bow aim stability, release biomechanics, joint angles, point impact, or “good form” from one sensor alone |
| Bouldering: secured carry | accelerometer/gyro; explicit attempt/start/finish markers | attempt duration, rest intervals, broad movement intensity/consistency | hold sequence, grade, wall contact, route completion, fall detection certainty, or injury risk |
| Fencing: safe drill only | accelerometer/gyro; optional coach-entered drill markers | drill interval and broad movement rhythm | touch/point, right-of-way, opponent action or bout result; these belong to validated referee/video systems |

Metrics remain disabled until the validation plan defines dataset, cohort, reference instrument,
device matrix, acceptance thresholds, error bars and failure behavior. Never tune thresholds on a
single owner's sessions and present them as population-valid.

## 6. Android technical direction (proposal, not an API commitment)

### Capture layer

Keep the platform layer thin, with a pure Kotlin core that can be replay-tested from recorded traces.
Candidate boundaries:

```text
MotionSessionSetup / SportProtocol / PlacementProfile
        ↓
Android SensorGateway + foreground capture owner
        ↓ timestamped, unit-bearing raw samples + gap/accuracy events
bounded append-only local chunk store (session ID, monotonic clock, schema version)
        ↓
sport-specific deterministic processors + validity/confidence result
        ↓
review UI / existing session journal / consented .croc export
```

Responsibilities:

- `SensorGateway`: enumerate sensors and capabilities; register only required sensors; preserve
  `SensorEvent.timestamp`, type, accuracy, axis convention and requested/observed sample rate.
- `CaptureSessionOwner`: single serialized lifecycle owner with generation/session token, explicit
  start/pause/resume/stop states, persistence-before-UI ordering, foreground notification, cleanup
  and duplicate-start rejection. Reuse the reliability lessons in Crocodyl's camera capture owner
  and `PoseFrameBuffer`, not their camera-specific data model.
- `PlacementCalibration`: store placement, orientation setup and calibration transform per session;
  record changes and uncertainty. Device axes are tied to natural device orientation; normalize only
  with recorded transformations and tests.
- `TraceStore`: bounded/chunked local writes, monotonic event time, schema version, checksums and
  recovery semantics. Decide raw-trace retention and deletion with product/privacy owner before
  shipping; don't retain unbounded high-rate arrays in Compose/ViewModel memory.
- `MotionProcessor`: pure deterministic input/output; no Android UI types. Processor version,
  thresholds and provenance travel with each result for reproducibility.
- `SportProtocol`: declares eligible sensors, supported placements, prompts, event markers,
  processors, validated metric IDs and safety notes. Unknown combinations fail closed to capture-only
  or an explicit unsupported message.

### Sampling, time and platform caveats

Do not hard-code a universal sampling frequency in the product contract. Android's requested sensor
delay is a hint and the delivered event rate must be measured from timestamps; request no faster than
the sport protocol requires. Account for the Android 12+ motion/position sensor rate limit, available
hardware/software sensor differences, missing events, timestamp jitter, and battery cost. Android
guidance recommends unregistering unused sensors; continuous motion sensors do not deliver normally
to background apps on Android 9+ unless capture is appropriately owned by a foreground service.
These are implementation requirements to validate against the target SDK and device matrix, not
permission to start an always-on service. See [Android sensor overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview),
[motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion),
and [position sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position).

Evaluate and document if screen lock also requires partial wake lock, notification permission,
foreground-service type declarations, and Play policy review. Use the least privileged combination;
test actual screen-off behavior on physical devices rather than inferring it from an emulator.

### Data model proposal

Do not finalize names until existing Room schema and exchange schema are reviewed. Minimum logical
records:

- `MotionSession`: ID, sport/protocol ID + version, local athlete ID, start/end monotonic and wall
  time, status, placement ID, device capability summary, calibration reference, processor version,
  data quality and interruption/gap summary.
- `MotionSampleChunk`: session ID, sequence number, first/last timestamp, sensor descriptors,
  encoding/version, checksum, bounded compressed payload, committed state.
- `MotionEvent`: user- or algorithm-proposed event type, timestamp, source, confidence and correction
  history (start/pause/repetition/rest/shot marker/attempt marker/finish).
- `MotionMetric`: metric ID/version, unit, value(s), interval, observed/derived/interpreted class,
  confidence/validity, sample count, placement/sensor dependencies and exclusions.
- `MotionAnnotation`: user label, cue, subjective effort/feel, optional link to related archery
  scoring/camera session; cross-link only with explicit user choice and temporal certainty.

Private/raw high-resolution samples should not be exported by default. Summary export must explicitly
name metric versions, include consent preview, avoid precise location unless requested, and continue
to use Crocodyl's signed, privacy-reviewed file exchange. Never let an export include sensor chunks
just because it includes summary rows.

## 7. Safety, privacy and trust requirements

- No hands-free mode can imply distraction-free if it still needs a screen glance, operation, or
  manual phone handling mid-activity.
- Do not put phone on bow, blade, climbing wall, helmet or protective gear without discipline rules,
  hardware safety and coach/owner validation.
- Fencing protocols must explicitly distinguish footwork drills, non-contact drills and live bouts.
- For archery, setup/teardown and all cues must not encourage handling a phone while at the shooting
  line or during a shot; range commands and coach instructions take precedence.
- Running cues must not mask traffic, coach, event or environmental audio. Default haptic-first;
  any audio option must be opt-in and have a safety disclaimer.
- Clear persistent “recording” and “paused” state; tactile/voice confirmation; minimum accessible
  screen brightness and stop entry when the screen is used; large off-screen/notification stop
  action.
- Local-first. No location, raw sensor trace, microphone or camera capture without distinct informed
  consent and visible product state. Never start capture on app launch or restart automatically after
  a crash/reboot.
- Screenless is not “no consent”: explain what is recorded before the user locks the screen.
- Provide a one-action stop, automatic maximum-session/idle bounds where appropriate, battery/heat
  warning, and a post-session “data saved / not saved” confirmation.
- Treat motion-derived metrics as training aids, not medical advice. Avoid injury prediction language.

## 8. Validation plan and release gates

### Stage A — concept and sport-specific protocols

- Owner/coach picks a first sport. Recommended first engineering pilot is Running because it has a
  comparatively clear phone-placement hypothesis and does not compete with current Recurve camera
  capture. Archery may be first if the owner's primary need outweighs lower signal validity.
- Choose one placement only; specify fixture, secure fit, handedness/orientation, allowable activity,
  and non-supported contexts.
- Define event ground truth and 2–3 candidate metrics. Separate testable observations from coaching
  interpretations.
- Review sensor, persistence, privacy, screen-off service, notification and migration scope.

### Stage B — capture plumbing without claims

- Sensor capability screen; explicit permission/notification explanation; start, pause, stop; screen
  off/on; process death; timestamp gaps; service termination; atomic persistence and recovery.
- Store/replay synthetic traces. Verify duplicate events and gaps; benchmark CPU, disk, battery and
  heat on low/mid/high Android devices.
- No metric should appear as validated in this stage.

### Stage C — labelled dataset and deterministic prototype

- Obtain informed, private, consented volunteer recordings across at least multiple devices and
  placements. Keep reference labels and sensor traces separate from public repo unless explicit
  release permission exists.
- Compare against an appropriate reference (e.g. independent manual/video event annotation or
  sport-specific instrument); report error distribution, missed events, false positives and
  device/placement strata.
- Pre-register acceptance thresholds with coach/owner. Keep a holdout dataset. Test repeatability
  within the same person and generalization across users/devices separately.
- Every processor has golden traces, boundary conditions, and expected uncertainty behavior.

### Stage D — closed beta and release

- On-device beta label and exportable diagnostic report with explicit sharing preview. No cloud
  dependency.
- P0s: no lost committed session, no silent stop/start, no unbounded raw storage, no unsafe cue,
  no false validated claim, no data leakage in `.croc`, background/notification and device matrix
  pass.
- Release only when each launched sport/placement's validated metrics have evidence and limitations
  reviewed. Other sport cards may remain “planned” and unavailable, not fake-working.

## 9. Proposed implementation milestones

1. Owner consolidates proposals from ChatGPT Work and Claude into the choices below.
2. Research/UX spec: protocol registry, placement artwork, safety language, result composition.
3. Android capture service + local trace chunks + lifecycle tests, behind an internal beta flag.
4. One sport/placement prototype + trace replay and instrumentation tests.
5. Dataset/reference validation and measurement card acceptance.
6. Athlete-facing post-session review, data controls, export/privacy integration.
7. Cross-sport contracts only after the first sport survives real-device validation.

Do not build all four sports as shallow cards in milestone 3. First make one supported protocol trustworthy.

## 10. Decisions to consolidate from parallel work sessions

Before implementation, record an owner decision for each:

1. Which pilot sport and why: Running, Archery, or another?
2. Exact phone placement/fixture for the first protocol and how user receives/obtains it.
3. First two or three outputs, each with measurement definition, unit, error tolerance, and excluded
   claims.
4. Whether first iteration is screen-off sensor-only or includes optional camera/voice event markers.
5. Audio, haptic, silence, interruption and emergency-stop behavior.
6. Raw sensor retention duration, deletion UX, backup/export inclusion policy, research consent, and
   whether beta diagnostics can leave the device.
7. Foreground service and notification approach after current Android target/Play requirements are
   checked.
8. Existing Room entities/migrations and `.croc` schema impact.
9. What is a coach/owner acceptance test versus a future research question.
10. How screenless mode fits the Crocodyl Home/Train navigation without obscuring ordinary recording.

## 11. Parallel-agent prompts

### ChatGPT Work / product and research

> Read `docs/crocodyl/MOTION_SESSIONS_HANDOFF.md` first. Develop the product/measurement proposal,
> safety and privacy review, sport-placement hypotheses, competitor/research questions, and a
> validation protocol. Do not imply sensor metrics are accurate without reference evidence. Do not
> implement code or change Crocodyl scope; return explicit decisions, open questions and testable
> acceptance criteria for reconciliation into the handoff.

### Claude Code / repository architecture

> Read `docs/crocodyl/MOTION_SESSIONS_HANDOFF.md`, `docs/architecture.md`, current Room entities,
> capture lifecycle code and release checklist before proposing implementation. Inspect actual
> current source; do not rely on old handoff claims. Produce an architecture/design review and
> migration/test plan for one sport + one mount only. Do not add placeholder metrics, use AI images,
> or weaken release gates. Do not implement until the owner selects the protocol and approves the
> measurement contract.

### Codex / implementation after owner approval

> Implement only the approved first sport/placement from the reconciled handoff. Start by checking
> branch/worktree state and existing capture abstractions, preserve user changes, add tests and
> release checks, document evidence, use no generated imagery, and commit every coherent change with
> context. Keep the capability local-first and unavailable rather than misleading when device,
> placement or validation prerequisites are absent.

## 12. Current Crocodyl context to carry into those sessions

- Current target: Recurve Android 0.6 production readiness; do not block that release on this future
  cross-sport capability.
- Android app: Kotlin + Compose, CameraX/MediaPipe Recurve form capture, Room/local-first storage,
  signed portable `.croc`/backup, Hyle visual language. Main UI in
  `app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui`.
- Existing reliable capture work: serialized deferred CameraX binding, `PoseFrameBuffer` generation
  guard, asynchronous session-start gate, startup transactions and failure-injection tests. Reuse
  lifecycle principles, not camera records or scoring assumptions.
- Sports currently visible in Home include Recurve, Fencing (manual referee/scaffold), Muay Thai and
  Bouldering journals. Their visible cards do not mean motion capture is implemented or validated.
- The app's Home, Train setup and Progress now have initial cinematic/Hyle refresh passes, with
  API-35 emulator captures in `docs/crocodyl/visuals/screenshots/`. Other destinations remain
  unfinished. See `docs/crocodyl/visuals/CINEMATIC_APP_REFRESH.md`; this is separate from
  motion-analysis validity. Use licensed actual photography only; no AI-generated imagery.
- Last recorded Recurve QA build: `0.6.0-progress-editorial-qa`, versionCode 655, QA signer, not
  production approved. Production keystore/CI signing, physical-device/range matrix and owner
  release sign-off remain outstanding.

### Parallel work-session protocol

ChatGPT Work and Claude sessions should treat this document as the shared source of truth, but each
should return a proposal rather than independently committing conflicting architecture. Ask each
session to include: (a) the exact protocol and placement it recommends, (b) explicit assumptions and
conflicts with this document, (c) named source links and access dates for platform/research claims,
(d) measurable acceptance thresholds and reference instrumentation, (e) safety/privacy failure
cases, and (f) decisions that only the owner can make. Preserve the two outputs verbatim in the
project task or attach them as review notes, then reconcile them into this file with an owner decision
log before any implementation. Do not copy unreviewed external code or generated assets into the
repository. Once a pilot protocol is approved, implementation work should use a separate branch and
the commit/context-documentation discipline already used for the Recurve release.

## 13. Sources and platform verification

Android's official sensor guidance explains device-axis conventions, that requested delays are
suggestions whose delivered rate must be measured from event timestamps, the Android 12+ rate limit
for certain motion/position sensors, and the need to unregister sensors when not needed. It also
documents continuous-sensor background restrictions from Android 9+. Recheck the official docs and
Play requirements for the actual target SDK at implementation time:

- [Sensors overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)
- [Motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion)
- [Position sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position)
