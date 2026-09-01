# images/

Sizes and check commands: `Personal-Tracker/store/ASSET_SPECS.md`.

## Needed, none present yet

- `icon.png` — 512x512, no alpha.
- `featureGraphic.png` — 1024x500, no alpha.
- `phoneScreenshots/` — 2 to 8, 1080x1920, no alpha.

## Shoot these, on a range

1. Live capture with the pose overlay on a real draw.
2. A per-shot deviation score in the review screen.
3. The session fatigue trajectory.
4. The form-versus-arrow-score correlation chart. This is the differentiator, and
   it is the one competitors do not have.

A mocked-up shot of this app reads as fake immediately. Capture it at an actual
session, with the phone on a tripod where the README says to put it (lateral or
sagittal to the archer).

**Check every frame before uploading:** no identifiable person other than you, no
club or venue signage you do not have permission to show.

```sh
adb exec-out screencap -p > shot.png
magick shot.png -background black -alpha remove -alpha off phoneScreenshots/01.png
```
