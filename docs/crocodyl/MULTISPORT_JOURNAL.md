# Muay Thai and bouldering journals

Reviewed: 2026-10-09. This feature work extends the existing Recurve app; it does not change the
Recurve production-release boundary.

## First implemented slice

The Home feed now has two cinematic sport cards with original, text-free artwork. Each opens a
dedicated local training journal.

- **Muay Thai:** record training focus (shadowboxing, heavy bag, pads, technique or sparring), round
  count, work/rest duration and athlete-reported effort. A session record keeps the per-round data.
- **Bouldering:** record a grade label alongside its selected V, Font, UK or custom system, wall
  style, attempts, outcome (top, zone, fall or stopped attempt), session duration and athlete-
  reported effort. Grade systems remain distinct; Crocodyl does not translate grades between them.
- Both journals show recent entries and store records in the local Room database. The additive
  database migration is V8 → V9. `sport_session` is registered as shareable under the existing
  consent-filtered `.croc` export, and retracted records are excluded from export.
- Both sport modules implement the shared `SportModule` boundary. Extracted signals contain only
  manually recorded durations, outcomes, attempts and effort. The module rubrics define future
  engine associations (athlete-rated Muay Thai self-rating, and bouldering send/no-send); the
  current journals do not display a performance score or infer one from captured video.

## Visual assets

The generated images are decorative UI artwork, not source material for training instruction:

- `app-android/src/main/assets/atelier/cinematic/muay-thai-training.jpg` — 1080 × 608, 121,996
  bytes; warm, dusk-lit training gym with left-side text space.
- `app-android/src/main/assets/atelier/cinematic/bouldering-session.jpg` — 1080 × 608, 157,371
  bytes; cool, sculptural indoor wall with left-side text space.
- `app-android/src/main/assets/atelier/cinematic/sports-manifest.json` records dimensions, hashes,
  and the non-instructional usage boundary.

Image generation used the built-in image-generation tool. The Muay Thai prompt requested a wide,
editorial photograph-style gym scene with one athlete working a heavy bag, right-weighted framing,
warm amber light, no text/logos and no injury or opponent. The bouldering prompt requested one
adult climber on an indoor overhang, right-weighted framing, cool blue-violet light, no ropes,
falling, injury, text or logos.

## Not claimed by this slice

These journals do not assess form from camera footage, prescribe technique, give sparring or
climbing safety instructions, infer medical readiness, or provide validated cross-sport fatigue
comparisons. The first screen records one bouldering problem per saved journal entry. It does not
yet provide a multi-problem session editor, an interval timer, route import, gym/coach directory
integration, or multi-sport coach reports.

The V9 Room schema snapshot and Android/Room/Compose verification must be generated and replayed
with the Android toolchain before these additions can be called release-verified. The last known
host check found no Java runtime, Android SDK or ADB; see [production status](PRODUCTION_STATUS.md).
