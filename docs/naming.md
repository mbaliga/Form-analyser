# Naming

- **Product name (user-facing): Crocodyl.** This is the app's display name — the launcher
  label (`app-android/src/main/res/values/strings.xml` → `app_name`) and the in-app title.
- **Codename / repo: `form-analyser`.** Carried over from the build handoff, where "Form
  Analyser" was always the working codename. The repo, Gradle module names and the Kotlin
  package namespace (`xyz.mdhv.formanalyser`) are unchanged for now.
- **applicationId: `com.asoc.crocodyl`.** Set in `app-android/build.gradle.kts`; one id on every
  channel, no suffixes, also reserved as the iOS bundle id (decision 6 of
  `docs/crocodyl/DECISIONS-2026-10.md`). It is **final pending Owner-only item 3** in that record
  (confirm it as permanent, and say whether a Play Console entry already exists under it). The
  Kotlin package stays `xyz.mdhv.formanalyser`; the two are independent, so no package refactor is
  needed for the id. The earlier `xyz.mdhv.formanalyser` applicationId belongs only to the seven
  pre-release, debug-signed GitHub releases, an abandoned lineage that cannot update in place to a
  `com.asoc.crocodyl` build.

## Deferred: repo rename and package refactor

A full rename to Crocodyl — GitHub repo → `crocodyl` and a Kotlin package refactor — remains
**deliberately deferred**. (The `applicationId` was settled separately, above; the
`xyz.mdhv.crocodyl` id this page used to plan is no longer the plan.) Reasons:

- **`applicationId` is permanent once published.** Changing it after release means a new app
  (users can't upgrade across it). That is why Owner-only item 3 must be answered before a Play
  track is opened or F-Droid metadata is merged.
- Repo rename changes remotes / PR URLs; a package refactor touches every file. No reason to
  take that churn while the app is still pre-release.

Until then: Crocodyl is what the user sees; `form-analyser` is what the tooling sees.
