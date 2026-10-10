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

Sport cards and the Recurve/Fencing Home cards use real photographer-created Unsplash photographs,
not generated artwork. They are decorative UI imagery only, not source material for training
instruction. Attribution, source-page links, license records, processing notes, dimensions and
SHA-256 hashes are kept in
`app-android/src/main/assets/atelier/cinematic/sports-manifest.json`.

- Muay Thai: Anastase Maragos, [“Man practicing muay thai kickboxing with a heavy bag”](https://unsplash.com/photos/man-practicing-muay-thai-kickboxing-with-a-heavy-bag-78Q5VKfedoc).
- Bouldering: Kambria Trout, [“Man climbing an indoor rock climbing wall”](https://unsplash.com/photos/man-climbing-an-indoor-rock-climbing-wall-m2s5M6bMxs0).
- Recurve: Phil Hearing, [“A woman is aiming a bow at a target”](https://unsplash.com/photos/a-woman-is-aiming-a-bow-at-a-target-9xcQ-KpxV8E).
- Fencing: Nathanaël Desmeules, [“Two men in fencing gear practicing their moves”](https://unsplash.com/photos/two-men-in-fencing-gear-practicing-their-moves-fY-gU30ciIo).
- Each photo page marks the image as free to use under the [Unsplash License](https://unsplash.com/license). Credit is recorded even though attribution is not required. No image implies that the pictured person, venue or brand endorses Crocodyl. The Recurve/Fencing component-specific 3D renders remain separately available as visualization-only equipment studies.

## Not claimed by this slice

These journals do not assess form from camera footage, prescribe technique, give sparring or
climbing safety instructions, infer medical readiness, or provide validated cross-sport fatigue
comparisons. The first screen records one bouldering problem per saved journal entry. It does not
yet provide a multi-problem session editor, an interval timer, route import, gym/coach directory
integration, or multi-sport coach reports.

The V9 Room schema snapshot and Android/Room/Compose verification must be generated and replayed
with the Android toolchain before these additions can be called release-verified. The last known
host check found no Java runtime, Android SDK or ADB; see [production status](PRODUCTION_STATUS.md).
