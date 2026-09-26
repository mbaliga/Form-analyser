# Crocodyl handoff to Claude Code

Status: current snapshot, 2026-09-22 (Asia/Kolkata)

Repository: `mbaliga/Form-analyser`

Checkout: `/Users/tulikapandey/Documents/Codex/2026-09-14/cur/work/crocodyl-current`

Branch: `codex/production-hardening`

Latest implementation commit: `2897edf feat(crocodyl): expose paired beta sessions`
Latest evidence commit: `a581542 docs(crocodyl): capture shared multiplayer beta evidence`
Latest test-hardening commit: `3add644 test(android): harden model inventory contracts`

The worktree was clean when this handoff was written. All changes made during this pass were committed incrementally with context in the commit messages.

The production trigger and signing-secret procedure is captured in
[`docs/crocodyl/PRODUCTION_RELEASE_RUNBOOK.md`](crocodyl/PRODUCTION_RELEASE_RUNBOOK.md).

## Current result

Crocodyl is a production candidate for its current Android/local-first scope. The earlier arrow-first visual handoff was integrated and preserved: the generic reference library remains modular by shaft profile, shaft construction, point family and nock/rear interface, with separate component selection and analysis in the local WebGL2 studio. No generic mesh is presented as the owner's equipment.

The build/release path is hardened with pinned toolchain/model inputs, fail-closed release signing, Room migration/recovery safeguards, atomic vault/model imports, validation around scoring links/candidates, release CI checks, deterministic generated-asset checks, APK signature verification and ZIP alignment verification.

## Verified in this checkout

- Root JVM suite: `./gradlew test --no-daemon` — passed fresh on 2026-09-22 with JDK 21.
- Shared performance-analysis and local-session-core suites passed fresh on 2026-09-22; the
  local-session suite includes TCP loopback, pairing acceptance/rejection and oversized-frame
  rejection coverage.
- Fresh Android verification on 2026-09-22 passed with an isolated JDK 21/Android SDK: Crocodyl's
  shared-session beta compile passed (41 tasks), Bocal's matching shared-session beta compile passed
  (9 tasks), and Crocodyl debug/release unit tests plus `lintRelease` passed (83 tasks). These are
  compile/unit/lint results; no physical device or connected two-device run was performed here.
- A fresh QA-signed minified artifact pair was built on 2026-09-22 as version `0.6.0-qa-verification`
  / versionCode `610`. The reusable release preflight passed APK/AAB signing, version metadata,
  cleartext/backup/FileProvider policy, APK alignment, model hash, backup resources and staging-file
  checks. APK SHA-256: `66fcda10ab31a730db2fd6d9ceac7cc61d06e96c7cca09ad068b93518813e9eb`; AAB
  SHA-256: `b9fa07f5b40e9c787bc1cfb7f716b9b357a68c6ab5a976aa337af73420f04987`. The QA key is not a
  production release identity.
- Repository-side Python contracts also passed fresh on 2026-09-22: Community Directory 4/4, Atelier
  assets 8/8, original arrow library 4/4 and arrow-reference library 7/7. Connected ADB could not
  start in this sandbox because its local daemon socket is denied; no device result is inferred.
- Android debug/release unit tests and `lintRelease` — passed.
- Android instrumentation smoke contract — 3/3 passed on the API-35 ARM64 emulator; the Android
  workflow runs it on a fresh API-35 emulator. The signed release workflow separately installs the
  minified APK and verifies direct launcher startup, process liveness and fatal/class-loading logs.
- Atelier asset contracts: `test_assets.py` (8), `test_arrow_library.py` (4), `test_arrow_reference_library.py` (7) — passed.
- Original and arrow-reference generators were rerun twice and produced no tracked drift.
- QA release APK and AAB built with a throwaway local QA keystore; APK v2 signature, AAB `jarsigner`, manifest/security checks and ZIP alignment passed.
- Release candidate `v0.6.0-rc.2` (versionCode 602) was rebuilt with `assembleRelease`,
  `bundleRelease`, `testReleaseUnitTest` and `lintRelease`. APK SHA-256:
  `3aefac4f4138de33313c10cc8b0dd9efe02370b619c5eb74e0c5b23525a66e85`; AAB SHA-256:
  `2b43deecf044d7ec2f8b4c430c4390a59fb72d643b4602a65c94e4adc7d1eeb7`.
- Final debug APK (`a25c5ec863be8cbe731c508b94e2c9986c104b3574b849682b37e53729124461`) was installed on the API-35 ARM64 emulator and exercised through Home → Equipment Atelier → Fletching. The corrected scaled family rendered; no fatal exception, uncaught JavaScript error, or WebGL error was observed.
- A current-tree replay also exercised Home, Train, Progress, Body and Calendar before reopening the
  Atelier. The accessibility tree exposed all eight selectors and the complete fletching component
  set; a post-clear isolated log contained no fatal, uncaught-JavaScript, WebGL or Chromium error.
- Fresh browser verification was not possible in this environment because Node/Chromium are unavailable. The committed real-Chromium report from the visual handoff remains at `docs/crocodyl/arrow-reference-previews/verification.json`.
- Fencing audit: the Home card, Community Directory card, Fencing activity, three weapon selectors,
  weapon metadata, manual two-fencer console, score, call history, undo/reset and referee vocabulary
  are visible and covered by the 3/3 Android smoke suite. Pure-JVM tests cover the state core; the
  lifecycle-scoped `FencingViewModel` preserves match state across configuration changes and its
  Android unit tests cover weapon switching and bounded fencer labels. The
  minified release connected suite also passed 3/3 with the QA-only signing substitute. Automatic
  camera adjudication remains explicitly out of scope for this slice; see
  `docs/crocodyl/FENCING_AND_ECOSYSTEM_ROADMAP.md`.
- The reusable `tools/release/verify_android_release.sh` preflight was run against that APK/AAB and
  passed metadata, signing, alignment, manifest privacy, model-hash, backup-resource and staging-file
  checks. The release workflow now calls this same script.
- Android debug and release unit suites pass with ArcheryAnalyzer persistence/baseline bridge
  coverage in `ArcheryAnalyzerTest`; the test runs under Robolectric because the bridge uses
  Android's JSON implementation.
- `ManifestSecurityTest` also passes in both variants, covering the committed cleartext/backup/
  FileProvider manifest policy and backup-rule sources; packaged policy remains checked by release
  preflight.
- `AppDatabaseMigrationTest` now covers durable reset-notice state and explicit dismissal in both
  variants, in addition to migration and recovery-byte preservation.
- `VaultTest` passes in both variants, covering Tink encryption/decryption, plaintext exclusion,
  metadata integrity, associated-data rejection and failed-view cleanup.
- `ModelInstallTest` also covers model inventory filtering and path-like display-name sanitization
  in both variants, preserving the atomic private model boundary.
- The Android and JVM test workflows explicitly request only `contents: read`; only the release
  workflow retains `contents: write` for publishing artifacts.
- Full current-tree verification on 2026-09-21 passed root JVM tests, Android debug/release unit
  tests, release lint, and connected instrumentation 3/3 on API-35 ARM64 emulator-5554 using
  verification versionCode 604.
- The release workflow now attaches a SHA-256 manifest beside the APK and AAB, allowing downloaded
  artifacts to be independently verified against the signed build output.
- Physical-device sign-off can be recorded with DEVICE_ACCEPTANCE_RUN_TEMPLATE.md, which mirrors
  the required install, camera, non-camera, volume and defect gates without marking them complete.

- Share-for-sharing exports now stage into a hidden same-directory `.partial` file, publish with a
  same-directory rename after successful archive creation, and clean up both paths on failure. This
  protects the cache-backed share flow from interrupted archives; SAF destination writes remain
  caller-owned and are intentionally outside this atomic-rename path. The debug and release Android
  unit suites passed after `21d52bd`; `ShareExportPublisherTest` covers both success and failure
  cleanup paths.
- A post-hardening offline verification run passed the root JVM suite, Android debug/release unit
  suites, and `lintRelease` together after `21d52bd`.
- The release preflight now requires the `CROCODYL_RELEASE_CERT_SHA256` secret and compares the
  normalized signer fingerprint in both APK and AAB. Configure it to the production certificate
  before publishing; a QA certificate is acceptable only for explicitly labeled local validation.
- Release CI now opts into `connectedReleaseAndroidTest` after signing through
  `-PcrocodylInstrumentRelease=true`; the existing debug workflow remains unchanged.
- The same task passed 3/3 locally on API-35 ARM64 with a QA-only debug-keystore signature; retain
  that result as minified-runtime evidence and do not treat it as production-signing acceptance.

- Shared beta integration is wired across repositories. Shared-Libraries-asoc provides
  `dev.aarso:local-session-core:0.1.0` with TCP loopback and pairing-code test coverage; Crocodyl
  and Bocal beta Android source sets forward the same optional pairing code. Crocodyl's
  `performance-report` module completed its shared search/session benchmark at revision `6b54b98`;
  the current report is under `performance-report/build/reports/beta/`. The JSON is a beta
  diagnostic artifact, not a release-performance claim. The pairing revision adds the pairing gate
  and a 64 KiB frame bound; Android compilation and physical two-device verification remain open.

## Important release boundary

The current user-facing nearby-session entry is named **Local Multiplayer**. A persisted first-run
walkthrough now explains host/join roles, same-network setup, six-digit code verification and the
desktop/file fallback. This is UX scaffolding around the existing beta transport; it does not
claim production discovery, reconnect, identity or two-device acceptance.

For coaches without a phone, `web-viewer/` is a dependency-free static desktop fallback. It reads
`.crocbak` recovery archives and signed `.croc` sharing archives entirely in-browser, with no upload
or automatic import. New `.croc` envelopes authenticate the payload checksum, sorted included-table
list and consent tier; legacy checksum-only signatures remain readable. The viewer verifies the
producer signature locally and is suitable for files moved through WhatsApp, removable media,
Bluetooth or LocalSend.

The walkthrough preference is behaviorally enforced: first entry shows the four-step guide and
subsequent entries show a compact setup summary with a review action.

Fresh verification note (2026-09-27): root JVM tests, Android debug/release unit tests and
`lintRelease` pass with Temurin JDK 21, Gradle 8.14.3 and API 35/build-tools 35.0.0. The offline
viewer contract suite passes 10/10, and executable WebCrypto interoperability covers valid,
tampered, legacy and consent-envelope signatures, and checksum-only recovery files. Connected
instrumentation, production signing and physical-device acceptance remain separate gates.

The current recovery path writes a SHA-256 manifest beside preserved database/WAL/SHM files and
accepts reset only for an intact backup directly under the app-private recovery root. Both Android
unit variants, release lint and connected debug instrumentation passed after this hardening.

The local release artifacts are QA-signed only. A production release still requires the real production keystore/CI identity, final versioning, owner approval, physical-device/accessibility/thermal/battery checks, and range acceptance. Do not distribute the QA-signed artifacts.

The exact owner-arrow catalog is intentionally not complete. The one-piece hardened 416 stainless glue-in point, adjustable glue-in bullet weight modules, and independent fletching taxonomy are now represented as separately named generic taxonomy components, with source/provenance and deterministic contract coverage. The next in-scope work is intake and approval of one exact owner arrow. Do not expand the recurve collection or infer component compatibility while doing so.

The next milestone is now scaffolded without disturbing that work: `fencing-module` defines Sabre,
Foil and Epee plus provisional Prêt/Allez/point-call referee state, and the Android Home surface
shows Fencing Studies and Community Directory cards. See
[`docs/crocodyl/FENCING_AND_ECOSYSTEM_ROADMAP.md`](crocodyl/FENCING_AND_ECOSYSTEM_ROADMAP.md)
and [`docs/crocodyl/community-directory/README.md`](crocodyl/community-directory/README.md).

Local multiplayer is scaffolded at the shared-library boundary. The beta TCP transport is usable
for development-network testing, but production multiplayer still needs discovery/pairing, trust
and identity UX, reconnect semantics, host-authoritative event mapping, and physical two-device
acceptance in both Bocal and Crocodyl.

## Recommended continuation order

1. Read `docs/crocodyl/atelier/CODEX_HANDOFF.md`, `docs/crocodyl/visuals/ARROW_FIRST_ART_DIRECTION.md`, `docs/crocodyl/visuals/LANCASTER_ARROW_REFERENCE_LEDGER.md`, and `docs/crocodyl/RELEASE_ACCEPTANCE_CHECKLIST.md`.
2. Run the asset and repository gates documented in `CROCODYL_BUILD_NOTES.md` and this handoff.
3. Stop for owner-arrow reference intake and approval before modelling an exact configured arrow.
4. For release, replace the QA signing inputs with the production signing identity and complete the physical-device/range acceptance checklist.

## Key commits from this pass

- `33d5481` — integrate the arrow-first visual handoff and Android studio entry.
- `c18aaad` — harden Android production/recovery/import/release paths.
- `6726bbf` — align the visual host with the theme contract.
- `e6d552e` — bind a fallback WebGL texture.
- `cae3f5e` — run Atelier validation on release-relevant paths.
- `ee5ba68` — canonicalize generated mesh artifacts and enforce generator drift checks.
- `7009f18` — refresh canonical artifact evidence.
- `6878605` — enforce APK alignment in release CI.
- `b74392c` — align the acceptance checklist with the alignment gate.
- `a8c32e8` — add the remaining 416 stainless and adjustable-module point families with contracts.
- `2663615` — add the fletching reference taxonomy and eighth studio selector.
- `44a0bac` — scale fletching studies for mobile and regenerate the canonical GLB.
- `44a9c23` — record final fletching smoke evidence and release status.
- `c57f1a7` — make arrow-taxonomy validation read-only and reject generated-asset drift.
- `700d1bd` — verify packaged APK version metadata in the release workflow.
- `9b1c84a` — record the current-tree emulator navigation and clean-log replay.
- `9cd7bdd` — add the release emulator smoke contract and wire it into Android/release CI.
- `fc92ab6` — refresh final rc.2 artifact digests and acceptance evidence after the smoke gate.
- `f4c0dcb` — centralize Android release artifact preflight in CI and local tooling.
- `8c7271a` — cover persisted analyzer features and baseline scoring at the Android app boundary.
- `06de23b` — lock down backup, cleartext and FileProvider privacy policy in Android tests.
- `617771d` — cover durable database recovery notices and explicit dismissal.
- `d62d0f7` — cover encrypted vault boundaries and authenticated view cleanup.
- `3add644` — harden model inventory and filename-sanitization contracts.
- `58941f3` — restrict ordinary CI workflows to read-only repository permissions.
- `7d55776` — record the full current-tree verification and clean emulator smoke replay.
- `e48f419` — publish release artifact SHA-256 checksums.
- `e9b0211` — add the physical-device acceptance run sheet.
- `db4747a` — publish cache-backed share exports atomically and clean up failed publications.
- `21d52bd` — add debug/release regression coverage for share-export publication and cleanup.
