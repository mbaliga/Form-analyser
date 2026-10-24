# Crocodyl arrow-first Codex handoff

Status: current working handoff, 2026-09-20 (Asia/Kolkata)

Repository: `mbaliga/Form-analyser`

Branch: `codex/production-hardening`

Pull request: [#11 — Make Crocodyl visually rich and athlete-focused](https://github.com/mbaliga/Form-analyser/pull/11), open into `main`
The earlier `feat/immersive-visuals` handoff was integrated into this branch; latest release-evidence commit: `fc92ab6` (`docs(release): refresh post-smoke artifact evidence`).

This is the execution index for the next Codex pass. It records what is real in the current branch, what the verification evidence proves, and the exact order for continuing the arrow-first work. It does not approve any generic mesh as the owner's equipment.

## Authority and file precedence

Use these files as the sources of truth. If an older document or generated asset uses a different name or taxonomy, do not silently merge the two lines.

| Concern | Authoritative file or directory |
| --- | --- |
| Owner scope and approval boundary | [`docs/crocodyl/visuals/ARROW_FIRST_ART_DIRECTION.md`](../visuals/ARROW_FIRST_ART_DIRECTION.md) |
| Lancaster facts, URLs and provenance status | [`docs/crocodyl/visuals/LANCASTER_ARROW_REFERENCE_LEDGER.md`](../visuals/LANCASTER_ARROW_REFERENCE_LEDGER.md) |
| Active taxonomy generator | [`tools/atelier/build_arrow_reference_library.py`](../../../tools/atelier/build_arrow_reference_library.py) |
| Active taxonomy contract tests | [`tools/atelier/test_arrow_reference_library.py`](../../../tools/atelier/test_arrow_reference_library.py) |
| Active real-Chromium/WebGL smoke test | [`tools/atelier/verify_arrow_reference_browser.mjs`](../../../tools/atelier/verify_arrow_reference_browser.mjs) |
| Active generated taxonomy assets | `app-android/src/main/assets/atelier/arrow-reference-library/` |
| Active studio runtime | `app-android/src/main/assets/atelier/index.html`, `atelier.js`, `atelier.css` |
| Committed browser evidence | `docs/crocodyl/arrow-reference-previews/verification.json` |
| General original-equipment handoff | [`ASSET_HANDOFF.md`](ASSET_HANDOFF.md) |

The older `tools/atelier/arrow_library.py` and `app-android/src/main/assets/atelier/arrow-library/` are the earlier v1 taxonomy experiment. They include a square target-face study, but are not the active Lancaster reference-library generator or the five-family studio surface. Do not extend that older path when continuing the arrow pass.

The generated files and manifests are outputs, not permission to invent hidden dimensions. The generator, ledger and tests must be changed together when a new taxonomy family is added.

## Current state

Crocodyl is still an Android-first, local-first athlete performance app whose first discipline is Olympic Recurve archery. This handoff is specifically about the equipment-visual work inside PR #11. The selected production direction is **arrow first**: establish one accurate, owner-reviewed arrow before widening the recurve equipment collection or polishing speculative anatomy.

The branch contains a generic, Lancaster-grounded reference library for reusable arrow systems. Lancaster Archery Supply product pages and photography were used to identify families, interfaces and published examples. The repository contains derived neutral geometry and source URLs/facts only; it does not contain Lancaster product photography.

## Implemented and present

### Active modular taxonomy

The active library keeps these axes independent. A later configured arrow must select one value on each relevant axis; no single “arrow type” value may replace them.

| Axis | Implemented families in the active GLB |
| --- | --- |
| Shaft external profile | `parallel`, `barrelled`, `front_taper`, `rear_taper` (front-parallel / rear-taper) |
| Shaft construction | `aluminium`, `all_carbon`, `carbon_over_aluminium`, `aluminium_over_carbon` |
| Point profile/family | stainless break-off target, tungsten break-off target, aluminium glue-in bullet, NIBB/long-shank, 8-32 screw-in bullet, 8-32 screw-in field, glue-in chisel, one-piece 416 stainless glue-in, adjustable glue-in bullet |
| Point material | stainless steel, tungsten alloy, aluminium, steel, or material-neutral where the profile is the subject |
| Point attachment | glue-in or 8-32 screw-in; break-off is a separate weight-adjustment construction, not an attachment label |
| Point weight adjustment | break-off sections and removable 10/20/50-grain adjustable-bullet modules are represented separately from nose shape, material and attachment |
| Nock/rear interface | direct-fit insert, pin-small groove, pin-large groove, pin-out/over-shaft, bushing-backed insert, conventional swaged rear, separate rear impact collar |
| Fletching | Mylar/spin-wing, low-profile polymer, shield-cut plastic, turkey feather; straight/offset/helical mounting; three/four-fletch clocking; wrap and nock-relative reference |

The current eight studio selections are: Recurve, complete generic Arrow, Target, Shaft profiles, Shaft construction, Points, Nocks and Fletching. The five active taxonomy GLBs are:

```text
app-android/src/main/assets/atelier/arrow-reference-library/
├── shaft_profiles.glb
├── shaft_constructions.glb
├── points.glb
├── nock_systems.glb
├── fletching.glb
└── manifest.json
```

The GLBs are generic educational studies, standard self-contained glTF 2.0 assets in metres, with stable semantic part IDs and source/provenance metadata. The published X10 Parallel Pro 600 example is represented with its published 0.194 in OD and 32 in length. Taper stations, layer thicknesses and other unpublished geometry remain generic topology.

### Studio and app integration

The active five-family assets load through the same local WebGL2 studio as the original studies. The implemented surface includes orbit, zoom, named component focus, separation/reassembly, dark/light presentation, reduced-motion handling, idle/lifecycle suspension and a contained horizontally scrolling mobile selector. The Android Home entry remains a static poster; the renderer opens only when the user deliberately enters the studio.

The WebView is local-assets-only. Preserve the current absence of CDN requests, retailer-image requests, athlete-data bridges, file access and content access.

The original recurve, complete-arrow and target models remain generic studies. They are not owner-approved commercial replicas, reference-accurate equipment or instructional anatomy. The body atlas remains 2D; no rigged human, 3D muscle atlas or validated shot animation is part of this delivery.

## What is verified

The committed evidence in `docs/crocodyl/arrow-reference-previews/verification.json` records the successful real-Chromium pass for the active reference families:

- each GLB uploaded and drew;
- WebGL reported no error;
- component controls were created;
- component selection/focus worked;
- mobile had no horizontal overflow;
- zero JavaScript exceptions;
- all requests remained local. The browser harness now explicitly requires the 416 stainless and adjustable-point controls plus the full fletching taxonomy control set on future runs.

The active contract test also regenerates the library in a temporary directory and checks deterministic structure, glTF 2.0 validity, finite geometry, semantic part IDs, source policy, embedded/self-contained assets, mobile-size limits and the published parallel-shaft conversion.

The original equipment pipeline has its own generator, GLB contract tests and browser evidence. Treat `docs/crocodyl/atelier-previews/verification.json` and `CINEMATIC_VERIFICATION_2026-09-17.md` as evidence for that separate original-study pack, not as evidence that the owner’s arrow has been modelled.

For this handoff, the active and original Python contract suites plus `git diff --check` passed locally. The active library was regenerated twice with identical output after adding the 416 stainless, adjustable-module and scaled-fletching components. The browser smoke harness now explicitly checks the new point and fletching controls. No fresh browser result was produced in this checkout because Node/Chromium tooling (`node`, `xvfb-run`) is not installed here; the committed JSON report remains the existing real-Chromium evidence, while the updated harness is the required rerun when those tools are available. The corrected fletching path was separately exercised through the Android emulator WebView, and the Android release-candidate build/test/lint gates passed with the QA-only signing identity.

The release workflows now fail closed on generated-asset drift and packaged version metadata. The
former branch-specific taxonomy baker is read-only and cannot auto-commit or push generated files.
The Android workflow runs the real-device smoke contract for launcher startup and the offline Atelier
WebView boundary; the local API-35 run passed 2/2 tests. The signed-release workflow separately
installs the minified release APK and verifies direct launcher startup, process liveness and clean
fatal/class-loading logs.

These checks do not establish:

- physical Android/WebView/GPU, accessibility, thermal, battery or range behaviour;
- exact commercial-product replication beyond explicitly published dimensions;
- arrow fitment, spine, length, point mass, FOC or tuning advice;
- compatibility between components;
- owner approval of any geometry;
- fresh browser evidence for the newly added fletching family or an exact configured owner arrow.

## Non-negotiable constraints

1. **Do not expand recurve yet.** Do not add or refine quiver, rest, extension, Y-bracket, sight bar, long rod, stabilisers, string, sight, finger tab, riser, limbs, dampeners, compound, barebow or another equipment collection in this arrow pass.
2. **Do not copy Lancaster images.** Product photographs remain remote references only. Do not vendor, embed, trace into textures or redistribute them. Render Crocodyl images from approved Crocodyl geometry.
3. **Do not infer compatibility.** Diameter, groove class, thread or visual similarity alone is not a compatibility claim. Preserve explicit product-family constraints and mark unknowns.
4. **Preserve modular taxonomy axes.** Keep shaft profile separate from construction; point shape/profile separate from material, attachment and weight adjustment; and nock shape separate from the rear attachment/interface. Never relabel an existing mesh to stand for a different system.
5. Keep source status explicit: `published`, `photo-observed`, `generic-topology`, `measured` or `unknown`. A render does not turn an estimate into a measurement.
6. Preserve athlete data, scoring mathematics, exchange/trust work and the Baseline boundary. Do not merge PR #10 or `main` as part of this asset handoff.

## Exact next steps

Work in this order; do not skip ahead to an exact arrow composition.

### 1. Compose the exact owner equipment

The generic point and fletching taxonomies are now present. Intake one coherent owner-selected arrow configuration:

- full-length side-on reference;
- point, nock and fletching close-ups;
- end-on rear view for rotational alignment where practical;
- ruler or known measurement;
- manufacturer/model information for each component, if available.

Record every dimension with units and a status of measured, manufacturer-published, visually estimated or unknown. Preserve hidden construction as unknown unless documented. Choose components only where compatibility is explicitly supported; do not build a plausible mixture of products. Review neutral-material side, top/end and macro views against the references, obtain owner approval, and only then apply cinematic materials, lighting, branding, labels or animation. The interactive GLB and every hero/macro image must come from that same approved geometry.

## QA commands

Run from the repository root. These commands are the active arrow-library gate:

```sh
python3 tools/atelier/build_arrow_reference_library.py
python3 tools/atelier/test_arrow_reference_library.py
xvfb-run -a node tools/atelier/verify_arrow_reference_browser.mjs
git diff --check
```

The browser command requires Chromium/Chrome and writes the committed report under `docs/crocodyl/arrow-reference-previews/`. Review the report rather than relying on an old commit message.

For the separate original equipment pack, run:

```sh
python3 tools/atelier/build_assets.py
python3 tools/atelier/test_assets.py
xvfb-run -a node tools/atelier/verify_browser.mjs
```

For repository gates, pure JVM tests require JDK 21:

```sh
./gradlew test --no-daemon
```

Android assembly requires an Android SDK and is not included in the default Gradle settings unless requested explicitly:

```sh
./gradlew :app-android:assembleDebug -PwithAndroid --no-daemon
```

An Android assemble is a packaging/compile check only. It is not physical-device QA.

## Handoff completion rule

This document is complete when it is committed on `feat/immersive-visuals` alongside the current branch context. Future implementation work is not complete merely because a model loads: each new family needs source/provenance, deterministic generation, semantic IDs, contract tests, real-Chromium evidence and explicit uncertainty/compatibility boundaries.
