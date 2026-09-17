# Cinematic equipment pack - verified delivery, 17 September 2026

This record covers the equipment-study pack, not a release approval for Crocodyl or the subsequent reference-accurate arrow work. Read `ASSET_HANDOFF.md`, `CLAUDE_HANDOFF.md`, and `../visuals/ARROW_FIRST_ART_DIRECTION.md` together. The owner-selected scope and approval process remain authoritative.

## Published files

The corrected GLBs and 13 model-matched cinematic JPEGs were committed in **3202e3ec15782a42b4f00b065d1e48d689e46875** (`assets: add cinematic study renders and matching corrected GLBs`). Publication succeeded in Actions run **35153625288**, using the authoring pipeline from **3f02169562dbef97425a918681d4af8a3fbbbdd1**. No force push was used.

Locations:
- `app-android/src/main/assets/atelier/models/`: original generic recurve, arrow and target studies.
- `app-android/src/main/assets/atelier/cinematic/`: 13 JPEGs and source-linked camera/checksum manifest.
- `tools/atelier/`: editable generator, tests, studio renderers and offline HTML packager.
- `dist/Crocodyl-Equipment-Studio.html`: produced review artifact, not a second independently implemented viewer.

Three study GLBs total **1,555,652 bytes**. The 13 cinematic images total **1,065,744 bytes** in the verified EGL render. All 13 output-image hashes and all corresponding source-model hashes were checked against their manifests in the downloaded delivery. Their imagery represents those actual meshes, not replacement pictures.

## Checks actually executed

| Check | Result | Evidence |
| --- | --- | --- |
| Geometry/export contracts | **8 tests passed** | Ran locally and in the art pipeline, including the new outward tube-cap winding regression. |
| Real Chromium/WebGL2 checks | **59/59 passed; zero JavaScript errors** | Actions **35153433922**, source **6cb2b5da81463e646de7b9dca74ce2d679698147**; `browser-report.json` in the review artifact. |
| High-resolution art generation | **13/13 renders produced** | Actions **35153625288**, EGL/Mesa + VTK; source/image hashes in `cinematic/manifest.json`. |
| Offline HTML packaging | **Produced successfully** | Same art run; embeds the runtime viewer and three GLBs, without external model dependencies. |
| Android integration assembly | **Succeeded** | Actions **35152198062**, source **e02873dd60933187fcab333c44afadfc52211b3c**. This is a compile/package result, not device testing. |

Browser coverage includes all three models in both themes; actual desktop/mobile screenshots; part selection; separation/reassembly; idle rendering; pause/resume; reduced-motion disable and restoration; explicit turntable start/stop; no external requests; nonblank model captures; and no mobile horizontal overflow. Earlier browser-test failures were harness issues (strict-CSP eval and an asynchronous media-query assertion), not silently excluded test cases. The revised harness retains strict CSP and passes both motion states.

Tube-cap orientation was corrected in b6c5f6e and protected by the regression in 0508d69. The final rendered-model hashes include that correction. The initial implementation commit's claim of prior local Chromium verification was premature; the actual browser evidence is the Actions run above. Local VTK rendering is separate evidence and is not represented as WebGL/device validation.

## Not established by these checks

- Approval or reference accuracy of any generic equipment study.
- Full Crocodyl cinematic/art completion.
- A rigged human, 3D anatomy or medically/biomechanically validated instruction.
- Correctness of every wider-repository workflow or any unmerged competing feature branch.
- Physical-device GPU compatibility, TalkBack, thermal/battery behavior, rotation and range use.

Keep the newer arrow-first art direction intact. Treat the atmospheric generated range image as atmosphere only, and do not use the rejected generated anatomy/UI composite as instruction. Claude can continue integration and repository work without rebuilding these source assets from scratch, but should not promote unapproved generic geometry as a verified equipment replica.
