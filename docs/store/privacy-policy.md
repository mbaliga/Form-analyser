# Privacy Policy — Crocodyl

> **This is the working copy, not the hosted one.** The URL Play Console points at
> is **https://asystemofcells.com/crocodyl/privacy**. Keep the two in sync by hand.

**Last updated: 29 August 2026**

Crocodyl is an archery form analyser for Android (package `xyz.mdhv.formanalyser`),
made by A System of Cells. This policy describes exactly what the app does with
data. It is short because the app does very little.

## The short version

Crocodyl has no accounts, no advertising, no analytics and no tracking. **The app
has no internet permission at all**, so nothing it sees can leave your device even
in principle.

## The camera

Crocodyl watches you through the camera to estimate your pose. Every frame is
processed **on your device**, in real time, by a pose model bundled inside the app.

- **No video is recorded.** Frames are analysed and discarded.
- **No frame is transmitted anywhere.** There is no server, and the app cannot make
  a network request.
- What is kept is the *derived* data: joint angles, timings, and the scores computed
  from them. Not imagery.

## What is stored, and where

Your athlete profile, sessions, per-shot features, arrow scores and your personal
baseline are stored in a private database on your device. All of it stays there,
and uninstalling Crocodyl deletes all of it.

## What is sent off your device, and to whom

**Nothing.** The app requests no internet permission, contains no analytics or ad
SDKs, and makes no network requests of any kind. The pose model is bundled at build
time, so there is not even a first-run download.

## Not a medical device

Crocodyl analyses sports form. It does not diagnose, treat or prevent any medical
condition, and it is not for clinical decisions.

## Permissions, and why each exists

| Permission | Why |
|---|---|
| `CAMERA` | To see your form. Processed on device, never recorded, never transmitted. |

That is the complete list.

## Children

Crocodyl is not directed at children and collects no personal information from
anyone, including children.

## Changes

If this policy changes, the "Last updated" date above changes with it, and the
revised policy is published at this same URL.

## Contact

Crocodyl is made by **A System of Cells**. Questions about this policy or the app:
crocodyl@asystemofcells.com
