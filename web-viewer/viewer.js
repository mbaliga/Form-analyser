/* Offline Crocodyl viewer. No network requests and no third-party dependencies. */
const input = document.querySelector('#file');
const zone = document.querySelector('#dropzone');
const status = document.querySelector('#status');
const report = document.querySelector('#report');
const MAX_ARCHIVE_BYTES = 256 * 1024 * 1024;
const MAX_ZIP_ENTRIES = 1024;
const MAX_ENTRY_BYTES = 64 * 1024 * 1024;
const MAX_EXPANDED_BYTES = 128 * 1024 * 1024;
const MAX_DISPLAY_ROWS = 100;
const MAX_DISPLAY_CHARS = 200000;

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
    if (manifest.schemaVersion !== 1 || typeof manifest.appVersion !== 'string' || !manifest.appVersion.trim()) {
      throw new Error('Unsupported manifest schema');
    }
    const integrity = await verifyPayloadChecksum(manifest, entries);
    validateTablePayloads(manifest, entries);
    const signature = await verifyEnvelopeSignature(file.name, manifest);
    render(file, manifest, entries, { ...integrity, ...signature });
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

function validateTablePayloads(manifest, entries) {
  const declaredCounts = manifest.rowCounts;
  if (declaredCounts !== undefined && (declaredCounts === null || typeof declaredCounts !== 'object' || Array.isArray(declaredCounts))) {
    throw new Error('Manifest row counts are invalid');
  }
  for (const name of Object.keys(entries).filter(entry => entry.startsWith('tables/') && entry.endsWith('.json'))) {
    let value;
    try { value = JSON.parse(new TextDecoder().decode(entries[name])); }
    catch (_) { throw new Error(`Table payload is not valid JSON: ${name.slice(7, -5)}`); }
    if (!Array.isArray(value)) throw new Error(`Table payload is not an array: ${name.slice(7, -5)}`);
    const logical = name.slice(7, -5);
    if (declaredCounts && Object.prototype.hasOwnProperty.call(declaredCounts, logical)) {
      const expected = declaredCounts[logical];
      if (!Number.isSafeInteger(expected) || expected < 0 || expected !== value.length) {
        throw new Error(`Manifest row count mismatch: ${logical}`);
      }
    }
  }
}

function render(file, manifest, entries, integrity) {
  const analysis = CrocodylReport.summarize(entries);
  const tables = Object.keys(entries).filter(name => name.startsWith('tables/') && name.endsWith('.json'));
  const included = Array.isArray(manifest.includedTables) ? manifest.includedTables : [];
  const parsedTables = tables.map(name => {
    try {
      const value = JSON.parse(new TextDecoder().decode(entries[name]));
      const count = Array.isArray(value) ? value.length : 0;
      const fields = count && value[0] && typeof value[0] === 'object' ? Object.keys(value[0]).slice(0, 6).join(', ') : 'no row fields';
      const preview = JSON.stringify(Array.isArray(value) ? value.slice(0, MAX_DISPLAY_ROWS) : value, null, 2)
        .slice(0, MAX_DISPLAY_CHARS);
      return { name: name.slice(7, -5), count, fields, preview };
    } catch (_) {
      return { name: name.slice(7, -5), count: 0, fields: 'payload unreadable', preview: '' };
    }
  });
  const tableRows = parsedTables.map(table =>
    `<li><code>${escapeHtml(table.name)}</code> · ${table.count} row(s) · ${escapeHtml(table.fields)}</li>`
  ).join('');
  const tablePreviews = parsedTables.map(table => table.preview
    ? `<details class="table-preview"><summary><code>${escapeHtml(table.name)}</code> · ${table.count} row(s)</summary><pre>${escapeHtml(table.preview)}</pre>${table.count > MAX_DISPLAY_ROWS ? `<p class="hint">Showing the first ${MAX_DISPLAY_ROWS} rows.</p>` : ''}</details>`
    : `<details class="table-preview"><summary><code>${escapeHtml(table.name)}</code> · payload unreadable</summary></details>`
  ).join('');
  report.innerHTML = `<div class="card"><h2>File</h2><div class="facts">
    ${fact('Name', escapeHtml(file.name))}${fact('Size', `${file.size.toLocaleString()} bytes`)}
    ${fact('Created', manifest.createdAtMs ? new Date(manifest.createdAtMs).toLocaleString() : 'Not supplied')}
    ${fact('Schema', String(manifest.schemaVersion))}${fact('App version', escapeHtml(manifest.appVersion))}
    ${fact('Tier', escapeHtml(manifest.tierName || 'Not supplied'))}
    ${fact('Integrity', escapeHtml(integrity.state))}
  </div></div>
  ${renderAnalysis(analysis)}
  <div class="card"><h2>Included data</h2><p>${included.length} manifest table(s), ${tables.length} readable payload(s).</p>
    <ul>${tableRows || (included.length ? included.map(t => `<li><code>${escapeHtml(t)}</code></li>`).join('') : '<li>No table list supplied</li>')}</ul>
    <div class="table-previews">${tablePreviews}</div>
  </div>
  <div class="card"><h2>Privacy boundary</h2><p>This read-only preview inspects the included manifest and table rows locally. It does not send data to a server or silently import anything into a device.</p><p class="hint">${escapeHtml(integrity.detail)}</p></div>`;
  report.hidden = false;
}

function renderAnalysis(analysis) {
  const { totals, rounds, formSessions, baselineFeatures, roundTrend } = analysis;
  const enoughForTrend = roundTrend.previousCount >= 3 && roundTrend.recentCount >= 3;
  const change = enoughForTrend ? roundTrend.recentAverage - roundTrend.previousAverage : null;
  const trendText = enoughForTrend
    ? `${change >= 0 ? '+' : ''}${change.toFixed(1)} percentage points across ${roundTrend.recentCount} recent vs ${roundTrend.previousCount} prior rounds`
    : 'Add at least 6 completed cumulative rounds to compare recent and prior scoring';
  const formRows = formSessions.slice(0, 25).map(session => `<tr>
    <td>${escapeHtml(formatDate(session.at))}</td><td>${session.distance == null ? '—' : `${session.distance} m`}</td>
    <td>${session.duration == null ? '—' : `${formatNumber(session.duration / 60, 1)} min`}</td>
    <td>${session.shotCount}</td><td>${session.baselineCount}</td>
    <td>${session.averageScore == null ? '—' : formatNumber(session.averageScore, 2)}</td></tr>`).join('');
  const roundRows = rounds.slice(0, 25).map(round => `<tr>
    <td>${escapeHtml(formatDate(round.at))}</td><td>${escapeHtml(round.name)}</td>
    <td>${round.total == null ? '—' : formatNumber(round.total, 0)}</td>
    <td>${round.maximum == null ? (round.scoringKind === 'SET_MATCH' ? 'Set match' : '—') : formatNumber(round.maximum, 0)}</td>
    <td>${round.percentage == null ? '—' : `${formatNumber(round.percentage, 1)}%`}</td>
    <td>${round.xCount == null ? '—' : formatNumber(round.xCount, 0)}</td></tr>`).join('');
  const featureRows = baselineFeatures.slice(0, 32).map(feature => `<tr>
    <td><code>${escapeHtml(feature.name)}</code></td><td>${feature.baselineCount}</td>
    <td>${feature.mean == null ? '—' : formatNumber(feature.mean, 3)}</td>
    <td>${feature.standardDeviation == null ? 'Need 2+ baseline shots' : formatNumber(feature.standardDeviation, 3)}</td>
    <td>${feature.latest == null ? '—' : formatNumber(feature.latest, 3)}</td>
    <td>${feature.zScore == null ? '—' : `${feature.zScore >= 0 ? '+' : ''}${formatNumber(feature.zScore, 2)} SD`}</td></tr>`).join('');
  const hasData = totals.formSessions > 0 || totals.completedRounds > 0;
  return `<section class="card analysis-card" aria-labelledby="analysis-title">
    <h2 id="analysis-title">Training analysis</h2>
    <p class="hint">Descriptive summaries of data included in this file. Missing rows are not treated as zero; comparisons do not establish cause or predict future performance.</p>
    <div class="facts analysis-facts">
      ${fact('Form sessions', formatNumber(totals.formSessions, 0))}
      ${fact('Captured shots', formatNumber(totals.formShots, 0))}
      ${fact('Scored shots', formatNumber(totals.scoredShots, 0))}
      ${fact('Mean scored shot', totals.averageShotScore == null ? '—' : `${formatNumber(totals.averageShotScore, 2)} / 10`)}
      ${fact('Baseline shots', formatNumber(totals.baselineShots, 0))}
      ${fact('Completed rounds', formatNumber(totals.completedRounds, 0))}
    </div>
    ${hasData ? '' : '<p class="empty-state">This export contains no shareable form-session or completed-round rows. Check its consent and included-table list; the viewer does not infer missing training.</p>'}
    ${rounds.length ? `<div class="analysis-section"><h3>Scoring trend</h3><p>${escapeHtml(trendText)}</p>
      <div class="table-wrap"><table><thead><tr><th>Date</th><th>Round</th><th>Score</th><th>Maximum</th><th>Score rate</th><th>X</th></tr></thead><tbody>${roundRows}</tbody></table></div>
      ${rounds.length > 25 ? `<p class="hint">Showing the 25 most recent of ${rounds.length} completed rounds.</p>` : ''}</div>` : ''}
    ${formSessions.length ? `<div class="analysis-section"><h3>Form-session history</h3>
      <div class="table-wrap"><table><thead><tr><th>Date</th><th>Distance</th><th>Duration</th><th>Shots</th><th>Baseline</th><th>Mean shot</th></tr></thead><tbody>${formRows}</tbody></table></div>
      ${formSessions.length > 25 ? `<p class="hint">Showing the 25 most recent of ${formSessions.length} form sessions.</p>` : ''}</div>` : ''}
    ${featureRows.length ? `<div class="analysis-section"><h3>Latest form relative to baseline</h3>
      <p class="hint">The latest non-baseline shot is compared feature-by-feature with available baseline samples. “SD” is a descriptive sample-standard-deviation difference, not Crocodyl’s on-device stability score.</p>
      <div class="table-wrap"><table><thead><tr><th>Feature</th><th>Baseline n</th><th>Mean</th><th>Sample SD</th><th>Latest</th><th>Difference</th></tr></thead><tbody>${featureRows}</tbody></table></div>
      ${baselineFeatures.length > 32 ? `<p class="hint">Showing 32 of ${baselineFeatures.length} baseline features.</p>` : ''}</div>` : ''}
  </section>`;
}

function formatDate(timestamp) {
  if (!Number.isFinite(timestamp)) return 'Not supplied';
  const date = new Date(timestamp);
  return Number.isNaN(date.getTime()) ? 'Invalid date' : date.toLocaleString();
}

function formatNumber(value, digits) {
  return Number.isFinite(value) ? new Intl.NumberFormat(undefined, {
    maximumFractionDigits: digits,
    minimumFractionDigits: digits,
  }).format(value) : '—';
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
    if (cursor + 46 > end) throw new Error('Invalid ZIP directory');
    if (view.getUint32(cursor, true) !== 0x02014b50) throw new Error('Invalid ZIP directory');
    const method = view.getUint16(cursor + 10, true), compressed = view.getUint32(cursor + 20, true);
    const uncompressed = view.getUint32(cursor + 24, true);
    const nameLength = view.getUint16(cursor + 28, true), extraLength = view.getUint16(cursor + 30, true), commentLength = view.getUint16(cursor + 32, true);
    const name = new TextDecoder().decode(bytes.slice(cursor + 46, cursor + 46 + nameLength));
    if (!name || name.startsWith('/') || name.includes('\\') || name.split('/').includes('..')) throw new Error('Unsafe ZIP entry path');
    const isDirectory = name.endsWith('/');
    const isManifest = name === 'manifest.json';
    const isTable = name.startsWith('tables/') && name.endsWith('.json');
    if (!isDirectory && !isManifest && !isTable) throw new Error('Unsupported ZIP entry');
    if (Object.prototype.hasOwnProperty.call(result, name)) throw new Error('Duplicate ZIP entry');
    if (uncompressed > MAX_ENTRY_BYTES || expandedBytes + uncompressed > MAX_EXPANDED_BYTES) throw new Error('Archive expands beyond the local viewer limit');
    const local = view.getUint32(cursor + 42, true);
    if (local + 30 > bytes.byteLength || view.getUint32(local, true) !== 0x04034b50) throw new Error('Invalid ZIP local header');
    const localNameLength = view.getUint16(local + 26, true), localExtraLength = view.getUint16(local + 28, true);
    const localName = new TextDecoder().decode(bytes.slice(local + 30, local + 30 + localNameLength));
    if (localName !== name) throw new Error('ZIP entry name mismatch');
    if (local + 30 + localNameLength + localExtraLength + compressed > bytes.byteLength) throw new Error('ZIP entry exceeds the file');
    const data = bytes.slice(local + 30 + localNameLength + localExtraLength, local + 30 + localNameLength + localExtraLength + compressed);
    if (!isDirectory) {
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
