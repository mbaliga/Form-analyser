# Crocodyl visual direction: arrow first

Owner direction recorded 2026-09-17 (Asia/Kolkata).

This document supersedes the earlier broad equipment/anatomy concept for the NEXT art-production pass. It records scope and acceptance requirements, not completed assets. Existing generator output and the promotional composite are studies, not owner-approved equipment replicas or instructional references. This documentation commit does not modify the running app, the target GLB, or the arrow GLB.

## 1. Target correction

For the target sheet requested by the owner, model a SQUARE paper sheet carrying the circular concentric scoring rings. Keep paper, printed scoring geometry and supporting butt/stand conceptually separate. A circular scoring region does not make the paper a circular disc.

The current `tools/atelier/build_assets.py::target()` builds the circular face without the requested square paper outline. This remains an explicit correction to make before that asset is next presented or promoted. Do not describe the existing GLB as corrected. Preserve the scoring radii and colours when correcting the paper; paper margins are not scoring rings. Match the selected real sheet's dimensions and mounting rather than inventing an official margin specification. No changes to the application's scoring mathematics are implied.

Target production beyond this correction is deferred; it is not a second active modelling project.

## 2. One active equipment asset: one specific arrow

Stop widening the equipment collection. The arrow is the first reference-accuracy project. Do not keep refining the speculative complete recurve assembly in parallel.

The existing `arrow.glb` is a GENERIC UNVALIDATED STUDY. It is not a replica of the user's arrow, an approved brand configuration, or a completed anatomy lesson. No actual arrow make/model, shaft specification, point, nock system, fletching or dimensions were supplied in this direction-setting exchange. Do not fill that gap with a plausible mixture of products.

### Reference intake

Use one coherent, owner-selected arrow configuration. Request a full-length side-on photograph and close-ups of the point, nock and fletching, with a ruler or a known measurement where practical. Record any available manufacturer/model information for each actual component. An end-on nock/fletching view is useful for rotational alignment. Do not ask the owner to remove bonded components; hidden construction must remain unverified unless separately documented.

Record each dimension with its source, unit and status: measured, manufacturer-published, visually estimated, or unknown. A pretty render does not convert an estimate into a measurement.

### Geometry approval before appearance

1. Establish full-arrow proportions from the reference, including the shaft's visible diameter profile and the actual component transitions.
2. Match the point profile and connection to the shaft; do not assume an insert system.
3. Match the actual nock throat, ears, base and interface; do not mix direct-fit and pin-system geometry.
4. Match the actual fletching type, count, outline, curvature, placement and orientation relative to the nock. Include wrap/tape only when present in the reference.
5. Supply neutral-material side, top, end and close-up views, with side-by-side reference comparisons. Clearly mark anything not recoverable from the reference.
6. Obtain owner approval of this geometry before cinematic lighting, branding, a cutaway, educational labels or animation is treated as final.

### Cinematic treatment after approval

Use the approved geometry for the interactive GLB AND the equipment hero/macro images. Do not generate a separate attractive but mechanically inconsistent arrow image. Keep the actual material identities and proportions. Camera framing, lighting and macro views should reveal small details; do not thicken the shaft or enlarge components merely for drama.

Prepare light- and dark-background treatments; named selectable component meshes; reference-supported exploded views; consistent surface detail; and reusable source geometry. Include invisible/internal interfaces only when their construction is documented. No invented flex, spine, vibration, aerodynamic or performance demonstration.

Final delivery is expected to include editable source, standard GLB, images rendered from that same approved model, asset manifest, reference/provenance ledger, an uncertainty list and visual QA evidence. The technical checks include valid normals/winding, no accidental intersections, faithful component alignment, portable embedded materials, sensible mobile budgets, offline rendering and lifecycle/reduced-motion behaviour. These checks do not establish equipment accuracy by themselves.

## 3. Other equipment and disciplines are deferred

The owner will open separate chats for quiver, arrow rest, extension rod, Y-bracket, sight bar, long rod, stabilisers, string, sight, finger tab (including leather and brand variants), riser, limbs and limb dampeners, and other equipment as needed. Preserve those distinctions instead of treating them as interchangeable decorative bow parts.

Compound, barebow and other discipline expansion come later. Do not add those variants, new equipment models, or a whole collection to this arrow pass without a new request.

## 4. Instructional art: real reference movement first

The owner proposes supplying real-archer photos or video. Start with a specific selected example and a specific teaching point, not a generated pose. Use owner-provided or otherwise cleared-for-reuse reference material and retain its source/permission record privately as appropriate; do not publish identifying source footage in this public repository by default.

Preserve the observable silhouette, joint relationships, contact points, handedness, equipment relationship and, for video, the actual temporal sequence. De-identify faces, tattoos, hair, clothing marks and distracting backgrounds without changing the movement being taught. A face swap or simplified silhouette must not silently alter hand placement, anchor, draw elbow, bow/string contact or posture.

Anonymisation and stylisation do NOT establish that a movement is ideal. The owner/coach must first approve the source sequence for its intended lesson. Do not infer unseen 3D anatomy, muscle activation, force, joint-angle targets or an injury-risk conclusion from a generated overlay. The earlier promotional composite's anatomy, numeric angle claims and improvement promises are not evidence and are not approved teaching content.

For motion, use video or an explicitly supplied sequence rather than inventing the intervening movement from one still. Maintain a side-by-side reference/stylised review throughout. Consistent artwork must follow approved motion, not replace it.

## 5. Handoff to Claude

Implement product integration around approved assets; do not interpret the presence of the existing GLBs or the concept image as approval. Avoid expanding scope, silently regenerating equipment geometry or promoting decorative anatomy into analysis. Preserve the existing athlete data, scoring, exchange and Baseline work. Do not merge competing PRs as part of this art-direction change.

Current next dependency: the owner-selected arrow reference/configuration. Until that is supplied, there is no basis to claim a reference-accurate replacement arrow is complete.
