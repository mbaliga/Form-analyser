# Crocodyl decision record — licence, distribution and the October 2026 fixes

Decision record, 2026-10-24. Status: decisions 1-3 and the licence are PROPOSED pending the owner-only items in section 5; everything else is implemented or in flight.

**Date:** 2026-10-24 · **Status of this document:** adopted for execution; items marked
HELD-FOR-OWNER are prepared but not merged, released or submitted until the owner answers the
matching Owner-only item. · **Repository:** `mbaliga/Form-analyser` (public) · **Base for all
work:** the integration stack top `integrate/crocodyl-status-2026-10-22` @ `9b8158d`, which
becomes `main` under decision 13.

## 1. Context

### 1.1 What the owner asked for
"I want to be able to put these apps on places other than the play store. On iOS, they have to be
paid. We will certainly expand to iOS soon. But I also want F-Droid and other flavours." Earlier
rule (draft PR #14): "I want to monetize if someone makes money off of it, otherwise no." The owner
delegated the decisions; where a decision needs a fact only the owner knows or an act only the
owner can perform, it is listed in §5 and we proceed meanwhile with the reversible choice.

### 1.2 The conflict that cannot be papered over
F-Droid: "All applications in the repository must be Free, Libre and Open Source Software (FLOSS)"
(https://f-droid.org/docs/Inclusion_Policy/). No FLOSS licence may forbid commercial use, so the
rule "pay if you make money" cannot be implemented literally alongside F-Droid. The closest FLOSS
approximation is strong copyleft plus a commercial exception (anyone may use, modify and
redistribute for free while publishing their source; a business wanting a proprietary derivative or
a hosted service buys an exception). Whether that trade is acceptable is Owner-only item 1 and gates
every licence-bearing step.

### 1.3 Facts verified on 2026-10-24 (what the decisions rest on)
- **Git state.** `main` = `d0987ce` (no LICENSE). Chain `d0987ce → 4992ce9 (PR #15) → 8a89d74
  (PR #16) → 9b8158d (PR #17)` is linear (`git merge-base --is-ancestor` on each link); 525 commits
  on the stack are not on main. PR #14's commit `2c3e14f` (PolyForm `LICENSE`,
  `COMMERCIAL-LICENSE.md`, `README.md` licence section) is an ancestor of `4992ce9`, so merging the
  stack unchanged puts PolyForm on main. Only `codex/architecture-v2` (4171054) and
  `codex/local-trust-foundation-2026-10-22` (02e5e58) are not merged into the stack top; both have
  an empty `git diff` against `051d931` (merged PR #9 head). CI on the three stack heads: `build`
  and `assemble` succeeded on all three; `review` (dependency review) failed on `8a89d74` and
  `4992ce9` because the repository's Dependency graph is disabled. The repository has
  `delete_branch_on_merge=false`, merge/squash/rebase all allowed, `license: null`. The session
  token has push rights but `GET /branches/main/protection` returns 403, so repository settings are
  owner-only.
- **Recorded authors** (from the licence record, `git shortlog -sne` on 9b8158d): one non-owner
  author identity (412 commits; the name is in `git shortlog -sne`), Madhav Baliga under two identities (92 + 42), "Claude" under a noreply identity
  (74), github-actions[bot] (9). No CLA, CONTRIBUTING or NOTICE exists. No statement about who these
  people are is made here; see Owner-only item 2.
- **Export/import code.** `ExportViewModel.RETRACTABLE` lists six tables; `tableDumpQuery` appends
  `WHERE deletedAt IS NULL` only for them; `mergeArchive` uses `db.insert(sqlName,
  CONFLICT_IGNORE, values)`. AOSP `SQLiteDatabase.insertWithOnConflict` appends column names raw:
  `sql.append(colName);` (frameworks/base, core/java/android/database/sqlite/SQLiteDatabase.java,
  `refs/heads/main`) — the A2 injection is real. Room declares no foreign keys; the 13 reference
  columns pointing at retractable parents are exactly those enumerated in decision 15. Schema
  snapshots 8–11 remove no column, so "reject unknown archive columns" cannot reject a legitimate
  older archive. `document` has no `deletedAt`; `BodyDao.deleteDocument` hard-deletes.
- **Build.** No product flavours. `app-android/build.gradle.kts:245-296` downloads
  `pose_landmarker_lite.task` at build time (SHA-256 `59929e1d…574a`, 5,777,746 bytes at the pinned
  URL today). Root build enforces `dependencyLocking { lockAllConfigurations(); lockMode STRICT }`
  and `gradle/verification-metadata.xml` (sha256 for every artifact) is contract-tested, so any
  dependency change needs `--write-locks` and `--write-verification-metadata sha256`.
  `tools/release/resolve_version.sh` derives versionCode = major·10000 + minor·100 + patch, so
  `v0.7.0` → **700** (floor is 697), and today's release assets are named with the tag including
  the "v" (`crocodyl-v0.5.1-reconciled.apk`). The current release APK is **120 MB** (universal,
  debug-signed, unminified); the MediaPipe native libraries are 14.3 MB (vision) + 12.0 MB
  (genai) per ABI.
- **MediaPipe telemetry (new finding).** `com.google.mediapipe:tasks-core:0.10.26` (Google Maven)
  `TaskRunner.create` → `TasksStatsLoggerFactory.create` → unconditionally
  `TasksStatsProtoLogger.create` → `new RemoteLoggingClient(context)` →
  `TransportRuntime.newFactory(CCTDestination.INSTANCE)` and a transport named
  `COREML_ON_DEVICE_SOLUTIONS`. It logs session start/end/clone, invocation reports (mode, elapsed
  ms, average and peak latency, dropped count), init errors with details, and `SystemInfo` (app
  id = package name, app version = versionCode, platform). `transport-backend-cct:3.1.0` carries a
  hard-coded endpoint (merged-string constants decode to
  `https://firebaselogging.googleapis.com/v0cc/log/batch?format=json_proto3`) and a built-in API
  key; its manifest merges `INTERNET` and `ACCESS_NETWORK_STATE`; `transport-runtime:3.1.0` merges
  `ACCESS_NETWORK_STATE`, `JobInfoSchedulerService`, `AlarmManagerSchedulerBroadcastReceiver` and
  `TransportBackendDiscovery`. The app's own manifest declares only CAMERA, VIBRATE, INTERNET, but
  `tools/release/verify_manifest_contract.py` already lists `ACCESS_NETWORK_STATE` as expected in
  the **merged** manifest — confirming the shipped APK carries it. `tasks-genai:0.10.24` has no
  logging classes and does not depend on tasks-core. No app code references datatransport. The
  F-Droid SUSS list (https://fdroid.gitlab.io/fdroid-suss/suss.json) does not flag `datatransport`
  and explicitly exempts `firebase-encoders`; this is therefore a privacy/truth problem, not a
  scanner problem — but it falsifies "no analytics" and would be F-Droid's `Tracking`
  anti-feature if shipped.
- **F-Droid mechanics** (https://f-droid.org/docs/Build_Metadata_Reference/,
  https://f-droid.org/docs/Reproducible_Builds/, fdroidserver `scanner.py` @ master):
  `Binaries`: "F-Droid will verify the output APK file of a build against the one specified … will
  use upstream binaries if the verification succeeded"; developer-signed only: "F-Droid will never
  attempt to publish APKs signed by F-Droid … Otherwise F-Droid will skip publishing this version";
  `AllowedAPKSigningKeys` is "a lower case hex value of the SHA-256 fingerprint of the signing
  certificate"; `gradle`: "If only one flavour is given and it is 'yes', no flavour will be used";
  `gradleprops`: "List of Gradle properties to pass via the command line"; `UpdateCheckData` works
  with `UpdateCheckMode: Tags` and a path/file relative to the repo root; `License` must be one SPDX
  identifier. Buildserver JDKs: 25 and 21 (Debian Trixie), 17 (Bookworm). Scanner: gradle lines are
  checked only when they start with a compile command (`implementation`, `api`, `compile`, …);
  `exclude(...)` and `configurations` lines are not; `gradle-wrapper.jar` is removed, not flagged;
  binaries are detected by suffix (`.a .aar .class .dex .gz .tgz .zip .jar .wasm .so .apk`), by
  extensionless/`.bin/.out/.exe` content, or by executable bit — a non-executable `.task` passes.
  `fdroid scanner` accepts app ids or APK paths, not source directories; with no `Builds` it scans
  `build/<appid>` "current source state". NonFreeNet: "applied to apps that promote or depend
  entirely on a proprietary network service" (https://f-droid.org/docs/Anti-Features/).
- **Unsplash License** (https://unsplash.com/license): grants commercial use but "Images cannot be
  sold without significant modification" and excludes "compiling images from Unsplash to replicate
  a similar or competing service" — a field-of-use restriction, not FLOSS. Four bundled
  photographs (`muay-thai-training.jpg`, `bouldering-session.jpg`, `recurve-training.jpg`,
  `fencing-training.jpg`) are Unsplash; every consumer loads them inside `runCatching { … }.getOrNull()`
  and already renders without them.
- **IzzyOnDroid**: README (Codeberg mirror) says apps are "official binaries built and signed by
  the original application developers" and that "too many non-free dependencies" is a removal
  reason, and refers to a "per-app size limit" without a figure. **Could not verify** the numeric
  cap (the GitLab wiki is archived; the Codeberg wiki redirects).
- **Could not verify:** whether fdroidserver's Gradle checksum list already includes the wrapper's
  Gradle 8.14.3; whether a Play Console entry for `com.asoc.crocodyl` exists; PolyForm's OSI status
  (not needed — its grant is noncommercial by construction).

## 2. Decisions

Each decision carries a status: **DECIDED** (executes now, reversible unless stated) or
**HELD-FOR-OWNER** (prepared on a branch, nothing merged/released/submitted until §5 is answered).

1. **Licence: `AGPL-3.0-or-later` for the whole tree, dual-licensed with a commercial exception
   sold by the owner.** HELD-FOR-OWNER (item 1, 2). PolyForm NC and the present
   `COMMERCIAL-LICENSE.md` never reach `main`; `main` stays licence-less (all rights reserved) with
   a `LICENSING.md` stating the intent. Rationale: the cores are pure-JVM, so the likeliest way
   someone "makes money off it" is a hosted coaching service running the engine — GPL leaves that
   free, AGPL §13 does not; local-first Crocodyl itself behaves identically under GPL and AGPL on
   Play and the App Store because the owner's own builds are not bound by the licence (GPL FAQ:
   "The developer itself is not bound by it"). Fallback A if any author will not sign within 30 days
   of being asked: `MPL-2.0` (needs no relicensing right for the paid iOS build). Fallback B if the
   owner says "pay if commercial" outranks F-Droid: keep PolyForm NC and drop F-Droid and
   IzzyOnDroid.
2. **Contributor mechanism: an individual CLA (perpetual, irrevocable, non-exclusive right for
   the owner to sublicense under any terms, with the promise that every contribution is also
   released under the project's FLOSS licence), signed retroactively by every recorded human author,
   enforced by a CI ledger (`tools/cla/signers.json` + check against `git shortlog -sne`).**
   HELD-FOR-OWNER (item 2). DCO is rejected because it certifies only "under the open source
   license indicated in the file", which neither dual licensing nor the owner's App Store build can
   rest on. Text is drafted in plain language modelled on the Apache ICLA structure (the Harmony
   templates' own licence is not stated on their site, so they are not copied); the owner's lawyer
   reviews before anyone signs.
3. **Baseline:** Community tier `AGPL-3.0-or-later` under the same CLA; Advanced tier (EEG,
   bow-IMU, advanced analytics) stays proprietary in the private `baseline` repo with its own
   applicationId, talks to Crocodyl only through `BaselineContract`, and is never present or
   promoted in any F-Droid-distributed build. HELD-FOR-OWNER (item 7).
4. **Channels, in priority order.** DECIDED. (1) GitHub Releases + Obtainium — first; the first
   production-signed `v0.7.0` lands here and is F-Droid's `Binaries:` source. (2) Google Play —
   parallel track; closed test (12 testers, 14 days) the day the owner's account and key exist;
   Health-apps declaration, "not a medical device" text, privacy URL. (3) Official F-Droid —
   reproducible, developer-signed only (`Binaries` + `AllowedAPKSigningKeys`), held on licence, key
   and reproducibility; declares `NonFreeNet`. (4) Apple App Store — after the KMP/iOS port (porting
   plan Phase 18), paid up-front, Coach tier via IAP if sold. **IzzyOnDroid — DEFERRED** (correction
   to the distribution record): it requires the developer-signed APK but its size cap could not be
   verified and the universal APK is ~120 MB; revisit after v0.7.0 with the measured minified size
   and, if needed, per-ABI split assets. Deferred: self-hosted F-Droid repo (only if the fdroiddata
   MR stalls > 60 days), Accrescent, Galaxy Store. Out: Amazon, Huawei.
5. **No product flavours until Play-specific code exists.** DECIDED (correction to both records,
   which proposed `play/fdroid/direct` and `foss/play`). Today the variants would differ in nothing
   user-visible, while introducing a flavour dimension renames every Gradle task used by
   `android.yml`, `release.yml` and `test_release_workflow_contract.py`, changes every lockfile
   configuration name and the R8 mapping path, and all of that while the executors' Android SDK is
   still being installed. F-Droid builds a flavourless app with `gradle: [yes]`; when the `play`
   flavour arrives (billing, in-app review, guided model download), it is added with
   `flavorDimensions += "channel"`, flavours `foss` and `play`, same applicationId/versionCode, and
   the fdroiddata recipe for *later* versions switches to `gradle: [foss]` — published versions are
   unaffected. `src/main` keeps an `EntitlementSource` interface and never imports a store SDK.
6. **Identity and versions.** DECIDED, with the id itself HELD-FOR-OWNER (item 3). One
   applicationId everywhere, `com.asoc.crocodyl`, no suffixes; reserved as the iOS bundle id.
   versionName carries no "v" (`0.7.0`), the git tag does (`v0.7.0`), versionCode is derived by the
   existing resolver — **700 for v0.7.0** (correction to the record's 698), then 701 for 0.7.1 and
   so on. Source of truth is a committed `app-android/version.properties` (`versionCode`,
   `versionName`) read by Gradle as the default; CI `-P` overrides remain for QA builds;
   `resolve_version.sh` fails closed when a release tag disagrees with the committed file. The
   seven `xyz.mdhv.formanalyser` debug-signed releases are an abandoned lineage: notes amended, tags
   kept. F-Droid reads the version from `version.properties` via `UpdateCheckData`.
7. **Signing.** DECIDED (prefill) / HELD-FOR-OWNER (item 4). One owner-generated, owner-held key K
   in the `production` GitHub environment as `CROCODYL_RELEASE_*`; Play App Signing by "Export and
   upload" K so Play, F-Droid, Obtainium and Releases share one signature; F-Droid never signs
   (F-Droid-signed installs are sticky). `signer_continuity_decision.json` is prefilled now with
   `distribution_channels: ["google-play","direct-apk","f-droid"]` and `strategy:
   "new-application-id"`, `status` stays `unresolved` (the verifier returns before validating those
   fields), and the channel enum gains `f-droid` and `izzyondroid`.
8. **Pose model committed; Gemma never bundled.** DECIDED. `pose_landmarker_lite.task` (same
   SHA-256, no execute bit, Apache-2.0 per the BlazePose model card) is committed under
   `app-android/src/main/assets/`; `downloadPoseModel` and `.gitignore:7-9` are deleted; the SHA
   check in `verify_android_release.sh` stays and a Python test hashes the committed file. Gemma:
   user-supplied file through the system picker only; the deferred "guided download", if ever
   built, is Play-flavour-only.
9. **MediaPipe telemetry is removed in every channel.** DECIDED (new). `transport-backend-cct`
   and `transport-runtime` are excluded from the dependency graph; the app ships a 60-line no-op
   `com.google.android.datatransport.runtime.TransportRuntime`/`Destination` and
   `com.google.android.datatransport.cct.CCTDestination` so MediaPipe's `RemoteLoggingClient` links
   and its events are dropped on device; `ACCESS_NETWORK_STATE` is removed from the merged manifest
   (`tools:node="remove"`), and `verify_manifest_contract.py` fails the release if that permission,
   any `backend:` meta-data or any `datatransport` component is present. This keeps "no analytics,
   INTERNET only for BYOK" true and avoids F-Droid's `Tracking` anti-feature. The policy documents
   say so plainly, naming the blocked endpoint.
10. **Reproducibility.** DECIDED. `dependenciesInfo { includeInApk = false; includeInBundle = false }`,
    release `vcsInfo.include = false`, unsigned release builds permitted (the production-signing
    check becomes `-PcrocodylRequireProductionSigning=true`, passed only by `release.yml`), a CI job
    that builds the unsigned release twice from clean checkouts and compares byte-for-byte, and a
    CI job that runs `fdroid lint` and `fdroid scanner` on the in-repo metadata and tree. A rehearsal
    `fdroid build` in the official buildserver image is a release-runbook step, not a CI gate.
11. **Unsplash photographs removed from every build.** DECIDED, reversible. The four files and
    `sports-manifest.json` go; the heroes already fall back to their gradient/scrim paths; renders
    from committed GLBs or CC0 photos may replace them later. One licence for the whole tree keeps
    F-Droid's single `License` field honest.
12. **Anti-features.** DECIDED. Declare `NonFreeNet` (optional BYOK cloud coach; conservative,
    reversible). Not `Tracking` (removed by decision 9), not `NonFreeAssets` (decision 11), not
    `NonFreeDep`, not `NonFreeAdd`.
13. **Merge the integration stack into `main` first, after a hold commit.** DECIDED. A commit on
    `integrate/crocodyl-status-2026-10-22` removes `LICENSE`, `COMMERCIAL-LICENSE.md` and the
    README licence section and adds `LICENSING.md`; then #17 → #16 → #15 are merged with "Create a
    merge commit" (never squash — it breaks the ancestor chain; never rebase — it rewrites audited
    SHAs); `git diff <hold commit> main` must be empty. Every other work package then bases on
    `main`, which removes the double-application risk the A1 design warned about. The stack is
    already public, so merging adds no exposure; `build`/`assemble` are green on all three heads.
14. **Branch housekeeping.** DECIDED. Delete now (all merged into main): `agent/crocodyl-chatgpt-sync`,
    `agent/crocodyl-current-spec-android`, `agent/crocodyl-package-export`,
    `claude/default-store-listings-u991hc`, `claude/export-retraction-leak`,
    `claude/form-analyser-baseline-split-e9s6lr`, `claude/github-storage-cleanup-7848fg`,
    `claude/meta-trunk-normalization-2y198w`, `codex/local-crocodyl-main-2026-10-22`,
    `codex/trust-foundation`, the seven `release/*` branches (tags stay), and the two orphans
    `codex/architecture-v2`, `codex/local-trust-foundation-2026-10-22` (identical trees to merged
    PR #9). After the stack lands: `codex/production-hardening`, `feat/immersive-visuals`,
    `claude/crocodyl-status-bqg31u`, `claude/multi-platform-porting-plan-nnvjug`,
    `claude/asystemofcells-redesign-s6yv4c`, the three `integrate/*` heads; PRs #10, #11, #13, #14
    closed as superseded; `.github/workflows/package-source.yml` (triggers only on a deleted
    branch) removed.
15. **Data-layer fixes reconciled (A3 → A1 → A2, in that order, on one lane).** DECIDED.
    - A3 (document retraction) lands first: schema v12 adds `document.deletedAt`; `RETRACTABLE`
      gains `"document"`; the core-wellness drift guard's expected set gains `"document"`; erase-
      permanently is allowed only for an already-retracted document (second, separately confirmed
      step), ciphertext untouched until then.
    - A1 (owned children of retracted parents) then replaces `RETRACTABLE` with
      `RetractionRegistry` in `core-wellness` with **seven** parents (`sessions`, `score_session`,
      `sport_session`, `checkin`, `pain_log`, `injury`, `document`) and exactly these 13 references,
      owned iff NOT NULL: `shots.sessionId`, `session_context.sessionId`, `capture_media.sessionId`
      (PRIVATE, never exported), `score_arrow.scoreSessionId`, `score_opponent_end.scoreSessionId`,
      `score_candidate.scoreSessionId`, `observer_score_event.scoreSessionId`, `soreness.checkinId`
      (owned); `document.injuryId`, `pain_log.injuryId`, `score_session.linkedFormSessionId`,
      `sessions.preCheckinId`, `sessions.postCheckinId` (links). A child is dropped from an export
      only when its parent is *known* retracted (`NOT EXISTS (... AND p.deletedAt IS NOT NULL)`);
      orphans and links still ride. `document` is a parent and a link child, so its dump query is
      `WHERE deletedAt IS NULL` only.
    - A2 (import column guard) lands last: pure `ImportColumnGuard` in `core-exchange`, allow-list
      against `PRAGMA table_info`, run at inspect and again at merge; explicit back-tick-quoted
      `INSERT OR IGNORE … VALUES (?,…)` with positional binding replaces `ContentValues`; typed
      binding by declared affinity (TEXT→string, INTEGER→integer, REAL→number, anything else
      rejected — no BLOB column exists); NOT-NULL-without-default presence check; tombstones
      (non-null `deletedAt` in a `RetractionRegistry.parents` table) are not imported; per-table
      cap 200,000 rows, archive cap 1,000,000 rows. It references `RetractionRegistry.parents`, not
      the deleted `RETRACTABLE`.
16. **Dock (A4) as designed**, with the corrections that the Compose UI-test dependencies require
    lockfile and verification-metadata regeneration and that the contract strings in
    `tools/release/test_navbar_contract.py` stay byte-identical. DECIDED.
17. **Store documents rewritten from the A5 claims matrix, corrected and guarded by a test.**
    DECIDED; owner facts stay `[OWNER: …]` placeholders. Corrections to the matrix: permissions are
    the *merged* set (`CAMERA`, `VIBRATE`, `INTERNET` after decision 9; the test reads the release
    verifier's allowed set, not just the source manifest); the pose model is bundled in the app and
    never downloaded (there is no build-time download after decision 8); MediaPipe's telemetry
    client exists and is neutralised (named endpoint, verifiable in the release preflight); exports
    are signed but not encrypted; the Google provider's key travels in the URL query. Listing text
    is licence-neutral until the relicence PR: "Free to install and use. Source code is published;
    see the repository licence." — never "open source".
18. **Documentation truth now, licence wording later.** DECIDED. Blueprint, STATUS, porting plan,
    phased plan, runbook and `naming.md` stop saying "open-source" and say "source published,
    licence pending (intended AGPL-3.0-or-later with a commercial exception, see LICENSING.md)";
    decisions 14 (channels) and 15 (one id, one key) are added to the blueprint as *proposed pending
    Owner-only item 1*. This record is the single distribution record (no separate
    `DISTRIBUTION.md`).
19. **Third-party notices and an in-app licences screen are licence-neutral and ship before any
    store release.** DECIDED (Apache-2.0 §4(d) applies to the binaries regardless of our licence).
20. **Relicence PR prepared, rebased continuously, never merged until §5 items 1–2 are answered.**
    HELD-FOR-OWNER. Contents: verbatim AGPLv3 `LICENSE`, `NOTICE.md`, `CLA.md`, `CONTRIBUTING.md`,
    rewritten `COMMERCIAL-LICENSE.md` (no price), `TRADEMARKS.md`, `REUSE.toml`, the CLA ledger
    check, the README notice (exact text in §7), the AGPL wording for blueprint/listing/Play sheet,
    and per-file SPDX headers added by script as the *last* commit before merge (so the mass edit
    never conflicts with in-flight work).
21. **Repository settings, accounts, registrations, legal review, price, trademark** — all
    HELD-FOR-OWNER; see §5.

## 3. Rejected alternatives

- **Apache-2.0 (maximise reach).** Discards the owner's rule: a coaching business could ship a
  rebranded fork or SaaS with no obligation; the only lever left is a trademark the owner has not
  registered. The grant is irrevocable per snapshot.
- **MPL-2.0 as the primary licence.** File-level copyleft lets a proprietary "Larger Work" embed
  the engine. Kept only as Fallback A because it needs no CLA for the paid iOS build.
- **PolyForm NC / BUSL as the primary licence.** Implement the rule exactly and make F-Droid and
  IzzyOnDroid impossible. Kept as Fallback B if the owner says the rule wins.
- **GPL-3.0 instead of AGPL.** Leaves the hosted-engine case free.
- **DCO instead of CLA.** Does not grant the relicensing right the owner's own store builds need.
- **CC-BY-SA for art and docs.** A second licence complicates F-Droid's single `License` field and
  the NOTICE; one licence now, a second can be added later but never removed.
- **Three flavours (`play/fdroid/direct`)** — a `direct` flavour differs in `BuildConfig.FLAVOR`
  and breaks F-Droid `Binaries` byte-identity. **Two flavours now (`foss/play`)** — correct shape,
  wrong time (decision 5).
- **Letting F-Droid sign at first.** Sticky: those installs can never move to the developer key.
- **Compiling BYOK out of the F-Droid build.** Keep it, declare `NonFreeNet`; zero traffic until a
  key is entered; swapping later is reversible.
- **versionCode 698 for v0.7.0.** Contradicts the existing semver derivation (700) that the
  resolver, its tests and the floor file already implement.
- **versionName with a leading "v".** Makes F-Droid's `%v` substitution and Play's version display
  ugly; the tag keeps the "v", the version does not.
- **Keeping the Unsplash photographs (or only outside the F-Droid build).** Their licence is not
  FLOSS and a channel-specific asset set would reintroduce the flavour problem.
- **Neutralising MediaPipe telemetry with R8 `-assumenosideeffects`** (inert because
  `proguard-rules.pro` sets `-dontoptimize` and debug builds would still upload), **reflection
  after task creation** (the session-start event is already queued), or **excluding only the CCT
  backend** (`CCTDestination` would be missing and `TaskRunner.create` would crash). The no-op
  runtime shim is deterministic, offline, testable without native code and verifiable in the APK.
- **Per-file SPDX headers as the first commit of the relicence PR.** Guaranteed conflicts with
  every in-flight package; done last, by script.
- **Squash or rebase merging the stack.** Breaks the audited ancestor chain / rewrites SHAs.
- **IzzyOnDroid and per-ABI split APKs in this round.** Size cap unverified; splits change F-Droid's
  `output:` path and add release surface for an unconfirmed benefit.
- **Auto-including `:app-android` in `settings.gradle.kts` when `ANDROID_HOME` is set.** GitHub's
  runners export `ANDROID_HOME`, which would silently turn the bare-JDK `./gradlew test` job into an
  Android build. F-Droid passes `gradleprops: [withAndroid=true]` instead.

## 4. Irreversible steps (and what stays reversible)

Irreversible once done:
1. Any licence grant on a pushed public snapshot. AGPL cannot be revoked for recipients; a later
   tightening binds only new code. The PolyForm grant already on the public stack branch is
   permanent for snapshot `2c3e14f`'s tree (harmless and narrower than AGPL, but made over code
   whose ownership is unconfirmed — the reason we hold).
2. `com.asoc.crocodyl` once a Play track is open or fdroiddata metadata is merged.
3. The signing key: Play's app-signing key once an open track exists; the `AllowedAPKSigningKeys`
   fingerprint once F-Droid publishes; any F-Droid-signed release.
4. Issued versionCodes (700 for v0.7.0) and the seven debug-signed releases (abandoned lineage;
   users must uninstall — a different signer can never update in place regardless of versionCode).
5. The first public `v0.7.0` release under AGPL.
6. Merging the stack into `main` (history; content already public). Branch deletion is practically
   reversible for ~90 days through GitHub's reflog of merged commits and loses nothing for branches
   whose trees are already in `main`.

Reversible: everything in §2 marked DECIDED that is not listed above — flavours (none yet),
version.properties, anti-feature declarations, store text, CLA text (additive), TRADEMARKS.md,
commercial price, BYOK in F-Droid builds, Unsplash removal, the telemetry shim, dock geometry,
export/import rules.

## 5. Owner-only items (priority order; what to confirm or do, and what we do meanwhile)

1. **The licence trade — decide.** Under AGPL a coach may use the unmodified app in a paid business
   without paying you; what you keep is that anyone who modifies, embeds or hosts it must publish
   their source or buy your exception. Question: "Do you accept F-Droid + AGPL-3.0-or-later on these
   terms, or does 'pay if commercial' win?" If the rule wins, we keep PolyForm NC (Fallback B) and
   the channels are GitHub Releases + Obtainium, a self-hosted F-Droid repo, Play and Galaxy Store.
   *Meanwhile:* the relicence PR is prepared and nothing licence-bearing is merged, tagged or
   submitted.
2. **Copyright facts and consents — confirm and obtain.** For each recorded identity: the non-owner author identity (412 commits);
   Madhav Baliga under two identities, the GitHub noreply identity and the second author identity
   (one person?); the 74 "Claude" commits (who ran them, under whose account);
   the 9 bot commits (mechanical). Is that author you, an employee or contractor with a written
   assignment, or independent? Obtain a signed `CLA.md` (or an assignment) from every natural person
   other than yourself; name the copyright holder(s) for `NOTICE.md`. If anyone will not sign
   within 30 days of being asked, we switch to Fallback A or rewrite their files. *Meanwhile:* the
   ledger lists every identity with status `unsigned`.
3. **Application id — confirm `com.asoc.crocodyl` as permanent** (`asoc.com` is not your domain,
   which is cosmetic but F-Droid reviewers may ask), and say whether a Play Console entry already
   exists under it (commit `e76ffe3` suggests so) or whether you prefer `com.asystemofcells.crocodyl`.
   *Meanwhile:* we proceed with `com.asoc.crocodyl` and publish nothing.
4. **Key K — generate and hold; approve the signer decision.** RSA-4096 or EC P-256, 30-year
   validity, kept offline and in the `production` environment (`CROCODYL_RELEASE_KEYSTORE_BASE64`,
   `_STORE_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD`, `_CERT_SHA256`) with yourself as required
   reviewer; give us the SHA-256 for `AllowedAPKSigningKeys`. Then set
   `tools/release/signer_continuity_decision.json` to `status: "approved"` with
   `owner_approval_reference` and `verification_evidence` (K fingerprint record; Play Console
   showing the app-signing certificate equals K; a `.crocbak` round-trip from a v0.5.1 install into
   v0.7.0). On Play choose "Export and upload" K. *Meanwhile:* the file is prefilled and
   `unresolved`; release signing stays blocked by design.
5. **Repository settings** (the session token cannot: `GET …/branches/main/protection` → 403).
   Enable Dependency graph (Settings → Code security and analysis), turn on delete-branch-on-merge,
   protect `main` requiring `build`, `assemble`, `test`, the reproducible job and, after the
   relicence PR, the CLA ledger check. If branch protection blocks the executing agents' merges in
   §6/plan WP0, merge PRs #17 → #16 → #15 yourself with "Create a merge commit".
6. **Accounts and registrations.** Play (US$25; personal accounts need 12 testers for 14 days),
   Apple Developer (US$99/yr, legal name, before Phase 18), Android developer verification for the
   package and K, the fdroiddata merge request and (if ever) the IzzyOnDroid request in your name,
   the privacy policy live at the promised `https://asystemofcells.com/crocodyl/privacy`, Play's
   Health-apps declaration and Data safety form (our drafts are in `docs/store/`).
7. **Baseline and shared libraries.** Confirm the Advanced tier is authored solely by you or
   assigned to you and stays in the private repo; confirm the Community tier should be
   `AGPL-3.0-or-later` under the same CLA; confirm `:engine` in Crocodyl is meant to be free (as
   commit `76fe3b1` states); confirm whether `mbaliga/Shared-Libraries-asoc` (`dev.aarso`) is yours
   and its licence — it stays off default builds until then.
8. **Legal, commercial, privacy facts.** Review `CLA.md` and the commercial-exception text; set the
   exception price (we draft "available from the copyright holder", no price); decide whether to
   claim "Crocodyl" as a trademark (we draft `TRADEMARKS.md`). For the privacy policy supply:
   controller name and postal address, contact e-mail, minimum age / age statement, governing law,
   the retention promise for app-private video after uninstall, your position on provider
   sub-processors. Confirm the Play Data safety answers against the live form.
9. **Optional.** After v0.7.0, delete the APK assets of the seven debug-signed releases (we keep
   tags and amended notes regardless). Tell us if you want the Unsplash photographs back for
   non-F-Droid channels (we removed them everywhere; reversible).

## 6. Consequences

- `main` advances by 525 commits and carries no licence; any copy made from it is "all rights
  reserved". The repository's GitHub licence badge stays empty until the relicence PR merges.
- No store, F-Droid or IzzyOnDroid submission and no AGPL tag can happen before Owner-only items
  1–4 are answered; everything else (eleven work packages) proceeds and is reversible.
- The shipped APK stops carrying Google's datatransport runtime, `ACCESS_NETWORK_STATE`, a
  JobScheduler service and a broadcast receiver; the release preflight fails if they come back. The
  privacy policy can honestly say "no analytics" and name what was blocked.
- Release artefacts are renamed (`crocodyl-0.7.0.apk`, `crocodyl-0.7.0.aab`,
  `crocodyl-0.7.0-SHA256SUMS.txt`); the tag remains `v0.7.0`; the committed
  `app-android/version.properties` becomes the version source; the floor file is bumped to 700 in
  the release commit.
- F-Droid metadata lives in-repo at `fdroid/metadata/com.asoc.crocodyl.yml` and is linted and
  scanned in CI; the fdroiddata MR is a copy of it, opened by the owner.
- Exports stop leaking owned children of retracted parents and documents become retractable;
  imports stop trusting archive column names; older archives still import.
- The dock meets 48 dp targets and degrades to a rail on short windows.
- Four decorative photographs disappear until replaced; the affected screens fall back to their
  existing gradient surfaces.
- Executors need the Android SDK for the Robolectric/KSP/lockfile steps; packages whose
  verification needs it are pushed and verified by `android.yml` and merged only when green.

## 7. README notice (exact text, used only by the held relicence PR)

Copyright © 2026 the Crocodyl authors (see NOTICE.md). Crocodyl is free software: you can
redistribute it and/or modify it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the License, or (at your option) any
later version. Crocodyl is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
LICENSE file for the full text (SPDX-License-Identifier: AGPL-3.0-or-later). If you want to use
Crocodyl in a product or service without the AGPL's source-sharing obligations, a commercial
licence is available from the copyright holder; see COMMERCIAL-LICENSE.md. Third-party components
and their licences are listed in NOTICE.md. This licence does not grant permission to use the name
"Crocodyl" or its logo.
