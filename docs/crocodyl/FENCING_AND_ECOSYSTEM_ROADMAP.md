# Fencing and ecosystem roadmap

Status: scaffolded, not live. This note is the near-immediate follow-on to the production-hardening
pass and preserves the existing arrow/component Atelier work.

## Fencing

The new `fencing-module` is a pure-JVM sport module behind the existing `SportModule` seam. It
currently defines:

- Sabre, Foil and Epee as stable weapon ids with target and priority-rule metadata.
- A fencing exchange/feature shape that can feed the shared baseline engine later.
- A deterministic referee vocabulary and state machine: **Prêt**, **Allez**, **Halte**, point-left,
  point-right, no-point and double-touch.
- Provisional decisions with optional confidence. The scaffold refuses to award a point before
  **Allez**; it does not pretend to infer right-of-way or blade priority.

The Android app now shows a Fencing Studies card on Home and a visible Fencing shell with all three
weapons plus a disabled referee-mode entry point. Cinematic/equipment cards are intentionally visible
before live capture work begins. The next visual slice should add dedicated Sabre/Foil/Epee cinematic
cards and equipment metadata without disturbing the arrow reference studio.

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

Referee mode will require a validated two-fencer capture pipeline, piste calibration, weapon-aware
right-of-way rules, explicit uncertainty handling, and a human-referee correction path. No scraping or
automatic match adjudication is enabled by this scaffold.

## Community directory

The Home surface now exposes a directory shell for coaches, training centres and stores. The
prescribed open format lives under `docs/crocodyl/community-directory/`; entries require provenance,
canonical URL, source/update timestamps and a contribution license. There are no bundled live records
yet. A future importer may ingest curated web data, but must retain attribution, respect site terms and
support user corrections/deletions.

## Search, baseline and news

`engine.integration.IntegrationPorts.kt` defines the internal seams for:

- adopting the advanced-search implementation from Shared-Libraries-ASOC or Fly Manager after a
  source/revision/license review;
- exposing the existing local baseline model to search and coach surfaces;
- consuming Archery/Fencing-specific Nooz items with canonical URLs, source names and timestamps.

These are adapters, not a claim that those external repositories are already linked. The next step is
to select one implementation source, pin its revision, add license/provenance notes, and build an
offline fixture-backed adapter before any live network fetch is enabled.
