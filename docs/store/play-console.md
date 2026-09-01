# Crocodyl — Play Console answer sheet

> Only the **deltas** from `Personal-Tracker/store/HOUSE_DEFAULTS.md`.

| | |
|---|---|
| applicationId | `xyz.mdhv.formanalyser` |
| Version at time of writing | `0.1.0` (versionCode `1`) |
| Category | **Health & Fitness** |
| Tags | archery, form analysis, pose estimation, coaching, sports, offline |
| Contact email | `crocodyl@asystemofcells.com` |
| Website | `https://asystemofcells.com/crocodyl` |
| Privacy policy | `https://asystemofcells.com/crocodyl/privacy` |

> ⚠️ **The rebrand has to happen before the first production release, not after.**
> `docs/naming.md` defers the rename (`applicationId` → `xyz.mdhv.crocodyl`, repo →
> `crocodyl`) "until just before the Play Store listing", for the correct reason:
> the applicationId is permanent once published. **This is that moment.** Decide it
> now. This sheet uses `xyz.mdhv.formanalyser` because that is what the build
> currently produces; change both if you do the rename.

> Also note `main` is a stub. The real app lives on a release branch
> (`CONSTELLATION.md`). Build the listing from the branch that actually has the app.

## Deltas from the house defaults

### Health apps declaration — required for this app
Play asks this because of the Health & Fitness category. All four answers are no,
and each is defensible:

| Question | Answer | Why |
|---|---|---|
| Is your app a medical device? | **No** | It analyses sports form. It makes no medical claim. |
| Does it provide diagnosis, treatment or prevention of a disease? | **No** | |
| Is it used for clinical decision-making? | **No** | |
| Does it conduct health research on human subjects? | **No** | |

Keep the listing copy free of any injury, rehabilitation or health claim. The copy
here deliberately says "form", "stability" and "fatigue" as *sports* measures and
never as clinical ones. Adding a "prevent injury" line would change this section
and invite a much harder review.

### Data safety
**No data collected. No data shared.**

| Question | Answer |
|---|---|
| Collect or share any user data? | **No** |
| Encrypted in transit? | Yes (there is no user-data transit) |
| Deletion? | Users can delete data in the app |

The point a reviewer will look for, and the answer:
- **Camera frames are processed on device by MediaPipe BlazePose and are never
  transmitted.** The app records no video to any server.
- Sessions, shots, scores and the baseline live in a local Room database.
- The pose model (`pose_landmarker_lite.task`) is **bundled at build time** by the
  `downloadPoseModel` Gradle task, so the installed app makes no model download and
  works fully offline. That is worth stating in the listing, and it is already there.

### Permissions
| Permission | Why | Play form? |
|---|---|---|
| `CAMERA` | The whole product: pose capture. | No form, but the in-app rationale must be shown before the first request. |

That is the only permission. **Notably there is no `INTERNET` permission**, which
is the strongest possible support for the data-safety answer. If a future feature
adds it, revisit this whole section.

### Content rating
- Category `Utility, Productivity, Communication, or Other`.
- **"Does the app use the camera to capture images of the user?"** If the
  questionnaire raises it: yes, on device, never transmitted or shared.
- Everything else No. Expected **Everyone**.

### Monetisation
- **No in-app purchases in this app.** It is free and open source, deliberately.
- The paid **Baseline** add-on (the EEG biosignal channel, from the separate
  `baseline` repo) is a **different product**. If it is ever sold *through* this
  app, this section, the IAP answer and the content rating's digital-purchases
  question all change. Today, keep them all as "no".

## F-Droid
- ⛔ **Blocked, twice over.**
  1. No `LICENSE` file at the repo root (`CONSTELLATION.md` §2, D-I). The README
     says "free and open source" and the listing copy repeats it, so this is a
     factual gap that should be closed regardless of F-Droid.
  2. The app currently **vendors the proprietary Baseline engine as source**. Until
     the engine is a genuinely optional, separately-licensed dependency, the app is
     not free software and cannot be listed.

## Pre-submit checklist

- [ ] Decide the rebrand: `applicationId` and repo name, now, permanently.
- [ ] Add a `LICENSE` file matching the "open source" claim in the listing.
- [ ] Build from the release branch, not the `main` stub.
- [ ] Icon 512x512 and feature graphic 1024x500.
- [ ] Screenshots: live capture with the pose overlay, a per-shot deviation score,
      the fatigue trajectory, the form-versus-score chart. **Shoot on a range with
      a real bow.** A studio mock of this app is instantly unconvincing.
- [ ] Nobody identifiable other than you appears in a screenshot.
