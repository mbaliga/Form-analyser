# Crocodyl offline file viewer

This static viewer is intended for a coach using a desktop browser without an Android device. Open
`index.html` locally, then choose or drop a Crocodyl export received through WhatsApp, a USB drive,
Bluetooth, LocalSend or another file-transfer method.

## Supported files

- `.crocbak` schema v1 archives produced by the current Android export flow.
- `.croc` is accepted as a filename extension for forward compatibility, but the signed `.croc`
  person-to-person envelope is not implemented yet. The viewer currently expects the same ZIP
  `manifest.json` shape as `.crocbak`.

The viewer parses the archive in the browser, shows manifest/table metadata, and does not upload,
persist, or import records. It deliberately does not claim signature verification or full report
rendering yet; those belong to the signed `.croc` implementation.

For a quick local preview, serve this directory with any static file server or open `index.html` in
a modern browser supporting `DecompressionStream`.
