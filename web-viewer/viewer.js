/* Offline Crocodyl viewer. No network requests and no third-party dependencies. */
const input = document.querySelector('#file');
const zone = document.querySelector('#dropzone');
const status = document.querySelector('#status');
const report = document.querySelector('#report');
const MAX_ARCHIVE_BYTES = 256 * 1024 * 1024;
const MAX_ZIP_ENTRIES = 1024;
const MAX_ENTRY_BYTES = 64 * 1024 * 1024;
const MAX_EXPANDED_BYTES = 128 * 1024 * 1024;

input.addEventListener('change', () => input.files[0] && openFile(input.files[0]));
['dragenter','dragover'].forEach(name => zone.addEventListener(name, e => { e.preventDefault(); zone.classList.add('drag'); }));
['dragleave','drop'].forEach(name => zone.addEventListener(name, e => { e.preventDefault(); zone.classList.remove('drag'); }));
zone.addEventListener('drop', e => e.dataTransfer.files[0] && openFile(e.dataTransfer.files[0]));
zone.addEventListener('keydown', e => {
  if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); input.click(); }
});

async function openFile(file) {
  report.hidden = true;
  status.textContent = `Reading ${file.name} locally…`;
  try {
    if (file.size > MAX_ARCHIVE_BYTES) throw new Error('File exceeds the 256 MiB local viewer limit');
    const bytes = new Uint8Array(await file.arrayBuffer());
    const entries = isZip(bytes) ? await readZip(bytes) : { 'manifest.json': bytes };
    const manifestBytes = entries['manifest.json'];
    if (!manifestBytes) throw new Error('manifest.json is missing');
    const manifest = JSON.parse(new TextDecoder().decode(manifestBytes));
    if (!Number.isInteger(manifest.schemaVersion) || !manifest.appVersion) throw new Error('Unsupported manifest');
    const integrity = await verifyPayloadChecksum(manifest, entries);
    render(file, manifest, entries, integrity);
    status.textContent = `Opened ${file.name} locally. No upload was performed.`;
  } catch (error) {
    status.textContent = `Could not open this file: ${error.message}`;
  }
}

async function verifyPayloadChecksum(manifest, entries) {
  const declared = typeof manifest.contentChecksum === 'string' ? manifest.contentChecksum : '';
  if (!declared) throw new Error('Manifest does not declare a payload checksum');
  const payloadNames = Object.keys(entries).filter(name => name.startsWith('tables/'));
  if (payloadNames.some(name => !name.endsWith('.json'))) throw new Error('Unsupported table payload');
  const actualTables = payloadNames.map(name => name.slice(7, -5)).sort();
  const declaredTables = Array.isArray(manifest.includedTables)
    ? manifest.includedTables.filter(table => typeof table === 'string').slice().sort()
    : null;
  if (!declaredTables || JSON.stringify(actualTables) !== JSON.stringify(declaredTables)) {
    throw new Error('Manifest table list does not match the archive');
  }
  if (!globalThis.crypto?.subtle) throw new Error('This browser cannot verify the payload checksum');
  payloadNames.sort();
  const total = payloadNames.reduce((size, name) => size + entries[name].byteLength, 0);
  const combined = new Uint8Array(total);
  let offset = 0;
  for (const name of payloadNames) { combined.set(entries[name], offset); offset += entries[name].byteLength; }
  const digest = await crypto.subtle.digest('SHA-256', combined);
  const actual = 'sha256:' + [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, '0')).join('');
  if (actual !== declared.toLowerCase()) throw new Error('Payload checksum mismatch; the file was not opened');
  return { state: 'verified', detail: actual };
}

function render(file, manifest, entries, integrity) {
  const tables = Object.keys(entries).filter(name => name.startsWith('tables/') && name.endsWith('.json'));
  const included = Array.isArray(manifest.includedTables) ? manifest.includedTables : [];
  const tableRows = tables.map(name => {
    try {
      const value = JSON.parse(new TextDecoder().decode(entries[name]));
      const count = Array.isArray(value) ? value.length : 0;
      const fields = count && value[0] && typeof value[0] === 'object' ? Object.keys(value[0]).slice(0, 6).join(', ') : 'no row fields';
      return `<li><code>${escapeHtml(name.slice(7, -5))}</code> · ${count} row(s) · ${escapeHtml(fields)}</li>`;
    } catch (_) {
      return `<li><code>${escapeHtml(name.slice(7, -5))}</code> · payload unreadable</li>`;
    }
  }).join('');
  report.innerHTML = `<div class="card"><h2>File</h2><div class="facts">
    ${fact('Name', escapeHtml(file.name))}${fact('Size', `${file.size.toLocaleString()} bytes`)}
    ${fact('Created', manifest.createdAtMs ? new Date(manifest.createdAtMs).toLocaleString() : 'Not supplied')}
    ${fact('Schema', String(manifest.schemaVersion))}${fact('App version', escapeHtml(manifest.appVersion))}
    ${fact('Tier', escapeHtml(manifest.tierName || 'Not supplied'))}
    ${fact('Integrity', escapeHtml(integrity.state))}
  </div></div>
  <div class="card"><h2>Included data</h2><p>${included.length} manifest table(s), ${tables.length} readable payload(s).</p>
    <ul>${tableRows || (included.length ? included.map(t => `<li><code>${escapeHtml(t)}</code></li>`).join('') : '<li>No table list supplied</li>')}</ul>
  </div>
  <div class="card"><h2>Privacy boundary</h2><p>This preview inspects the manifest and table names only. It does not send data to a server or silently import anything into a device.</p><p class="hint">${escapeHtml(integrity.detail)}</p></div>`;
  report.hidden = false;
}

function fact(label, value) { return `<div class="fact"><small>${label}</small><code>${value}</code></div>`; }
function escapeHtml(value) { return String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function isZip(bytes) { return bytes[0] === 0x50 && bytes[1] === 0x4b && bytes[2] === 0x03 && bytes[3] === 0x04; }

async function readZip(bytes) {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const result = {};
  let pos = findEnd(view);
  const entryCount = view.getUint16(pos + 10, true);
  if (entryCount > MAX_ZIP_ENTRIES) throw new Error('Archive contains too many entries');
  const centralSize = view.getUint32(pos + 12, true), centralOffset = view.getUint32(pos + 16, true);
  if (centralOffset + centralSize > bytes.byteLength) throw new Error('ZIP directory exceeds the file');
  let cursor = centralOffset, end = centralOffset + centralSize;
  let expandedBytes = 0;
  while (cursor < end) {
    if (view.getUint32(cursor, true) !== 0x02014b50) throw new Error('Invalid ZIP directory');
    const method = view.getUint16(cursor + 10, true), compressed = view.getUint32(cursor + 20, true);
    const uncompressed = view.getUint32(cursor + 24, true);
    const nameLength = view.getUint16(cursor + 28, true), extraLength = view.getUint16(cursor + 30, true), commentLength = view.getUint16(cursor + 32, true);
    const name = new TextDecoder().decode(bytes.slice(cursor + 46, cursor + 46 + nameLength));
    if (!name || name.startsWith('/') || name.split('/').includes('..')) throw new Error('Unsafe ZIP entry path');
    if (Object.prototype.hasOwnProperty.call(result, name)) throw new Error('Duplicate ZIP entry');
    if (uncompressed > MAX_ENTRY_BYTES || expandedBytes + uncompressed > MAX_EXPANDED_BYTES) throw new Error('Archive expands beyond the local viewer limit');
    const local = view.getUint32(cursor + 42, true), localNameLength = view.getUint16(local + 26, true), localExtraLength = view.getUint16(local + 28, true);
    if (local + 30 + localNameLength + localExtraLength + compressed > bytes.byteLength) throw new Error('ZIP entry exceeds the file');
    const data = bytes.slice(local + 30 + localNameLength + localExtraLength, local + 30 + localNameLength + localExtraLength + compressed);
    if (!name.endsWith('/')) {
      const inflated = await inflate(data, method);
      if (inflated.byteLength > MAX_ENTRY_BYTES || expandedBytes + inflated.byteLength > MAX_EXPANDED_BYTES) {
        throw new Error('Archive expands beyond the local viewer limit');
      }
      result[name] = inflated;
      expandedBytes += inflated.byteLength;
    }
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  return result;
}
function findEnd(view) { for (let p = view.byteLength - 22; p >= 0; p--) if (view.getUint32(p, true) === 0x06054b50) return p; throw new Error('ZIP end record missing'); }
async function inflate(data, method) {
  if (method === 0) return data;
  if (method !== 8 || typeof DecompressionStream === 'undefined') throw new Error('This browser cannot inflate the archive');
  const stream = new Blob([data]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}
