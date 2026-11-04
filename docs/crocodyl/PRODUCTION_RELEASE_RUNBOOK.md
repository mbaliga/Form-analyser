# Crocodyl production release runbook

This runbook is for the current Recurve Android release only. It does not enable automatic Fencing
camera adjudication, live community-directory ingestion, Advanced Search/Baseline adapters, or the
Nooz news pipeline; those remain documented roadmap work. The manual Fencing referee console is a
separate next-target slice and is not a Recurve 0.6 release criterion.

When exercising the optional beta Local Multiplayer or shared-performance build, verify the local
Shared-Libraries-asoc checkout against `shared-libraries.lock` first:

```bash
python3 tools/shared/verify_shared_checkout.py /path/to/Shared-Libraries-asoc
```

Production builds remain self-contained and do not silently consume an unpinned shared checkout.

## Before triggering CI

Dependency changes are also checked on pull requests by the pinned Dependency Review workflow.
It fails when a PR introduces a known moderate-or-higher vulnerability in a dependency represented
in GitHub's dependency graph. This is a change-level gate, not a scan of every existing dependency;
confirm that the repository dependency graph is enabled and review any manifest the graph cannot
parse. While the graph is disabled the workflow probes it first and skips the review with a
`Dependency graph disabled` warning instead of failing; enabling it (Settings → Code security and
analysis) is an owner action, after which the review blocks moderate-or-higher findings with no
further change. See [GitHub dependency review](https://docs.github.com/en/pull-requests/how-tos/review-pull-requests/reviewing-dependency-changes-in-a-pull-request).

Configure these repository secrets from the production signing identity:

- `CROCODYL_RELEASE_KEYSTORE_BASE64` — base64-encoded production keystore.
- `CROCODYL_RELEASE_STORE_PASSWORD`
- `CROCODYL_RELEASE_KEY_ALIAS`
- `CROCODYL_RELEASE_KEY_PASSWORD`
- `CROCODYL_RELEASE_CERT_SHA256` — the signer certificate SHA-256 fingerprint, with or without
  colon separators. It must match both the APK and AAB signer.

The fingerprint can be derived from the production keystore before storing it as a secret:

```bash
keytool -list -v -keystore production.keystore | grep -i 'SHA256:'
```

The release workflow rejects the repository's committed QA/debug certificate fingerprint
(`36f33a33f9415fbe4f31556a3dde9bc010d03c502bb3dfe6b2bfaf0cbafdaebc`) before materializing the
keystore. A passing local preflight with that certificate is QA evidence only and can never serve
as production signing evidence.

### Release blocker: signer continuity for existing installs

The inspected public `v0.5.1-reconciled` APK is signed with the same certificate as the committed
`app-android/debug.keystore`. The production workflow correctly refuses to use that key, so choosing
a different production key will not by itself preserve in-place updates for existing installs.
Android accepts an update when the signing certificate matches or the new APK carries a valid
proof-of-rotation; the recorded v0.5.1→v701/v716 emulator upgrades reused the old QA/debug key and
do not prove that a new production key can update existing installs. See Android's [app update
rules](https://developer.android.com/google/play/app-updates) and [`apksigner` key-rotation
guidance](https://developer.android.com/tools/apksigner#rotate-keys).

Before release, the owner must select and verify a distribution-specific migration strategy: for
example, a confirmed Play App Signing upgrade path if this package is managed in Play, a security-
reviewed signing lineage for direct APK distribution, or a new application ID accompanied by an
explicit user-data migration plan. The old signing key is committed in a public repository, so it
must not be treated as a secret or silently promoted to production. Do not remove this gate based
only on a successful QA-signed update test. The release workflow enforces this through
`tools/release/signer_continuity_decision.json`; it remains `unresolved` until the owner records an
approved strategy, distribution channel, approval reference and verified migration evidence.

The owner has confirmed Google Play as the intended distribution channel and `com.asoc.crocodyl` as
the intended Play package ID. The current production application ID is configured to match. The
earlier public GitHub APK uses the different ID `xyz.mdhv.formanalyser`; Android treats these as
separate apps, so that APK cannot be updated in place by the Play package. Public listing searches
did not establish whether `com.asoc.crocodyl` is already enrolled in Play Console; private,
closed-testing, and unpublished listings are not publicly discoverable. Before choosing
`play-app-signing`, inspect the exact app in Play Console and record whether Play
App Signing is enabled, the app-signing certificate, upload-key state, and the available key-upgrade
path. If this is a new Play listing, verify that separately and make sure the upload key is not
mistaken for the app-signing key. A Play update path does not establish update continuity for APKs
previously installed from GitHub; retain a separate decision for direct APK distribution if it is
ever offered.

The signing job targets GitHub's `production` environment after unsigned tests pass and before any
production signing inputs are read. Store the five `CROCODYL_RELEASE_*` signing secrets in that
environment. In repository Settings → Environments, configure it with the owner/release reviewer
as a required reviewer, enable prevention of self-review when another reviewer is available, and
disable administrator bypass where available. The workflow's environment reference alone does not
create these protection rules; verify the settings in GitHub before treating owner approval as
enforced. The publication job remains isolated with `contents: write` and runs only after signed
artifact tests pass. GitHub documents that required reviewers pause a job until approval and that
self-review can be disabled: [deployment environment reviews](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/review-deployments).

Never commit the keystore, passwords, or fingerprint-bearing private key material. The release
workflow checks the expected certificate fingerprint only in the protected signing job, after the
unsigned unit, lint, source-contract and debug-smoke job succeeds and the production environment
review has passed. The unsigned verification job receives no production signing secrets. Passwords
and the keystore are checked only in the signing job; it writes the keystore into the ephemeral CI
workspace with owner-only permissions. The keystore is removed before the final unsigned launch
smoke and is also covered by an `always()` cleanup step. The workflow fails closed if any signing
input or the expected certificate fingerprint is absent.

## Channels, signing key and version source

Crocodyl ships one APK, built from one source tree with one application ID (`com.asoc.crocodyl`)
and no product flavours, to these channels in this order:

1. **GitHub Releases and Obtainium.** The release workflow publishes `crocodyl-<version>.apk`,
   `crocodyl-<version>.aab` and `crocodyl-<version>-SHA256SUMS.txt`. This APK is the source for the
   other channels.
2. **Google Play**, enrolled in Play App Signing by uploading key K (below), so Play delivers the same
   signature.
3. **IzzyOnDroid**, which pulls the same developer-signed APK.
4. **F-Droid**, reproducible and developer-signed only (`fdroid/metadata/com.asoc.crocodyl.yml`;
   see [`fdroid/README.md`](../../fdroid/README.md)). F-Droid never signs the app: it rebuilds the
   tag and publishes our APK only if the rebuild matches byte for byte.

**Key K.** One owner-generated signing key, held by the owner and stored as the `CROCODYL_RELEASE_*`
secrets in the `production` environment, signs every channel, so users can move between channels
without reinstalling. Its SHA-256 certificate fingerprint goes into `CROCODYL_RELEASE_CERT_SHA256` and
into `AllowedAPKSigningKeys` in the F-Droid recipe (quoted, lowercase, no colons; read it with
`apksigner verify --print-certs`). Generating K and approving
`tools/release/signer_continuity_decision.json` are owner decisions: the file lists the intended
channels and strategy but stays `unresolved` until the owner records the approval reference and the
verification evidence.

**Version source.** `app-android/version.properties` holds `versionCode` and `versionName`, because
F-Droid builds the tag without the workflow's `-PcrocodylVersionName/-PcrocodylVersionCode`
overrides. Between releases it carries a `-dev` name. The release commit sets it to the release,
for example `versionName=0.7.0` and `versionCode=700`, and the release is tagged `v<versionName>`
(`v0.7.0`). `tools/release/resolve_version.sh` now emits three values: `tag` (the name exactly as
given, used for the GitHub Release tag), `version` (the same name with one leading `v` removed when
it is semver; the Android versionName and the asset file names) and `version_code`. It fails with
"does not match app-android/version.properties; bump it in the release commit" when the file
disagrees with the version and code being released, so a release cannot be cut from a commit that
has not been bumped.

**Highest-issued ledger.** `tools/release/highest_issued_version_code.txt` stays at the highest code
already issued (697 now); the resolver requires the new code to be strictly greater. Raise it to 700
in the commit that follows the issued `v0.7.0` release, together with the `"697"` literal in
`test_release_workflow_contract.py`. Raising it inside the release commit itself would make the
resolver reject code 700.

**Rehearsal before the F-Droid merge request.** On a machine with Docker, in a checkout of
`https://gitlab.com/fdroid/fdroiddata` with the recipe copied to `metadata/com.asoc.crocodyl.yml` and
the real `AllowedAPKSigningKeys` filled in, build the tag in F-Droid's build image, then compare the
result with the released, signed APK:

```bash
docker run --rm -v "$PWD":/repo -w /repo registry.gitlab.com/fdroid/fdroidserver:buildserver \
  fdroid build --verbose --latest com.asoc.crocodyl
apksigcopier compare crocodyl-0.7.0.apk --unsigned unsigned/com.asoc.crocodyl_700.apk
```

`apksigcopier compare` must succeed, meaning the only difference is our signature. The `reproducible`
workflow checks the same property earlier, by building the unsigned release twice from separate
checkouts, and the `fdroid` workflow lints the recipe and runs F-Droid's source scanner on pull
requests.

## Trigger and verify

1. Before releasing, compare the actual previous production APK's versionCode with the project's
   highest issued code and set `tools/release/highest_issued_version_code.txt` to the greater value.
   The latest public GitHub Release is currently `v0.5.1-reconciled`; its actual APK asset was
   downloaded and inspected, confirming legacy package `xyz.mdhv.formanalyser`, versionCode 4, and SHA-256
   `84e21831c085905f2beedea704c3998b32e76c4ed84966c1e797ab006c2a440b`. It clean-installed and
   updated to QA v716 on the API-36/16 KB emulator. The production-issued ledger currently records
   697. Latest QA artifacts are versionCode 721 (`0.6.4-export-e2e-qa`); they are QA/debug-signed, have
   not been issued as production, and do not advance that ledger. The exact v721 APK/AAB pass local
   artifact preflight. Current-source connected debug and minified-release suites pass 20/20 on
   API-35 ARM64 and API-36 ARM64/16 KB emulators. These results are not evidence of production
   signing, hosted release-workflow execution, or physical-device acceptance; see
   [`PRODUCTION_STATUS.md`](PRODUCTION_STATUS.md) for artifact digests and replay scope. The resolver
   fails closed unless the new code is strictly higher. On a successful release, advance this
   ledger to the released code in the next release-preparation commit. A plain semver branch derives
   `major*10000 + minor*100 + patch` (minor and patch must each be 0–99); derived values below the
   ledger are rejected. For example, `0.6.1` derives 601 and is rejected against the current 697
   floor; workflow dispatch therefore needs an explicit code of at least 698, and higher if any
   production release newer than the inspected public APK exists. Confirm the actual installed or
   store-issued production version before release; a public GitHub Release may not be the full
   distribution history. Prerelease/build labels also require an explicit versionCode.
2. Require the release workflow to pass JVM tests, Android debug/release unit tests, release lint,
   Android 15 (API 35) signed minified-release smoke, debug connected smoke, signed minified-release
   smoke on standard and explicit 16 KB page-size API-36 emulators, generated-asset drift checks,
   signed artifact preflight, and checksum publication. The CI now retains connected-test XML/HTML
   reports and emulator logcat for debug and minified-release runs even when a smoke step fails;
   download those diagnostics before triaging an intermittent instrumentation failure.
   The workflow also runs the release-workflow contract tests, which fail if signing-material
   permissions/cleanup or the release verification steps are removed.
3. Confirm the preflight output verifies APK v2 signing, AAB signing, 16 KB APK ZIP alignment,
   `BundleConfig.pb` requesting uncompressed native libraries with `PAGE_ALIGNMENT_16K`, and 16 KB
   ELF load-segment alignment for every `arm64-v8a` and `x86_64` shared library in both artifacts,
   expected version metadata, cleartext/backup/FileProvider policy, the merged
   manifest's permission allowlist and exported-component contract, BlazePose hash, backup
   resources, no staging files, and the expected signer fingerprint in both artifacts. The
   manifest allowlist is intentionally exact; any new app/library permission or exported
   component requires review and an explicit, tested contract update. Android documents this
   bundle setting as the check for 16 KB ZIP alignment in Play-generated APKs: [16 KB page-size
   guidance](https://developer.android.com/guide/practices/page-sizes).
4. Download the APK/AAB and `*-SHA256SUMS.txt` from the GitHub Release and independently run:

```bash
sha256sum -c crocodyl-<version>-SHA256SUMS.txt
```

Do not distribute a QA-signed artifact, even if every functional check passes.

5. Download the `crocodyl-release-r8-mapping-<version>` Actions artifact from the same successful
   workflow run. Verify its `.sha256` file and preserve the mapping in the long-term release archive.
   GitHub says signed-in users with repository read access can download workflow artifacts, so this
   artifact is not confidential in this public repository. The mapping contains symbol/line
   mappings, not signing material. Artifact retention is limited to 90 days here; do not treat it as
   durable storage. Record the archive location alongside release acceptance. The public GitHub
   Release intentionally does not include the mapping file. See [GitHub's artifact download access
   rules](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts).

## Physical acceptance and approval

Before owner approval, complete one run record per device using
[`DEVICE_ACCEPTANCE_RUN_TEMPLATE.md`](DEVICE_ACCEPTANCE_RUN_TEMPLATE.md). The required matrix,
camera/range flows, privacy checks, recovery checks, 30-session volume, final-ten-session stability,
P0 disposition and distribution-channel approval are listed in
[`RELEASE_ACCEPTANCE_CHECKLIST.md`](RELEASE_ACCEPTANCE_CHECKLIST.md).

The release is not production-approved until the production-signed artifact, physical acceptance
records, owner approval and selected distribution channel are all recorded.

## CI supply-chain note

The `fdroid` and `reproducible` workflows are advisory gates for the F-Droid channel. The `fdroid`
workflow installs a pinned `fdroidserver` and reads only the `config/` vocabularies from a pinned
fdroiddata commit; bump `FDROIDDATA_REF` in `.github/workflows/fdroid.yml` deliberately. fdroidserver
ignores `--exit-code` for source scans, so the workflow reads the scanner's problem count itself.

All third-party workflow actions are pinned to reviewed upstream commit SHAs. The release and CI
jobs run `tools/release/verify_workflow_actions.py`, which fails closed if a future edit uses a
mutable tag or branch. Dependabot is configured for weekly GitHub Actions updates in
`.github/dependabot.yml`; every proposed SHA change remains reviewable before release.

Production and standalone JVM project dependencies use strict dependency locking in addition to the checked-in SHA-256
verification metadata. `gradle.lockfile` records the selected module versions; verification
metadata separately authenticates the downloaded artifacts. When intentionally changing a
dependency, update the resolved graph for both supported build shapes and review the lockfile diff:

```bash
./gradlew test --write-locks
./gradlew :app-android:testDebugUnitTest :app-android:testReleaseUnitTest \
  :app-android:lintRelease :app-android:compileDebugAndroidTestKotlin \
  -PwithAndroid --write-locks
./gradlew :app-android:connectedDebugAndroidTest -PwithAndroid --write-locks
```

The first command updates the self-contained JVM build. The second includes `app-android`, which is
opted into the settings only with `-PwithAndroid`. Use the repository's pinned JDK/SDK setup and
verification metadata when resolving new artifacts. Plugin versions remain explicitly pinned in
the root build and their artifacts are covered by Gradle dependency verification; Gradle's project
dependency locking does not lock plugin/buildscript classpaths.

The debug and minified release connected-test tasks are also part of the supported production
verification graph. For intentional lock regeneration of the minified test lane, provide a local
QA-only signing identity (never production release credentials) and run:

```bash
./gradlew :app-android:connectedReleaseAndroidTest -PwithAndroid \
  -PcrocodylInstrumentRelease=true --write-locks
```

Release assembly configurations such as `composeMappingProducerClasspath` are resolved by that
lane. Use a connected emulator for these commands; a successful compile/lint-only lock update is not
evidence that instrumentation configurations are locked.

The optional `performance-report` bridge is intentionally excluded from Gradle module locking: its
three declared coordinates are substituted at build time with unchanged source modules from the
revision pinned in `shared-libraries.lock`. `tools/performance/run_beta_report.sh` verifies that
checkout and builds an isolated composite under strict artifact checksum verification. Update that
pin and its verification metadata through the shared-library review flow rather than editing a
generated Crocodyl lockfile for the substituted coordinates.
