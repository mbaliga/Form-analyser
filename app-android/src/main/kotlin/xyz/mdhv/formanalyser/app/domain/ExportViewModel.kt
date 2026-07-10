package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.exchange.AndroidKeyProvider
import xyz.mdhv.formanalyser.exchange.ConsentDecision
import xyz.mdhv.formanalyser.exchange.ConsentFilter
import xyz.mdhv.formanalyser.exchange.CrocbakManifest
import xyz.mdhv.formanalyser.exchange.ExportTier
import xyz.mdhv.formanalyser.wellness.PrivacyClass
import xyz.mdhv.formanalyser.wellness.PrivacyRegistry
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Phase 5 export ceremony (over `core-exchange`). Drives the tier/grant selection, computes the
 * live [ConsentDecision] preview, and — on confirm — assembles a `.crocbak` zip to a caller-supplied
 * SAF [Uri].
 *
 * Privacy enforcement is delegated entirely to [ConsentFilter]/[PrivacyRegistry]: this VM only ever
 * reads rows from tables the decision cleared, so PRIVATE tables are never even queried, and MEDICAL
 * tables are read only when the athlete granted them. The manifest's `createdAtMs` is stamped here
 * (`System.currentTimeMillis()`) and passed IN to the pure core, per the core's determinism contract.
 */
class ExportViewModel(app: Application) : AndroidViewModel(app) {

    private val keyProvider = AndroidKeyProvider()
    private val json = Json { prettyPrint = false; encodeDefaults = true }

    /** Result of the last export attempt (null before any attempt). */
    data class ExportOutcome(val ok: Boolean, val message: String)

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

    /** MEDICAL tables the athlete may grant, per the registry (e.g. medication_entry, document). */
    val medicalTables: List<String> = PrivacyRegistry.medicalTables().sorted()

    fun load() {
        if (_fingerprint.value != null) return
        viewModelScope.launch {
            _fingerprint.value = withContext(Dispatchers.IO) {
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
            _outcome.value = result.fold(
                onSuccess = { ExportOutcome(true, it) },
                onFailure = { ExportOutcome(false, "Export failed: ${it.message ?: it.javaClass.simpleName}") },
            )
        }
    }

    private fun writeArchive(uri: Uri): String {
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

        val fingerprint = _fingerprint.value
            ?: keyProvider.identity().fingerprint.also { _fingerprint.value = it }

        val manifest = CrocbakManifest.fromDecision(
            appVersion = APP_VERSION,
            createdAtMs = System.currentTimeMillis(),
            athletePubkeyFingerprint = fingerprint,
            tier = tier,
            decision = decision,
            contentChecksum = checksum,
            rowCounts = rowCounts,
        )

        val out = getApplication<Application>().contentResolver.openOutputStream(uri)
            ?: error("cannot open destination")
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

    /** Generic `SELECT *` dump of a fixed, code-owned table name to a JSON array (bytes + row count). */
    private fun dumpTable(sqlName: String): Pair<ByteArray, Long> {
        val db = AppDatabase.get(getApplication()).openHelper.readableDatabase
        val rows = ArrayList<JsonElement>()
        db.query("SELECT * FROM `$sqlName`").use { c ->
            val cols = c.columnCount
            while (c.moveToNext()) {
                val obj = LinkedHashMap<String, JsonElement>(cols)
                for (i in 0 until cols) {
                    val name = c.getColumnName(i)
                    obj[name] = when (c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> JsonNull
                        android.database.Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(c.getLong(i))
                        android.database.Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(c.getDouble(i))
                        android.database.Cursor.FIELD_TYPE_STRING -> JsonPrimitive(c.getString(i))
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

    companion object {
        /**
         * App version stamped into the manifest. Mirrors `versionName` in the app build script;
         * BuildConfig is not enabled for this module, so it is a constant. (Integration note: if
         * `buildConfig` is turned on, swap this for `BuildConfig.VERSION_NAME`.)
         */
        const val APP_VERSION: String = "0.4.4"

        /** Suggested SAF filename for the CreateDocument picker. */
        const val SUGGESTED_FILENAME: String = "crocodyl-export.crocbak"

        /** MIME for the archive. */
        const val MIME_ZIP: String = "application/zip"

        /** Every registered logical table — the requested set fed to the consent filter. */
        val ALL_TABLES: Set<String> = PrivacyRegistry.byTable.keys

        /**
         * Logical table name (PrivacyRegistry / spec §8) -> actual Room `tableName`. Only the three
         * incumbent plural tables differ; everything else is identity. Code-owned (never user input),
         * so it is safe to interpolate into the dump query.
         */
        private val SQL_NAME: Map<String, String> = mapOf(
            "athlete" to "athletes",
            "session" to "sessions",
            "shot" to "shots",
        )
    }
}
