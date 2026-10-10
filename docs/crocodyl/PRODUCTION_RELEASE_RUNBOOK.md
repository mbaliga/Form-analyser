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

Never commit the keystore, passwords, or fingerprint-bearing private key material. The release
workflow validates all five inputs before materializing anything, then writes the keystore only
into the ephemeral CI workspace with owner-only permissions,
removes it in an `always()` cleanup step after artifact publication, and fails closed if the
signing inputs or expected certificate fingerprint are absent.

## Trigger and verify

1. Create a release branch named `release/v<major>.<minor>.<patch>` or use the workflow dispatch
   input. Use a new, monotonically increasing Android `versionCode`; the workflow derives one only
   for plain `major.minor.patch` releases (minor and patch must each be 0–99 so the mapping remains
   unique). Prerelease/build labels (for example, `-rc.1` or `+build`) require an explicit code so
   they cannot collide with the corresponding stable release. An explicitly supplied positive code
   takes precedence over derivation and is range-checked.
2. Require the release workflow to pass JVM tests, Android debug/release unit tests, release lint,
   debug connected smoke, signed minified release connected smoke, generated-asset drift checks,
   signed artifact preflight, and checksum publication.
   The workflow also runs the release-workflow contract tests, which fail if signing-material
   permissions/cleanup or the release verification steps are removed.
3. Confirm the preflight output verifies APK v2 signing, AAB signing, ZIP alignment, expected
   version metadata, cleartext/backup/FileProvider policy, BlazePose hash, backup resources, no
   staging files, and the expected signer fingerprint in both artifacts.
4. Download the APK/AAB and `*-SHA256SUMS.txt` from the GitHub Release and independently run:

```bash
sha256sum -c crocodyl-<version>-SHA256SUMS.txt
```

Do not distribute a QA-signed artifact, even if every functional check passes.

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
