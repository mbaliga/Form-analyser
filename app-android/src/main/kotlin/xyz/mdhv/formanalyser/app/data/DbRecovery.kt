package xyz.mdhv.formanalyser.app.data

import android.content.Context
import java.io.File

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
        for (suffix in listOf("", "-wal", "-shm")) {
            val src = File(live.parentFile, dbName + suffix)
            if (src.exists()) {
                src.copyTo(File(staging, dbName + suffix), overwrite = true)
                if (suffix.isEmpty()) copiedMain = true
            }
        }
        if (!copiedMain) {
            staging.deleteRecursively()
            return@runCatching null
        }
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
        return dir.isDirectory && db.isFile && db.length() > 0L
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
