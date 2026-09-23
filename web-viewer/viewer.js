/* Offline Crocodyl viewer. No network requests and no third-party dependencies. */
const input = document.querySelector('#file');
const zone = document.querySelector('#dropzone');
const status = document.querySelector('#status');
const report = document.querySelector('#report');

input.addEventListener('change', () => input.files[0] && openFile(input.files[0]));
['dragenter','dragover'].forEach(name => zone.addEventListener(name, e => { e.preventDefault(); zone.classList.add('drag'); }));
['dragleave','drop'].forEach(name => zone.addEventListener(name, e => { e.preventDefault(); zone.classList.remove('drag'); }));
zone.addEventListener('drop', e => e.dataTransfer.files[0] && openFile(e.dataTransfer.files[0]));

async function openFile(file) {
  report.hidden = true;
  status.textContent = `Reading ${file.name} locally…`;
  try {
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
  if (!declared) return { state: 'not supplied', detail: 'The manifest does not declare a payload checksum.' };
  if (!globalThis.crypto?.subtle) throw new Error('This browser cannot verify the payload checksum');
  const payloadNames = Object.keys(entries).filter(name => name.startsWith('tables/')).sort();
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
  const centralSize = view.getUint32(pos + 12, true), centralOffset = view.getUint32(pos + 16, true);
  let cursor = centralOffset, end = centralOffset + centralSize;
  while (cursor < end) {
    if (view.getUint32(cursor, true) !== 0x02014b50) throw new Error('Invalid ZIP directory');
    const method = view.getUint16(cursor + 10, true), compressed = view.getUint32(cursor + 20, true);
    const nameLength = view.getUint16(cursor + 28, true), extraLength = view.getUint16(cursor + 30, true), commentLength = view.getUint16(cursor + 32, true);
    const name = new TextDecoder().decode(bytes.slice(cursor + 46, cursor + 46 + nameLength));
    const local = view.getUint32(cursor + 42, true), localNameLength = view.getUint16(local + 26, true), localExtraLength = view.getUint16(local + 28, true);
    const data = bytes.slice(local + 30 + localNameLength + localExtraLength, local + 30 + localNameLength + localExtraLength + compressed);
    if (!name.endsWith('/')) result[name] = await inflate(data, method);
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
