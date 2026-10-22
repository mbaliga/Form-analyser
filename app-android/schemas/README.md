# Room schema snapshots

Room writes one JSON schema snapshot per database version here during an Android build. Commit the
generated `*.json` files with every entity or migration change so migration review and
`MigrationTestHelper` fixtures remain reproducible. Do not hand-edit snapshots; generate them with
the Android Gradle build first.
