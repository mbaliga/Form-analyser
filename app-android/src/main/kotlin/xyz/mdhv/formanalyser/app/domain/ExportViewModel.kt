package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import xyz.mdhv.formanalyser.app.BuildConfig
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.exchange.AndroidKeyProvider
import xyz.mdhv.formanalyser.app.exchange.ExchangeTrustStore
import xyz.mdhv.formanalyser.exchange.ConsentDecision
import xyz.mdhv.formanalyser.exchange.ConsentFilter
import xyz.mdhv.formanalyser.exchange.CrocEnvelope
import xyz.mdhv.formanalyser.exchange.CrocbakManifest
import xyz.mdhv.formanalyser.exchange.ExportTier
import xyz.mdhv.formanalyser.exchange.ExchangeTrust
import xyz.mdhv.formanalyser.exchange.ExchangeTrustState
import xyz.mdhv.formanalyser.wellness.PrivacyClass
import xyz.mdhv.formanalyser.wellness.PrivacyRegistry

/**
 * Phase 5 export ceremony (over `core-exchange`). Drives the tier/grant selection, computes the
 * live [ConsentDecision] preview, and — on confirm — assembles a `.crocbak` zip to a
 * caller-supplied SAF [Uri].
 *
 * Privacy enforcement is delegated entirely to [ConsentFilter]/[PrivacyRegistry]: this VM only ever
 * reads rows from tables the decision cleared, so PRIVATE tables are never even queried, and
 * MEDICAL tables are read only when the athlete granted them. The manifest's `createdAtMs` is
 * stamped here (`System.currentTimeMillis()`) and passed IN to the pure core, per the core's
 * determinism contract.
 */
class ExportViewModel(app: Application) : AndroidViewModel(app) {

    private val keyProvider = AndroidKeyProvider()
    private val trustStore = ExchangeTrustStore(app)
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    /** Result of the last export attempt (null before any attempt). */
    data class ExportOutcome(val ok: Boolean, val message: String)

    data class ImportPreview(
        val appVersion: String,
        val createdAtMs: Long,
        val sourceFingerprint: String,
        val tableCount: Int,
        val rowCount: Long,
        val athleteNames: List<String>,
        val signed: Boolean,
        val trustState: ExchangeTrustState,
    )

    private data class ArchiveBundle(
        val manifest: CrocbakManifest,
        val tables: Map<String, JsonArray>,
        val signedFingerprint: String? = null,
    )

    private val _tier = MutableStateFlow(ExportTier.SHAREABLE_ONLY)
    val tier: StateFlow<ExportTier> = _tier

    private val _medicalGrants = MutableStateFlow<Set<String>>(emptySet())
    val medicalGrants: StateFlow<Set<String>> = _medicalGrants

    private val _decision = MutableStateFlow(recompute(ExportTier.SHAREABLE_ONLY, emptySet()))
    val decision: StateFlow<ConsentDecision> = _decision

    private val _fingerprint = MutableStateFlow<String?>(null)
    val fingerprint: StateFlow<String?> = _fingerprint

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _outcome = MutableStateFlow<ExportOutcome?>(null)
    val outcome: StateFlow<ExportOutcome?> = _outcome

    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview: StateFlow<ImportPreview?> = _importPreview

    private val _importOutcome = MutableStateFlow<ExportOutcome?>(null)
    val importOutcome: StateFlow<ExportOutcome?> = _importOutcome

    private var inspectedArchive: ArchiveBundle? = null

    /** MEDICAL tables the athlete may grant, per the registry (e.g. medication_entry, document). */
    val medicalTables: List<String> = PrivacyRegistry.medicalTables().sorted()

    fun load() {
        if (_fingerprint.value != null) return
        viewModelScope.launch {
            _fingerprint.value =
                withContext(Dispatchers.IO) {
                    runCatching { keyProvider.identity().fingerprint }.getOrNull()
                }
        }
    }

    fun setTier(t: ExportTier) {
        _tier.value = t
        // Grants are meaningless outside FULL; drop them so the preview can't imply medical export.
        if (t != ExportTier.FULL) _medicalGrants.value = emptySet()
        refreshPreview()
    }

    fun toggleMedicalGrant(table: String) {
        val cur = _medicalGrants.value
        _medicalGrants.value = if (table in cur) cur - table else cur + table
        refreshPreview()
    }

    private fun refreshPreview() {
        _decision.value = recompute(_tier.value, _medicalGrants.value)
    }

    private fun recompute(tier: ExportTier, grants: Set<String>): ConsentDecision =
        // Feed EVERY registered table (including PRIVATE) so the preview surfaces what will and
        // won't leave the device, each withheld item tagged with the reason.
        ConsentFilter.filter(ALL_TABLES, tier, grants)

    /**
     * Assemble the `.crocbak` archive into [uri] (a `CreateDocument` result). Runs off the main
     * thread; publishes the result to [outcome].
     */
    fun export(uri: Uri) {
        if (_busy.value) return
        _busy.value = true
        _outcome.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { writeArchive(uri) } }
            _busy.value = false
            _outcome.value =
                result.fold(
                    onSuccess = { ExportOutcome(true, it) },
                    onFailure = {
                        ExportOutcome(
                            false,
                            "Export failed: ${it.message ?: it.javaClass.simpleName}",
                        )
                    },
                )
        }
    }

    /**
     * Build the archive into the app's own cache and hand back a content [Uri] for the sharesheet.
     *
     * Sharing does not widen what leaves the device by a single row: the archive is assembled from
     * exactly the same [ConsentDecision] the preview above is showing, so the tier and the medical
     * grants govern a shared file identically to a saved one. What changes is only the destination
     * — SAF writes it somewhere the athlete picked on this device, this hands it to an app they
     * pick.
     *
     * Previous share exports are deleted first. A .crocbak can carry medical rows, and leaving old
     * copies in a world-readable-to-the-recipient cache is exactly the quiet accumulation the
     * privacy model exists to prevent.
     */
    fun exportForSharing(onReady: (Uri) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _outcome.value = null
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val app = getApplication<Application>()
                        val dir = File(app.cacheDir, SHARE_DIR).apply { mkdirs() }
                        dir.listFiles()?.forEach { it.delete() }
                        val file = File(dir, SUGGESTED_FILENAME)
                        val message = file.outputStream().use { writeArchive(it) }
                        val uri =
                            FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                        uri to message
                    }
                }
            _busy.value = false
            result.fold(
                onSuccess = { (uri, message) ->
                    _outcome.value = ExportOutcome(true, message)
                    onReady(uri)
                },
                onFailure = {
                    _outcome.value =
                        ExportOutcome(
                            false,
                            "Export failed: ${it.message ?: it.javaClass.simpleName}",
                        )
                },
            )
        }
    }

    /** Build a cryptographically signed `.croc` envelope around the consent-filtered archive. */
    fun exportSignedForSharing(onReady: (Uri) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _outcome.value = null
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val app = getApplication<Application>()
                        val dir = File(app.cacheDir, SHARE_DIR).apply { mkdirs() }
                        dir.listFiles()?.forEach { it.delete() }
                        val backup = java.io.ByteArrayOutputStream().also { writeArchive(it) }.toByteArray()
                        val identity = keyProvider.identity()
                        val envelope =
                            CrocEnvelope.create(
                                createdAtMs = System.currentTimeMillis(),
                                senderPublicKey = identity.keyBytes,
                                payloadType = CrocEnvelope.BACKUP_PAYLOAD,
                                payload = backup,
                                signer = keyProvider::sign,
                            )
                        val file = File(dir, SUGGESTED_CROC_FILENAME)
                        file.writeText(envelope.serialize())
                        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                    }
                }
            _busy.value = false
            result.fold(
                onSuccess = { uri ->
                    _outcome.value = ExportOutcome(true, "Signed .croc exchange ready.")
                    onReady(uri)
                },
                onFailure = {
                    _outcome.value = ExportOutcome(false, "Signed exchange failed: ${it.message ?: it.javaClass.simpleName}")
                },
            )
        }
    }

    /** Read and validate an archive without modifying the database. */
    fun inspectImport(uri: Uri) {
        if (_busy.value) return
        _busy.value = true
        _importOutcome.value = null
        _importPreview.value = null
        inspectedArchive = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { readArchive(uri) } }
            _busy.value = false
            result.fold(
                onSuccess = { archive ->
                    inspectedArchive = archive
                    val manifest = archive.manifest
                    val athletes = archive.athletes()
                    val trustState = trustState(archive, athletes.map { it.first })
                    _importPreview.value =
                        ImportPreview(
                            appVersion = manifest.appVersion,
                            createdAtMs = manifest.createdAtMs,
                            sourceFingerprint = manifest.athletePubkeyFingerprint,
                            tableCount = archive.tables.size,
                            rowCount = archive.tables.values.sumOf { it.size.toLong() },
                            athleteNames = athletes.map { it.second },
                            signed = archive.signedFingerprint != null,
                            trustState = trustState,
                        )
                },
                onFailure = {
                    _importOutcome.value =
                        ExportOutcome(false, "Archive rejected: ${it.message ?: it.javaClass.simpleName}")
                },
            )
        }
    }

    /** Merge the inspected archive. Existing rows win; the import never erases local history. */
    fun importInspected() {
        val archive = inspectedArchive ?: return
        val preview = _importPreview.value ?: return
        if (preview.trustState == ExchangeTrustState.KEY_CHANGED) return
        if (_busy.value) return
        _busy.value = true
        _importOutcome.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val message = mergeArchive(archive)
                    archive.signedFingerprint?.let { fingerprint ->
                        trustStore.pin(archive.athletes().map { it.first }, fingerprint)
                    }
                    message
                }
            }
            _busy.value = false
            result.fold(
                onSuccess = { message ->
                    _importOutcome.value = ExportOutcome(true, message)
                    _importPreview.value = null
                    inspectedArchive = null
                },
                onFailure = {
                    _importOutcome.value =
                        ExportOutcome(false, "Import failed; no rows were changed: ${it.message ?: it.javaClass.simpleName}")
                },
            )
        }
    }

    fun cancelImport() {
        inspectedArchive = null
        _importPreview.value = null
    }

    /** First tap in the deliberate key-replacement ceremony; data remains quarantined. */
    fun armKeyReplacement() {
        val preview = _importPreview.value ?: return
        if (preview.trustState == ExchangeTrustState.KEY_CHANGED) {
            _importPreview.value = preview.copy(trustState = ExchangeTrustState.REPLACEMENT_ARMED)
        }
    }

    private fun writeArchive(uri: Uri): String {
        val out =
            getApplication<Application>().contentResolver.openOutputStream(uri)
                ?: error("cannot open destination")
        return out.use { writeArchive(it) }
    }

    /**
     * The one archive writer. Both destinations — a SAF document the athlete picked and the cache
     * file the sharesheet hands on — go through it, so a shared archive and a saved one are
     * byte-for-byte the same ceremony and cannot drift apart in what they include.
     */
    private fun writeArchive(out: OutputStream): String {
        val decision = _decision.value
        val tier = _tier.value
        val included = decision.included.sorted()

        // Serialize each cleared table to a JSON array of rows (generic cursor dump).
        val payloads = LinkedHashMap<String, ByteArray>()
        val rowCounts = LinkedHashMap<String, Long>()
        for (logical in included) {
            // Defence in depth on top of ConsentFilter: never dump PRIVATE, never dump the unknown.
            if (PrivacyRegistry.classOf(logical) == PrivacyClass.PRIVATE) continue
            val sqlName = SQL_NAME[logical] ?: logical
            val (bytes, count) = dumpTable(sqlName)
            payloads[logical] = bytes
            rowCounts[logical] = count
        }

        // Checksum over the payload bytes in deterministic (sorted) order.
        val digest = MessageDigest.getInstance("SHA-256")
        for (logical in payloads.keys) digest.update(payloads.getValue(logical))
        val checksum = "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }

        val fingerprint =
            _fingerprint.value
                ?: keyProvider.identity().fingerprint.also { _fingerprint.value = it }

        val manifest =
            CrocbakManifest.fromDecision(
                appVersion = APP_VERSION,
                createdAtMs = System.currentTimeMillis(),
                athletePubkeyFingerprint = fingerprint,
                tier = tier,
                decision = decision,
                contentChecksum = checksum,
                rowCounts = rowCounts,
            )

        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.serialize().toByteArray())
            zip.closeEntry()
            for ((logical, bytes) in payloads) {
                zip.putNextEntry(ZipEntry("tables/$logical.json"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }

        val rows = rowCounts.values.sum()
        return "Exported ${payloads.size} table(s), $rows row(s) to a .crocbak archive."
    }

    /**
     * Generic `SELECT *` dump of a fixed, code-owned table name to a JSON array (bytes + row
     * count).
     */
    private fun dumpTable(sqlName: String): Pair<ByteArray, Long> {
        val db = AppDatabase.get(getApplication()).openHelper.readableDatabase
        val rows = ArrayList<JsonElement>()
        // A retracted row must not ride out in an archive the athlete believes they cleaned up.
        // The athlete deleted it here; handing it to a coach anyway would make the delete a lie.
        val where = if (sqlName in RETRACTABLE) " WHERE deletedAt IS NULL" else ""
        db.query("SELECT * FROM `$sqlName`$where").use { c ->
            val cols = c.columnCount
            while (c.moveToNext()) {
                val obj = LinkedHashMap<String, JsonElement>(cols)
                for (i in 0 until cols) {
                    val name = c.getColumnName(i)
                    obj[name] =
                        when (c.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> JsonNull
                            android.database.Cursor.FIELD_TYPE_INTEGER ->
                                JsonPrimitive(c.getLong(i))
                            android.database.Cursor.FIELD_TYPE_FLOAT ->
                                JsonPrimitive(c.getDouble(i))
                            android.database.Cursor.FIELD_TYPE_STRING ->
                                JsonPrimitive(c.getString(i))
                            android.database.Cursor.FIELD_TYPE_BLOB ->
                                JsonPrimitive(Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP))
                            else -> JsonNull
                        }
                }
                rows.add(JsonObject(obj))
            }
        }
        val text = json.encodeToString(JsonElement.serializer(), JsonArray(rows))
        return text.toByteArray() to rows.size.toLong()
    }

    private fun readArchive(uri: Uri): ArchiveBundle {
        val input =
            getApplication<Application>().contentResolver.openInputStream(uri)
                ?: error("cannot open archive")
        val bytes = input.use { it.readBounded(MAX_ARCHIVE_BYTES) }
        val first = bytes.firstOrNull { !it.toInt().toChar().isWhitespace() }
        if (first?.toInt()?.toChar() == '{') {
            val envelope = CrocEnvelope.deserialize(bytes.decodeToString())
            require(envelope.payloadType == CrocEnvelope.BACKUP_PAYLOAD) { "unsupported .croc payload" }
            require(envelope.verify()) { "the .croc signature is invalid" }
            val archive = readArchive(java.io.ByteArrayInputStream(envelope.payload()))
            require(archive.manifest.athletePubkeyFingerprint == envelope.senderFingerprint) {
                "signed sender does not match the archive identity"
            }
            return archive.copy(signedFingerprint = envelope.senderFingerprint)
        }
        return readArchive(java.io.ByteArrayInputStream(bytes))
    }

    private fun readArchive(input: InputStream): ArchiveBundle {
        val entries = LinkedHashMap<String, ByteArray>()
        input.buffered().use { source ->
            ZipInputStream(source).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(!entry.isDirectory) { "unexpected directory: $name" }
                    require(name == "manifest.json" || TABLE_ENTRY.matches(name)) {
                        "unexpected archive entry: $name"
                    }
                    require(name !in entries) { "duplicate archive entry: $name" }
                    entries[name] = zip.readBounded(MAX_ENTRY_BYTES)
                    zip.closeEntry()
                }
            }
        }
        val manifestBytes = entries["manifest.json"] ?: error("manifest.json is missing")
        val manifest = CrocbakManifest.deserialize(manifestBytes.decodeToString())
        require(manifest.schemaVersion in 1..CrocbakManifest.CURRENT_SCHEMA_VERSION) {
            "unsupported schema ${manifest.schemaVersion}"
        }
        require(manifest.includedTables.distinct().size == manifest.includedTables.size) {
            "manifest contains duplicate tables"
        }
        require(manifest.includedTables.all { it in ALL_TABLES }) { "archive contains an unknown table" }

        val payloads = LinkedHashMap<String, ByteArray>()
        val tables = LinkedHashMap<String, JsonArray>()
        for (logical in manifest.includedTables.sorted()) {
            val bytes = entries["tables/$logical.json"] ?: error("table payload missing: $logical")
            val rows = json.parseToJsonElement(bytes.decodeToString()).jsonArray
            val expected = manifest.rowCounts?.get(logical)
            require(expected == null || expected == rows.size.toLong()) { "row count mismatch: $logical" }
            payloads[logical] = bytes
            tables[logical] = rows
        }
        require(entries.keys.all { it == "manifest.json" || it.removePrefix("tables/").removeSuffix(".json") in manifest.includedTables }) {
            "archive contains an undeclared table"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        payloads.values.forEach(digest::update)
        val checksum = "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
        require(MessageDigest.isEqual(checksum.toByteArray(), manifest.contentChecksum.toByteArray())) {
            "checksum does not match; the archive may be damaged or altered"
        }
        return ArchiveBundle(manifest, tables)
    }

    private fun ArchiveBundle.athletes(): List<Pair<String, String>> =
        tables["athlete"].orEmpty().mapNotNull { row ->
            val obj = row.jsonObject
            val id = (obj["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val name = (obj["displayName"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: "Athlete"
            id to name
        }

    private fun trustState(archive: ArchiveBundle, athleteIds: List<String>): ExchangeTrustState =
        ExchangeTrust.evaluate(
            archive.signedFingerprint,
            athleteIds.mapNotNull(trustStore::fingerprintFor),
        )

    private fun mergeArchive(archive: ArchiveBundle): String {
        val room = AppDatabase.get(getApplication())
        var inserted = 0L
        var kept = 0L
        room.runInTransaction {
            val db = room.openHelper.writableDatabase
            val ordered =
                archive.tables.keys.sortedWith(compareBy({ IMPORT_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it }))
            for (logical in ordered) {
                val sqlName = SQL_NAME[logical] ?: logical
                for (element in archive.tables.getValue(logical)) {
                    val values = ContentValues()
                    for ((column, value) in element.jsonObject) {
                        when (value) {
                            JsonNull -> values.putNull(column)
                            is JsonPrimitive ->
                                when {
                                    value.isString -> values.put(column, value.content)
                                    value.longOrNull != null -> values.put(column, value.longOrNull!!)
                                    value.doubleOrNull != null -> values.put(column, value.doubleOrNull!!)
                                    else -> error("unsupported value in $logical.$column")
                                }
                            else -> error("nested value in $logical.$column")
                        }
                    }
                    if (db.insert(sqlName, SQLiteDatabase.CONFLICT_IGNORE, values) == -1L) kept++
                    else inserted++
                }
            }
        }
        return "Imported $inserted new row(s); kept $kept existing row(s). Nothing was overwritten."
    }

    private fun InputStream.readBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "archive entry is too large" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        /**
         * App version stamped into every exported manifest, read from the build rather than copied.
         * This was a hand-maintained constant that had drifted two releases stale — it still said
         * "0.4.4" while the app shipped 0.6.0 — and every archive exported in between carries that
         * wrong version. A generated value cannot drift.
         */
        val APP_VERSION: String
            get() = BuildConfig.VERSION_NAME

        /** Suggested SAF filename for the CreateDocument picker. */
        const val SUGGESTED_FILENAME: String = "crocodyl-export.crocbak"
        const val SUGGESTED_CROC_FILENAME: String = "crocodyl-share.croc"

        /** MIME for the archive. */
        const val MIME_ZIP: String = "application/zip"
        const val MIME_CROC: String = "application/vnd.crocodyl.exchange+json"

        /** Cache subdirectory the FileProvider exposes; nothing else in the cache is shareable. */
        const val SHARE_DIR: String = "share"

        /** Every registered logical table — the requested set fed to the consent filter. */
        val ALL_TABLES: Set<String> = PrivacyRegistry.byTable.keys

        /**
         * Logical table name (PrivacyRegistry / spec §8) -> actual Room `tableName`. Only the three
         * incumbent plural tables differ; everything else is identity. Code-owned (never user
         * input), so it is safe to interpolate into the dump query.
         */
        private val SQL_NAME: Map<String, String> =
            mapOf("athlete" to "athletes", "session" to "sessions", "shot" to "shots")

        /** SQL tables carrying a `deletedAt` retraction column (see AppDatabase.MIGRATION_6_7). */
        private val RETRACTABLE: Set<String> = setOf("sessions", "score_session")

        private val TABLE_ENTRY = Regex("tables/[a-z0-9_]+\\.json")
        private const val MAX_ENTRY_BYTES = 32 * 1024 * 1024
        private const val MAX_ARCHIVE_BYTES = 64 * 1024 * 1024

        /** Parents before children so foreign-key enforcement remains active throughout import. */
        private val IMPORT_ORDER =
            listOf(
                "athlete", "rig", "session", "score_session", "checkin", "injury",
                "physio_plan", "training_plan", "shot", "score_arrow", "score_opponent_end",
                "soreness", "physio_exercise", "physio_session", "document",
                "score_candidate", "observer_score_event", "session_context",
            )
    }
}
