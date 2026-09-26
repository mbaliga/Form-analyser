/* Local-only .croc signature verification. No network requests or third-party dependencies. */
async function verifyEnvelopeSignature(fileName, manifest) {
  const isCroc = fileName.toLowerCase().endsWith('.croc');
  const fields = [manifest.signatureAlgorithm, manifest.publicKeyBase64, manifest.signatureBase64];
  const hasAny = fields.some(value => value != null && value !== '');
  if (!isCroc && !hasAny) {
    return { state: 'verified', detail: 'Payload checksum verified; recovery archive is unsigned.' };
  }
  if (
    manifest.signatureAlgorithm !== 'SHA256withECDSA' ||
    typeof manifest.publicKeyBase64 !== 'string' ||
    typeof manifest.signatureBase64 !== 'string'
  ) {
    throw new Error('Signed .croc metadata is incomplete or unsupported');
  }
  if (!globalThis.crypto?.subtle) throw new Error('This browser cannot verify the .croc signature');
  const key = await crypto.subtle.importKey(
    'spki',
    base64Bytes(manifest.publicKeyBase64),
    { name: 'ECDSA', namedCurve: 'P-256' },
    false,
    ['verify'],
  );
  const valid = await crypto.subtle.verify(
    { name: 'ECDSA', hash: 'SHA-256' },
    key,
    derSignatureToRaw(base64Bytes(manifest.signatureBase64)),
    new TextEncoder().encode(manifest.contentChecksum),
  );
  if (!valid) throw new Error('Signature verification failed; the file was not opened');
  return { state: 'signed and verified', detail: 'Payload checksum and producer signature verified locally.' };
}

function base64Bytes(value) {
  const raw = atob(value);
  return Uint8Array.from(raw, char => char.charCodeAt(0));
}

// Java's ECDSA provider emits ASN.1 DER; WebCrypto verifies IEEE P1363 r||s.
function derSignatureToRaw(der) {
  if (der[0] !== 0x30) throw new Error('Invalid ECDSA signature encoding');
  let offset = 2;
  if (der[1] & 0x80) offset += der[1] & 0x7f;
  if (der[offset] !== 0x02) throw new Error('Invalid ECDSA signature encoding');
  const rLength = der[offset + 1];
  const r = der.slice(offset + 2, offset + 2 + rLength);
  offset += 2 + rLength;
  if (der[offset] !== 0x02) throw new Error('Invalid ECDSA signature encoding');
  const sLength = der[offset + 1];
  const s = der.slice(offset + 2, offset + 2 + sLength);
  const raw = new Uint8Array(64);
  raw.set(r.slice(-32), 32 - Math.min(32, r.length));
  raw.set(s.slice(-32), 64 - Math.min(32, s.length));
  return raw;
}
