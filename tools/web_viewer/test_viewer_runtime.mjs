import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import { createHash as nodeCreateHash, webcrypto } from 'node:crypto';

const viewerDirectory = new URL('../../web-viewer/', import.meta.url);
const [reportSource, signatureSource, viewerSource] = await Promise.all([
  fs.readFile(new URL('report.js', viewerDirectory), 'utf8'),
  fs.readFile(new URL('signature.js', viewerDirectory), 'utf8'),
  fs.readFile(new URL('viewer.js', viewerDirectory), 'utf8'),
]);

const listeners = new Map();
let pickerClickCount = 0;
const elements = {
  '#file': {
    addEventListener(name, callback) { listeners.set(`#file:${name}`, callback); },
    click() { pickerClickCount++; },
  },
  '#dropzone': {
    addEventListener(name, callback) { listeners.set(`#dropzone:${name}`, callback); },
    classList: { add() {}, remove() {} },
  },
  '#status': { textContent: '' },
  '#report': { hidden: true, innerHTML: '' },
};
const context = vm.createContext({
  crypto: webcrypto,
  document: { querySelector: selector => elements[selector] },
  atob: value => Buffer.from(value, 'base64').toString('binary'),
  TextEncoder,
  TextDecoder,
  Uint8Array,
  DataView,
  Blob,
  Response,
  DecompressionStream,
  Intl,
  Date,
  Number,
  JSON,
  Math,
});
vm.runInContext(
  `${reportSource}\n${signatureSource}\n${viewerSource}\nglobalThis.openFileForTest = openFile;`,
  context,
);

for (const key of ['Enter', ' ']) {
  let prevented = false;
  listeners.get('#dropzone:keydown')({
    key,
    preventDefault() { prevented = true; },
  });
  assert.equal(prevented, true, `${key === ' ' ? 'Space' : key} should be handled as a button action`);
}
assert.equal(pickerClickCount, 2, 'Enter and Space should both open the native file picker');
listeners.get('#dropzone:keydown')({ key: 'Tab', preventDefault() { assert.fail('Tab must remain available'); } });
assert.equal(pickerClickCount, 2, 'unrelated keys must not open the native file picker');

const encoder = new TextEncoder();
const tables = {
  score_session: [{
    id: 'round-1', status: 'FINISHED', roundComplete: 1, scoringKind: 'CUMULATIVE',
    roundName: 'Synthetic WA 720', total: 612, arrowsPerEnd: 6, endCount: 12,
    xCount: 8, completedAt: 1_760_000_000_000,
  }],
  session: [{
    id: 'session-1', startedAtEpochMs: 1_760_000_000_000,
    distanceMeters: 18, durationS: 3_600, arrowsActual: 36,
  }],
  shot: [
    { id: 'shot-1', sessionId: 'session-1', indexInSession: 1, score: 9, isBaseline: 1, featuresJson: JSON.stringify({ hold_stability: 0.42 }) },
    { id: 'shot-2', sessionId: 'session-1', indexInSession: 2, score: 8, isBaseline: 1, featuresJson: JSON.stringify({ hold_stability: 0.38 }) },
    { id: 'shot-3', sessionId: 'session-1', indexInSession: 3, score: 9, isBaseline: 0, featuresJson: JSON.stringify({ hold_stability: 0.51 }) },
  ],
};
const tableNames = Object.keys(tables).sort();
const tableEntries = tableNames.map(name => [
  `tables/${name}.json`,
  encoder.encode(JSON.stringify(tables[name])),
]);
const checksum = digest(Buffer.concat(tableEntries.map(([, bytes]) => Buffer.from(bytes))));

const keyPair = await webcrypto.subtle.generateKey(
  { name: 'ECDSA', namedCurve: 'P-256' },
  true,
  ['sign', 'verify'],
);
const publicKey = new Uint8Array(await webcrypto.subtle.exportKey('spki', keyPair.publicKey));
const tierName = 'SHAREABLE_ONLY';
const signedEnvelope = `${checksum}\n${tableNames.join(',')}\n${tierName}`;
const rawSignature = new Uint8Array(await webcrypto.subtle.sign(
  { name: 'ECDSA', hash: 'SHA-256' },
  keyPair.privateKey,
  encoder.encode(signedEnvelope),
));
const signature = Buffer.concat([
  Buffer.from([0x30]),
  Buffer.from([derInteger(rawSignature.slice(0, 32)).length + derInteger(rawSignature.slice(32)).length]),
  derInteger(rawSignature.slice(0, 32)),
  derInteger(rawSignature.slice(32)),
]);
const baseManifest = {
  schemaVersion: 1,
  appVersion: '0.6.4-synthetic-runtime-test',
  createdAtMs: 1_760_000_000_000,
  contentChecksum: checksum,
  includedTables: tableNames,
  rowCounts: Object.fromEntries(tableNames.map(name => [name, tables[name].length])),
  tierName,
};
const signedManifest = {
  ...baseManifest,
  signatureAlgorithm: 'SHA256withECDSA/CrocodylEnvelopeV2',
  publicKeyBase64: Buffer.from(publicKey).toString('base64'),
  signatureBase64: signature.toString('base64'),
};

function digest(bytes) {
  return `sha256:${nodeCreateHash('sha256').update(bytes).digest('hex')}`;
}

function derInteger(bytes) {
  let value = Buffer.from(bytes);
  while (value.length > 1 && value[0] === 0) value = value.subarray(1);
  if (value[0] & 0x80) value = Buffer.concat([Buffer.from([0]), value]);
  return Buffer.concat([Buffer.from([0x02, value.length]), value]);
}

function createZip(entries) {
  const localRecords = [];
  const centralRecords = [];
  let localOffset = 0;
  for (const [name, content] of entries) {
    const nameBytes = Buffer.from(name);
    const data = Buffer.from(content);
    const checksum32 = crc32(data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6);
    local.writeUInt32LE(checksum32, 14);
    local.writeUInt32LE(data.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBytes.length, 26);
    localRecords.push(local, nameBytes, data);

    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0x0800, 8);
    central.writeUInt32LE(checksum32, 16);
    central.writeUInt32LE(data.length, 20);
    central.writeUInt32LE(data.length, 24);
    central.writeUInt16LE(nameBytes.length, 28);
    central.writeUInt32LE(localOffset, 42);
    centralRecords.push(central, nameBytes);
    localOffset += local.length + nameBytes.length + data.length;
  }
  const localBytes = Buffer.concat(localRecords);
  const centralBytes = Buffer.concat(centralRecords);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(centralBytes.length, 12);
  end.writeUInt32LE(localBytes.length, 16);
  return Buffer.concat([localBytes, centralBytes, end]);
}

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function archive(manifest, payloads = tableEntries) {
  return createZip([
    ['manifest.json', encoder.encode(JSON.stringify(manifest))],
    ...payloads,
  ]);
}

function file(name, bytes) {
  const data = Buffer.from(bytes);
  return {
    name,
    size: data.length,
    arrayBuffer: async () => data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength),
  };
}

await context.openFileForTest(file('synthetic-training.croc', archive(signedManifest)));
assert.match(elements['#status'].textContent, /Opened synthetic-training\.croc locally\. No upload was performed\./);
assert.equal(elements['#report'].hidden, false);
assert.match(elements['#report'].innerHTML, /signed and verified/);
assert.match(elements['#report'].innerHTML, /Synthetic WA 720/);
assert.match(elements['#report'].innerHTML, /hold_stability/);
assert.match(elements['#report'].innerHTML, /612/);

const changedShots = [
  ...tableEntries.filter(([name]) => name !== 'tables/shot.json'),
  ['tables/shot.json', encoder.encode(JSON.stringify(tables.shot.map((shot, index) => index === 0 ? { ...shot, score: 1 } : shot)))],
].sort(([left], [right]) => left.localeCompare(right));
await context.openFileForTest(file('tampered-payload.croc', archive(signedManifest, changedShots)));
assert.match(elements['#status'].textContent, /Payload checksum mismatch/);
assert.equal(elements['#report'].hidden, true, 'tampered payload must not render a report');

const changedChecksum = digest(Buffer.concat(changedShots.map(([, bytes]) => Buffer.from(bytes))));
const staleSignatureManifest = { ...signedManifest, contentChecksum: changedChecksum };
await context.openFileForTest(file('tampered-signature.croc', archive(staleSignatureManifest, changedShots)));
assert.match(elements['#status'].textContent, /Signature verification failed/);
assert.equal(elements['#report'].hidden, true, 'invalid signature must not render a report');

const unsafeArchive = createZip([
  ['manifest.json', encoder.encode(JSON.stringify(baseManifest))],
  ...tableEntries,
  ['tables/../outside.json', encoder.encode('[]')],
]);
await context.openFileForTest(file('unsafe-path.crocbak', unsafeArchive));
assert.match(elements['#status'].textContent, /Unsafe ZIP entry path/);
assert.equal(elements['#report'].hidden, true, 'unsafe ZIP paths must not render a report');

const wrongRowCounts = {
  ...baseManifest,
  rowCounts: { ...baseManifest.rowCounts, shot: tables.shot.length - 1 },
};
await context.openFileForTest(file('wrong-row-count.crocbak', archive(wrongRowCounts)));
assert.match(elements['#status'].textContent, /Manifest row count mismatch: shot/);
assert.equal(elements['#report'].hidden, true, 'inconsistent row counts must not render a report');

await context.openFileForTest(file('synthetic-recovery.crocbak', archive(baseManifest)));
assert.match(elements['#status'].textContent, /Opened synthetic-recovery\.crocbak locally/);
assert.match(elements['#report'].innerHTML, /Payload checksum verified; recovery archive is unsigned/);
assert.equal(elements['#report'].hidden, false);

console.log('Offline viewer runtime passed signed .croc, tamper rejection, unsafe-path and row-count rejection, and .crocbak flows');

