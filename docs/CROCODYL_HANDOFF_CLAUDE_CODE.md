# Crocodyl handoff to Claude Code

Status: current snapshot, 2026-09-20 (Asia/Kolkata)

Repository: `mbaliga/Form-analyser`

Checkout: `/Users/tulikapandey/Documents/Codex/2026-09-14/cur/work/crocodyl-current`

Branch: `codex/production-hardening`

Latest implementation commit: `44a0bac fix(atelier): scale fletching studies for mobile`
Latest evidence commit: `9b1c84a docs(qa): record current-tree emulator replay`
Latest test-hardening commit: `9cd7bdd test(android): add release emulator smoke contract`

The worktree was clean when this handoff was written. All changes made during this pass were committed incrementally with context in the commit messages.

## Current result

Crocodyl is a production candidate for its current Android/local-first scope. The earlier arrow-first visual handoff was integrated and preserved: the generic reference library remains modular by shaft profile, shaft construction, point family and nock/rear interface, with separate component selection and analysis in the local WebGL2 studio. No generic mesh is presented as the owner's equipment.

The build/release path is hardened with pinned toolchain/model inputs, fail-closed release signing, Room migration/recovery safeguards, atomic vault/model imports, validation around scoring links/candidates, release CI checks, deterministic generated-asset checks, APK signature verification and ZIP alignment verification.

## Verified in this checkout

- Root JVM suite: `./gradlew test --offline --no-daemon` — passed.
- Android debug/release unit tests and `lintRelease` — passed.
- Android instrumentation smoke contract — 2/2 passed on the API-35 ARM64 emulator; CI and the
  signed release workflow now run it on a fresh API-35 emulator.
- Atelier asset contracts: `test_assets.py` (8), `test_arrow_library.py` (4), `test_arrow_reference_library.py` (7) — passed.
- Original and arrow-reference generators were rerun twice and produced no tracked drift.
- QA release APK and AAB built with a throwaway local QA keystore; APK v2 signature, AAB `jarsigner`, manifest/security checks and ZIP alignment passed.
- Release candidate `v0.6.0-rc.2` (versionCode 602) was rebuilt with `assembleRelease`,
  `bundleRelease`, `testReleaseUnitTest` and `lintRelease`. APK SHA-256:
  `494b33c802230b0651310592fa8629d63fe0d55cef7a6b36cdbb7c319bbd268d`; AAB SHA-256:
  `8964f27a5d2577a17f5ee65510c958384d02fc3e7e3aaae0f49d99d1e0b3dc45`.
- Final debug APK (`a25c5ec863be8cbe731c508b94e2c9986c104b3574b849682b37e53729124461`) was installed on the API-35 ARM64 emulator and exercised through Home → Equipment Atelier → Fletching. The corrected scaled family rendered; no fatal exception, uncaught JavaScript error, or WebGL error was observed.
- A current-tree replay also exercised Home, Train, Progress, Body and Calendar before reopening the
  Atelier. The accessibility tree exposed all eight selectors and the complete fletching component
  set; a post-clear isolated log contained no fatal, uncaught-JavaScript, WebGL or Chromium error.
- Fresh browser verification was not possible in this environment because Node/Chromium are unavailable. The committed real-Chromium report from the visual handoff remains at `docs/crocodyl/arrow-reference-previews/verification.json`.

## Important release boundary

The local release artifacts are QA-signed only. A production release still requires the real production keystore/CI identity, final versioning, owner approval, physical-device/accessibility/thermal/battery checks, and range acceptance. Do not distribute the QA-signed artifacts.

The exact owner-arrow catalog is intentionally not complete. The one-piece hardened 416 stainless glue-in point, adjustable glue-in bullet weight modules, and independent fletching taxonomy are now represented as separately named generic taxonomy components, with source/provenance and deterministic contract coverage. The next in-scope work is intake and approval of one exact owner arrow. Do not expand the recurve collection or infer component compatibility while doing so.

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
