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
  build/reports/beta/performance-report.json
```

The report measures the shared search ranking path and local-session event path, then writes JSON
with the shared-library revision. Share that JSON with the Crocodyl task; it is evidence, not a
device-independent performance guarantee.
