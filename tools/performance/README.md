# Crocodyl beta performance report

The beta report consumes the pinned pure-JVM modules from Shared-Libraries-asoc:

- `dev.aarso:performance-analysis:0.1.0`
- `dev.aarso:local-session-core:0.1.0`
- `dev.aarso:search-core:0.2.0`

Run it from the Crocodyl repository with a local checkout of Shared-Libraries-asoc:

```bash
export JAVA_HOME=/path/to/jdk-21
./tools/performance/run_beta_report.sh \
  /absolute/path/to/Shared-Libraries-asoc \
  performance-report/build/reports/beta/performance-report.json
```

The runner verifies the checkout against `shared-libraries.lock`, rejects local changes to the
measured modules/version catalog, checks that any requested revision matches the checkout's actual
HEAD, and resolves relative output paths from the Crocodyl repository root. It builds a temporary
composite containing only the unchanged `performance-analysis`, `local-session-core`, and
`search-core` modules, avoiding unrelated Android/UI plugin dependencies. The temporary copy is
removed after the run. It measures search ranking and local-session event paths, then writes JSON
with the verified source revision. Share that JSON with the Crocodyl task; it is evidence, not a
device-independent performance guarantee.
