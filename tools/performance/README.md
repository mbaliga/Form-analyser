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

The runner verifies the checkout against `shared-libraries.lock`, checks that any requested revision
matches the checkout's actual HEAD, and resolves relative output paths from the Crocodyl repository
root. It measures the shared search ranking path and local-session event path, then writes JSON
with the verified shared-library revision. Share that JSON with the Crocodyl task; it is evidence,
not a device-independent performance guarantee.
