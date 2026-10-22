# Crocodyl 3D and cinematic asset handoff

Date: 2026-09-20. Integrated branch: `codex/production-hardening` (PR #11 context retained).

For the current Codex execution sequence and branch-state index, read [`CODEX_HANDOFF.md`](CODEX_HANDOFF.md) first.

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
| Licensed photo attribution/provenance | `app-android/src/main/assets/atelier/cinematic/sports-manifest.json` |

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
- One-piece hardened 416 stainless glue-in target point
- Adjustable glue-in bullet body
- Removable 10-, 20- and 50-grain adjustable-bullet module studies

Point **shape**, **material**, **attachment method**, and **weight-adjustment construction** remain separate concepts. Do not collapse “bullet”, “tungsten”, “break-off”, and “glue-in” into one mutually exclusive field.

Lancaster documents the 416 stainless and adjustable-bullet families. The current library now represents them as distinct generic topology with explicit source-backed fitment and weight metadata; it does not claim a commercial replica, universal compatibility, module dimensions or tuning outcome.

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

The equipment studio now exposes eight selectable families:

1. Recurve study
2. Complete generic arrow study
3. Target study
4. Shaft profiles
5. Shaft constructions
6. Point families
7. Nock/rear-interface families
8. Fletching families

All five new arrow taxonomy GLBs load through the same local WebGL2 renderer as the original studies. Orbit, zoom, component focus, separation/reassembly, light/dark presentation, reduced motion and lifecycle suspension are retained. Mobile navigation is a contained horizontally scrollable strip rather than widening the document.

The Android entry still uses a static poster until the user deliberately opens the studio; the renderer does not run continuously in the Home feed. The WebView remains local-assets-only with no CDN, retailer image requests, athlete-data bridge, file access or content access.

## Verification

The Lancaster taxonomy build validates deterministic GLB output, expected family/part IDs, self-contained assets, mobile-size budgets and source policy. The committed real-Chromium evidence covers **shaft profiles, shaft constructions, points and nock systems**; the browser harness has since been extended to include the fifth **fletching** family and its full component set. When the local Node/Chromium toolchain is available, rerun the harness through the production studio for every family and verify:

- GLB uploaded and drawn;
- no WebGL error;
- component controls created;
- component focus works;
- no mobile horizontal overflow.

The current browser pass also records zero JavaScript exceptions and zero external requests. It explicitly does **not** verify physical Android-device behaviour, manufacturer-replica accuracy beyond published dimensions, equipment compatibility or fitting advice.

The original model pipeline separately retains eight geometry/export tests including outward-facing tube side/cap winding. The first broad viewer pass and later corrected passes should be judged by their current generated reports rather than historical commit messages.

## Image usage

Home and sport-card photography is bundled from photographer-created Unsplash images, with a source
page, license, author, checksum and use restrictions recorded in `cinematic/sports-manifest.json`.
The previous AI-generated `art/range-dawn.webp` was removed; generated imagery is not used for card
photography. Component-specific 3D renders continue to come from the approved original Crocodyl
geometry and remain visualization-only, not instructional or manufacturing evidence. Lancaster
catalog/product images are remote visual references only and must not be bundled into the app.

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
3. The generic point and fletching taxonomies are present. Use them only to describe or configure a real arrow after recording explicit product-family compatibility.
4. For instructional movement, use an approved real-archer photo/video sequence and preserve observable contact relationships. Do not invent ideal joint angles, muscle activation, forces or hidden anatomy.
5. Complete physical Android/device verification separately.

Do not merge PR #10 or `main`, change scoring mathematics, or alter athlete history as part of this asset handoff. Concurrent branch changes were preserved; no force push was used.
