<!-- markdownlint-disable MD013 MD024 MD025 MD060 -->

# Crocodyl Blueprint v2.1 — Architecture, Requirements, Roadmap & UX

This continues `01-product-direction.md` and captures the remainder of the Crocodyl Product Blueprint v2.1 work produced in ChatGPT on 2026-08-10.

## 6. Platform and module architecture

Target architecture: Android athlete app, static web viewer, future iOS app, and paid Coach workspace share a local core. The core exposes a Sport API; the Archery family contains Olympic Recurve first, then Compound and named Korean/traditional profiles. Exchange/privacy, model adapters, and a Baseline adapter remain explicit seams.

The shared core owns identity/consent; athlete/coach relationships; sessions/attempts/observations/results/goals/assignments; equipment containers/interventions; wellness/body/calendar/privacy; portable exchange/backups; model registry/evidence-grounded AI; and the Baseline seam.

Each sport module must supply a `SportDescriptor`, `DisciplineProfile`, `SessionTemplate`, `AttemptSchema`, `ResultSchema`, `CaptureProtocol`, `FeatureSet`, `EquipmentSchema`, `DrillCatalog`, `ExchangeMapper`, and `InsightAdapter`. `BowType` is not a sufficient discipline model.

Multiplatform evolution is incremental: make exchange/scoring/domain types portable first, keep CameraX/Room/Keystore/Health Connect/Compose platform-local, compile portable parsers/analytics to JS/Wasm where useful, abstract crypto/files/database, and add iOS only after exchange/Recurve scoring stabilize.

### Longitudinal observation contract

Every record preserves stable ID; athlete/sport/discipline/session/end/optional shot scope; event time or validity interval; source/provenance; raw/normalized value/unit/confidence; algorithm/model version; correction and authoritative state; privacy class; and linkage resolution: `SHOT_CONFIRMED`, `SHOT_INFERRED`, `END_ONLY`, `SESSION_ONLY`, `DAY_WINDOW`, or `PERIOD_WINDOW`.

The join engine may aggregate to a coarser resolution but may never promote coarse data to a finer one. Unordered target-photo impacts are end-level until explicitly reconciled. Observer entries retain declared score/sector/time/input/confirmation and remain inferred unless unambiguous confirmation promotes the link. Conditions and habits retain their real time windows. Rig snapshots are immutable across their active intervals.

## 7. Core domain objects

The stable vocabulary includes Athlete, Sport Profile, Discipline Profile, Session, Attempt, Observation, Target Observation, Observer Score Event, Correction, Result, Equipment Item, Equipment Model, Equipment Offer, Equipment Evidence, Rig, Training Plan, Habit Event, Intervention, Causal Study, Goal, Assignment, Insight, and Exchange Envelope. Sport-specific data lives in versioned typed payloads rather than leaking into shared engine columns.

## 8. Functional requirements

### Onboarding and profile

No account is required. Athlete/Coach/Both, sport/discipline, handedness/units/draw-length/initial rig are set with advanced wellness/model/notification choices deferred. Camera permission is requested at capture time. Local storage, backup responsibility, and network allowlist are explained plainly.

### Home and Quick Add

Resume last session in no more than two taps. Quick Add covers Practice, Score Round, Scan Target, Unscored Arrows, Plan Session, Wellness, Rest, Workout, Equipment Change, Goal, Event, Note, and Import. Personalization is local-recency/pins only. Home prioritizes actionable results over a wall of statistics and supports quiet/hiatus mode.

### Training session

Scoring and form capture are independently optional. Last-used discipline/rig/venue/distance/target/arrow count prefill. Check-ins are optional. Quality gates block unreliable analysis. Scores, plot, note, equipment change, and conditions remain available inside the active session. Sessions autosave and reconcile before finishing. Stable shot IDs are reused only when linkage is valid. Combined sessions explicitly choose End Scan, Live Observer, or Paired Target Cam.

### Scoring and plotting

Cold-thumb keypad supports X, 10–1, miss, undo/correction/auto-advance. Numeric and plotted scoring stay synchronized. Recurve pack includes WA 18 m, WA 60 m, WA 70 m/720, Olympic set match, custom practice, and unscored arrows; round definitions are versioned JSON. State includes ends, totals, X/10 counts, set points, PB eligibility, completion. Plot analytics include group center/radius/spread/drift/normalized coordinates. End Scan candidates are always editable and retain machine/correction/final provenance. Live Observer supports offline ring + optional 3×3-sector grammar, immediate acknowledgement, `repeat`, `undo`, and correction. Sector-only inputs never become fabricated precise coordinates. Paired Target Cam remains later/experimental.

### Form capture and analysis

BlazePose capture/segmentation must be range-validated. Normalize handedness once before segmentation/feature extraction. Capture quality records framing/visibility/drop/lighting/calibration. Review exposes phases, features, confidence, baseline, deviations, and source/algorithm versions. Side-by-side comparison aligns equivalent phases. Measurement, inference, coach note, and AI explanation remain visually distinct. Causality is never inferred from simple association.

### Progress and insights

Local/free views include scores, PBs, volume, form stability, feature trends, load, and equipment context with meaningful filters. Intervention comparisons mark technique/equipment/physio changes. Missing/unshared data remains unavailable rather than zero. Association cards show direction/effect/uncertainty/sample/window/resolution/confounders. Causal contribution percentages are paid Baseline outputs only when an eligible study exists and must disclose model, assumptions, adjustment set, sensitivity, calibration, and a “not estimable” fallback.

### Equipment, rigs, and tuning

Manage multiple rigs and immutable session snapshots. Recurve equipment covers riser, limbs, string, arrows, rest, plunger, clicker, sight, stabilizers, tab, and accessories. Individual arrows retain lifecycle/shot/incident history. Tuning history is versioned. OTF follows measured > transparent estimate > marked weight. Owned instances, shared catalog models, evidence, and offers are separate. Artifact cards expose provenance/confidence and never invent a universal power score. Recommendations compare keep/adjust/service/repair/borrow-test/buy; commission and merchant state cannot affect ranking.

### Conditions, calendar, goals

Manual conditions work offline; optional external weather is confirmed/corrected. Venue can store indoor/outdoor and optional azimuth. Competitions connect round, rig, conditions, result, rank, notes. Calendar combines training, rest, competition, coach assignment, equipment service, recovery/life context, and goals. Training plans support phases/focus/volume/intensity/taper/recovery without medical prescription.

### Wellness/body/health

Check-ins, rest, mood/life events, cycle, medication, pain/injury/physio/documents are optional modules. The 52-region body contract is stable. Medical/private classes use stronger sharing ceremonies. Readiness explains reasons and never blocks training. Injury-risk is heuristic/general-wellness, not diagnosis. Soreness/stiffness/pain are distinct. No single ACWR controls readiness. Recovery actions come from versioned qualified-reviewer content; LLMs may explain but not originate medical instruction. Suggestions remain overrideable.

### Exchange, backup, identity

`.croc` is the signed versioned person-to-person envelope; `.crocbak` is device recovery. Both have published schemas, compatibility fixtures, fail-closed parsing, consent previews, signature checks, dedupe/conflict handling, and privacy controls. Pairing uses public-key identity/QR-file cards/TOFU and visible mismatch quarantine. Live Observer pairing is short-lived and active-session-only.

### Static web viewer

Static PWA only. Drag/drop or picker, browser-local parsing, no report-byte upload, signature/schema/consent inspection, conditional sections for sessions/scores/form/equipment/body/calendar, local PDF/CSV/share exports, ephemeral-by-default storage with explicit IndexedDB opt-in/clear actions. No secrets needed for read-only viewing.

### AI/model support

Offline deterministic rule coach is guaranteed. Fixed intents cover session summary, evidence explanation, drill suggestion, comparison. Every output retains provenance. BYOK supports DeepSeek plus OpenAI/Anthropic/Google behind a common adapter. Model registry records license/source/checksum/size/quantization/runtime/RAM/task support. Guided local import is mandatory. Raw media, API keys, private and medical data are withheld from providers by default. “What was sent/withheld” is inspectable. General free chat is deferred until fixed intents pass evaluation.

### Human Coach workspace

Paid official entitlement, open-source implementation. Roster, attention inbox, athlete detail, timestamped observations, drill/plan assignment, compare, coach-local scorebook, signed `.croc` round trips, and retention controls are required. Missing/unshared data is never interpreted as normal.

### Baseline adapter and evidence ladder

Crocodyl sends versioned consent-filtered `InsightInput`; Baseline returns versioned `Insight` with result, provenance, facts, effect estimate, uncertainty, evidence grade, applicability window, and invalidation conditions. Evidence levels are descriptive → association → temporally supported association → individualized intervention effect → eligible causal contribution. “Strong association” is based on predeclared effect thresholds, not p-value alone. A causal percentage is a posterior probability that a factor materially contributed at least a declared meaningful effect under the stated design/model/assumptions; it is never “percent of performance caused.”

## 9. Privacy and network model

Data classes: Shareable, Raw media, Medical, Private, Secret. The network allowlist covers explicit BYOK provider calls, user-initiated model download/catalog, optional weather, static web assets, signed static equipment/model artifacts, user-tapped manufacturer/merchant links, and app-store licensing/update checks. Health Connect/BLE/Wear/local file exchange/local analysis do not require Crocodyl servers. No mandatory telemetry; diagnostics are rolling/local and manually exportable after redaction.

## 10. Non-functional requirements

All P0 athlete/coach workflows work offline after installation. Writes are transactional/autosaved. Every migration has fixture/update-install coverage. Capture reports thermal/drop degradation honestly. Supported device matrix replaces vague support. Accessibility includes 48 dp targets, scalable text, screen-reader labels, reduced motion, shape/pattern redundancy. Security uses Keystore-backed secrets and fail-closed parsing. Reproducibility requires deterministic/versioned inputs. Derived results expose source/formula/version/gate/confidence. Target vision, observer voice, temporal integrity, catalog provenance, commerce independence, health boundaries, and causal validity all have explicit validation gates.

## 11. Archery v1 definition of done

Archery v1 requires: 30 consecutive real range sessions across at least three supported Android device classes without blocking crash/data loss; two-tap/<10s returning start; ≤20s median six-arrow manual entry; process/update-safe scoring; measured correction-inclusive End Scan; live observer tap/voice while form capture continues; documented Recurve capture quality; preserved linkage resolution; auditable equipment provenance/commerce neutrality; reviewed training/recovery flows; complete coach file loop; 20 repeated `.croc` transport round trips; cross-version backup restore; network-isolated static web; rule/AI factuality/privacy; accessibility/range ergonomics; and voluntary four-week continued use by coach + at least five club archers.

---

# Part II — Competitor parity

Representative benchmarks include MyTargets, ArcherySuccess, Artemis, CapTarget, ArcherSense, ArcheryBuddy, AimTrack, Arrow Tempo, Ianseo ScoreKeeper NG, and BowSmith. The blueprint deliberately separates advertised capability from verified quality.

Before v1 Crocodyl must match credible range scoring basics: fast numeric scoring, standard/custom rounds, plotting/group analysis, editable automatic End Scan and Live Observer, PB/history/sight marks, equipment/rig context, training/recovery/calendar, reliable form review, athlete-to-coach reporting, Android reliability and offline use.

It must exceed through the *combined* system, not every isolated feature: sovereign coach loop, integrated evidence, honest local intelligence, desktop-without-upload, open implementation, three-mode scoring, and auditable equipment guidance. Deliberate non-parity: social feeds, public leaderboards, cloud profiles, live global matchmaking, and server-operated tournaments.

---

# Part III — Release backlog

Outcome-gated sequence:

| Release | Outcome |
|---|---|
| 0.5.1 Prove It | Establish install/device/range truth and remove destructive recovery. |
| 0.6.0 Score Fast | Deliver a credible manual Recurve scorer and weekly club wedge. |
| 0.7.0 See and Score the Shot | Validate form, End Scan, Live Observer, reconciliation, rule coach. |
| 0.8.0 Carry the Data | Complete `.croc`, `.crocbak`, consent, identity, import/export. |
| 0.9.0 Coach the Squad | Paid human Coach pilot, file-based round trip. |
| 0.10.0 Open on Desktop | Static local-only web viewer. |
| 0.11.0 Choose the Intelligence | Common provider layer, DeepSeek/BYOK/local-model UX. |
| 0.12.0 Recurve Complete | Close equipment/training/recovery/accessibility/parity gaps. |
| 1.0.0 Club Release | Harden, pilot, document, distribute. |
| 1.1.0 Baseline Insights | Paid eligible association/causal contribution. |
| 1.2.0 Compound | Real Compound discipline. |
| 1.3.0 Korean/Traditional | Practitioner-validated named tradition profile(s). |
| 1.4.0 iOS | Native athlete parity. |
| 2.0.0 Second Sport | Fencing validates sport-neutral core. |

Cross-release work requires migrations, versioned fixtures, performance budgets, threat modeling, accessibility every release, explicit evidence-status labels, separate AI/Human Coach domains, end-to-end correction/linkage versioning, catalog governance, and named health-content review.

Open decisions include public license, Coach purchase model, non-Play entitlement, initial round pack, final scoring sequence, web backup/sensitive access, KMP migration, local-model runtime, Baseline details, first non-archery sport, and observer vocabulary.

---

# Part IV — UX architecture

## Experience model

Global intent: **Improve repeatable performance through honest evidence.** Laws under intent: **Local by default · Evidence before advice · Athlete controls sharing.** Core objects include Athlete, Sport Profile, Discipline, Session, Round, End, Shot, Target Observation, Observer Score, Rig, Equipment Item/Model, Arrow, Training Plan, Habit Event, Venue, Competition, Goal, Assignment, Body Region, Injury, Physio Plan, Coach Relationship, Exchange Item.

Core business results: Ready to Train; Session Completed; End Reconciled; Form Reviewed; Performance Understood; Equipment Stabilized; Equipment Decision Supported; Training Balanced; Coach Feedback Applied; Data Carried Safely.

### Athlete workspace

- Home — improve performance; Active Rig/Plan/Competition/Assignment; Ready to Train, Work Completed, Latest Change, Coach Feedback.
- Train — complete purposeful session; Session Template/Rig/Venue/Round; Session Ready, End Recorded, Session Completed.
- Progress — understand what changes performance; Metric/Period/Intervention/Goal; Trend Explained, Goal on Track, Equipment Stabilized.
- Body — train without ignoring body; Body Region/Injury/Physio; Body State Logged, Recovery Followed, Context Shared.
- Calendar — balance practice/competition/recovery; Session/Rest/Competition/Event; Week Balanced, Plan Followed, Upcoming Work Understood.

Persistent utilities: Quick Add, Equipment/Quiver, Import Inbox, Settings/Identity, Workspace switcher when Coach entitlement exists.

### Coach workspace

Roster, Attention, Scores, Library as primary destinations; Compare/Settings secondary. Athlete detail retains Overview/Sessions/Form/Scores/Body/Equipment/Notes, limited to explicitly shared data.

### Web viewer

Intent: **Open my performance file privately.** Before selection: **Your file is processed in this browser. Crocodyl does not upload it.** After opening, show only included Overview/Sessions/Scores & Plots/Form/Equipment/Body/Calendar/File & Consent Details. Missing data says “Not included in this export,” not “No data.”

## Quick Add

Persistent thumb-reachable `+`; pinned actions first, local recency thereafter. Default actions: Quick Practice, Score a Round, Unscored Arrows, Plot an End, Wellness, Rest, Workout, Equipment Change, Scan Target, Plan Session, Goal, Event, Note, Import. During sessions it adapts to End, Scan Target, Observer Score, Note, Equipment Change, Condition, Stop. Sensitive/destructive actions never become one-tap shortcuts.

## Session modes and review

Manual, End Scan, Live Observer, Paired Target Cam each disclose people/devices, timing, and maximum honest linkage. Active layout preserves session identity/progress, main input mode, Score/Plot/Capture/Scan mode switch, context, Undo/Next/guarded Stop, and actionable quality warnings. Live Observer shows latest acknowledged entry/sequence/connection/correction without obscuring camera framing.

End Scan: photo → detect/calibrate → correction inspector → reconcile → confirm authoritative end → store candidates/corrections/final. Live Observer: shot detected → entry window → ring/sector tap or phrase → acknowledgement → confirm/undo/correct → timestamped event → end reconciliation. Voice remains small and reject-over-guess.

Review order: result summary; score/plot; form evidence/confidence; provenance/linkage; relevant rig/conditions/body context; rule insight; optional model explanation; coach share. Provenance sits adjacent to output.

## Coach exchange

Athlete previews consent and signs `.croc` → messaging/files transport → Coach verifies/imports → reviews/creates signed assignment → transport back → Athlete imports/completes/acknowledges → next report carries acknowledgement. Transport is never called sync. Every import shows sender/kind/date/size/consent/signature/merge effects. Changed identities quarantine. Coach observations are separate signed records, never edits to athlete facts.

## Equipment/training/evidence UX

Equipment workspace tabs: Owned, Explore, Compare, Service. Artifact cards separate identity, compatibility, verified attributes/provenance, evidence lanes, owned-item history, community observations, and offers. Recommendation sheets start with keep/adjust/inspect/service/repair/borrow-test/buy, why/effect/uncertainty/alternatives/evidence/next test; offers come last and cannot steer ranking.

Calendar overlays: Plan, Completed, Load, Recovery. Body check-in is fast and optional. Recovery cards answer what changed, evidence/missingness, certainty, conservative options, and escalation. Athlete can override suggested rest; override becomes context, not “noncompliance.” Injury language is concern/context, never deterministic prediction.

Association/Baseline cards explicitly label evidence class: Strong association; Likely contribution; Not estimable. Every card shows effect, interval, outcome, period, count, resolution, contributing facts, missing confounders, grade, model/version, and what would strengthen it. Causal cards add threshold, design, adjustment set, overlap, sensitivity, calibration. Never “caused 74%.”

## Hyle/accessibility law

AMOLED-black/violet-cyan visual system; provenance uses material/luminance/shape plus labels rather than hue alone. Motion around 300 ms with Reduce Motion. Haptics confirm actions and are configurable. No celebratory motion around injury/hiatus/sensitive logging. Range ergonomics: ≥48 dp, sunlight/dark tests, glove/cold-thumb layouts, persistent undo, session-aware wake, equal-speed tap fallback for voice.

Content is short/literal/nonjudgmental. Recorded/estimated/inferred/suggested are distinct. No fake certainty or motivational scolding. Medical-adjacent copy gives context and qualified-help escalation without alarmism. Hiatus/grief removes streak pressure.

Every major surface defines Empty, Building baseline, Partial data, Stale, Low confidence, Offline, Permission denied, Storage constrained, Import conflict, Unsupported version, and Hiatus behavior.

## UX validation and acceptance

Pilot participants: primary coach, 5–10 Recurve archers, at least one left-hander, older/low-vision participant if possible, and at least three Android performance tiers. Core tasks cover install/first practice/resume/scoring/plotting/form/share/coach assignment/desktop/backup/End Scan/observer tap+voice/equipment evidence/recovery/evidence-class comprehension.

Track task success, session/end times, corrections, target CV metrics, observer acceptance/false acceptance/latency/linkage, form miss/repeatability, share/import conflicts, coach review time, voluntary reuse, privacy trust, commerce trust, and evidence comprehension. Pilot feedback stays manual/opt-in; no background analytics.

v1 UX acceptance requires: optional advanced setup, two-tap recurring starts, one unified session, End Scan/Live Observer continuity and correction, tap recovery from voice failure, provenance on AI, distinct association/causal states, neutral equipment recommendations with adjacent disclosures, conservative/overrideable recovery, explicit pre-share destination/categories, import preview/integrity, local-processing web, paid surfaces that never block athlete-owned data, and accessibility/range ergonomics at the same release gate as functionality.

## Immediate next actions

1. Approve/edit governing decisions.
2. Audit actual repository against status claims.
3. Convert v0.5.1 into issues with owners and device/range fixtures.
4. Resolve license and official Coach entitlement before v0.9.
5. Recruit the coach and initial Recurve cohort before more horizontal breadth.
6. Keep Baseline design in a separate specification while preserving the adapter boundary.
