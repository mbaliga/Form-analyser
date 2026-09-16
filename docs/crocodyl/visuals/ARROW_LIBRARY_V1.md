# Crocodyl arrow library v1 — generic target-arrow taxonomy

This pass models **systems, not brands**. It gives Crocodyl reusable 3D building blocks that later exact-arrow passes can instantiate from real references. The meshes are educational studies, not fitting advice, spine/length prescriptions, or dimensional copies of a manufacturer's product.

## Taxonomy represented

### Shaft geometry

Three external profile families are modelled in `shaft_profiles.glb`:

- **Parallel** — constant outside diameter through the working length.
- **Barrelled** — largest outside diameter around the middle, reducing toward both ends.
- **Single-taper** — progressively smaller outside diameter toward one end.

Easton's shaft-design literature describes parallel, tapered and barrelled as major target-shaft technologies; current target catalogues provide real examples of barrelled and single-taper aluminium-carbon shafts. The model differences are mildly exaggerated so they remain visible on a phone and must not be treated as product dimensions.

### Shaft construction/material

`shaft_constructions.glb` separates construction from external profile:

- **Aluminium alloy tube**.
- **All-carbon composite tube**.
- **Aluminium-core / carbon-shell hybrid**.

The hybrid study exposes a shorter carbon outer shell over an aluminium core so the construction reads visually. Layer thickness is intentionally schematic. Easton documents its A/C family as a precision aluminium core combined with externally bonded carbon fibre.

### Point profile, attachment and material

These are separate axes; they should not be flattened into one "point type" field. `points.glb` contains five generic studies:

- **One-piece bullet point** — a rounded point *profile/family*. The generic mesh is material-neutral because bullet does not imply one universal alloy.
- **Zinc glue-in target point**.
- **Stainless-steel break-off target point** — break-off describes removable weight sections.
- **Tungsten-alloy target point** — material/family study, not an X10 dimensional replica.
- **Screw-in field/target point** — demonstrates a threaded attachment system rather than glue-in construction.

Future exact-arrow passes should represent at least `nose/profile`, `attachment`, `weight system`, `material`, and actual component dimensions independently.

### Nock and nock-attachment systems

`nock_systems.glb` contains four common interface families:

- **Direct-fit / insert nock** — polymer stem fits directly in the shaft bore.
- **Pin nock** — a metal pin adapter fits the shaft and the polymer nock presses onto the external pin.
- **Overnock / out-nock** — polymer nock fits around the outside of the shaft rear.
- **Bushing + insert nock** — a metal rear bushing provides a socket for a separate insert nock.

Groove size, throat fit, indexing, polymer, shaft compatibility and exact interface dimensions remain setup-specific. Compound-specific variants are deferred.

## Square target-face correction

`target_face_square.glb` fixes the earlier conceptual error: the **paper/substrate is square** while the scoring zones remain concentric circles. The generic 122 cm study uses the current World Archery ten-zone geometry (6.1 cm radial scoring-zone width and 6.1 cm inner-X diameter). It is a face study, not a licensed manufacturer print or a claim about paper stock or print tolerances. This does not alter Crocodyl's scoring mathematics.

## Files

Generated Android assets live under `app-android/src/main/assets/atelier/arrow-library/`:

- `shaft_profiles.glb`
- `shaft_constructions.glb`
- `points.glb`
- `nock_systems.glb`
- `target_face_square.glb`
- `arrow_library_manifest.json`

Source tooling:

- `tools/atelier/arrow_library.py` — deterministic generator using the existing Crocodyl GLB primitives.
- `tools/atelier/test_arrow_library.py` — structure, semantic-node, source-provenance and square-target tests.

The manifest carries the exact source URLs and records which taxonomy claim each source supports.

## Principal references

- Easton, **Arrow Shaft Design and Performance**: https://eastonarchery.com/2018/12/arrow-shaft-design-and-performance/
- Easton, **2026 Target Product Guide**: https://eastonarchery.com/wp-content/uploads/2026/03/Easton-2026.pdf
- Easton, **A/C Arrow Construction**: https://eastonarchery.com/2022/05/a-c-arrow-construction-how-a-c-arrows-are-made/
- Easton, **Nocks — The Vital Connection**: https://eastonarchery.com/2014/01/nocks-the-vital-connection/
- Easton, **Components Guide**: https://eastonarchery.com/components-guide/
- World Archery, **Book 2 (2026)**: https://extranet.worldarchery.sport/documents/index.php/Rules/Rule_Book_versions/2026-01-27/EN-Book_2_-_2026-01-27_Version.pdf

## Validation

The generator is dependency-free beyond the existing Crocodyl atelier module. Automated tests verify valid GLB 2.0 structure, finite geometry, valid indices, stable semantic component IDs, source-reference metadata, mobile asset-size ceilings and the 1.22 m × 1.22 m square target substrate.

## Claude handoff

Integrate these as an educational taxonomy/library surface first. Do **not** turn any generic mesh into the athlete's recorded equipment rig automatically. A later exact-arrow chat should pick one shaft profile + construction + point system/material + nock system and then replace illustrative geometry with measurements from the real arrow or manufacturer documentation.

Do not restart the complete-bow model during this pass. Keep fletching, wraps, vane geometry, arrow length/spine/FOC, point mass, nock groove and exact compatibility as independent future axes rather than silently choosing them here.
