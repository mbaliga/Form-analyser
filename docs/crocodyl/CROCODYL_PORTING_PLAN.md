# Crocodyl — multi-platform porting plan

> Part of the constellation-wide porting program (`Personal-Tracker/PORTING_PROGRAM.md`, 2026-10-06).
> Status: **PLAN — nothing in this document has been built.** Every claim about a target platform is
> labelled with its evidence class (§0). This file is owned by the lead planning session; a platform
> track updates only its own §4 row and appends dated entries to `CROCODYL_BUILD_NOTES.md` (this repo's
> engineering log) and `CROCODYL_STATUS.md` (its status snapshot).
>
> Product name is **Crocodyl**; `form-analyser` is the repository and codename (`docs/naming.md`). Targets,
> in the owner's order: Ubuntu Touch, Linux desktop, iOS/iPadOS, macOS, Windows.

## 0. Evidence labels (never dropped)

`PLAN` (this document) · `CI (hosted VM)` · `SIMULATOR` · `CI-APPROX — NOT DEVICE EVIDENCE` ·
`NEEDS-DEVICE-VALIDATION` (NDV) · `NEEDS-OWNER-VALIDATION` (NOV) · `NOT-APPLICABLE` (with reason).
The program's §2 also defines `CONTAINER-BUILD-ONLY` and `BROWSER-HEADLESS`; they are used below only where named.
Nothing here was compiled, run or measured on any target. The numbers in §1 and §2 were counted from the checkout
on 2026-10-06 with the commands shown; every effort figure is an estimate in engineer-weeks, not a schedule.

## 1. What this repo is, in porting terms

**Product.** Crocodyl is a free, open-source, local-first athlete performance app. Its first discipline is Olympic
Recurve archery: phone-camera sagittal form and shot-sequence analysis (on-device BlazePose), manual round scoring
and target plotting, rigs and tuning, wellness/body/injury context, an offline rule coach plus BYOK or on-device LLM
explanation, and consent-filtered `.crocbak` export (`README.md`, `CROCODYL_STATUS.md` §1). The blueprint's roadmap adds
End Scan, Live Observer, signed `.croc` exchange, a paid open-source Coach workspace, a static web viewer (Phase 9,
0.10.0) and a native iOS client (Phase 18, 1.4.0) (`docs/crocodyl/CROCODYL_PHASED_IMPLEMENTATION_PLAN.md` §5.1).
Per PT:D-S, Crocodyl (open) licenses the Baseline engine; the in-repo seam is `:engine` (§3, rule 3), and which licence governs it is an open question (Q5).

**State, 2026-10-06.** `main` is at `d0987ce` (PR #12, 2026-09-18). `app-android/build.gradle.kts` declares
applicationId `xyz.mdhv.formanalyser`, versionName `0.6.0-spec-dev`, versionCode 6, minSdk 26, targetSdk 35; Room is at
schema version 8 with hand-written migrations 1→2 … 7→8 (`AppDatabase.kt`). The header of `CROCODYL_STATUS.md` still says
v0.5.1 (2026-08-12), which is stale against the build file. STATUS §4.1 states the Android UI has never been exercised
on a device, so the phased plan's Phase 0 exit gate is not passed. There is no `LICENSE` file, and the
`xyz.mdhv.crocodyl` applicationId rebrand is deferred until just before a store listing (`docs/naming.md`).

**Targets today.** Android phone (debug-signed APK on GitHub Releases, `release.yml`) and the ten pure-JVM modules on
any JDK 21. Android on ChromeOS and tablets is accommodated: manifest `camera.any required=false`, a front-camera
fallback in `ui/CaptureScreen.kt`, and back handling for windows without a system back gesture (`MainActivity.kt`,
comment near line 152). No KMP, Compose Multiplatform, Tauri, Qt or Click files exist, and no document mentions Linux
desktop, macOS, Windows or Ubuntu Touch. In this repo "desktop" means the Phase 9 static PWA viewer ("Open on Desktop",
blueprint 02 Part III), not a native app.

**Stack.** Kotlin 2.1.0 (150 files, 100% of source), Gradle 8.14.3 wrapper with Kotlin DSL, JDK 21 toolchain, AGP 8.7.3,
KSP 2.1.0-1.0.29, plugin versions hardcoded per module (no version catalog). UI: Jetpack Compose with Material3
(compose-bom 2024.12.01), navigation-compose 2.8.5, Compose Canvas custom drawing; Hyle tokens are ported constants in
`ui/theme/Theme.kt`, not the `dev.aarso:hyle` dependency. Frameworks: Room 2.6.1 (+KSP), DataStore 1.1.1, CameraX 1.4.1,
MediaPipe tasks-vision 0.10.20 (BlazePose lite) and tasks-genai 0.10.24, Tink 1.15.0, kotlinx.serialization 1.7.3,
`java.net.HttpURLConnection` for BYOK calls. Manual DI, no Hilt. `:app-android` is included only with `-PwithAndroid`
(`settings.gradle.kts`), so `./gradlew test` runs on a bare JDK. Native dependencies are the MediaPipe and Tink AARs;
there is no JNI, NDK or CMake in this repo.

**Size (counted).** `find . -name '*.kt' -not -path './.git/*' | wc -l` gives 150 files; the same files through
`xargs cat | wc -l` give 20,056 lines; 27 test files; `grep -r '@Test' --include=*.kt . | wc -l` gives 167, all in the
pure modules. `:app-android` has 75 main files, about 13,970 lines and no tests (Robolectric is deferred, STATUS §4.4).

## 2. Portable core vs platform-bound layers

Lines are main-source `.kt` lines (`find <m>/src/main -name '*.kt' | xargs cat | wc -l`); tests are `@Test` counts.

| Module / dir | Role | Portability | Approx LOC | Notes |
|---|---|---|---|---|
| `engine` | Sport-agnostic analysis engine: `SportModule` seam, baseline, deviation, fatigue, signal-score stats | pure Kotlin/JVM | 537 | 12 tests. No `java.*` use. `maven-publish` artifactId `baseline-engine`, group `xyz.mdhv.formanalyser`, version 0.1.0 (`engine/build.gradle.kts`). Package is `xyz.mdhv.crocodyl.engine`. |
| `archery-module` | Pose types (BlazePose-33 contract), handedness normaliser, shot segmenter, features, inverse statics | pure Kotlin/JVM | 588 | 17 tests incl. the feature-invariance exit test. One JVM-only API (`Math.toDegrees`, table below). Guardrail: do not modify existing archery source. |
| `core-model` | Handedness, BowType | pure | 40 | No tests. |
| `core-equipment` | Poundage estimator, tuning math (FOC/GPP/KE) | pure | 448 | 30 tests. One `Math.round`. |
| `core-wellness` | Load, ACWR, streak, readiness, cycle, `PrivacyRegistry` | pure, JVM-only imports | 475 | 28 tests. The only module importing `java.time`. |
| `core-body` | 52-region atlas ID contract, soreness resolver | pure | 203 | 9 tests. Atlas geometry lives in the app (`BodyAtlasCanvas`). |
| `core-coach` | Model registry, grounding, redaction, rule coach, `LlmClient` seam | pure | 663 | 37 tests. `String.format(Locale.US, …)` in text rendering. |
| `core-exchange` | `ConsentFilter`, `.crocbak` manifest (clock injected), `PubkeyIdentity` + `KeyProvider` seam | pure | 246 | 16 tests. Signed `.croc` envelope is not built (Phase 7). |
| `core-scoring` | WA Recurve round packs, score keypad parser, plotting geometry | pure | 474 | 9 tests. |
| `core-athlete` | Goals and progress math | pure | 277 | 9 tests. |
| `app-android` | Compose UI, 14 ViewModels, Room v8, DataStore, CameraX→MediaPipe, Tink vault, SAF/share, haptics | Android-bound, mostly portable UI | 13,970 | 44 `android.*` import lines in 30 files (`grep -rn 'import android\.'`); 15 import `android.app.Application`. Camera, pose, crypto, file and haptics code is confined to about 8 files; `Application`/`Context` injection is the widespread part. |

The ten pure modules total about 3,950 lines and 167 tests. They are the portable core. Measured JVM-only API sites
inside them (these block Kotlin/Native, Wasm and any non-JVM common source set; none blocks a JVM desktop head):

| Site | Where | Replacement note |
|---|---|---|
| `java.time.LocalDate`, `ChronoUnit` | `core-wellness`: `Stats.kt`, `Streak.kt`, `Acwr.kt`, `Load.kt`, `Cycle.kt` (+4 test files) | kotlinx-datetime or an epoch-day type; the 28 tests are the equivalence proof. |
| `String.format("%.2f", x)`, default locale | `core-wellness/Readiness.kt:63` | Locale-independent fixed-decimal formatter. |
| `String.format(Locale.US, …)` | `core-coach`: `RuleCoach.kt`, `CoachFacts.kt` | Same formatter; the source comments require text identical on every device, so add golden-text fixtures. |
| `Math.toDegrees` ×3 | `archery-module/.../pose/Geometry.kt:24,35,47` | Not guaranteed bit-identical to `x * 180 / PI`; verify against existing outputs and log it as a deviation (BUILD_NOTES convention). |
| `Math.round` ×1 | `core-equipment/TuningValidation.kt:165` | Ties-up rounding must be preserved. |

Platform-bound APIs that matter:

| API | Where | Porting impact |
|---|---|---|
| CameraX `ImageAnalysis`, camera permission, `FEATURE_CAMERA_ANY` | `capture/PoseRecorder.kt`, `ui/CaptureScreen.kt`, manifest | Capture is per platform. The manifest declares `camera.any required=false` and its comment says the app is usable without a camera (scoring, logs, progress); this is unverified on any device (STATUS §4.1), so a capture-less port is a declared, not a proven, state. |
| MediaPipe `PoseLandmarker` (`pose_landmarker_lite.task`, `RunningMode.IMAGE`, `numPoses=1`); model fetched at build by `downloadPoseModel` (`build.gradle.kts:113`, gitignored) | `capture/PoseRecorder.kt` | No MediaPipe JVM artefact. iOS has an official pod. Output is `PoseSequence(frames, fps)` in the 33-index contract; only 11 indices are defined as consumed (`PoseLandmarks`, `PoseFrame.kt`). |
| MediaPipe `LlmInference` (Gemma) | `ai/OnDeviceLlmClient.kt` | Behind the pure `LlmClient` seam; optional everywhere because the rule coach needs no model. |
| Tink AEAD/StreamingAEAD + AndroidKeyStore (EC P-256) | `ai/KeyVault.kt`, `vault/Vault.kt`, `exchange/AndroidKeyProvider.kt` | Per-platform secure-storage actuals. `KeyProvider` (core-exchange) is already the seam. Tink has no iOS build. |
| Room 2.6.1 v8, 13 files, `DbRecovery` | `data/*.kt` | Room KMP needs a newer Room than the pinned 2.6.1 (version not verified here). The migration chain cannot be rewritten (program law). |
| DataStore preferences | `data/AppPrefs.kt`, `ai/AiSettings.kt` | KMP artefacts exist; needs a path-provider actual. |
| SAF, `FileProvider`, share sheet, `java.util.zip` | `domain/ExportViewModel.kt` (`.crocbak` is a ZIP of `manifest.json` + `tables/*.json`), `ui/ExportScreen.kt` | Native dialogs on desktop; iOS needs a zip writer/reader that the JVM reader accepts. |
| `HttpURLConnection` BYOK clients | `ai/providers/HttpJson.kt` | Unchanged on JVM; Ktor on iOS. Property to keep: never logs headers or bodies. |
| `Vibrator` | `ui/theme/HyleHaptics.kt` | Trivial actual; no-op on desktop. |
| `AndroidViewModel(app: Application)` ×14, repositories taking `Context` | `domain/*ViewModel.kt`, `data/*Repository.kt` | The largest mechanical refactor: constructor-injected environment instead of `Application`. |
| navigation-compose | `MainActivity.kt` | JetBrains navigation is multiplatform. |
| Compose Canvas drawing; one `android.graphics` use | `ui/components/*`, `ui/InjuryPhysioScreens.kt` | Portable, except the document/image viewer needs a platform decoder. |

## 3. Binding rules this port must not break

| # | Rule | Source |
|---|---|---|
| 1 | Local-first, no maintained backend, no mandatory telemetry, no silent collection. Network allowlist: explicit BYOK provider calls, user-initiated model download/catalog, optional weather, static web assets, signed static catalog artefacts, user-tapped links, store licensing/update checks. Ports inherit it exactly; program I-1 adds no analytics and no phoning home on any platform. The blueprint allows app-store licensing/update checks; the program's platform briefs forbid in-app update checks. **Proposed for ports (not ruled): no in-app updater; updates belong to the package manager or store and the docs say so.** Owner confirms under Q6. | blueprint 02 §9; phased plan §4 ("No silent collection"); STATUS §1 |
| 2 | Pure-JVM-first: engine, archery-module and core-* have zero `android.*` imports and `./gradlew test` passes on a bare JDK with no Android SDK; `:app-android` stays behind `-PwithAndroid`. Any KMP conversion keeps the `jvm` target and the 167 tests green. | README "Build and test"; STATUS §2; `settings.gradle.kts` |
| 3 | **Baseline seam.** What belongs to Baseline never enters this repo's history; Crocodyl reaches Baseline only through the adapter seam (`SportModule`, `:engine`). No Baseline code is moved between repos by any port step; `:engine`'s package, public API and published coordinates (`baseline-engine`) do not change. | STATUS §1; blueprint 02 §8 (Baseline adapter and evidence ladder); PT:D-S; `engine/build.gradle.kts` |
| 4 | Free athlete loop stays free; Coach workspace and Baseline insights are entitlement-gated in official builds but open source, with no false DRM promise. Non-Play builds use a locally verified signed licence file; the purchase model is the owner's call before Phase 8. | blueprint 01 decisions 4–5; phased plan §14, §36 |
| 5 | Privacy classes live in pure cores: `PrivacyRegistry` (SHAREABLE/MEDICAL/PRIVATE; the blueprint adds raw media and secret), `ConsentFilter`, redaction. PRIVATE never reaches cloud or export; MEDICAL needs an explicit grant. `PrivacyRegistryTest` reads `app-android/.../data/AppDatabase.kt` by relative path and **returns early (skips) if the file is missing**, so moving the Room DDL would silently disable it. | `core-wellness/.../Privacy.kt`, `PrivacyRegistryTest.kt` |
| 6 | Hyle law: #121212-class surfaces (never pure black), one violet accent, provenance on-device/cloud colours, red/green never the sole semantic channel, 300 ms calm easing with Reduce Motion, ≥48 dp targets, state shown by material not language. Colour is never the only signal on any surface, including QML. | blueprint 02 Part IV; `ui/theme/Theme.kt`; program I-3 |
| 7 | Schema before surface; every supported DB version must migrate; no precision inflation (observation resolution `SHOT_CONFIRMED`…`PERIOD_WINDOW` is never promoted); human confirmation; outcome gates beat dates. "Compilation is evidence of compilation. It is not evidence that a user job works." | phased plan §4, §31 |
| 8 | Sequencing: Phase 0 (30 real sessions, three device classes) precedes extension; do not start production multi-sport UI early; iOS is Phase 18 / 1.4.0, after the 1.0.0 club release. A port either respects this ladder or carries an owner-approved amendment. | phased plan §6, §24, §27 |
| 9 | Environment honesty: no device or emulator in the build container; Android behaviour is CI-compiled and owner-verified only. Never claim on-device behaviour. | STATUS §4.1, §5 |
| 10 | Naming: `applicationId` is permanent once published; the `xyz.mdhv.crocodyl` rename is deferred. Bundle, click, Flatpak, MSI and winget ids are not written anywhere until they have a `NAMES.md` row (program R11, OQ-25). | `docs/naming.md`; `docs/store/play-console.md` |
| 11 | No `LICENSE` file exists (constellation D-I). Do not add one without the owner's choice. | `docs/store/play-console.md`; `CONSTELLATION.md` §2 |
| 12 | Pose contract: BlazePose-33 indices, image-normalised [0,1], y-down; handedness is normalised exactly once (`x' = 1 − x`, `HandednessNormalizer`); thresholds are first-pass and unvalidated against real footage. Any other estimator must map onto this and pass `PosePipelineTest` and the feature-invariance test. | `PoseFrame.kt`; `app-android/README.md` validation plan; `docs/biomechanics/CALIBRATED_2D_PROTOCOL.md` (L1 limits) |
| 13 | Vision stays on-device; raw video is not retained by default; no live skeleton overlay that distracts the athlete; recovery guidance is general wellness, never diagnosis, in every store listing too. | blueprint 01 decision 13; privacy policy; `KEEP_RAW_VIDEO` pref |
| 14 | Stale store documents: `docs/store/play-console.md` and `privacy-policy.md` say the app has no INTERNET permission and "vendors the proprietary Baseline engine as source"; the manifest declares INTERNET (for BYOK) and STATUS §1 says the engine is open. This plan repeats neither claim (Q5, Q6). | `AndroidManifest.xml:17`; `play-console.md:65,87`; `privacy-policy.md:15,37` |
| 15 | Commerce laws (affiliate disclosure adjacent to every monetised link; safety/compatibility/wear warnings never suppressed) and the exchange rule that coach observations are separate signed records, never edits to athlete facts, apply to every surface, including any desktop Coach workspace. | blueprint 01 §5.2; blueprint 02 Part IV (Coach exchange) |

## 4. Target matrix (owner's order)

| Target | Feasibility | Approach | Blockers | Effort (eng-weeks, estimate) | Evidence today |
|---|---|---|---|---|---|
| Ubuntu Touch | reframe: nearest shape is a "Crocodyl Scorer & Viewer" | QML + Lomiri.Components over the pure cores (a bundled jlinked JVM core through F7 if S-UT1 passes; Kotlin/Native `linuxArm64` `.so` as fallback): manual scoring and plotting, calendar/wellness log, rig/tuning math, read-only `.crocbak`/`.croc` import preview. No capture, no keys, no BYOK. Capture stays a research track, stated as unsupported in the UI. | No JVM or Compose in a Click app; no MediaPipe build for arm64 Linux; no UT device on record (OQ-1, S-UT1); exchange format not frozen (0.8.0); Qt 5.15 is end-of-road. | 10 (S-UT1 and F7 are not in this row) | PLAN |
| Linux desktop | straight for the restructure and packaging; capture is the hard part | Compose Multiplatform desktop on the JVM: cores to KMP with `jvm()`, shared `app-common`, a desktop head, desktop actuals, jpackage tarball and `.deb`, Flatpak. First shape per OQ-11: the Coach workspace host plus scorer, reviewer and exporter, without capture (Coach surfaces stay entitlement-gated, rule 4, and none ships before Phase 8, Q9); webcam/video capture with a non-MediaPipe estimator as a second milestone behind golden fixtures. | OQ-11; Phase 0 and Phase 1 gates; Room KMP and the pin bump (OQ-17); no MediaPipe JVM artefact; weaker libsecret key tier (OQ-22); OQ-12 before any public binary. | 10 (about 6 for restructure plus packaging without capture, about 4 for capture and pose; about 6 in total if OQ-11 rules out capture) | PLAN |
| iOS / iPadOS | moderate | The repo's own Phase 18 on the shared Compose stack: cores and `app-common` to `iosArm64`/`iosSimulatorArm64`, Room KMP and DataStore, Compose iOS in an XcodeGen SwiftUI shell, AVFoundation with MediaPipe iOS (BlazePose parity; Apple Vision only as a mapped fallback), Keychain and CryptoKit in place of Tink, document picker and share sheet, Ktor. | Phase 18 sits after the 1.0.0 club release; golden fixtures do not exist; Tink has no iOS target; no Apple account, bundle id or macOS CI (OQ-2, OQ-25); Compose-vs-SwiftUI call (Q3); OQ-12. | 14 (assumes the restructure already landed; a SwiftUI rewrite of the UI would roughly double the UI share) | PLAN |
| macOS | native-fit | The Linux JVM build packaged by jpackage as a `.dmg`; Keychain or CryptoKit-wrapped key through a Swift helper; signing and notarisation as disabled templates; capture reuses the Linux provider or Apple Vision with an explicit joint map. | Depends on the Linux restructure; Developer ID and notarisation (OQ-3); no Mac on record (OQ-5), so device gates are NOV. | 3 | PLAN |
| Windows | native-fit | The Linux JVM build packaged as a WiX MSI; DPAPI or Credential Manager through JNA; Media Foundation webcam; winget manifest as a disabled template. | Depends on the Linux restructure; signing route (OQ-3; SignPath Foundation presumably needs an OSI-approved licence, unverified, and this repo has none); Windows path lint before the lane; fate of the Dell (OQ-5). | 3 | PLAN |

The rows overlap: C-steps in §6 are paid once and shared by every row after Linux, and macOS and Windows ride the
Linux build, so their real cost is packaging, signing and per-OS actuals.

## 5. Tier and sequencing

**Tier B-port** (master §5). Crocodyl is the studio's named consumer product. It has about 3,950 lines of pure cores
and a 14k-line Compose UI whose camera, pose, crypto, file and haptics code is confined to about eight files behind existing seams
(`SportModule`, `LlmClient`, `KeyProvider`, `PoseSequence`), and the blueprint already asks for exactly this
incremental multiplatform evolution (blueprint 02 §6). It is not tier A: the repo's own gates forbid breadth before
truth (Phase 0 not passed, contracts not frozen, the exchange formats the viewers depend on not final), the plan puts
iOS at 1.4.0 and defines "desktop" as a PWA, and the headline feature, camera form capture, has no MediaPipe path on
desktop or Ubuntu Touch. It is not tier D because the core restructure and a desktop shape deliver value inside the
existing roadmap.

**Waves** (master §7), repo-local gate in the last column; the gates themselves are defined below the table.

| Target | Program wave | Repo-local gate (master §5 row: Phase 18 after 1.0.0; desktop shape OQ-11; Baseline seam never moved, PT:D-S) |
|---|---|---|
| Linux | P-LX, "Crocodyl desktop (OQ-11)", after Ebbflow desktop and before the IN-RTS Linux export | G0, then G1; G2 for capture |
| iOS / iPadOS | P-iOS, "Crocodyl Phase 18", after Foto-Xplorr and before Bocal/Runout | G3 |
| macOS | P-mac (every CMP app's DMG) | G4 |
| Windows | P-win (every CMP app's MSI and winget) | G4 |
| Ubuntu Touch | **Not named** in master §7 under P-UT a or P-UT b. Nearest fit, proposed for the lead program to assign: P-UT b, after nooz and csapp. OQ-21 (Waydroid) could delete the row. | G5 |

| Gate | Holds when |
|---|---|
| G0 foundation, additive, no product change | OQ-17 ruled for this repo's pins (Kotlin 2.1.0, AGP 8.7.3, KSP 2.1.0-1.0.29, Room 2.6.1); the owner accepts Proposal 1; `ci.yml` and `android.yml` are green on `main`. Covers C0–C2 (C1e only on a Q5 ruling). |
| G1 shared UI and DB move, desktop head | OQ-11 ruled; Phase 1 contracts frozen, so the DB and ViewModel layer moves once rather than twice (Phase 1 introduces stable IDs and typed payloads, which is likely to mean new migrations); Phase 0 exit passed or an owner-recorded waiver, because a device-verified baseline is what lets anyone attribute a regression to the move; C2's schemas and migration tests exist. Covers C3 and the desktop, macOS and Windows steps. |
| G2 desktop capture | G1; Phase 3 exit (range-valid Android form capture with a held-out evaluation, which supplies the reference outputs the desktop estimator is measured against); Q7 ruled; golden fixtures exist. |
| G3 iOS | The 1.0.0 club release shipped (Phase 13) or an explicit owner amendment; G1 and G2's fixtures; OQ-2; OQ-25 (bundle id decided together with the rebrand); OQ-12; Q3. |
| G4 macOS and Windows packaging | G1 and a green Linux lane; OQ-3 for signing; OQ-5 for device gates (macOS stays NOV without a Mac). |
| G5 Ubuntu Touch | OQ-1 (an S-UT1 verdict) or an explicit CI-only waiver; OQ-21 answered; the 0.8.0 exchange format frozen, since the viewer needs a stable `.croc`/`.crocbak` schema; the pure cores green on the chosen host (JVM aarch64 on `ubuntu-24.04-arm`, or Kotlin/Native `linuxArm64`). |

**Proposals, not rulings.** Proposal 1: resolve the blueprint's open decision "KMP migration" as incremental, pure cores
first, `jvm()` target first (blueprint 02 §6 already says "incremental"; this makes it explicit and allows C0–C2 before
the desktop shape is ruled). Proposal 2: if OQ-11 chooses a native desktop app, record it as complementary to the Phase 9
static viewer, never a replacement, because "desktop without upload" is a stated differentiator (blueprint 02, Part II,
competitor parity). No amendment to the iOS ladder is proposed. Both proposals belong to the blueprint's open-decisions
list; this plan edits no blueprint file.

## 6. Work breakdown

**Placement (program R1–R4).** New directories and new workflow files only; existing modules change through this
repo's normal PR process, and only where a step says so.

| Path | Purpose | Added by |
|---|---|---|
| `app-common/` | New KMP module: shared Compose UI, ViewModels, repositories, Room, DataStore (`jvm()`; `androidTarget()` only under `-PwithAndroid`; iOS targets from G3) | C3 |
| `app-android/` | Stays; shrinks to the Android head. **Two steps edit the Android module: C2 (commit `app-android/schemas/` and migration tests) and C3 (the move).** Each goes in its own PR through the repo's normal process (R1), never bundled with a platform track. | C3 |
| `desktop/app-desktop/` | Compose Desktop head, included only with a new `-PwithDesktop` property, following the existing `-PwithAndroid` gate in `settings.gradle.kts` | L1 |
| `apple/` | XcodeGen shell, Swift shims (iOS shell; macOS key helper) | I6, M2 |
| `ubuntu-touch/` | QML app, `clickable.yaml`, `manifest.json.in`, `apparmor.in` | T1 |
| `packaging/{linux,macos,windows}/` | jpackage, Flatpak, WiX, entitlements and disabled signing templates | L3, M3, W1 |
| `.github/workflows/` | New files only: `desktop-linux.yml`, `desktop-macos.yml`, `desktop-windows.yml`, `ios.yml`, `ubuntu-touch.yml`. `ci.yml`, `android.yml`, `release.yml` are not edited by a port. | L2, M1, W1, I1, T4 |

With no property set, the default Gradle configuration stays the ten pure modules, so `ci.yml`'s `./gradlew test`
surface does not grow; its path filters (`engine/**`, `archery-module/**`, `core-*/**`, `app-android/**`,
root `*.gradle.kts`) do not match `desktop/**`, `apple/**` or `ubuntu-touch/**`, so each new workflow carries its own
filters. Every new workflow uses SHA-pinned actions (the existing ones use `@v4` tags), uploads nothing to Actions
storage, and builds packages only on `main` or tags; PR runs compile and test. Release candidates go to a draft GitHub
Release marked UNSIGNED, not for release (R6). The existing `android.yml` APK upload and `package-source.yml` are left
alone; `cleanup-artifacts.yml` already purges artefacts and caches every six hours because storage is exhausted (OQ-20).

**Shared steps (C), paid once.** Effort in engineer-weeks (estimate), included in the Linux row.

| Step | Work and placement | Done when | Evidence, effort |
|---|---|---|---|
| C0 | Spike, findings written to `CROCODYL_BUILD_NOTES.md`: (a) after a module becomes `jvm()`-only KMP, does `./gradlew test` still run its tests, or is a `test` alias to `jvmTest` needed, since a port may not edit `ci.yml`; (b) does `com.android.application` resolve a `jvm()` KMP variant; (c) which Room release is KMP and what Kotlin/KSP/AGP it needs; (d) the pin matrix for OQ-17; (e) how migration tests can run in CI (Robolectric, or a JVM-hosted KMP Room test). | Written findings; no code merged. | `CI (hosted VM)`; 0.5 |
| C1 | Convert the ten pure modules to `kotlin("multiplatform")` with `jvm()` only, mapping `src/main/kotlin` and `src/test/kotlin` with `srcDir` so no source file moves (archery history and the "do not modify archery source" guardrail hold). Order: core-model, core-body, core-scoring, core-athlete, core-equipment, core-wellness, core-coach, core-exchange, archery-module; `:engine` is C1e. Replace the five JVM-only sites in §2 and add golden-text fixtures for rule-coach and fact strings. | `./gradlew test` on a bare JDK runs all 167 tests (not run for this plan); `ci.yml` and `android.yml` green; any `Math.toDegrees` numeric deviation logged; `PrivacyRegistryTest` still finds the DDL. | `CI (hosted VM)`; 1.5 |
| C1e | **Default: no port step edits `engine/` (build file or source) until Q5 is ruled.** Common code reaches engine types through a small interface in `jvmMain`/`androidMain` (the four files that name engine types: `domain/ArcheryAnalyzer.kt`, `domain/SessionViewModel.kt`, `ui/ReviewScreen.kt`, `data/Repository.kt` for `Rep`), so no common code names an engine type and `baseline-engine`'s coordinates and layout are untouched. iOS at G3 needs the alternative below or the licensed artefact. **Proposed alternative (C1e), executed only if Q5 rules that the in-repo engine stays open and published from this repo:** convert `:engine`'s build file to KMP with `jvm()` only, no source edit, and verify in the baseline repo's CI that `baseline-engine` still resolves. (A KMP publication is laid out differently from today's `components["java"]`, so compatibility for the baseline repo's consumption is unverified and is the check.) | Default: no diff under `engine/`. Alternative: the baseline repo's own CI still resolves `baseline-engine` (verified there, not here). No Baseline code moved in either direction. | `CI (hosted VM)`; within C1 |
| C2 | Room schema evidence. `exportSchema = true` and `room.schemaLocation` already point at `app-android/schemas`, but that directory is not tracked. Commit the v8 schema; reconstruct v1–v7 by building historical commits in CI (unverified that history yields them); add a test per `MIGRATION_n_n+1` and one that opens each historical version. Useful on Android whatever happens to the port. | Schema JSON for every version and migration tests green. If C0(e) finds no CI mechanism, this stays `NEEDS-DEVICE-VALIDATION`. | `CI (hosted VM)`; 0.5 |
| C3 | Create `app-common/`. Move screens, `ui/theme`, `ui/components`, the 14 ViewModels, repositories, Room and DataStore out of `app-android`. Replace `AndroidViewModel(Application)` with a constructor-injected `AppEnvironment` (database, prefs, `KeyVault`, `Vault`, `KeyProvider`, haptics, file open/save/share, clock and paths, pose source). The `OnBackPressedDispatcher` use in `MainActivity.kt` and any `BackHandler` guards get a common back-handling seam (which multiplatform API exists at the chosen Compose pin is unverified). `app-android` keeps `Application`, `MainActivity`, manifest, CameraX/MediaPipe capture, Tink actuals, SAF/`FileProvider`, `OnDeviceLlmClient`. Same PR: repoint `PrivacyRegistryTest` to the new DDL path and make it fail, not skip, when no path exists; add the F5 `android.*` ban for common and jvm source sets. The Android migration chain keeps its SQL text unchanged. | Zero `android.*` imports in common code; `:app-common:jvmTest` passes on a bare JDK; `android.yml` `assembleDebug` green. Behaviour unchanged on a device is **NEEDS-OWNER-VALIDATION**. | `CI (hosted VM)`; 2.5 |

**Linux desktop (G0, G1; capture G2).** 10 weeks in total including the C-steps above.

| Step | Work and placement | Done when | Evidence, effort |
|---|---|---|---|
| L1 | `desktop/app-desktop/`: window, back handling (the shell already draws an on-screen back arrow for windows with no system back gesture; desktop adds Escape), open/save dialogs for `.crocbak`, "save" and "open with" in place of the share sheet, haptics no-op, `HttpJson` unchanged. Secrets: Tink's pure-Java keyset with a master key held through libsecret when present, else a passphrase file; **the UI reports the tier** and the weaker guarantee (Q8). On-device LLM: absent and labelled "not available on this platform" (a stub that says so); rule coach and BYOK only. | Starts on X11/XWayland with no Wayland, Vulkan, camera or Bluetooth; capture UI hidden or labelled unsupported. | `CI (hosted VM)`; NDV on the Steam Deck (Desktop and Gaming Mode), the Dell only if OQ-5 says Linux, RedMagic under Termux:X11; 0.5 |
| L2 | `desktop-linux.yml` (path filters `desktop/**`, `app-common/**`, `core-*/**`, `engine/**`, `archery-module/**`): `jvmTest` and head compile on `ubuntu-24.04`; `createDistributable` on `main` and tags only. | Green hosted run; no per-platform identifier in any manifest (R11). | `CI (hosted VM)`; 0.5 with L3 |
| L3 | `packaging/linux/` from F10: jpackage app-image tarball with `install.sh`, `.deb`, and a Flatpak manifest consuming the prebuilt app-image (Flathub builds offline). No `--device=all` until L4 ships capture. BYOK and model download need network, so the listing states the allowlist (Q6). No in-app updater (proposed, Q6). Native libraries (if any) built on `ubuntu-22.04` for the glibc baseline. | Package builds in CI; install and launch are NDV. | `CI (hosted VM)`; see L2 |
| L4 (G2) | Capture and pose. (a) A `PoseProvider` interface in common code returning `PoseFrame` in the 33-index, unmirrored, image-normalised contract with an explicit `fps`. (b) A non-MediaPipe provider; default candidate ONNX Runtime on the JVM with a redistributable model (licence check) mapped onto the eleven consumed indices (a COCO-17-style output contains all eleven; confirm against the chosen model). (c) Video-file import first (lowest risk, no camera permission), then webcam capture via V4L2 (webcam-capture or JavaCV) inside `desktop/`. (d) Golden fixtures: `PoseSequence` JSON traces with expected segmentation and feature outputs (landmarks only, no pixels, committable, marked `-text`), plus a few consented or synthetic clips for estimator parity (raw media in a public repo: Q7). (e) Parity tolerances are measured against the Android MediaPipe output on the same clips and published as a distribution; this plan invents no number. Mirror trap: webcam previews are often mirrored, but the estimator must see the unmirrored frame because handedness is normalised once, in `HandednessNormalizer`. Frame-rate trap: segmentation uses `PoseSequence.fps`, so webcam pacing belongs in the parity test. `visibility` is carried but never thresholded in archery-module today. A MediaPipe Python sidecar (`Personal-Tracker/porting/platforms/linux.md`, `windows.md`) keeps BlazePose parity but is not assumed here (Python runtime, process lifecycle, antivirus false positives); it is an option inside Q7. | `PosePipelineTest` and `FeatureInvarianceTest` green on the new provider's output; parity report exists. Until then the capture UI says "experimental: not validated against the Android reference" (our heuristic) and shows no form metric as advice. | `CONTAINER-BUILD-ONLY` for a model smoke at most; webcam is NDV; 4.0 |

**macOS (G4).** 3 weeks.

| Step | Work and placement | Done when | Evidence, effort |
|---|---|---|---|
| M1 | `desktop-macos.yml` on `macos-latest`: `jvmTest` of cores and `app-common` on macOS first (R4), then `createDistributable` and `.dmg` on `main` and tags, unsigned. | Green hosted run. | `CI (hosted VM)`; 1.0 |
| M2 | `apple/macos-helper/`: a Swift helper holding a data-protection-Keychain or CryptoKit-wrapped file key (never the `security` CLI into the login keychain), reached through the same F6 actual as Linux; reports its tier. | Compiles in CI; behaviour NOV (no Mac on record). | `CI (hosted VM)`, NOV; 1.0 |
| M3 | `packaging/macos/`: entitlements (hardened runtime, minimum set; `allow-jit` only if HotSpot needs it, per asom's S-M3 self-test), `Info.plist` with a camera usage string only when capture ships, `notarize.sh` as a **disabled template** (R6, OQ-3); Homebrew cask recorded as an option (OQ-4). | Templates exist; nothing signed or submitted. | `PLAN`; `CI (hosted VM)` once the templates compile; 0.5 |
| M4 | Capture reuses L4's provider with AVFoundation access through JavaCV, or Apple Vision inside the Swift helper with an explicit 19-joint to 33-index map in common code (Q7). "Designed for iPad" is a zero-cost presence once iOS exists. | Same parity gate as L4. | NOV; 0.5 |

**Windows (G4).** 3 weeks.

| Step | Work and placement | Done when | Evidence, effort |
|---|---|---|---|
| W0 | Before any Windows lane: the R3 path lint (no colon, angle brackets, pipe, question mark, asterisk or quote, and no reserved device names, in tracked paths) and `.gitattributes` `-text` on byte-exact fixtures. The repo has no `.gitattributes` today; `gradle.properties` already pins UTF-8. | Lint green. | `CI (hosted VM)`; 0.25 |
| W1 | `desktop-windows.yml` on `windows-2025`: `jvmTest` first, watching charset and CRLF traps (the `.crocbak` writer, rule-coach text, `Readiness.kt`'s default-locale format), then jpackage WiX MSI on `main` and tags, unsigned; winget manifest as a disabled template. | Green hosted run. | `CI (hosted VM)`; 1.25 |
| W2 | DPAPI or Credential Manager actual through JNA behind F6, user scope, tier reported. | Compiles; behaviour NDV. | NDV; 1.0 |
| W3 | Webcam via Media Foundation (JavaCV) and ONNX Runtime win-x64. Windows arm64 is secondary: tests and app-image only on `windows-11-arm`. | Same parity gate as L4. | NDV; 0.5 |

**iOS / iPadOS (G3, Phase 18).** 14 weeks; iPadOS first, since the iPad Pro M4 is the only Apple device on record.

| Step | Work and placement | Done when | Evidence, effort |
|---|---|---|---|
| I1 | Add `iosArm64` and `iosSimulatorArm64` to the cores and `app-common` (the JVM-only sites are already gone after C1, so this should be a target addition; unverified). `ios.yml` on `macos-latest` runs `iosSimulatorArm64Test` (R4). | Simulator tests green. | `SIMULATOR`; 1.0 |
| I2 | Persistence: Room KMP with the bundled SQLite driver, and DataStore, on iOS; migrations adapted to the KMP API with SQL text unchanged; `isExcludedFromBackup` on the database and vault so iCloud Backup is not a silent egress, which makes `.crocbak` the recovery path (default for Q3 to confirm). | Every historical schema opens and migrates in the simulator. | `SIMULATOR`; 2.0 |
| I3 | Security: Keychain (`WhenUnlockedThisDeviceOnly`, never synchronisable) plus CryptoKit through a small Swift shim behind `KeyVault`, `Vault` and `KeyProvider`; Secure Enclave for the EC P-256 identity where supported. Check `core-exchange` tiers for whether any export carries vault ciphertext (then Tink's streaming-AEAD wire format must be readable on iOS); documents are class MEDICAL (`Privacy.kt:56`). Unverified. | Round-trip fixtures between JVM and iOS pass. | `SIMULATOR`, NDV; 2.0 |
| I4 | Capture: AVFoundation into MediaPipe iOS PoseLandmarker (pod, same `.task` model, BlazePose parity) by default; Apple Vision (19 joints) only as a fallback with the explicit map and the parity gate. Fixtures from L4 run on the simulator for the pure parts. | Parity report on the iPad. | `SIMULATOR`; `NEEDS-DEVICE-VALIDATION` on the iPad; 3.5 |
| I5 | Export and network: `UIDocumentPicker` and `UIActivityViewController`; a zip reader/writer that round-trips `.crocbak` with the JVM writer (cross-platform golden archives); Ktor for BYOK with logging off and the same allowlist; consent copy before personal data reaches a third-party model (App Review 5.1.2(i)); MediaPipe LLM Inference iOS or no on-device model in the first cut; `UIImpactFeedbackGenerator` haptics. | Archives interchange both ways. | `SIMULATOR`; 1.5 |
| I6 | `apple/`: XcodeGen `project.yml`, SwiftUI host with Compose iOS (or SwiftUI screens if Q3 rules so), `PrivacyInfo.xcprivacy` declaring no tracking, `CODE_SIGNING_ALLOWED=NO` on PRs, unsigned `.xcarchive`, no identifier before a `NAMES.md` row. Listing copy mirrors `docs/store/play-console.md` health answers (all "No") with no medical claims. TestFlight's automatic tester crash-report sharing is disclosed and accepted by the owner first (OQ-2). | Unsigned archive builds in CI. | `CI (hosted VM)`; 2.5 |
| I7 | Validation: golden fixtures on iOS, `DEVICE_CHECKLIST_IOS.md` (F11); iPhone items stay NDV until a device exists. | Checklist exists; nothing claimed as working. | NDV; 1.5 |

**Ubuntu Touch (G5).** 10 weeks, reframed; the S-UT1 spike and the F7 scaffold are separate program items.

| Step | Work and placement | Done when | Evidence, effort |
|---|---|---|---|
| T1 | `ubuntu-touch/`: QML app on Lomiri.Components with tokens from F1's `Tokens.qml`: scoring (round packs, keypad parser, target plot), calendar and wellness log, rig and tuning math. No capture, so no `camera` policy group; no keys, no BYOK and no network group in the first cut; `content_exchange` only. Colour is never the only signal. | Screens render in CI-approximated runs. | `CI-APPROX — NOT DEVICE EVIDENCE`; 5.0 |
| T2 | Core hosting. Default: F7's bundled jlinked headless JVM core behind its C++ bridge, reusing the existing JVM modules (no KMP conversion needed; gated on the S-UT1 verdict). Fallback: Kotlin/Native `linuxArm64` (Tier 2) `.so` with a C API, which needs C1's `java.time` swap. R4: cores tested on `ubuntu-24.04-arm` before any QML. | Core tests green on aarch64. | `CI (hosted VM)`; 2.0 |
| T3 | Read-only import preview of `.crocbak`/`.croc` through content-hub, showing consent and privacy-class labels; export of an unsigned `.crocbak` only once the format is frozen. No key is stored on UT in this cut (I-2, OQ-22). | Preview matches the JVM manifest parser on fixtures. | `CI-APPROX — NOT DEVICE EVIDENCE`; 1.0 |
| T4 | `ubuntu-touch.yml`: Clickable in the digest-pinned CI image on `ubuntu-latest`, click-review, a 26.04 canary lane that does not gate; UNSIGNED; no click package name before a `NAMES.md` row (R11). | Click builds and passes review in CI. | `CI (hosted VM)`; 1.0 |
| T5 | Labelling and evidence: proposed README line "Crocodyl Scorer & Viewer for Ubuntu Touch is a reframed companion, not a port of the Android app; form capture is not supported" (R12), repeated in the UI; `DEVICE_CHECKLIST_UT.md`; every device gate NDV. Qt 5.15 is end-of-road, so a Qt 6 rebuild is expected later. | Labels present. | `PLAN`; `NEEDS-DEVICE-VALIDATION`; 1.0 |

Recorded alternatives, outside the 10 weeks: a read-only Morph/webapp-container click over the Phase 9 viewer (about 2
weeks once Phase 9 exists, per the profile's estimate; the container-engine and WasmGC questions apply if the viewer is
Kotlin/Wasm; unverified), and Waydroid on a UT device (OQ-21), which is owner-device evidence only and could delete this
row. This container compiles JVM x86_64 only; Swift, Xcode, Clickable and a device are all absent (program §9).

## 7. Shared foundation this repo consumes or provides

**Consumes** (program §6):

| Item | What Crocodyl needs from it |
|---|---|
| F1 hyle-kmp | Tokens as a KMP artefact and `Tokens.qml`. `ui/theme/Theme.kt` is the consumer debt the constellation registry already records. Until F1 ships, the ported constants move into `app-common` unchanged (Q11). The shape-or-word check for meaning colours applies to every new surface. |
| F5 kmp-conventions | The shared version catalog at the OQ-17 pin, the `android.*` ban for common and jvm source sets, conditional Android inclusion, jpackage and XcodeGen head conventions. SPDX headers are opt-in per repo; this repo has none and gains none, because no licence is chosen (rule 11). |
| F6 platform-ports | Secure key storage with a reported tier, app directories, file picker and share, haptics: exactly Crocodyl's `KeyVault`, `Vault`, `KeyProvider`, export and `HyleHaptics` seams. Blocked on OQ-22. |
| F7 ubuntu-touch-shell | The default T2 path, the click scaffold and templates, and the single S-UT1 verdict. |
| F9 CI matrix template, F10 packaging templates | The shape of `desktop-<os>.yml`, `ios.yml`, `ubuntu-touch.yml` and `packaging/*`; the Windows path lint; the Flathub offline rule. |
| F11 evidence and device checklists | `DEVICE_CHECKLIST_{UT,LINUX,IOS,MACOS,WINDOWS}.md` and the evidence record. |
| F2, F4, F8 (optional) | crash-recovery as KMP only if the constellation adopts it here (the registry places it on a feature branch, not on `main`); an asom client as an optional `LlmClient` for local models, unusable off-Android until asom's M2; the llama.cpp pin only if desktop later gets a local LLM. No step depends on any of the three. F3 and F12 are not consumed. |

**Provides.** `:engine` is the Baseline seam, not a Crocodyl deliverable for others: publication of Baseline as a KMP
library or XCFramework belongs to the baseline repo's own plan, and this repo only keeps the seam stable (rule 3).
Proposed, and not an F-item in the master program: the `PoseProvider` seam and the golden pose fixtures from L4, offered
for reuse if another camera app joins the constellation (none in master §5 needs one today). The Phase 9 viewer's
technology is this repo's own call; if it picks Kotlin/Wasm, `core-exchange` and `core-scoring` could compile to it
(blueprint 02 §6 anticipates "portable parsers/analytics to JS/Wasm"), so C1 adds no JVM-only code to them.

## 8. Open questions for the owner

Twelve numbered questions, deduplicated from the reader profile; master ids are shown where they exist.

1. **Desktop product shape (master OQ-11).** Is a native Linux/macOS/Windows Crocodyl the athlete capture instrument
   (a desktop camera, a non-MediaPipe estimator and parity fixtures) or the Coach workspace host plus scorer, reviewer and exporter without
   capture (master OQ-11's wording)? Does it sit beside or replace Phase 9's static viewer (Proposal 2)? *Blocks* G1, L4, M4, W3; decides whether
   Linux is about 10 or about 6 weeks.
2. **Sequencing and the KMP start (master OQ-17, OQ-28).** Does the program amend the phased plan's ladder (Phase 0
   truth, Phase 1 contracts, iOS at Phase 18 after 1.0.0), or do ports wait for it? May C0–C2 (Proposal 1; the
   blueprint's open decision "KMP migration") start now? Which toolchain pin applies to this repo? *Blocks* G0 and every
   C-step.
3. **iOS shape and Apple access (master OQ-2, OQ-25).** Compose Multiplatform with native actuals, or a SwiftUI rewrite
   of about 14k lines to satisfy "native iOS athlete client"? Who provides the Apple Developer account, and is the
   bundle id decided together with the deferred `xyz.mdhv.crocodyl` rebrand? Is excluding the database from iCloud Backup
   (I2) acceptable? *Blocks* G3 and I1–I7.
4. **Ubuntu Touch shape (master OQ-1, OQ-21, OQ-6).** A reframed Lomiri scorer/viewer, a PWA in the webapp container
   only, Waydroid, or skip? Is a capture-less Crocodyl a product you want on any platform? *Blocks* G5 and T1–T5.
5. **Licence and the engine's status (master OQ-12).** No `LICENSE` exists (D-I). `docs/store/play-console.md` says the
   app vendors the proprietary Baseline engine as source; STATUS §1 and the blueprint say Baseline is open and
   entitlement-gated; PT:D-S says proprietary and licensed as a binary. Which is true for `:engine`, and should its
   artefact still be called `baseline-engine`? *Blocks* C1e, any public desktop or iOS binary, F-Droid, Flathub and App
   Store listings, and the SignPath route.
6. **Network posture.** The privacy policy and Play sheet say there is no INTERNET permission; the manifest grants it
   for BYOK. Which is intended, and whether ports may carry no in-app update check at all (the blueprint allows store update checks; the program briefs forbid them)? Ports inherit the answer, and the Flatpak permissions, the iOS privacy manifest and the
   store copy must state the allowlist. *Blocks* L3, I6, listing copy, and the correction of the two store documents.
7. **Pose-estimator policy.** Must every platform use MediaPipe BlazePose for parity, or are an ONNX COCO-17-style
   model and Apple Vision acceptable on desktop and Apple platforms after a measured parity report? Is a MediaPipe
   Python sidecar acceptable? May consented clips live in a public repo, or only landmark traces and synthetic clips?
   *Blocks* G2, L4, M4, W3, I4.
8. **Secure-storage tiers (master OQ-22).** Is a software-wrapped Tink keyset (libsecret, DPAPI or a passphrase file)
   acceptable for BYOK keys, the document vault and the export identity key, with the weaker tier shown in the UI? On
   Ubuntu Touch the first cut stores no key at all. *Blocks* L1, M2, W2, I3 and F6.
9. **Coach entitlement off Play.** Purchase channel and grace policy for the signed local licence file (phased plan §36:
   before Phase 8). *Blocks* any gated Coach surface on a port; none is planned before Phase 8.
10. **Signing budget and channels (master OQ-3, OQ-4, OQ-5).** Apple notarisation and Developer ID, a Windows signing
    route, and whether Flathub, Snap, App Store or Microsoft Store listings are wanted or GitHub Releases suffice. Is a
    Mac available for macOS device gates? *Blocks* M3, the signing templates in W1, and F10.
11. **Hyle.** Migrate `Theme.kt` to the published `dev.aarso:hyle` before C3, or carry the ported constants into
    `app-common` until F1 ships a KMP artefact? *Blocks* C3's theme move.
12. **Room schema export.** `exportSchema = true` is already set but no schema files are committed. Confirm that C2
    (commit v8, rebuild v1–v7 from history, add migration tests) is in scope as the precondition for moving Room.
    *Blocks* C2, C3 and I2.

Master questions this plan leans on without restating them: OQ-1 (UT device), OQ-3 and OQ-4 (signing, channels), OQ-5
(hardware), OQ-17 (pins), OQ-20 (this repo is public, so hosted runners on all five operating systems are free, but
Actions artefact storage is exhausted, hence R6), OQ-24 (sharing non-Gradle artefacts) and OQ-25 (identifiers).

## 9. Sources read

Repo files named in the reader profile: `README.md`, `CROCODYL_STATUS.md`, `CROCODYL_BUILD_NOTES.md`,
`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`, the `build.gradle.kts` of `engine`,
`archery-module` and every `core-*`, `app-android/build.gradle.kts`, `app-android/README.md`,
`app-android/src/main/AndroidManifest.xml`, and under `app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/`:
`MainActivity.kt`, `capture/PoseRecorder.kt`, `ui/CaptureScreen.kt`, `ai/OnDeviceLlmClient.kt`, `ai/KeyVault.kt`,
`ai/providers/HttpJson.kt`, `vault/Vault.kt`, `exchange/AndroidKeyProvider.kt`, `domain/ExportViewModel.kt`,
`data/AppPrefs.kt`, `data/AppDatabase.kt`, `ui/theme/Theme.kt`, `ui/theme/HyleHaptics.kt`; plus
`archery-module/.../pose/PoseFrame.kt`, `core-exchange/.../Identity.kt`, `core-scoring/.../RoundPack.kt` and
`ScoringModel.kt`, `core-athlete/.../AthleteModel.kt`, `docs/architecture.md`, `docs/naming.md`,
`docs/crocodyl/blueprint/01-product-direction.md`, `docs/crocodyl/blueprint/02-architecture-requirements-roadmap-ux.md`,
`docs/crocodyl/CROCODYL_PHASED_IMPLEMENTATION_PLAN.md`, `docs/biomechanics/METRIC_REGISTRY.md`,
`docs/biomechanics/CALIBRATED_2D_PROTOCOL.md`, `docs/store/play-console.md`, `docs/store/privacy-policy.md`,
`fastlane/metadata/android/en-US/full_description.txt`, and the five files in `.github/workflows/`.

Also read for this plan: `engine/build.gradle.kts` (publication coordinates), `core-wellness/.../Privacy.kt` and
`PrivacyRegistryTest.kt`, `core-wellness/.../Readiness.kt`, `core-coach/.../RuleCoach.kt` and `CoachFacts.kt`,
`core-equipment/.../TuningValidation.kt`, `archery-module/.../pose/Geometry.kt` and `HandednessNormalizer.kt`,
`domain/ArcheryAnalyzer.kt`, `domain/SessionViewModel.kt`, `ui/ReviewScreen.kt`, `data/Repository.kt`,
`gradle/wrapper/gradle-wrapper.properties`. Outside this repo: `Personal-Tracker/PORTING_PROGRAM.md`,
`Personal-Tracker/DECISIONS.md` (D-S), `Personal-Tracker/CONSTELLATION.md`, and
`Personal-Tracker/porting/platforms/{ubuntu-touch,linux,ios,macos,windows,framework-strategy}.md`.

Where measurement differs from the reader profile, this plan follows the measurement: Room `exportSchema` is `true`
(the profile says off; the schema directory is simply not tracked); there are 14 ViewModel classes (profile: 17); the
pure modules are not all "`kotlin.math` only" (the five JVM-only sites in §2); and `CROCODYL_STATUS.md`'s v0.5.1 header
is stale against `versionName` 0.6.0-spec-dev.
