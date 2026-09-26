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
an exact match between manifest and table entries before rendering a bounded read-only table
preview, rejects oversized/unsafe ZIP structures, and does not upload, persist, or import records.
The local limits
are 256 MiB per input, 1,024 ZIP entries, 64 MiB per entry, and 128 MiB total expanded payload.
The viewer verifies the `.croc` producer signature locally with WebCrypto and fails closed on an
invalid or incomplete signature. It remains a bounded read-only table preview rather than a full
report/analysis renderer.

For a quick local preview, serve this directory with any static file server or open `index.html` in
a modern browser supporting `DecompressionStream`.
