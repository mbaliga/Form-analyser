# Crocodyl beta performance report — 2026-09-24

This is a beta benchmark, not a device-independent production guarantee. It was generated with:

```bash
JAVA_HOME=/private/tmp/crocodyl-jdk21/jdk-21.0.12.1+1/Contents/Home \
tools/performance/run_beta_report.sh \
  /Users/tulikapandey/Documents/Codex/2026-09-01/let-2/work/shared-libraries-asoc \
  build/reports/beta/performance-report.json
```

Shared-Libraries-asoc revision: `6b54b980448f4791f4b68c33347e3b92e522aacd`.

| Measurement | Warmup | Samples | p50 | p95 | p99 | Throughput |
|---|---:|---:|---:|---:|---:|---:|
| `shared-search.rank` | 5 | 30 | 2.198 ms | 3.151 ms | 4.054 ms | 425.1/s |
| `shared-session.event` | 5 | 30 | 2.209 µs | 8.500 µs | 16.500 µs | 317,742.8/s |

The complete machine-readable output is generated at
`performance-report/build/reports/beta/performance-report.json`; the build directory is
intentionally not a release source artifact. Re-run the command on the target beta environment and
attach that JSON when comparing devices or library revisions.

