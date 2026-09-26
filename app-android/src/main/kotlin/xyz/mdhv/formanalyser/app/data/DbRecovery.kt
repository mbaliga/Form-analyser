package xyz.mdhv.formanalyser.app.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Records the "we reset your database after your confirmation" event so the UI can disclose it to
 * the athlete. Deliberately plain
 * [android.content.SharedPreferences] rather than the app's usual DataStore ([AppPrefs]): this
 * boundary must remain synchronous and usable while the normal database/app state is unavailable.
 */
object DbRecovery {
    private const val PREFS_NAME = "crocodyl_db_recovery"
    private const val KEY_RESET_AT_MS = "lastDbResetAtMs"
    private const val KEY_BACKUP_PATH = "lastDbResetBackupPath"
    private const val BACKUP_MANIFEST = "backup.sha256"
    private val DB_SUFFIXES = listOf("", "-wal", "-shm")

    /**
     * Copy the database file (and its `-wal`/`-shm` siblings, if present) into a timestamped folder
     * under [recoveryDir]. The copy is first written to a hidden staging folder and published only
     * after at least the main database file has been copied. A failed backup returns null; callers
     * must refuse to delete or reset the live database in that case.
     *
     * @return the complete backup directory's absolute path, or null if it could not be preserved.
     */
    fun backUpBeforeReset(context: Context, dbName: String): String? = runCatching {
        val live = context.getDatabasePath(dbName)
        if (!live.isFile || live.length() == 0L) return@runCatching null

        val stamp = "${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}"
        val staging = File(recoveryDir(context), ".$stamp.partial").apply { mkdirs() }
        var copiedMain = false
        val copied = mutableListOf<File>()
        for (suffix in DB_SUFFIXES) {
            val src = File(live.parentFile, dbName + suffix)
            if (src.exists()) {
                val dest = File(staging, dbName + suffix)
                src.copyTo(dest, overwrite = true)
                copied += dest
                if (suffix.isEmpty()) copiedMain = true
            }
        }
        if (!copiedMain) {
            staging.deleteRecursively()
            return@runCatching null
        }
        // The manifest is written inside the hidden staging directory and becomes visible only
        // with the directory rename below. A reset can therefore require both a complete file set
        // and matching SHA-256 digests, rather than trusting file existence alone.
        File(staging, BACKUP_MANIFEST).writeText(
            copied.sortedBy { it.name }.joinToString("\n") { "${sha256(it)}  ${it.name}" } + "\n"
        )
        val dest = File(recoveryDir(context), stamp)
        if (!staging.renameTo(dest)) {
            staging.deleteRecursively()
            return@runCatching null
        }
        dest.absolutePath
    }.getOrNull()

    /** A reset is allowed only when the original database bytes are actually preserved. */
    fun isUsableBackup(path: String?, dbName: String): Boolean {
        val dir = path?.takeIf { it.isNotBlank() }?.let(::File) ?: return false
        val db = File(dir, dbName)
        if (!dir.isDirectory || !db.isFile || db.length() == 0L) return false
        val manifest = File(dir, BACKUP_MANIFEST)
        if (!manifest.isFile || manifest.length() == 0L) return false
        val allowed = DB_SUFFIXES.map { dbName + it }.toSet()
        val entries = runCatching {
            manifest.readLines().filter { it.isNotBlank() }.associate { line ->
                val split = line.split("  ", limit = 2)
                require(split.size == 2)
                val digest = split[0]
                val name = split[1]
                require(digest.matches(Regex("[0-9a-f]{64}")))
                require(name in allowed)
                name to digest
            }
        }.getOrNull() ?: return false
        if (entries.isEmpty() || dbName !in entries.keys) return false
        return entries.all { (name, expected) ->
            val file = File(dir, name)
            file.isFile && sha256(file) == expected
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun recoveryDir(context: Context): File =
        File(context.filesDir, "db-recovery").apply { mkdirs() }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun markReset(context: Context, backedUpTo: String?) {
        prefs(context).edit()
            .putLong(KEY_RESET_AT_MS, System.currentTimeMillis())
            .apply { if (backedUpTo != null) putString(KEY_BACKUP_PATH, backedUpTo) else remove(KEY_BACKUP_PATH) }
            .apply()
    }

    /** Non-null iff a reset has happened that the athlete hasn't been shown/dismissed yet. */
    data class ResetEvent(val resetAtMs: Long, val backupPath: String?)

    fun pendingNotice(context: Context): ResetEvent? {
        val p = prefs(context)
        val at = p.getLong(KEY_RESET_AT_MS, -1L)
        if (at < 0) return null
        return ResetEvent(at, p.getString(KEY_BACKUP_PATH, null))
    }

    /** Call once the athlete has seen the notice, so it doesn't reappear on the next launch. */
    fun dismissNotice(context: Context) {
        prefs(context).edit().remove(KEY_RESET_AT_MS).remove(KEY_BACKUP_PATH).apply()
    }
}
