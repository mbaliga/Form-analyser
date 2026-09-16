# Crocodyl 3D and cinematic asset handoff

Date: 2026-09-17. Working branch: `feat/immersive-visuals` (PR #11).

## Scope and approval

This pass establishes portable original 3D meshes, an offline equipment viewer, reproducible export/render tooling, an environmental image, and a **Lancaster-grounded modular target-arrow reference library**. It does **not** establish faithful replication of a specific commercial product, anatomical accuracy, measured biomechanics, equipment compatibility, or validated instructional motion.

Respect [`../visuals/ARROW_FIRST_ART_DIRECTION.md`](../visuals/ARROW_FIRST_ART_DIRECTION.md): **arrow first**. The original recurve, arrow and target remain unapproved generic studies. Preserve them without expanding the bow collection. The Lancaster library is deliberately generic and composable: shaft profile, shaft construction, point profile/material/attachment and nock/rear interface are distinct axes rather than one monolithic arrow model.

The requested square paper target sheet remains a separate correction: circular scoring rings do not imply a circular sheet. Do not turn further target work into a second active modelling project.

## Lancaster reference policy

Lancaster Archery Supply was used as a current reference catalog for product specifications, interface families and product-photo inspection. **Lancaster product photographs are not vendored, embedded, traced into textures, or redistributed in this repository.** Remote URLs and factual specifications are retained in [`../visuals/LANCASTER_ARROW_REFERENCE_LEDGER.md`](../visuals/LANCASTER_ARROW_REFERENCE_LEDGER.md).

The provenance ledger distinguishes:

- `published`: material/dimension/interface explicitly stated on a Lancaster product or guide page;
- `photo-observed`: visible silhouette or relationship in product photography, never promoted to a measurement;
- `generic-topology`: the equipment family is source-backed but Lancaster does not publish enough geometry to build an exact replica.

Examples: the parallel-shaft study uses the published X10 Parallel Pro 600 dimensions (0.194 in OD, 32 in uncut length), while exact taper stations for the barrelled X10, ProTour front taper and VXT rear taper remain generic topology. Compatibility is never inferred from diameter alone.

## Files to use

| Deliverable | Location |
| --- | --- |
| Original equipment procedural source | `tools/atelier/build_assets.py` |
| Original equipment GLBs | `app-android/src/main/assets/atelier/models/` |
| Lancaster-grounded taxonomy generator | `tools/atelier/build_arrow_reference_library.py` |
| Taxonomy contract tests | `tools/atelier/test_arrow_reference_library.py` |
| Taxonomy browser/WebGL smoke tests | `tools/atelier/verify_arrow_reference_browser.mjs` |
| Lancaster provenance/spec ledger | `docs/crocodyl/visuals/LANCASTER_ARROW_REFERENCE_LEDGER.md` |
| Generated taxonomy GLBs + manifest | `app-android/src/main/assets/atelier/arrow-reference-library/` |
| Offline WebGL2 viewer | `app-android/src/main/assets/atelier/index.html`, `atelier.js`, `atelier.css` |
| Non-exported Android host | `app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/EquipmentAtelierActivity.kt` |
| Static Home entry | `app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/components/EquipmentAtelierCard.kt` |
| Original-mesh regression suite | `tools/atelier/test_assets.py` |
| Original studio browser harness | `tools/atelier/verify_browser.mjs` / `.github/workflows/bake-atelier.yml` |
| Same-mesh dark/light posters | `app-android/src/main/assets/atelier/posters/` |
| Generated environmental art and provenance | `app-android/src/main/assets/atelier/art/` |

## Arrow reference library currently available

### Shaft profiles

- Parallel cylindrical
- Barrelled
- Front taper
- Front-parallel / rear taper

### Shaft constructions

- Aluminium alloy
- All-carbon composite
- Carbon exterior bonded to aluminium core
- Aluminium exterior around carbon core, included as the inverse laminate construction example rather than a target recommendation

### Point families

- Stainless break-off target point
- Tungsten break-off target point
- Aluminium glue-in bullet
- NIBB / long-shank target point
- 8-32 screw-in bullet
- 8-32 screw-in field point
- Glue-in chisel target point

Point **shape**, **material**, **attachment method**, and **weight-adjustment construction** remain separate concepts. Do not collapse “bullet”, “tungsten”, “break-off”, and “glue-in” into one mutually exclusive field.

Lancaster also documents one-piece hardened 416 stainless glue-in points and adjustable glue-in bullet systems with separate weight modules. Those are valid future taxonomy additions if the equipment UI needs that level of completeness; do not silently reinterpret an existing mesh as one of those systems.

### Nock and rear-end systems

- Direct-fit / insert nock
- Pin nock with small-groove exemplar
- Pin nock with large-groove exemplar
- Pin-out / over-shaft nock
- Bushing-backed insert nock
- Conventional swaged/tapered rear
- Separate rear impact collar

Representative groove values from Lancaster/Beiter/Easton references are examples, not universal fit values. Preserve product-family compatibility constraints.

## What is wired into Crocodyl

The equipment studio now exposes seven selectable families:

1. Recurve study
2. Complete generic arrow study
3. Target study
4. Shaft profiles
5. Shaft constructions
6. Point families
7. Nock/rear-interface families

All four new arrow taxonomy GLBs load through the same local WebGL2 renderer as the original studies. Orbit, zoom, component focus, separation/reassembly, light/dark presentation, reduced motion and lifecycle suspension are retained. Mobile navigation is a contained horizontally scrollable strip rather than widening the document.

The Android entry still uses a static poster until the user deliberately opens the studio; the renderer does not run continuously in the Home feed. The WebView remains local-assets-only with no CDN, retailer image requests, athlete-data bridge, file access or content access.

## Verification

The Lancaster taxonomy build validates deterministic GLB output, expected family/part IDs, self-contained assets, mobile-size budgets and source policy. A dedicated real-Chromium smoke test then loads **shaft profiles, shaft constructions, points and nock systems** through the production studio and verifies, for every family:

- GLB uploaded and drawn;
- no WebGL error;
- component controls created;
- component focus works;
- no mobile horizontal overflow.

The current browser pass also records zero JavaScript exceptions and zero external requests. It explicitly does **not** verify physical Android-device behaviour, manufacturer-replica accuracy beyond published dimensions, equipment compatibility or fitting advice.

The original model pipeline separately retains eight geometry/export tests including outward-facing tube side/cap winding. The first broad viewer pass and later corrected passes should be judged by their current generated reports rather than historical commit messages.

## Image usage

`art/range-dawn.webp` is an original AI-generated environmental illustration and is optional atmosphere only. It is not a photograph of a real range or evidence for dimensions, equipment or safety layout.

Lancaster catalog/product images are remote visual references only and must not be bundled into the app. When Crocodyl needs production card/hero imagery, render it from the approved Crocodyl geometry so the attractive image and interactive object stay mechanically consistent.

The earlier promotional anatomy/UI composite is not approved teaching material. There is no rigged human model, 3D anatomical atlas or validated shot animation in this delivery; the existing body atlas remains 2D.

## Reproduction

```sh
python3 tools/atelier/build_assets.py
python3 tools/atelier/test_assets.py
python3 tools/atelier/build_arrow_reference_library.py
python3 tools/atelier/test_arrow_reference_library.py
xvfb-run -a node tools/atelier/verify_arrow_reference_browser.mjs
# Android compilation when an SDK is available:
./gradlew :app-android:assembleDebug -PwithAndroid --no-daemon
```

## Next work for Claude / future equipment chats

1. Use the modular reference families to describe or configure a real arrow without inventing incompatible combinations.
2. When the owner supplies a specific arrow, record each dimension as measured, manufacturer-published, visually estimated or unknown; then compose/replace only the relevant generic parts.
3. For additional point coverage, model the one-piece 416 stainless glue-in family and adjustable bullet + removable weight-module family as new, separate components rather than relabelling existing break-off/bullet meshes.
4. Add fletching taxonomy only when requested: vane/feather/spin-wing type, count, offset/helical mounting, wrap and nock clocking are another independent layer.
5. For instructional movement, use an approved real-archer photo/video sequence and preserve observable contact relationships. Do not invent ideal joint angles, muscle activation, forces or hidden anatomy.
6. Complete physical Android/device verification separately.

Do not merge PR #10 or `main`, change scoring mathematics, or alter athlete history as part of this asset handoff. Concurrent branch changes were preserved; no force push was used.
