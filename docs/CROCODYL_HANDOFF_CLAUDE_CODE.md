# Crocodyl handoff to Claude Code

Status: current snapshot, 2026-09-20 (Asia/Kolkata)

Repository: `mbaliga/Form-analyser`

Checkout: `/Users/tulikapandey/Documents/Codex/2026-09-14/cur/work/crocodyl-current`

Branch: `codex/production-hardening`

Latest implementation commit: `b3a91bb ci(android): instrument signed minified release`
Latest evidence commit: `0bcdaf4 docs(status): record release-variant verification`
Latest test-hardening commit: `3add644 test(android): harden model inventory contracts`

The worktree was clean when this handoff was written. All changes made during this pass were committed incrementally with context in the commit messages.

## Current result

Crocodyl is a production candidate for its current Android/local-first scope. The earlier arrow-first visual handoff was integrated and preserved: the generic reference library remains modular by shaft profile, shaft construction, point family and nock/rear interface, with separate component selection and analysis in the local WebGL2 studio. No generic mesh is presented as the owner's equipment.

The build/release path is hardened with pinned toolchain/model inputs, fail-closed release signing, Room migration/recovery safeguards, atomic vault/model imports, validation around scoring links/candidates, release CI checks, deterministic generated-asset checks, APK signature verification and ZIP alignment verification.

## Verified in this checkout

- Root JVM suite: `./gradlew test --offline --no-daemon` — passed.
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
- Fencing scaffold audit: the Home card, Community Directory card, Fencing activity, three weapon
  shells and referee vocabulary are visible and covered by the 3/3 Android smoke suite. QA release
  build `0.6.0-fencing-scaffold` (versionCode 603) also passed release minification, lint, packaging
  and direct cold start; see `docs/crocodyl/FENCING_AND_ECOSYSTEM_ROADMAP.md` for digests and scope.
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

## Important release boundary

The local release artifacts are QA-signed only. A production release still requires the real production keystore/CI identity, final versioning, owner approval, physical-device/accessibility/thermal/battery checks, and range acceptance. Do not distribute the QA-signed artifacts.

The exact owner-arrow catalog is intentionally not complete. The one-piece hardened 416 stainless glue-in point, adjustable glue-in bullet weight modules, and independent fletching taxonomy are now represented as separately named generic taxonomy components, with source/provenance and deterministic contract coverage. The next in-scope work is intake and approval of one exact owner arrow. Do not expand the recurve collection or infer component compatibility while doing so.

The next milestone is now scaffolded without disturbing that work: `fencing-module` defines Sabre,
Foil and Epee plus provisional Prêt/Allez/point-call referee state, and the Android Home surface
shows Fencing Studies and Community Directory cards. See
[`docs/crocodyl/FENCING_AND_ECOSYSTEM_ROADMAP.md`](crocodyl/FENCING_AND_ECOSYSTEM_ROADMAP.md)
and [`docs/crocodyl/community-directory/README.md`](crocodyl/community-directory/README.md).

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
