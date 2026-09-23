# Fencing and ecosystem roadmap

Status: first manual referee slice live; automatic adjudication and directory ingestion remain
roadmap work. This note is the near-immediate follow-on to the production-hardening pass and
preserves the existing arrow/component Atelier work.

## Fencing

The new `fencing-module` is a pure-JVM sport module behind the existing `SportModule` seam. It
currently defines:

- Sabre, Foil and Epee as stable weapon ids with target and priority-rule metadata.
- A fencing exchange/feature shape that can feed the shared baseline engine later.
- A deterministic referee vocabulary and state machine: **Prêt**, **Allez**, **Halte**, point-left,
  point-right, no-point and double-touch.
- Provisional decisions with optional confidence. The scaffold refuses to award a point before
  **Allez**; it does not pretend to infer right-of-way or blade priority.

The Android app now shows a Fencing Studies card on Home and a Fencing console with all three
weapons, weapon-specific equipment metadata, named fencers, Prêt/Allez/Halte controls, point-left/
point-right controls, score, auditable call history, undo and reset. Cinematic/equipment information
is visible before live capture work begins. The next visual slice should add dedicated Sabre/Foil/Epee
cinematic imagery without disturbing the arrow reference studio.

The console state is lifecycle-scoped in `FencingViewModel`: rotation/recomposition does not erase
the selected weapon, fencer labels, score or call history. Fencer labels are capped at 40 characters
for predictable narrow-screen layout. Selecting another weapon intentionally starts a fresh match while
preserving the entered names.

Local API-35 smoke evidence: the Home accessibility tree exposed `Sabre · Foil · Epee` and
`COMMUNITY DIRECTORY`; opening Fencing exposed all three weapon cards and the rendered `Prêt · Allez ·
Point — left fencer · Point — right fencer` vocabulary. This is scaffold visibility evidence only.
The Android instrumentation contract now also launches both the Fencing and Community Directory
activities so a future UI change cannot silently break either entry point.

Release-variant scaffold audit: QA build `0.6.0-fencing-scaffold` (versionCode 603) passed
`bundleRelease`, `assembleRelease`, `testReleaseUnitTest` and `lintRelease`, plus APK v2 signature,
ZIP alignment, AAB verification and direct minified cold start. APK SHA-256:
`64cacf12109913f07f9b7db86d134badbfb6d0b0df800b44fb469e9d0af242d8`; AAB SHA-256:
`8fc21f816cc9ea38ea6b53c7bd2765e4609b6500aff9cfb0ef7fe3d6ae81a1fd`. These are throwaway QA
artifacts for scaffold validation, not distribution artifacts.

The manual console is not automatic match adjudication. Production camera mode will still require a
validated two-fencer capture pipeline, piste calibration, weapon-aware right-of-way rules, explicit
uncertainty handling, and a human-referee correction path. No scraping or automatic match adjudication
is enabled yet.

Current implementation evidence: the pure-JVM Fencing suite passes weapon metadata, Prêt/Allez gate,
score tracking, halt-after-point, undo and reset coverage. The Android debug connected smoke passed
3/3 on API-35 ARM64 with verification versionCode 607, and the signed/minified release connected
smoke passed 3/3 with the QA-only release substitute (`0.6.0-fencing-manual`, versionCode 606).
Android unit tests also cover ViewModel state retention, weapon switching and bounded fencer labels.
These validate the manual console entry path and state core, not camera adjudication or a production
signing identity.

On 2026-09-21 the lifecycle-safe build was replayed on the API-35 ARM64 emulator: debug connected
instrumentation passed 3/3 with versionCode 608, and minified release connected instrumentation
passed 3/3 with QA-only signing (`0.6.0-fencing-vm`, versionCode 609). These are emulator and QA
evidence, not physical-device or production-signing acceptance.

## Community directory

The Home surface now exposes a directory shell for coaches, training centres and stores. The
prescribed open format lives under `docs/crocodyl/community-directory/`; entries require provenance,
canonical URL, source/update timestamps and a contribution license. There are no bundled live records
yet. A future importer may ingest curated web data, but must retain attribution, respect site terms and
support user corrections/deletions.

## Local multiplayer

The authoritative platform-neutral contract now lives in Shared-Libraries-asoc as
`dev.aarso:local-session-core:0.1.0`, latest shared transport-hardening revision `6b54b98` (the
prior beta transport verification ran at `6051c7d`)
(with JVM17 compatibility in `5a855cf` and build unblocking in `c240a79`). It provides session identity, packet vocabulary, monotonic
per-participant event ordering and duplicate suppression. Bocal and Crocodyl beta builds can consume
the same contract through `-PsharedLibrariesDir=...`; both beta bridges now expose the shared
line-framed TCP local-network transport with an optional six-digit pairing-code handshake. Nearby/
Bluetooth/LAN discovery, code display/entry, reconnect handling, encryption/platform identity,
event-to-feature mapping and physical two-device acceptance remain host-layer work. The shared
transport also rejects frames larger than 64 KiB. This
is now a shared implementation boundary plus a runnable beta transport, not a duplicated
Crocodyl-only placeholder.

On 2026-09-22, the Crocodyl beta Android source set compiled against `6b54b98`, Bocal's matching
beta source set compiled against the same shared revision, and Crocodyl debug/release unit tests plus
release lint passed. This confirms build compatibility, not physical two-device behavior.

The beta runner at `tools/performance/run_beta_report.sh` measures the shared search and session
paths and writes JSON under `performance-report/build/reports/beta/`. The latest run against
`6b54b98` completed successfully: 30 measured iterations after 5 warmups for each measurement,
with session-event p50 `3,916 ns` and p95 `159,834 ns`; shared-search rank p50 was `4,183,917 ns`.
These are synthetic JVM measurements, not camera, rendering, thermal or two-device network
acceptance results.

The user-facing name is now **Paired Session**. Crocodyl presents a persisted first-run walkthrough
covering host/join roles, trusted-network setup, six-digit code verification and the file-transfer
fallback. The walkthrough is guidance, not a claim that production discovery or two-device
acceptance is complete.

The new `web-viewer/` static viewer is the desktop fallback for a coach without an Android device.
It opens current `.crocbak` ZIP exports locally and displays manifest/table metadata without
uploading or importing records. It accepts the `.croc` extension for forward compatibility, but
the signed `.croc` person-to-person envelope, signature verification and full report rendering are
still pending. A file can be carried over WhatsApp, USB, Bluetooth, LocalSend or any equivalent
file-transfer method.

## Search, baseline and news

The locally available Shared-Libraries-asoc search core was audited at revision
`597561847bce4ad20d5400d8df8ba1d796378598`; it is Apache-2.0 and publishes pure-JVM
`dev.aarso:search-core:0.2.0` contracts (`SearchDoc`, `QueryParser`, facet registry and
`CoverageRecencyScorer`). Baseline's open repository separately defines contracts, ledger and
descriptive stats, while its Advanced distribution is optional and must not gate the open app.
Crocodyl therefore keeps the existing integration seams until a repo-pinned submodule/composite-build
decision is made. No unreviewed filesystem path or network dependency has been added to the
production build.

`engine.integration.IntegrationPorts.kt` defines the internal seams for:

- adopting the advanced-search implementation from Shared-Libraries-ASOC or Fly Manager after a
  source/revision/license review;
- exposing the existing local baseline model to search and coach surfaces;
- consuming Archery/Fencing-specific Nooz items with canonical URLs, source names and timestamps.

These are adapters, not a claim that those external repositories are already linked. The next step is
to select one implementation source, pin its revision, add license/provenance notes, and build an
offline fixture-backed adapter before any live network fetch is enabled.
