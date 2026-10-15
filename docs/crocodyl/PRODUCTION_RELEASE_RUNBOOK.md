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

Never commit the keystore, passwords, or fingerprint-bearing private key material. The release
workflow validates all five inputs before materializing anything, then runs the unsigned unit,
lint, source-contract, and debug-smoke lanes before writing the keystore into the ephemeral CI
workspace with owner-only permissions. The keystore remains only for signed release assembly and
instrumentation, is removed before the remaining unsigned checks, and is also covered by an
`always()` cleanup step. The workflow fails closed if the
signing inputs or expected certificate fingerprint are absent.

## Trigger and verify

1. Before releasing, compare the actual previous production APK's versionCode with the project's
   highest issued code and set `tools/release/highest_issued_version_code.txt` to the greater value.
   The latest public GitHub Release is currently `v0.5.1-reconciled`, whose tag's Gradle config sets
   versionCode 4; this source metadata has not been confirmed against the APK bytes or an installed
   production copy. The ledger currently records 697, the latest v697 QA build. The resolver fails closed unless the new
   code is strictly higher. On a successful release, advance this ledger to the released code in
   the next release-preparation commit. A plain semver branch derives `major*10000 + minor*100 +
   patch` (minor and patch must each be 0–99); derived values below the ledger are rejected. For
   example, `0.6.1` derives 601 and is rejected against the current 697 floor, so use workflow
   dispatch with an explicit code of at least 698 (and higher if the previous production APK
   requires it). Prerelease/build labels also require an explicit versionCode.
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

All third-party workflow actions are pinned to reviewed upstream commit SHAs. The release and CI
jobs run `tools/release/verify_workflow_actions.py`, which fails closed if a future edit uses a
mutable tag or branch. Dependabot is configured for weekly GitHub Actions updates in
`.github/dependabot.yml`; every proposed SHA change remains reviewable before release.
