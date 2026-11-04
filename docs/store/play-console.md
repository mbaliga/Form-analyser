# Crocodyl — Play Console answer sheet

> Only the **deltas** from `Personal-Tracker/store/HOUSE_DEFAULTS.md`.

> Nothing here has been submitted. Facts only the owner knows are `[OWNER: …]` placeholders; the
> owner-only items are listed in §5 of `docs/crocodyl/DECISIONS-2026-10.md`. The statements below
> match `docs/store/privacy-policy.md`, where every behavioural sentence cites its source line.

| | |
|---|---|
| applicationId | `com.asoc.crocodyl` |
| Version | read from `app-android/version.properties` |
| Category | **Health & Fitness** |
| Tags | archery, form analysis, pose estimation, coaching, sports |
| Contact email | [OWNER: contact e-mail] |
| Website | `https://asystemofcells.com/crocodyl` [OWNER: confirm] |
| Privacy policy | `https://asystemofcells.com/crocodyl/privacy` [OWNER: confirm the hosted page serves exactly the text of `docs/store/privacy-policy.md`] |

> **The application id is permanent once a Play track is open.** The build produces
> `com.asoc.crocodyl` and decision 6 of the decision record keeps it as the single id for every
> channel. Whether it is final, and whether a Play Console entry already exists under it, is
> Owner-only item 3. [OWNER: confirm `com.asoc.crocodyl` before the first upload.]

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
**Nothing is collected by the developer. One optional, user-initiated transfer to a third party
the user chooses: the cloud AI coach.**

| Question | Answer |
|---|---|
| Does the app collect user data, meaning send it to the developer? | **No.** The developer runs no server. Everything is processed and stored on the device. |
| Does the app share user data with third parties? | **Yes, optionally and only when the user asks.** If the user enters their own API key for a cloud AI provider and taps a coach task, a redacted factsheet goes to that provider. Play counts a transfer to a third party as sharing. [OWNER: confirm this classification against the live Data safety form.] |
| Data types shared | **Health & fitness info** (training load, readiness, injury regions and severity; medications only while the user has switched on "Include medications" on the coach screen) and **App activity** (session and shot counts, streak). [OWNER: map these to the exact data types on the live form.] |
| Recipients | Only the provider the user chose: `https://api.anthropic.com`, `https://api.openai.com`, `https://generativelanguage.googleapis.com` or `https://api.deepseek.com`. |
| Is the sharing optional? | **Yes.** The app, including its offline rule coach and the on-device model option, works fully without it. |
| Encrypted in transit? | **Yes.** HTTPS only; cleartext traffic is disabled. |
| Deletion? | **Yes, in the app.** Per-item delete (kept under Settings → Data → Recently deleted until restored; documents can then be erased permanently) and Settings → Data → Wipe everything. |

Never shared, whatever the settings: the text of mood notes and life events, and cycle information (a life event can only add "high-stress window" to the readiness reasons); camera frames,
raw video, photos and documents. Exports are files the user saves or shares themselves; the app
sends them nowhere. They are signed, **not encrypted**.

The point a reviewer will look for, and the answer:
- **Camera frames are processed on device by MediaPipe BlazePose and are never
  transmitted.** Raw session video is off by default; when the user switches it on it is kept in
  the app's own storage, is never exported and never sent.
- Sessions, shots, scores and the baseline live in a local Room database.
- **Home-screen widgets** (only if the user places them) show a short summary from that database on
  the device's home screen, drawn by the launcher on the device; nothing is sent anywhere, and no
  physio plan name, injury detail, mood, life-event, cycle, medication or document entry is shown.
  See "Home-screen widgets" in the privacy policy. [OWNER: review with that section.]
- The pose model (`pose_landmarker_lite.task`) is **bundled inside the app**, so the installed app
  makes no model download and the form analysis works fully offline.
- **Google's MediaPipe Tasks library contains a usage-statistics client** that would report the
  package name, version, task name and mode, and latency statistics to
  `https://firebaselogging.googleapis.com/v0cc/log/batch`. Crocodyl excludes the upload backend,
  replaces the transport runtime with a no-op sink and strips `ACCESS_NETWORK_STATE`; the release
  preflight fails if the backend, its services or that permission reappear in the merged manifest.
  No analytics or advertising library is declared.

### Permissions
| Permission | Why | Play form? |
|---|---|---|
| `CAMERA` | The whole product: pose capture, plus document and target photos. | No form, but the in-app rationale must be shown before the first request. |
| `VIBRATE` | Haptic cues, which the user can turn off. | No. |
| `INTERNET` | Only the optional cloud AI coach and the manual model check. | No. |

These three are the complete list; `ACCESS_NETWORK_STATE`, which a library used to contribute, is
stripped from the merged manifest. If a future feature adds a permission, revisit this whole
section and the Data safety answers above.

### Content rating
- Category `Utility, Productivity, Communication, or Other`.
- **"Does the app use the camera to capture images of the user?"** If the
  questionnaire raises it: yes, on device, never transmitted or shared.
- The optional AI coach shows text written by a provider the user chooses.
  [OWNER: answer any generative-AI question on the current questionnaire.]
- Everything else No. Expected **Everyone**.

### Monetisation
- **No in-app purchases in this app today.** Keep the IAP answer and the digital-purchases
  question as "no".
- The paid **Baseline** add-on (the EEG biosignal channel, from the separate
  `baseline` repo) is a **different product**. If it is ever sold *through* this
  app, this section, the IAP answer and the content rating's digital-purchases
  question all change.

## F-Droid
See `docs/crocodyl/DECISIONS-2026-10.md`, decision 4 (channels and their order) and decision 12
(anti-features), and `fdroid/README.md` for the recipe. Nothing is submitted until the owner
answers items 1 to 4 of §5 there.

## Pre-submit checklist

- [ ] Owner-only items 1 to 4 of §5 in `docs/crocodyl/DECISIONS-2026-10.md` answered (licence,
      copyright facts, application id, signing key).
- [ ] Privacy policy hosted at the URL above, identical to `docs/store/privacy-policy.md`, with
      every `[OWNER: …]` placeholder filled in.
- [ ] Data safety form answered as above and checked against the live form.
- [ ] Listing text reads "Free to install and use. Source code is published; see the repository
      licence." until a licence is granted (`LICENSING.md`).
- [ ] Closed test with the number of testers and days Play currently requires for a personal
      account (decision 4 records 12 testers for 14 days).
- [ ] Build from `main` at the release tag, signed with the owner's key.
- [ ] Icon 512x512 and feature graphic 1024x500.
- [ ] Screenshots: live capture with the pose overlay, a per-shot deviation score,
      the fatigue trajectory, the form-versus-score chart. **Shoot on a range with
      a real bow.** A studio mock of this app is instantly unconvincing.
- [ ] Nobody identifiable other than you appears in a screenshot.
