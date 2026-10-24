# Privacy Policy — Crocodyl

> **This is the working copy, not the hosted one.** The URL Play Console points at is
> **https://asystemofcells.com/crocodyl/privacy**. Keep the two in sync by hand.
> [OWNER: confirm the hosted page serves exactly this text before the store listing goes live.]
>
> The `<!-- Cnn: file:line -->` comments name the source line behind each statement about what the
> app does. They are invisible when the page is rendered, and `StoreClaimsTest` fails if a cited
> file or line no longer exists. When code changes, re-open the cited lines and update the text.

**Last updated: 24 October 2026** [OWNER: set the date on which the hosted copy goes live.]

Crocodyl is an archery form analyser and training log for Android (package `com.asoc.crocodyl`),
made by [OWNER: legal name and postal address of the data controller]. This policy says exactly
what the app does with data, including the two things that can move data off your phone, both of
which you start yourself.

## The short version

- **No accounts, no advertising, no analytics, no tracking and no crash reporting.** The app
  declares no library for any of them.
  <!-- C01: app-android/build.gradle.kts:181-250 -->
- **Your data lives on your phone**, in the app's own storage, and is excluded from Android cloud
  backup and device-to-device transfer.
  <!-- C02: app-android/src/main/AndroidManifest.xml:30-32; app-android/src/main/res/xml/backup_rules.xml:4; app-android/src/main/res/xml/data_extraction_rules.xml:4-9 -->
- **Data leaves your phone in two ways, and only because you ask:** the optional cloud AI coach,
  and a file you export or share. With no API key entered, the app makes no network request at all.
  <!-- C03: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/HttpJson.kt:66; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/CoachViewModel.kt:148-152; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/AiSettingsScreen.kt:114-117 -->
- **Exports are signed, not encrypted.** Anyone who receives an export file can read it.
  <!-- C04: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/ExportViewModel.kt:379-401 -->
- **One Google library inside the app contains a usage-statistics reporter. Crocodyl switches it
  off.** See "Third-party components" below.
  <!-- C05: app-android/build.gradle.kts:167-175; app-android/src/main/java/com/google/android/datatransport/runtime/TransportRuntime.java:45-51 -->

## The camera

Crocodyl watches you through the camera to estimate your pose.

- Every frame is analysed **on your phone**, in real time, by a pose model that is bundled inside
  the app. <!-- C06: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/capture/PoseRecorder.kt:39-47,83 -->
- The model is never downloaded: it ships in the app, and the only network code in the app talks to
  the AI providers listed below. <!-- C07: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/capture/PoseRecorder.kt:43,132; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/HttpJson.kt:66,115,172 -->
- Each frame is analysed and released. The analysis does not save camera frames.
  <!-- C08: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/capture/PoseRecorder.kt:68-103 -->
- What is stored from the analysis is derived data: joint angles, timings and the scores computed
  from them, not imagery. <!-- C09: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/Entities.kt:63-70; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/ArcheryAnalyzer.kt:26-36 -->
- **Raw video is off by default.** Only if you switch on "Keep raw video" in Settings → Capture does
  Crocodyl record the session to a video file. <!-- C10: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/AppPrefs.kt:37; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/SettingsScreens.kt:344-350; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/CaptureScreen.kt:443-445 -->
- That file is written without sound to the app's own storage area (the Movies folder inside the
  app's external files directory, or the app's private files if that is unavailable).
  <!-- C11: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/CaptureScreen.kt:383-396; app-android/src/main/AndroidManifest.xml:6-18 -->
- You choose how long clips are kept: forever (the default), 30 days or 7 days. Expired clips are
  removed the next time the training workspace opens. <!-- C12: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/AppPrefs.kt:38-40; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/SettingsScreens.kt:351-360; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/SessionViewModel.kt:110-118 -->
- You can delete a clip yourself from the Review screen at any time.
  <!-- C13: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/ReviewScreen.kt:100; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/SessionViewModel.kt:384-393; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/Repository.kt:98-103 -->
- Raw video is never included in an export and is never sent to an AI provider.
  <!-- C14: core-wellness/src/main/kotlin/xyz/mdhv/formanalyser/wellness/Privacy.kt:28-29; core-exchange/src/main/kotlin/xyz/mdhv/formanalyser/exchange/ConsentFilter.kt:66-67; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/ExportViewModel.kt:879-880; core-coach/src/main/kotlin/xyz/mdhv/formanalyser/coach/PromptBuilder.kt:56-62 -->
- **End Scan** reads a photo of a target into memory so you can mark your arrows. Crocodyl does
  not save that photo and does not send it anywhere.
  <!-- C15: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/capture/EndScanCapture.kt:19-24; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/components/EndScanPhoto.kt:67-72 -->
- A **document photo** (for example a physio report) is taken with your device's camera app into a
  temporary file in Crocodyl's cache, encrypted into the document vault, and the temporary file is
  deleted afterwards. <!-- C16: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/InjuryPhysioScreens.kt:227-253; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/BodyViewModel.kt:244-247,264-289 -->

## What is stored on your phone

| What | Where and how | How you remove it |
|---|---|---|
| Your profile, sessions, per-shot features, scores, plans, check-ins, injuries, pain and physio logs, medication list, and mood, life-event and cycle logs | One local database, `form-analyser.db`, in the app's private storage. <!-- C17: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/AppDatabase.kt:84,150; core-wellness/src/main/kotlin/xyz/mdhv/formanalyser/wellness/Privacy.kt:21-60 --> | Delete items in the app (see below), or wipe everything. |
| Documents you add (reports, photos) | Encrypted with AES-256-GCM streaming encryption (Tink) in the app's private `vault` folder; the key is wrapped by the Android Keystore. <!-- C18: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/vault/Vault.kt:14-17,26-35,41-66 --> A readable copy exists in the app's cache only while you view a document, and is removed when you close the viewer and when the app starts. <!-- C19: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/vault/Vault.kt:76-102,121-133; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/InjuryPhysioScreens.kt:629-633; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/MainActivity.kt:108 --> | Delete, then erase permanently (see below), or wipe everything. |
| Your AI provider API keys (if you enter any) | Encrypted with AES-256-GCM (Tink) under a key wrapped by the Android Keystore, in a private preferences file. <!-- C20: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/KeyVault.kt:23-36,41-49,67-72 --> | "Remove" on the key in Settings → AI coach, or wipe everything. <!-- C20b: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/KeyVault.kt:61-63; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/AiSettingsScreen.kt:103-107 --> |
| Preferences | Your chosen coach model, the coach privacy switches, the location of an imported model, and a counter of coach requests and tokens per model (never prompt or answer text), plus ordinary app settings. <!-- C21: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/AiSettings.kt:30-36,63-69,82-88; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/AppPrefs.kt:16-29 --> | Wipe everything. |
| An on-device AI model, if you import one | Copied from the file you pick into the app's private `on-device-models` folder. <!-- C22: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/OnDeviceLlmClient.kt:168-197; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/AiSettingsScreen.kt:145-169 --> | "Remove" in Settings → AI coach, or wipe everything. <!-- C22b: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/AiSettingsScreen.kt:181-192 --> |
| Raw session video, only if you switched it on | See "The camera". | Delete from Review, retention setting, or wipe everything. |
| Your export signing key | A P-256 key pair made once in the Android Keystore; the private key never leaves the Keystore. The public key and its fingerprint are written into every export. <!-- C23: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/exchange/AndroidKeyProvider.kt:16-23,57-72; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/ExportViewModel.kt:364-387 --> | Wipe everything deletes the key. <!-- C23b: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/AppDataReset.kt:25-30,80,90-93 --> |
| A temporary copy of the last file you shared | In the app's cache; the next share replaces it. <!-- C24: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/ExportViewModel.kt:208-211; app-android/src/main/res/xml/file_paths.xml:11 --> | Wipe everything clears the cache. <!-- C24b: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/AppDataReset.kt:66-71 --> |

**What "delete" means.** Deleting a session, scorecard, check-in, pain entry, injury or document
hides it: it leaves your lists, your trends and your exports, but stays on your phone under
Settings → Data → Recently deleted until you restore it.
<!-- C25: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/SettingsViewModel.kt:57-76; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/SettingsScreens.kt:457-471; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/ExportViewModel.kt:881-887 -->
A deleted document can then be erased permanently, which removes its encrypted file, after a second
confirmation.
<!-- C26: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/SettingsScreens.kt:477-499; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/DocumentRetraction.kt:14-36 -->
**Settings → Data → Wipe everything** (you must type WIPE) erases the database, the documents,
your API keys, imported models, raw video, caches, preferences and the Keystore keys.
<!-- C27: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/AppDataReset.kt:17,32-88; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/SettingsScreens.kt:534-558 -->

[OWNER: state what happens to Crocodyl's data and to any raw-video clips when the app is
uninstalled, and the retention promise you want to make.]

## What can leave your phone

### 1. The optional AI coach

Crocodyl has a built-in rule-based coach that works entirely on your phone with no key and no
network. <!-- C28: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/CoachViewModel.kt:44-46,134-136 --> You can also use a cloud AI coach (Anthropic, OpenAI, Google or DeepSeek), or an AI model that runs on your phone.

- **Off until you switch it on.** The cloud coach needs your own API key for a provider you choose.
  With no key stored for the chosen model, tapping a coach task stops before any call is made.
  <!-- C29: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/CloudLlmClients.kt:18-26; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/CoachViewModel.kt:148-152; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/AnthropicClient.kt:38-39 -->
- **Only when you tap.** A request is sent only when you tap a coach task. Nothing is sent on
  launch or on a schedule: the coach request and the model check below are the only callers of the
  app's network code.
  <!-- C30: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/CoachScreen.kt:145; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/CoachViewModel.kt:148-169; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/AiSettingsScreen.kt:119 -->
- **Model check.** The "Check available models" button in Settings → AI coach asks the provider
  which models your key can reach. It sends only your API key, and only when you press it.
  <!-- C31: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/AiSettingsScreen.kt:91,114-122; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/ModelDiscovery.kt:74-96; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/HttpJson.kt:160-164 -->
- **What is sent.** The model id, fixed coaching instructions, a fixed question for the task you
  tapped, and a factsheet built from your own logs: readiness level and reasons, training-load
  counts, streak, active injuries as body region and severity, your bow set-up (name, type, draw
  weight, draw length) and hours since your last check-in, depending on what you have logged.
  <!-- C32: core-coach/src/main/kotlin/xyz/mdhv/formanalyser/coach/CoachFacts.kt:49-59,84-128; core-coach/src/main/kotlin/xyz/mdhv/formanalyser/coach/PromptBuilder.kt:22-26,44-50,56-67; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/CoachViewModel.kt:300-325 -->
- **What is never sent to a cloud provider.** Your mood notes, life events and cycle information are
  never included. <!-- C33: core-coach/src/main/kotlin/xyz/mdhv/formanalyser/coach/Redaction.kt:70-71; core-coach/src/main/kotlin/xyz/mdhv/formanalyser/coach/CoachFacts.kt:65-71 --> Your medications are included only while you have switched on "Include medications" on the coach screen; that switch is off by default.
  <!-- C34: core-coach/src/main/kotlin/xyz/mdhv/formanalyser/coach/Redaction.kt:69; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/CoachScreen.kt:72,126-145 -->
  Camera frames, video, photos and documents are never included, and there is no free-typed chat:
  the prompt is the fixed instructions, the factsheet and the fixed question.
  <!-- C35: core-coach/src/main/kotlin/xyz/mdhv/formanalyser/coach/PromptBuilder.kt:3-6,56-67 -->
- **You can see what was held back.** After each request the coach screen lists the facts that were
  not sent and why.
  <!-- C36: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/CoachScreen.kt:341-345; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/CoachViewModel.kt:144-146 -->
- **On-device model.** If you import a model file and select it, the coach runs on your phone and
  makes no network request.
  <!-- C37: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/OnDeviceLlmClient.kt:46,53-60; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/ModelDiscovery.kt:97-98 -->

**Where a cloud request goes.** These are the only internet destinations in the app. Crocodyl's
maker runs no server and receives none of these requests.
<!-- C38: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/AnthropicClient.kt:333; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/OpenAiClient.kt:179; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/GoogleClient.kt:294; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/DeepSeekClient.kt:153; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/ModelDiscovery.kt:78-94 -->

| Provider | Coach request | Model check | How your key travels |
|---|---|---|---|
| Anthropic | `https://api.anthropic.com/v1/messages` | `https://api.anthropic.com/v1/models` | `x-api-key` header <!-- C39: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/AnthropicClient.kt:61; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/ModelDiscovery.kt:79 --> |
| OpenAI | `https://api.openai.com/v1/chat/completions` | `https://api.openai.com/v1/models` | `Authorization: Bearer` header <!-- C40: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/OpenAiClient.kt:46; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/ModelDiscovery.kt:84 --> |
| Google | `https://generativelanguage.googleapis.com/v1beta/models/` (`generateContent`, `streamGenerateContent`) | `https://generativelanguage.googleapis.com/v1beta/models` | **In the request URL** (`?key=`) <!-- C41: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/GoogleClient.kt:58,151-152; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/ModelDiscovery.kt:88-89 --> |
| DeepSeek | `https://api.deepseek.com/v1/chat/completions` | `https://api.deepseek.com/v1/models` | `Authorization: Bearer` header <!-- C42: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/DeepSeekClient.kt:35; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/ModelDiscovery.kt:95 --> |

- Requests use HTTPS; the app does not allow plain-HTTP traffic.
  <!-- C43: app-android/src/main/AndroidManifest.xml:37; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/AnthropicClient.kt:333; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/OpenAiClient.kt:179; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/GoogleClient.kt:294; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/DeepSeekClient.kt:153 -->
- **The Google key is sent in the request URL and is therefore visible in Google's access logs.**
  The other three providers receive it in a header. In every case the provider also sees your
  internet address, as any website does.
- The provider's own terms and privacy policy govern what it does with a request.
  [OWNER: state your position on the AI providers as sub-processors, and whether any data
  processing agreement applies.]

### 2. Exports and sharing

- **You choose what goes into an export.** Exports come in two tiers. Private data (mood, life
  events, cycle, and raw-video records) can never be included. Medical tables (medication,
  document records) are included only with a separate grant for each.
  <!-- C44: core-exchange/src/main/kotlin/xyz/mdhv/formanalyser/exchange/ExportTier.kt:13-21; core-exchange/src/main/kotlin/xyz/mdhv/formanalyser/exchange/ConsentFilter.kt:66-72; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/ExportScreen.kt:94-129 -->
- Before you export, the screen lists what will leave the device and what stays.
  <!-- C45: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/ExportScreen.kt:131-156 -->
- Items you deleted, and the items that belonged to them, are left out.
  <!-- C46: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/ExportViewModel.kt:877-888 -->
- **An export is a zip of readable JSON files plus a manifest. The manifest is signed with your
  device's key; nothing in the file is encrypted.** Anyone who gets the file can read it.
  <!-- C47: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/ExportViewModel.kt:342-401 -->
- The manifest carries your public signing key and its fingerprint.
  <!-- C48: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/ExportViewModel.kt:364-387 -->
- Document files are not part of an export; if you grant the medical tier, the record of a document
  (its title, type, size and checksum) is.
  <!-- C49: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/ExportViewModel.kt:392-401; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/BodyViewModel.kt:270-281; core-wellness/src/main/kotlin/xyz/mdhv/formanalyser/wellness/Privacy.kt:59 -->
- The file is written where you tell the system file picker, or handed to the app you choose in the
  share sheet.
  <!-- C50: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/ExportScreen.kt:72-77,180-200 -->
- You can also share your pairing fingerprint as text through the share sheet; nothing is shared
  unless you press the button.
  <!-- C51: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/ExportScreen.kt:167-175 -->

### Nothing else

The equipment studio shows pages bundled inside the app and is set up so that it cannot load
anything from the network.
<!-- C52: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/EquipmentAtelierActivity.kt:62-67,117-124,138-143,157-158 -->
The community directory screen is a static page with no records and no network access.
<!-- C53: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/CommunityDirectoryActivity.kt:13-33 -->

## Third-party components

Crocodyl uses Google's MediaPipe Tasks library for pose estimation. That library contains a
usage-statistics client which, left in place, would report the app's package name and version, the
task name and mode, and latency statistics to Google at
`https://firebaselogging.googleapis.com/v0cc/log/batch`.
<!-- C54: docs/crocodyl/DECISIONS-2026-10.md:64-82; app-android/build.gradle.kts:167-171 -->

Crocodyl does not allow that. The upload backend and the transport runtime that would send the
reports are excluded from every build.
<!-- C55: app-android/build.gradle.kts:172-175 -->
In their place the app ships a no-op sink that counts each event and discards it, without encoding,
storing or sending it.
<!-- C56: app-android/src/main/java/com/google/android/datatransport/runtime/TransportRuntime.java:12-23,45-51,70-74; app-android/src/main/java/com/google/android/datatransport/cct/CCTDestination.java:5-9 -->
A unit test checks both halves.
<!-- C57: app-android/src/test/kotlin/xyz/mdhv/formanalyser/app/MediaPipeTelemetrySinkTest.kt:28-50 -->
The release preflight fails if the built app's manifest contains the backend, its scheduler services
or `ACCESS_NETWORK_STATE`, and the app's own manifest strips that permission.
<!-- C58: tools/release/verify_manifest_contract.py:11-25,73-85; tools/release/verify_android_release.sh:55; app-android/src/main/AndroidManifest.xml:24 -->

The app declares no Firebase, Google Play services, advertising, billing or crash-reporting library,
and a test fails the build if one is added.
<!-- C59: app-android/build.gradle.kts:181-250; app-android/src/test/kotlin/xyz/mdhv/formanalyser/app/StoreClaimsTest.kt:175-194 -->
The libraries Crocodyl does ship, and their licences, are listed in Settings → About → Third-party
licences.

## Permissions, and why each exists

| Permission | Why |
|---|---|
| `CAMERA` | To see your form, to photograph a document for the vault and to photograph a target for End Scan. Camera frames are analysed on your device. The camera is optional: the app installs and works without one. <!-- C60: app-android/src/main/AndroidManifest.xml:6,11-14; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/CaptureScreen.kt:101-103; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/InjuryPhysioScreens.kt:261-273; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/EndScanScreen.kt:116-122 --> |
| `VIBRATE` | Haptic cues, for example to confirm a recorded arrow. You can turn them off in Settings → Appearance. <!-- C61: app-android/src/main/AndroidManifest.xml:7-10; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/theme/HyleHaptics.kt:59-64; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/SettingsScreens.kt:400-407 --> |
| `INTERNET` | Only for the optional cloud AI coach and the manual model check described above. <!-- C62: app-android/src/main/AndroidManifest.xml:16-18; app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers/HttpJson.kt:66,115,172 --> |

These three are the complete list. The app does not ask for location, contacts, the microphone,
storage, notifications or network-state access.
<!-- C63: app-android/src/main/AndroidManifest.xml:5-24; tools/release/verify_manifest_contract.py:11-16 -->
The Android support library also declares one internal permission,
`com.asoc.crocodyl.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, that only this app can hold; it is not
something you are asked about.
<!-- C64: tools/release/verify_manifest_contract.py:15 -->

## Not a medical device

Crocodyl analyses sports form. It does not diagnose, treat or prevent any medical condition, and it
is not for clinical decisions.

## Children

Crocodyl is not directed at children. It never asks for a date of birth or an age.
<!-- C65: app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/Entities.kt:12-22 -->
[OWNER: age statement and minimum age.]

## Changes

If this policy changes, the "Last updated" date above changes with it, and the revised policy is
published at this same URL.

## Contact

Crocodyl is made by [OWNER: controller name and postal address]. Questions about this policy or the
app: [OWNER: contact e-mail]. [OWNER: governing law, if you want to state one.]
