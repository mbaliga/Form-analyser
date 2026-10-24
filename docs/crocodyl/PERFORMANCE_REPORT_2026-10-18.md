# Crocodyl beta performance report — 2026-10-18

This is a local synthetic JVM diagnostic, not a device-independent performance guarantee or a
production acceptance result. The measured library sources are from Shared-Libraries-asoc commit
`da50c62dd93bb67d590584a788eb794a6b7b55ec`, the revision pinned in `shared-libraries.lock`.

The exact checkout was verified first. The beta runner built a temporary pure-JVM composite
containing only the unchanged `performance-analysis`, `local-session-core`, and `search-core` module
sources from that commit. The full Shared-Libraries root also configures Android/UI modules at AGP
8.9.1, whose unrelated plugin graph is not covered by Crocodyl's current dependency lock; no
verification bypass was used. The report task completed successfully through the beta runner.

| Measurement | Warmup | Samples | p50 | p95 | p99 | Throughput |
|---|---:|---:|---:|---:|---:|---:|
| `shared-search.rank` | 5 | 30 | 2.265 ms | 3.568 ms | 5.446 ms | 397.0/s |
| `shared-session.event` | 5 | 30 | 2.791 µs | 9.708 µs | 28.709 µs | 232,861.4/s |

Machine-readable measurements are in [`PERFORMANCE_REPORT_2026-10-18.json`](PERFORMANCE_REPORT_2026-10-18.json).
They cover a synthetic 1,000-document search corpus and in-memory local-session events only—not
camera analysis, persistence, UI rendering, thermal behavior, device battery, network transport or
two-device operation. Re-run on target beta hardware before comparing performance across devices.
