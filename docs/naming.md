# Naming

- **Product name (user-facing): Crocodyl.** This is the app's display name — the launcher
  label (`app-android/src/main/res/values/strings.xml` → `app_name`) and the in-app title.
- **Codename / repo: `form-analyser`.** Carried over from the build handoff, where "Form
  Analyser" was always the working codename. The repo, Gradle module names, and the Kotlin
  package namespace (`xyz.mdhv.formanalyser`) and `applicationId` (`xyz.mdhv.formanalyser`)
  are unchanged for now.

## Deferred: full rebrand

A full rename to Crocodyl — GitHub repo → `crocodyl`, `applicationId` → `xyz.mdhv.crocodyl`,
package refactor — is **deliberately deferred until just before the Play Store listing**, because:

- **`applicationId` is permanent once published.** Changing it after release means a new app
  (users can't upgrade across it). Decide it deliberately at publish time, not mid-build.
- Repo rename changes remotes / PR URLs; a package refactor touches every file. No reason to
  take that churn while the app is still pre-release.

Until then: Crocodyl is what the user sees; `form-analyser` is what the tooling sees.
