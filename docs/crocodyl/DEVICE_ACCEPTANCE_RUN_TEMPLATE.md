# Crocodyl device acceptance run sheet

Use one copy per device. This is the evidence record for the physical-device gates in
RELEASE_ACCEPTANCE_CHECKLIST.md; it does not replace owner sign-off.

## Device and build

- Device / vendor:
- Android version / API:
- ABI:
- Camera: rear / front / none
- App version and versionCode:
- APK/AAB SHA-256:
- Previous production version installed:
- Tester:
- Date / location:

## Install and persistence

- [ ] Clean install launches and onboarding completes.
- [ ] Update over the previous production build launches.
- [ ] Profile, active rig and settings survive force-stop and relaunch.
- [ ] System back does not discard an active form, score or filled form.
- Notes / evidence:

## Camera and capture

- [ ] Permission grant path works.
- [ ] Permission denial and “don’t ask again” explain recovery.
- [ ] Rotation and background/foreground are safe.
- [ ] Camera interruption and camera-unavailable paths recover safely.
- [ ] Start/stop capture and post-session review persist.
- [ ] Real range session produces usable shots without crash or data loss.
- Notes / logcat reference:

## Non-camera flows

- [ ] Scoring, progress, body, calendar, coach and export remain usable.
- [ ] SAF document import, encrypted preview and cancellation/retry work.
- [ ] Shareable export contains no PRIVATE records.
- [ ] Low-storage and offline behavior remains recoverable.
- Notes / evidence:

## Volume and defects

- Sessions completed on this device:
- Crashes:
- Data-loss events:
- P0/P1 defects and reproduction details:
- Evidence links / attached logs:

## Tester disposition

- [ ] Pass
- [ ] Pass with explicitly accepted defect(s)
- [ ] Fail — release blocking

Tester signature / owner approval:
