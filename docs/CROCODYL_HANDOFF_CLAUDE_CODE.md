# Crocodyl handoff to Claude Code

Status: current snapshot, 2026-09-18 (Asia/Kolkata)

Repository: `mbaliga/Form-analyser`

Checkout: `/Users/tulikapandey/Documents/Codex/2026-09-14/cur/work/crocodyl-current`

Branch: `codex/production-hardening`

Latest implementation commit: `2663615 feat(atelier): add fletching reference taxonomy`

The worktree was clean when this handoff was written. All changes made during this pass were committed incrementally with context in the commit messages.

## Current result

Crocodyl is a production candidate for its current Android/local-first scope. The earlier arrow-first visual handoff was integrated and preserved: the generic reference library remains modular by shaft profile, shaft construction, point family and nock/rear interface, with separate component selection and analysis in the local WebGL2 studio. No generic mesh is presented as the owner's equipment.

The build/release path is hardened with pinned toolchain/model inputs, fail-closed release signing, Room migration/recovery safeguards, atomic vault/model imports, validation around scoring links/candidates, release CI checks, deterministic generated-asset checks, APK signature verification and ZIP alignment verification.

## Verified in this checkout

- Root JVM suite: `./gradlew test --offline --no-daemon` — passed.
- Android debug/release unit tests and `lintRelease` — passed.
- Atelier asset contracts: `test_assets.py` (8), `test_arrow_library.py` (4), `test_arrow_reference_library.py` (5) — passed.
- Original and arrow-reference generators were rerun twice and produced no tracked drift.
- QA release APK and AAB built with a throwaway local QA keystore; APK v2 signature, AAB `jarsigner`, manifest/security checks and ZIP alignment passed.
- Final debug APK was installed on the available emulator and exercised through Home → Equipment Atelier. No fatal exception or renderer warning was observed.
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
