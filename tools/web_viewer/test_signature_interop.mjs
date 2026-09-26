import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import { webcrypto } from 'node:crypto';

const source = await fs.readFile(new URL('../../web-viewer/signature.js', import.meta.url), 'utf8');
const context = vm.createContext({
  crypto: webcrypto,
  TextEncoder,
  atob: value => Buffer.from(value, 'base64').toString('binary'),
});
vm.runInContext(`${source}\nglobalThis.verifyEnvelopeSignature = verifyEnvelopeSignature;`, context);

const pair = await webcrypto.subtle.generateKey(
  { name: 'ECDSA', namedCurve: 'P-256' },
  true,
  ['sign', 'verify'],
);
const checksum = 'sha256:0123456789abcdef';
const rawSignature = new Uint8Array(await webcrypto.subtle.sign(
  { name: 'ECDSA', hash: 'SHA-256' },
  pair.privateKey,
  new TextEncoder().encode(checksum),
));
const publicKey = new Uint8Array(await webcrypto.subtle.exportKey('spki', pair.publicKey));

function derInteger(bytes) {
  let value = bytes.slice();
  while (value.length > 1 && value[0] === 0) value = value.slice(1);
  if (value[0] & 0x80) value = Uint8Array.of(0, ...value);
  return Uint8Array.of(0x02, value.length, ...value);
}

const r = derInteger(rawSignature.slice(0, 32));
const s = derInteger(rawSignature.slice(32));
const derSignature = Uint8Array.of(0x30, r.length + s.length, ...r, ...s);
const base64 = bytes => Buffer.from(bytes).toString('base64');
const manifest = {
  contentChecksum: checksum,
  signatureAlgorithm: 'SHA256withECDSA',
  publicKeyBase64: base64(publicKey),
  signatureBase64: base64(derSignature),
};

const verified = await context.verifyEnvelopeSignature('training.croc', manifest);
assert.equal(verified.state, 'signed and verified');
await assert.rejects(
  context.verifyEnvelopeSignature('tampered.croc', { ...manifest, contentChecksum: 'sha256:tampered' }),
  /Signature verification failed/,
);

const envelope = {
  contentChecksum: checksum,
  includedTables: ['session', 'athlete'],
  tierName: 'SHAREABLE_ONLY',
};
const envelopePayload = `${envelope.contentChecksum}\n${envelope.includedTables.slice().sort().join(',')}\n${envelope.tierName}`;
const envelopeRaw = new Uint8Array(await webcrypto.subtle.sign(
  { name: 'ECDSA', hash: 'SHA-256' },
  pair.privateKey,
  new TextEncoder().encode(envelopePayload),
));
const envelopeR = derInteger(envelopeRaw.slice(0, 32));
const envelopeS = derInteger(envelopeRaw.slice(32));
const envelopeManifest = {
  ...envelope,
  signatureAlgorithm: 'SHA256withECDSA/CrocodylEnvelopeV2',
  publicKeyBase64: base64(publicKey),
  signatureBase64: base64(Uint8Array.of(0x30, envelopeR.length + envelopeS.length, ...envelopeR, ...envelopeS)),
};
assert.equal((await context.verifyEnvelopeSignature('envelope.croc', envelopeManifest)).state, 'signed and verified');
await assert.rejects(
  context.verifyEnvelopeSignature('scope-tampered.croc', { ...envelopeManifest, includedTables: ['athlete'] }),
  /Signature verification failed/,
);
const recovery = await context.verifyEnvelopeSignature('backup.crocbak', { contentChecksum: checksum });
assert.equal(recovery.state, 'verified');
console.log('ECDSA .croc signature interoperability passed');
