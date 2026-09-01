<!-- markdownlint-disable MD013 MD024 MD025 MD060 -->

# Crocodyl Product Blueprint v2.1

**Status:** Proposed source of truth  
**Date:** 2026-08-10  
**Primary launch:** Android · Olympic Recurve · club pilot  
**Surfaces:** Athlete app · paid Coach workspace · static web viewer · future iOS  
**Operating model:** Local-first · no maintained backend · open-source code · portable files

---

## Document authority

This blueprint rewrites the product direction contained in `CROCODYL_FEATURE_SPEC.md` rev 1.3 and the Phase 1–3 briefs. It uses `Crocodyl Status.md` as the implementation baseline, but implementation claims remain provisional until the source repository and APK are audited on real devices.

Where this document conflicts with the older documents, this document governs after owner approval.

### Governing decisions

1. **Olympic Recurve is the first complete discipline.** Compound follows; Korean and other traditional forms follow after the discipline contract is proven.
2. **Android is the first native client.** A static, local-only web viewer is required for v1. iOS follows after the shared contracts stabilize.
3. **There is no maintained application backend.** Core training, scoring, analysis, storage, sharing, coach exchange, and browser viewing must remain usable without one.
4. **The athlete product is free and open-source.** Single-athlete scoring, form analysis, equipment, wellness, progress, local AI, BYOK, export, and backup are not paywalled.
5. **Human Coach features are paid and open-source.** The official binary gates the multi-athlete workspace, but the source remains public.
6. **Baseline is a cross-app engine.** Crocodyl integrates with it through a stable adapter. Cross-app and cross-cutting Baseline insights are paid and open-source. Baseline internals are intentionally not specified here.
7. **AI is subordinate to evidence.** Measurements and statistical conclusions come from deterministic or testable engines. Language models explain, summarize, and suggest; they do not invent measurements.
8. **Sharing is user-directed.** Messaging apps, Android Sharesheet, Quick Share, files, and QR pairing replace account-based synchronization.
9. **Feature-complete means complete for athlete training and coach collaboration.** Crocodyl deliberately does not pursue social feeds, global matchmaking, public leaderboards, or account-based cloud sync.
10. **Target scoring supports three camera-compatible modes.** End Scan scores a post-end photo; Live Observer records landing arrows by tap or constrained voice while form capture runs; Paired Target Cam detects impacts from a second camera. Every machine or observer entry is reconciled before it becomes authoritative.
11. **Longitudinal evidence keeps its resolution.** Shot-, end-, session-, day-, and period-level observations are never joined more precisely than the source data permits.
12. **Equipment commerce is recommendation-neutral.** Affiliate revenue may support Crocodyl, but commission never changes ranking, evidence, or whether an upgrade is recommended.
13. **Recovery guidance stays general-wellness.** Risk and recovery surfaces explain evidence and offer conservative actions; they do not diagnose, treat, or replace qualified care.

### v2.1 additions

- Three complementary target-scoring modes: editable End Scan, manual Live Observer, and optional Paired Target Cam.
- Offline, constrained observer voice entry such as “8 bottom left” and “9 top middle,” with immediate confirmation and correction.
- A longitudinal observation graph for Baseline correlations and causal studies.
- An explicit evidence ladder separating correlation from causal estimates.
- Equipment instances, a provenance-rich equipment catalog, artifact cards, wear and upgrade recommendations, and affiliate-link laws.
- Training plans, goals, habits, calendar scheduling, soreness/pain, recovery, rest-day, and injury-risk requirements.

### Package contents

- Part I — Rewritten Product Specification
- Part II — Competitor Parity Matrix
- Part III — Release Backlog
- Part IV — UX Architecture

---

# Part I — Rewritten Product Specification

## 1. Product definition

### 1.1 One-line definition

**Crocodyl is a sovereign performance system that helps athletes record, understand, and improve technique with local vision analysis, honest statistics, and portable coach collaboration.**

### 1.2 Archery launch proposition

For a Recurve archer, Crocodyl combines:

- fast manual scoring, arrow plotting, editable automatic target-photo scoring, and live observer entry;
- phone-camera form analysis without bow-mounted hardware;
- equipment, tuning, sight-mark, wear, recommendation, and arrow history;
- training plans, goals, habits, readiness, body, recovery, and competition context;
- evidence-grounded offline and BYOK coaching;
- signed report exchange with a human coach;
- local desktop review through a browser.

### 1.3 North-star outcome

> An athlete can identify what changed, whether it improved performance, and what to work on next—without surrendering their data or adopting a cloud account.

### 1.4 Initial adoption unit

The launch unit is not a solitary download. It is:

> **One coach + one club squad + repeated weekly practice.**

The coach receives a free pilot entitlement. Archers use the full free athlete app. The product succeeds when it fits real range behavior, not when its feature list is longest.

## 2. Product boundaries

### 2.1 In scope for Archery v1

- Olympic Recurve scoring, practice, form capture, and technique review.
- Editable automatic target-photo scoring, live observer scoring during form capture, and an optional later paired live target camera.
- Recurve equipment, rigs, sight marks, basic tuning history, and individual arrows.
- A provenance-rich equipment catalog, artifact-style comparisons, wear forecasts, upgrade evidence, and clearly disclosed affiliate links.
- Manual score entry, target-face plotting, and target-photo retention.
- Recurve round pack plus extensible community round definitions.
- Human coach pairing, roster, report review, notes, assignments, and acknowledgements.
- Local performance trends, PBs, stability, load, wellness, body, and competition context.
- Training plans, rest days, habits, goals, events, injury-risk context, and conservative recovery recommendations.
- Static web viewing of exported files with no upload.
- Offline rule-based coaching, on-device model support, and BYOK cloud providers including DeepSeek.
- Signed portable exchange, backup, restore, and explicit privacy ceremonies.

### 2.2 Later discipline scope

1. Compound target archery.
2. Korean and other traditional archery profiles.
3. Additional archery styles based on community round and technique packs.
4. Fencing as the first non-archery validation candidate.
5. Swimming and rock climbing after the sport-module contract survives fencing.

### 2.3 Deliberately out of scope

- User accounts, hosted profiles, or automatic cloud synchronization.
- Social feeds, public follower graphs, global matchmaking, or public leaderboards.
- A Crocodyl-operated AI proxy or subsidized hosted inference.
- Live tournament infrastructure requiring a central server.
- Automatic medical diagnosis, sentiment analysis, or prescriptive cycle-based training.
- Simultaneous shot-level form and automatic target imaging from one phone. True live linkage instead uses a locally paired target camera, a Live Observer entry, or explicit manual arrow-order assignment.
- Live skeleton overlays that distract the athlete during shooting.
- A generic multi-sport UI before Recurve proves the underlying contracts.

## 3. Product principles and laws

| Law | Product consequence |
|---|---|
| Local by default | Core features work in airplane mode; local storage is the source of truth. |
| Athlete-owned | No account is required; export and deletion are always available. |
| Explicit transmission | Every network call and every share action identifies destination and included data. |
| Evidence before advice | Advice cites sessions, shots, scores, trends, or drills; unsupported claims are blocked. |
| Fast at the range | Starting a familiar session takes no more than two taps; common input is glove-friendly. |
| Progressive depth | The default path is short; advanced equipment, wellness, and analytics remain available without becoming a tax. |
| Honest uncertainty | Data gates, confidence, missing data, and model provenance are visible. |
| Accessible encoding | Luminance, shape, pattern, icon, and numerals carry meaning before hue. Red/green is never the sole semantic channel. |
| Open implementation | Free and paid official features are open-source under the chosen project license. |
| No false DRM promise | Paid official binaries may enforce entitlements, but public source means the wall is a commercial boundary, not an unbreakable secret. |

## 4. Users, roles, and jobs

### 4.1 Athlete roles

| Role | Primary jobs |
|---|---|
| Developing Recurve archer | Learn a repeatable shot sequence, record practice quickly, understand coach feedback. |
| Competitive Recurve archer | Track rounds, pressure performance, equipment changes, form stability, load, and goals. |
| Self-coached archer | Receive grounded offline guidance and compare interventions over time. |
| Multi-discipline archer | Maintain separate discipline profiles, rigs, metrics, and history without mixing baselines. |

### 4.2 Human Coach role

| Job | Required outcome |
|---|---|
| Review the squad | See who needs attention without reading every session. |
| Review an athlete | Reconstruct score, form, equipment, conditions, and shared wellness context. |
| Give direction | Send notes, drills, plans, and due dates as portable assignments. |
| Close the loop | See acknowledgement and completion in the next athlete report. |
| Respect consent | Never infer that missing sensitive data is normal, zero, or consented. |

### 4.3 Key user stories

1. As an archer, I can resume my usual practice in two taps.
2. As an archer, I can score or plot an end without typing.
3. As an archer, I can see whether form, equipment, or conditions changed with my score.
4. As an archer, I can record video and receive evidence-grounded form feedback locally.
5. As an archer, I can share exactly the report I choose through any messaging app.
6. As a coach, I can import an athlete report without requiring either of us to create an account.
7. As a coach, I can return a note or assignment through the same file-based loop.
8. As an athlete or coach, I can open an exported report on a computer without uploading it.
9. As a spotter, I can use a monocular and record each landing arrow while the athlete’s phone continues form analysis.
10. As an athlete, I can hear or see each observer entry acknowledged and correct it before the end is finalized.
11. As a solo archer, I can photograph an end, correct the proposed arrows and scores, and save the reconciled result.
12. As an athlete, I can understand whether equipment should be kept, adjusted, serviced, tested, or replaced without sales incentives changing the answer.
13. As an athlete, I can plan training and rest, log soreness/stiffness/pain, and receive conservative recovery context without being diagnosed or blocked.

## 5. Tiering and source model

| Surface | Official entitlement | Source availability | Data model |
|---|---|---|---|
| Athlete app | Free | Open-source | One or more local athlete profiles on the device. |
| Sport-local statistics and performance analysis | Free | Open-source | Crocodyl-only data and deterministic analysis. |
| Offline rule coach | Free | Open-source | Local facts and drill catalog. |
| On-device model support | Free | Open-source | User-imported or explicitly downloaded models. |
| Cloud AI | BYOK | Open-source clients | User pays provider directly; Crocodyl never proxies. |
| Human Coach workspace | Paid in official builds | Open-source | Multi-athlete roster and coaching workflow. |
| Baseline cross-app insights | Paid in official builds | Open-source | Supplied through the Baseline adapter; details deferred. |
| Static web viewer | Free | Open-source | Reads local export files; no hosted user data. |

### 5.1 Entitlement constraint

Because the product has no maintained backend and the paid code is open-source:

- early coach pilots use a local pilot entitlement;
- Play-distributed Android builds may use Play Billing with a locally cached entitlement;
- non-Play builds may use a locally verified signed license file;
- the product must continue to function offline after entitlement verification within a documented grace policy;
- the project must not imply that source-visible feature gates are impossible to remove.

The final license, purchase model, and grace period remain owner decisions.

### 5.2 Equipment affiliate model

Affiliate links may monetize physical equipment discovery without introducing ads, sponsored placement, or promotional interruption.

#### Commerce laws

1. Recommendation scoring cannot read merchant, commission rate, campaign, or payout fields.
2. A recommendation is computed before eligible offers are attached.
3. “Keep current equipment,” “adjust,” “service,” “repair,” and “borrow/test first” are valid recommendations and must precede purchase when the evidence supports them.
4. Safety, compatibility, and wear warnings can never be suppressed because no affiliate offer exists.
5. Every monetized link carries an adjacent plain-language disclosure such as “Crocodyl may earn a commission; this does not affect the recommendation.”
6. Cards distinguish manufacturer claims, independently measured characteristics, community observations, and the athlete’s own history.
7. The canonical manufacturer/product reference remains available even when no merchant or affiliate link is shown.
8. Multiple compatible merchants may be listed; price, stock, region, and last-checked time remain offer metadata, not equipment-performance evidence.
9. Crocodyl does not accept paid placement, sponsored rankings, or undisclosed consideration.

Physical equipment purchases are outside Google Play Billing, but store policy and regional consumer/affiliate disclosure requirements must be reviewed at release time. The FTC’s current guidance requires material connections to be disclosed clearly and conspicuously; Crocodyl applies that standard globally as a product law even where local wording differs.

### 5.3 Static equipment catalog pipeline

The no-backend constraint is preserved with a versioned, signed catalog built in a public repository and distributed as static artifacts.

Source precedence:

1. Manufacturer-published specifications and manuals.
2. Licensed manufacturer, distributor, affiliate-network, or merchant feeds/APIs.
3. Public structured product data such as Schema.org `Product` and `Offer` where reuse is permitted.
4. Permissioned crawling that respects site terms, robots directives, rate limits, attribution, and removal requests.
5. Community pull requests with source citations and review.
6. Independent test datasets whose license permits redistribution.

The pipeline must not bypass authentication, paywalls, access controls, or explicit prohibitions. Scraped marketing prose is not republished wholesale. Each field retains source URL, publisher, retrieved/effective date, license/reuse status, parser version, and confidence. Merchant offers expire independently of stable product characteristics.
