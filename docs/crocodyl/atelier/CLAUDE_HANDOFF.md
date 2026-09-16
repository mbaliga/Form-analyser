# Cinematic rendering addendum for Claude

Continue on `feat/immersive-visuals` / PR #11. Read `ASSET_HANDOFF.md` here and `../visuals/ARROW_FIRST_ART_DIRECTION.md` first: the newer owner-selected **arrow-first** scope governs the next accuracy/approval pass. Do not expand the speculative bow collection or interpret generic study meshes as approved equipment.

## Additional authoring deliverables

- `tools/atelier/render_posters.py` imports the actual standard GLBs into an authoring-only VTK/EGL/PBR studio and renders matching images. Its lighting is created locally; no external HDRI or replacement equipment picture is used.
- `app-android/src/main/assets/atelier/cinematic/` contains 13 JPEG study renders plus source-model hashes, camera settings, dimensions and output checksums. These are review assets, not automatic instructional content or owner-approved geometry. Regenerate them after any mesh revision.
- `tools/atelier/make_preview.py` creates `dist/Crocodyl-Equipment-Studio.html`, a single-file offline review of the actual viewer and embedded GLBs.
- `tools/atelier/render_qa.py` records real dark/light desktop/mobile rendering and interaction checks. Reduced-motion changes are polled because media-query callbacks are asynchronous. Its diagnostic captures are separate from runtime posters.

The Home entry and compact `posters/` remain separate from the high-resolution study set. Select application placement after visual/reference approval. No live 3D rendering is added to feed cards.

## Commands

```sh
python3 tools/atelier/build_assets.py
python3 tools/atelier/test_assets.py
python3 tools/atelier/make_preview.py
# For authoring-only beauty renders:
python3 -m pip install vtk==9.6.2 numpy==2.2.6 Pillow==11.3.0
VTK_DEFAULT_OPENGL_WINDOW=vtkEGLRenderWindow python3 tools/atelier/render_posters.py
```

Headless Linux rendering needs Mesa/EGL; `atelier-artpack.yml` installs it. VTK, Python, NumPy and Pillow are not APK dependencies. Raster output can vary with the graphics driver; use the manifest to bind each image to its actual source model.

Use actual Actions results and the latest `browser-report.json` as verification, not historical commit claims. Physical Android/WebView operation, reference accuracy and owner approval are separate gates. No rigged human, 3D muscle atlas, validated technique animation, or medical analysis is delivered by this equipment-rendering pack. The earlier generated anatomy/UI composite is not a production or instructional asset.

Preserve current geometry corrections, semantic part IDs, source/provenance documents, and all athlete/scoring/exchange data. Do not merge PR #10 as part of this art handoff.
