# Crocodyl 3D and cinematic asset handoff

Date: 2026-09-17. Working branch: `feat/immersive-visuals` (PR #11).

## Scope and approval

This pass establishes portable original 3D meshes, an offline equipment viewer, reproducible export/render tooling and an environmental image. It does **not** establish faithful replication of a specific commercial product, anatomical accuracy, measured biomechanics or validated instructional motion.

Respect the newer owner direction in [`../visuals/ARROW_FIRST_ART_DIRECTION.md`](../visuals/ARROW_FIRST_ART_DIRECTION.md): **arrow first**. The existing recurve, arrow and target are unapproved generic studies. Preserve them without expanding the bow collection. The requested square paper target sheet remains a separate correction: circular scoring rings do not imply a circular sheet. Follow the latest owner-approved arrow configuration/variant brief before further geometry work.

## Files to use

| Deliverable | Location |
| --- | --- |
| Editable procedural source | `tools/atelier/build_assets.py` |
| Portable GLB models and part/checksum manifest | `app-android/src/main/assets/atelier/models/` |
| Offline WebGL2 viewer | `app-android/src/main/assets/atelier/index.html`, `atelier.js`, `atelier.css` |
| Non-exported Android host | `app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/EquipmentAtelierActivity.kt` |
| Static Home entry | `app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/components/EquipmentAtelierCard.kt` |
| Mesh/export regression suite | `tools/atelier/test_assets.py` |
| Actual browser rendering and interaction harness | `tools/atelier/render_qa.py` and `.github/workflows/atelier-quality.yml` |
| Same-mesh dark/light posters | `app-android/src/main/assets/atelier/posters/` (render-harness output) |
| Generated environmental art and provenance | `app-android/src/main/assets/atelier/art/` |

The three GLBs total approximately 1.56 MB before packaging. The recurve contains 8 named parts and 16,504 triangles; the arrow 5 parts and 3,288 triangles; the target/stand 4 parts and 22,928 triangles. Use the current generated manifest for exact bytes/checksums after revisions.

The files are standard glTF 2.0 binary, metres, +Y up, with embedded textures and material properties. Each part has a stable node name plus `extras` metadata: `id`, `title`, `description`, `hotspot`, `explode`. Preserve those IDs when replacing study geometry with approved assets. Exploded positions are presentation aids, not validated assembly instructions.

## What is already wired

The studio supports orbit, zoom, named-part focus, separation/reassembly, dark/light presentation and an optional turntable. Rendering stops when idle and responds to host/page visibility. Reduced-motion behaviour has a browser check. The Android entry uses a static poster until opened, then an isolated local-assets-only WebView: no CDN, external model downloads, athlete-data bridge, file access or content access.

The renderer intentionally supports this package's static GLB subset. It is not an arbitrary animation/skinning importer. There is no rigged human model, 3D anatomical body atlas or validated shot animation in this delivery. The existing body atlas remains 2D.

## Image usage

`art/range-dawn.webp` is an original **AI-generated environmental illustration**, 640 x 360, 17,086 bytes. It is bundled for optional integration, not automatically inserted into athlete records or lesson content. Its manifest records a SHA-256 checksum, generation disclosure, crop guidance and alt-text suggestions. Use the quiet left area for copy with a theme-appropriate scrim.

It is not a photograph of a real range or a specification for target sheets, distances, equipment or safety layout. Do not derive technical lessons from it. The earlier promotional anatomy/UI composite is not an approved production asset and has not been committed as teaching material.

The generated dark/light studio posters come from the actual meshes. Regenerate them whenever the corresponding geometry changes; do not replace them with unrelated generated equipment pictures.

## Reproduction and evidence

```sh
python3 tools/atelier/build_assets.py
python3 tools/atelier/test_assets.py
# Install authoring-only dependencies specified in atelier-quality.yml, then:
python3 tools/atelier/render_qa.py
# Android compilation:
./gradlew :app-android:assembleDebug -PwithAndroid --no-daemon
```

Eight mesh/export tests passed locally after the cap-winding correction. The new regression asserts outward-facing tube sides and both caps; the original implementation incorrectly reversed the already-correct cap fans. The fix matters for standard back-face-culling renderers even though a two-sided shader concealed it.

Use each Actions run and its `browser-report.json` as the browser-verification record. The first complete real-render pass produced desktop/mobile dark/light screenshots and passed 57 of 58 checks; its remaining assertion was the reduced-motion update timing. Follow later reports rather than treating that historic result, or an old commit message, as current status.

Desktop Chromium/GLB inspection does not establish Android WebView correctness. Physical-device rendering, lifecycle/rotation, gestures, memory, thermal, battery, accessibility and sunlight checks remain open. SDK setup failures are not evidence of either successful or failed Kotlin compilation.

## Next work for Claude and the art pass

1. Keep the latest arrow-first/reference brief authoritative. Separate representative shaft/point/material/nock variants from a chosen exact equipment configuration; track published, measured, estimated and unknown dimensions.
2. Replace only approved geometry, preserve part IDs, test winding/intersections and regenerate posters from those same meshes. Do not mark a model reference-accurate merely because it renders.
3. For how-to illustrations and motion, use an approved real-archer reference and retain actual contact relationships. Do not invent ideal angles, muscle activation, forces or hidden anatomy.
4. Complete Android/device verification and decide where the atmospheric art belongs. Keep Coach, wearable and general application development outside the asset patch.

Do not merge PR #10 or `main`, change scoring mathematics, or alter athlete history as part of this handoff. Concurrent branch changes were preserved; no force push was used.
