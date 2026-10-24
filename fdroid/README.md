# F-Droid packaging

This directory holds the source of Crocodyl's F-Droid listing. Nothing here is submitted anywhere
yet, and it must not be until the owner has granted a licence (see [`LICENSING.md`](../LICENSING.md)),
generated the release signing key and approved the signer-continuity decision
(`tools/release/signer_continuity_decision.json`, currently `unresolved`).

- `metadata/com.asoc.crocodyl.yml` is the build recipe and listing text for the fdroiddata merge
  request. The file name is the application ID, which must never change once published.

## How the listing works

F-Droid rebuilds each tagged release from source and, because the recipe sets `Binaries` and
`AllowedAPKSigningKeys`, publishes **our** signed APK only if the rebuild is byte-identical once our
signature is copied onto it. F-Droid never signs the app itself, so users can move between GitHub
Releases, Obtainium, IzzyOnDroid and F-Droid without reinstalling.

- The build is `gradle: [yes]` with `gradleprops: [withAndroid=true]`, run in `app-android`;
  there are no flavours. It produces `app-android-release-unsigned.apk`.
- `versionName` and `versionCode` come from `app-android/version.properties`, which the release
  commit bumps; F-Droid builds the tag with none of CI's `-P` overrides.
- `Binaries` points at the GitHub Release asset `crocodyl-<versionName>.apk`, which is exactly what
  `.github/workflows/release.yml` publishes for tag `v<versionName>`.
- `UpdateCheckMode: Tags` follows `vX.Y.Z` tags and `UpdateCheckData` reads the version from
  `app-android/version.properties`.
- `AntiFeatures: [NonFreeNet]` is declared because the optional bring-your-own-key AI coach talks to a
  provider the user chooses. No other anti-feature is declared: there is no tracking, the model files are
  committed data, and nothing is downloaded at build or run time except by that opt-in coach.

## Before the first merge request (owner)

1. Grant the licence, then set `License:` to its SPDX identifier (the value in the file is a
   placeholder) and make sure the repository contains the licence text F-Droid looks for.
2. Release `v0.7.0` through the release workflow (it signs with the owner's key K and uploads
   `crocodyl-0.7.0.apk`).
3. Take K's certificate fingerprint from the released APK:

   ```bash
   apksigner verify --print-certs crocodyl-0.7.0.apk | grep 'certificate SHA-256 digest'
   ```

   Put the lowercase hex digest, without colons and **in quotes**, into `AllowedAPKSigningKeys`.
   Unquoted, a value made only of digits is read by YAML as a number and fdroidserver rejects it.
4. Rehearse the build in F-Droid's build image on the tag and compare it with the released APK
   (commands in [`docs/crocodyl/PRODUCTION_RELEASE_RUNBOOK.md`](../docs/crocodyl/PRODUCTION_RELEASE_RUNBOOK.md)).
5. Fork `https://gitlab.com/fdroid/fdroiddata`, copy `metadata/com.asoc.crocodyl.yml` into its
   `metadata/` directory, run `fdroid rewritemeta com.asoc.crocodyl` and `fdroid lint com.asoc.crocodyl`
   there (`rewritemeta` normalises the formatting and drops the comments), and open the merge request
   from your account.

When the fastlane listing text changes, update `Description` here by hand; it is a copy.

## CI

- `.github/workflows/fdroid.yml` lints this metadata with `fdroid lint` against the category and
  anti-feature vocabularies of the upstream fdroiddata checkout, and runs `fdroid scanner` over the
  source tree.
- `.github/workflows/reproducible.yml` builds the unsigned release twice from separate clean
  checkouts and compares the APKs byte for byte.
