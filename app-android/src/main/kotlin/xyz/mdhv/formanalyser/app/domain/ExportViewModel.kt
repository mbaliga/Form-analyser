package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
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
import xyz.mdhv.formanalyser.exchange.PubkeyIdentity
import xyz.mdhv.formanalyser.wellness.PrivacyClass
import xyz.mdhv.formanalyser.wellness.PrivacyRegistry

/**
 * Phase 5 export ceremony (over `core-exchange`). Drives the tier/grant selection, computes the
 * live [ConsentDecision] preview, and — on confirm — assembles a signed `.croc` zip to a
 * caller-supplied SAF [Uri].
 *
 * Privacy enforcement is delegated entirely to [ConsentFilter]/[PrivacyRegistry]: this VM only ever
 * reads rows from tables the decision cleared, so PRIVATE tables are never even queried, and
 * MEDICAL tables are read only when the athlete granted them. The manifest's `createdAtMs` is
 * stamped here (`System.currentTimeMillis()`) and passed IN to the pure core, per the core's
 * determinism contract.
 */
class ExportViewModel @JvmOverloads constructor(
    app: Application,
    private val database: AppDatabase = AppDatabase.get(app),
    private val keyProvider: AndroidKeyProvider = AndroidKeyProvider(),
) : AndroidViewModel(app) {
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
        val newRows: Long,
        val identicalRows: Long,
        val conflictRows: Long,
    )

    private data class RowInspection(val newRows: Long, val identicalRows: Long, val conflictRows: Long)

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
     * Assemble the signed `.croc` archive into [uri] (a `CreateDocument` result). Runs off the main
     * thread; publishes the result to [outcome].
     */
    fun export(uri: Uri) {
        if (_busy.value) return
        _busy.value = true
        _outcome.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { writeArchive(uri, signed = true) } }
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
     * Previous share exports are deleted first. A .croc can carry medical rows, and leaving old
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
                        val message = ShareExportPublisher.publish(file) { writeArchive(it, signed = true) }
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

    /** Read and validate an archive without modifying the database. */
    fun inspectImport(uri: Uri) {
        if (_busy.value) return
        _busy.value = true
        _importOutcome.value = null
        _importPreview.value = null
        inspectedArchive = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val archive = readArchive(uri)
                    archive to inspectRows(archive)
                }
            }
            _busy.value = false
            result.fold(
                onSuccess = { (archive, rows) ->
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
                            newRows = rows.newRows,
                            identicalRows = rows.identicalRows,
                            conflictRows = rows.conflictRows,
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

    private fun writeArchive(uri: Uri, signed: Boolean): String {
        val app = getApplication<Application>()
        val stagingFile = SafExportPublisher.newStagingFile(app.cacheDir)
        return SafExportPublisher.publish(
            stagingFile = stagingFile,
            openDestination = { app.contentResolver.openOutputStream(uri) },
            removeIncompleteDestination = {
                DocumentsContract.deleteDocument(app.contentResolver, uri)
            },
            writeArchive = { writeArchive(it, signed = signed) },
        )
    }

    /**
     * The one archive writer. Both destinations — a SAF document the athlete picked and the cache
     * file the sharesheet hands on — go through it, so a shared archive and a saved one are
     * byte-for-byte the same ceremony and cannot drift apart in what they include.
     */
    private fun writeArchive(out: OutputStream, signed: Boolean): String {
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

        val signedManifest = if (signed) {
            manifest.copy(
                signatureAlgorithm = AndroidKeyProvider.ENVELOPE_SIGNATURE_ALGORITHM,
                publicKeyBase64 = Base64.encodeToString(keyProvider.identity().keyBytes, Base64.NO_WRAP),
                signatureBase64 = Base64.encodeToString(
                    keyProvider.signEnvelope(checksum, manifest.includedTables, manifest.tierName),
                    Base64.NO_WRAP,
                ),
            )
        } else {
            manifest
        }

        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(signedManifest.serialize().toByteArray())
            zip.closeEntry()
            for ((logical, bytes) in payloads) {
                zip.putNextEntry(ZipEntry("tables/$logical.json"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }

        val rows = rowCounts.values.sum()
        return "Exported ${payloads.size} table(s), $rows row(s) to a ${if (signed) ".croc" else ".crocbak"} archive."
    }

    /**
     * Generic `SELECT *` dump of a fixed, code-owned table name to a JSON array (bytes + row
     * count).
     */
    private fun dumpTable(sqlName: String): Pair<ByteArray, Long> {
        val db = database.openHelper.readableDatabase
        val rows = ArrayList<JsonElement>()
        // A retracted row must not ride out in an archive the athlete believes they cleaned up.
        // The athlete deleted it here; handing it to a coach anyway would make the delete a lie.
        db.query(tableDumpQuery(sqlName)).use { c ->
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
        require(manifest.includedTables.none { PrivacyRegistry.classOf(it) == PrivacyClass.PRIVATE }) {
            "archive contains a table that never leaves a device"
        }

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
        return ArchiveBundle(manifest, tables, verifiedSignerFingerprint(manifest))
    }

    /**
     * Verify the person-to-person signature a `.croc` manifest carries (see [writeArchive]) and
     * return the signer's fingerprint, or null for a legacy unsigned `.crocbak`. A manifest that
     * carries only part of a signature, or a key that does not match its declared identity, is
     * rejected outright rather than downgraded to "unsigned".
     */
    private fun verifiedSignerFingerprint(manifest: CrocbakManifest): String? {
        val signature = manifest.signatureBase64
        val publicKey = manifest.publicKeyBase64
        val algorithm = manifest.signatureAlgorithm
        if (signature == null && publicKey == null && algorithm == null) return null
        require(
            signature != null && publicKey != null &&
                algorithm == AndroidKeyProvider.ENVELOPE_SIGNATURE_ALGORITHM
        ) {
            "unsupported or incomplete .croc signature"
        }
        val keyBytes = Base64.decode(publicKey, Base64.NO_WRAP)
        val fingerprint = PubkeyIdentity.of(keyBytes).fingerprint
        require(fingerprint == manifest.athletePubkeyFingerprint) {
            "signed sender does not match the archive identity"
        }
        val verifier = Signature.getInstance(AndroidKeyProvider.SIGNATURE_ALGORITHM)
        verifier.initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes)))
        verifier.update(
            AndroidKeyProvider.envelopePayload(
                manifest.contentChecksum,
                manifest.includedTables,
                manifest.tierName,
            ).toByteArray(Charsets.UTF_8)
        )
        require(verifier.verify(Base64.decode(signature, Base64.NO_WRAP))) {
            "the .croc signature is invalid"
        }
        return fingerprint
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
        val room = database
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

    /** Compare every imported primary key before mutation so duplicates and conflicts are visible. */
    private fun inspectRows(archive: ArchiveBundle): RowInspection {
        val db = database.openHelper.readableDatabase
        var fresh = 0L
        var identical = 0L
        var conflicts = 0L
        archive.tables.forEach { (logical, rows) ->
            val table = SQL_NAME[logical] ?: logical
            val primaryKeys = mutableListOf<Pair<Int, String>>()
            db.query("PRAGMA table_info(`${table.replace("`", "``")}`)").use { schema ->
                while (schema.moveToNext()) {
                    val order = schema.getInt(5)
                    if (order > 0) primaryKeys += order to schema.getString(1)
                }
            }
            val keys = primaryKeys.sortedBy { it.first }.map { it.second }
            require(keys.isNotEmpty()) { "table has no primary key: $logical" }
            rows.forEach { element ->
                val imported = element.jsonObject
                val args = keys.map { key -> imported[key].asSqlArg(logical, key) }.toTypedArray()
                val where = keys.joinToString(" AND ") { "`${it.replace("`", "``")}` = ?" }
                db.query("SELECT * FROM `${table.replace("`", "``")}` WHERE $where LIMIT 1", args).use { cursor ->
                    if (!cursor.moveToFirst()) fresh++
                    else if (cursor.asJsonObject(imported.keys) == imported) identical++
                    else conflicts++
                }
            }
        }
        return RowInspection(fresh, identical, conflicts)
    }

    private fun JsonElement?.asSqlArg(table: String, column: String): Any {
        val value = this as? JsonPrimitive ?: error("missing primary key: $table.$column")
        return when {
            value.isString -> value.content
            value.longOrNull != null -> value.longOrNull!!
            value.doubleOrNull != null -> value.doubleOrNull!!
            else -> error("invalid primary key: $table.$column")
        }
    }

    private fun android.database.Cursor.asJsonObject(columns: Set<String>): JsonObject {
        val obj = LinkedHashMap<String, JsonElement>(columns.size)
        for (i in 0 until columnCount) {
            val name = getColumnName(i)
            if (name !in columns) continue
            obj[name] =
                when (getType(i)) {
                    android.database.Cursor.FIELD_TYPE_NULL -> JsonNull
                    android.database.Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(i))
                    android.database.Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getDouble(i))
                    android.database.Cursor.FIELD_TYPE_STRING -> JsonPrimitive(getString(i))
                    android.database.Cursor.FIELD_TYPE_BLOB -> JsonPrimitive(Base64.encodeToString(getBlob(i), Base64.NO_WRAP))
                    else -> JsonNull
                }
        }
        return JsonObject(obj)
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
        const val SUGGESTED_FILENAME: String = "crocodyl-export.croc"

        /**
         * MIME for Crocodyl's portable archive. Keep this vendor type rather than
         * `application/zip`: Android's DocumentsUI otherwise appends `.zip` to the requested
         * `.croc` filename, hiding the artifact type from users and import tooling.
         */
        const val MIME_CROC: String = "application/vnd.crocodyl.croc"

        /** MIME used when picking a plain zip-form archive to import. */
        const val MIME_ZIP: String = "application/zip"

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

        /** SQL tables carrying a `deletedAt` retraction column (see AppDatabase migrations). */
        private val RETRACTABLE: Set<String> =
            setOf(
                "sessions",
                "score_session",
                "sport_session",
                "checkin",
                "pain_log",
                "injury",
            )

        /** Build the fixed-name cursor query, excluding retracted rows wherever the schema allows it. */
        internal fun tableDumpQuery(sqlName: String): String {
            require(sqlName.matches(Regex("[a-z_]+"))) { "Unexpected export table name" }
            val where = if (sqlName in RETRACTABLE) " WHERE deletedAt IS NULL" else ""
            return "SELECT * FROM `$sqlName`$where"
        }

        private val TABLE_ENTRY = Regex("tables/[a-z0-9_]+\\.json")
        private const val MAX_ENTRY_BYTES = 32 * 1024 * 1024
        private const val MAX_ARCHIVE_BYTES = 64 * 1024 * 1024

        /** Parents before children so foreign-key enforcement remains active throughout import. */
        private val IMPORT_ORDER =
            listOf(
                "athlete", "rig", "session", "score_session", "checkin", "injury", "pain_log", "sport_session",
                "physio_plan", "training_plan", "shot", "score_arrow", "score_opponent_end",
                "soreness", "physio_exercise", "physio_session", "document",
                "score_candidate", "observer_score_event", "session_context",
            )
    }
}
