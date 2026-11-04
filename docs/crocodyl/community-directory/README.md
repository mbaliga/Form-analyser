# Community directory contribution format

The directory is intentionally data-first and open-source friendly. Add records to a future curated
dataset using `entry.schema.json`; do not paste unverified search results into the app bundle.

Required fields are stable `id`, `kind` (`coach`, `training_centre`, or `store`), display `name`,
country/region, at least one contact URL, `source`, `sourceUrl`, `lastVerifiedAt`, and `license`.
User contributions must identify themselves as `community` source records. Scraped/curated records
must preserve the canonical source URL and comply with the source site's terms and robots policy.

The Android app should eventually support local add/edit/report/delete, with moderation and sync kept
separate from the athlete's private training database. Location should be coarse by default; precise
coordinates require contributor consent.

Before records are bundled or imported, run the repository gate:

```bash
python3 -m unittest discover -s tools/community_directory -p 'test_*.py'
python3 tools/community_directory/validate_directory.py docs/crocodyl/community-directory/entries
```

The validator rejects unknown fields, duplicate ids, missing provenance/contact data, invalid URLs,
future verification dates, invalid coordinates and oversized text. An empty `entries/` directory is
valid while the curated dataset is still awaiting reviewed contributions.
