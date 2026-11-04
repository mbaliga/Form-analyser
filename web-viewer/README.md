# Crocodyl offline file viewer

This static viewer is intended for a coach using a desktop browser without an Android device. Open
`index.html` locally, then choose or drop a Crocodyl export received through WhatsApp, a USB drive,
Bluetooth, LocalSend or another file-transfer method.

## Supported files

- `.croc` signed person-to-person archives produced by the current sharing flow. New envelopes
  carry a P-256 public key and ECDSA signature over the declared payload checksum plus the sorted
  table list and consent tier; legacy checksum-only signatures remain readable.
- `.crocbak` schema v1 recovery archives from earlier/current backup flows. These remain checksum-verified but are not required to
  carry a producer signature.

The viewer parses the archive in the browser, requires the manifest's SHA-256 payload checksum and
an exact match between manifest and table entries before rendering a local read-only report and
bounded table previews, rejects oversized/unsafe ZIP structures, and does not upload, persist, or
import records. The report summarizes included form sessions, scored shots, completed cumulative
rounds, recent-vs-prior round score rate when there are enough rounds, and latest-shot measurements
relative to available baseline samples. Set-match points are not treated as target-score percentages;
retracted rows are ignored. Baseline values are descriptive and are not presented as the app's
on-device stability score, a causal explanation, or a prediction. Missing tables remain missing and
are never interpreted as zeros.
The local limits
are 256 MiB per input, 1,024 ZIP entries, 64 MiB per entry, and 128 MiB total expanded payload.
The viewer verifies the `.croc` producer signature locally with WebCrypto and fails closed on an
invalid or incomplete signature. Table previews remain bounded; report summaries use validated
shareable rows already present in the archive. All processing stays in the browser.

For a quick local preview, serve this directory with any static file server or open `index.html` in
a modern browser supporting `DecompressionStream`.
