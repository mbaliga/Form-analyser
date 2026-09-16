# Crocodyl equipment atelier - visual handoff

## Scope and branch

Continue on `feat/immersive-visuals` / PR #11. This is the equipment-visual pack requested on 17 September 2026. It is not the complete Crocodyl roadmap. Do not merge PR #10 blindly; its exchange, scoring, DI and migration work overlaps this branch independently.

The pack contains original inspectable equipment, real runtime 3D, and model-matched still imagery. It does **not** contain a rigged human, a validated musculoskeletal model, biomechanical teaching animations, additional sports or an anatomy diagnosis engine. The AI-generated concept board shown in the conversation is not a functional screenshot and must not be used as anatomical or analytical evidence.

## Actual assets

Runtime root: `app-android/src/main/assets/atelier/`.

| Asset | Geometry | Structure |
| --- | ---: | --- |
| `models/recurve.glb` | 16,504 triangles; 593,164 bytes | 8 semantic parts: riser, grip, limbs, string, sight, stabilisers, rest/button/clicker, hardware |
| `models/arrow.glb` | 3,288 triangles; 127,428 bytes | 5 parts: shaft, point, nock, vanes, wrap |
| `models/target.glb` | 22,928 triangles; 835,076 bytes | 4 parts: face, butt, stand, pins |

These are standard glTF 2.0 binary files, metres, +Y up, with embedded original PNG colour textures and PBR material factors. There are no network dependencies or brand marks. Open the GLBs in Blender or another glTF-aware editor. Nodes carry stable IDs plus `extras.title`, `description`, `hotspot` and `explode`. Preserve those IDs when replacing geometry. These are illustrative visual assets, not manufacturing CAD, calibrated measurements or equipment prescriptions.

`models/manifest.json` is the generated size/checksum/part inventory. Bake source is `tools/atelier/build_assets.py`; it uses only Python's standard library. Seven CPU-only contract tests cover headers/alignment, embedded dependencies, finite geometry, normals, indices, non-degenerate triangles, stable parts, materials, budgets and reproducibility. These tests are not a sports-equipment expert sign-off.

### Imagery

`posters/` contains thumbnails captured from the actual WebGL viewer by the existing browser verifier. `cinematic/` is the separate high-resolution art set rendered from the **same GLBs**, so the stills do not promise shapes that the interactive model lacks. Its manifest records source hashes and camera settings.

The cinematic set has 13 JPEGs: dark/light recurve hero crops, dark/light full recurve, dark/light arrow, dark/light target, plus riser/grip/sight/vanes/nock close-ups. Hero crops intentionally leave space at the left for native text; never bake application labels or athlete metrics into these images. `recurve-full-*` are complete assemblies; `recurve-*` are close hero crops.

Art direction: satin bronze/anodised metal, carbon composite, walnut, restrained emerald details; forest-black and warm ivory studio themes. Avoid neon HUD ornament, plastic-looking body parts, invented accuracy percentages, or fabricated muscle activation maps.

`render_posters.py` is an authoring tool using VTK/EGL/PBR, not an Android dependency. It creates its lighting environment procedurally. Raster bytes may vary slightly with renderer/driver; source geometry is deterministic. No third-party meshes, HDRIs, font files or texture downloads are included. The repository owner still needs to decide the overall project licence; this pack does not change that decision.

## Runtime integration

`EquipmentAtelierCard` is in Home after the Train/Score actions. It opens the non-exported `EquipmentAtelierActivity`. The feed uses a static poster, not a continuously rendering canvas. The activity maps only an allowlisted `https://crocodyl.local/atelier/` origin to bundled assets, blocks network/file/content access and has no JavaScript/native bridge or athlete-data access.

The viewer supports model switching, drag/pinch/keyboard orbit and zoom, part focus, explanatory hotspots, separate/reassemble, explicit turntable, light/dark themes, reduced-motion handling, and idle/background render suspension. The custom WebGL2 loader intentionally supports this package's static glTF subset; do not silently treat it as a universal glTF loader. It does not yet handle skins, animation clips or arbitrary imported GLBs.

Keep all styling in native/HTML UI; do not put text inside mesh textures. Use `cinematic/recurve-dark.jpg` or `cinematic/recurve-light.jpg` for the Home teaser when adopting the high-resolution artwork; maintain `posters/` as the compact fallback. Equipment detail pages can reuse the close-ups without allocating a WebView.

## Rebuild and review

```sh
python3 tools/atelier/build_assets.py
python3 tools/atelier/test_assets.py
python3 tools/atelier/make_preview.py
python3 -m http.server 8000 --directory app-android/src/main/assets
# Browse /atelier/index.html, or open dist/Crocodyl-Equipment-Studio.html directly.
```

For art regeneration only:

```sh
python3 -m pip install vtk==9.6.2 numpy==2.2.6 Pillow==11.3.0
VTK_DEFAULT_OPENGL_WINDOW=vtkEGLRenderWindow python3 tools/atelier/render_posters.py
```

Do not replace the real viewer with the concept board or a still-image slideshow. The portable HTML embeds exactly the runtime geometry and code; it is a review artifact, not the Android host's security configuration.

## Validation boundaries and continuation

Geometry tests have passed both locally and in Actions. Android assembly succeeded after the SDK setup fix (run 35152198062 on e02873d). Browser QA produced real dark/light desktop/mobile renders and passed model loading, part selection, separation, idle, pause and network checks; the first extended run flagged timing in its reduced-motion assertion, which is being corrected separately. Physical Android operation is not verified by those checks.

Before beta: exercise actual WebView GPU compatibility, rotation/return state, low-memory recovery, TalkBack and enlarged text, outdoor contrast, smoothness/heat on the target phone, and app pause/resume. Check that Back always returns to the athlete workspace. Test reduced-motion on the real OS setting, not only Chromium emulation. The local authoring container blocks Chromium WebGL, so real browser graphics evidence comes from GitHub Actions; local stills were separately rendered from the exported GLBs with VTK.

Next integration work for Claude: keep these assets; reconcile the product branches carefully; reuse stills on equipment/detail entry points; do not put live 3D on every feed card; retain the explanatory text fallback; run physical-device QA. A future human/anatomy pack must be commissioned/created and independently reviewed rather than extrapolated from these equipment illustrations.
