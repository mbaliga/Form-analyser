package xyz.mdhv.formanalyser.app.data

import android.content.Context
import java.io.File

/**
 * Records the "we had to reset your database" event so the UI can disclose it to the athlete,
 * instead of the recovery in [AppDatabase.openResilient] happening silently. Deliberately plain
 * [android.content.SharedPreferences] rather than the app's usual DataStore ([AppPrefs]): this runs
 * synchronously from a non-suspend context, before any Activity/Compose tree exists, so a
 * coroutine-based store isn't available (or appropriate) here.
 */
object DbRecovery {
    private const val PREFS_NAME = "crocodyl_db_recovery"
    private const val KEY_RESET_AT_MS = "lastDbResetAtMs"
    private const val KEY_BACKUP_PATH = "lastDbResetBackupPath"

    /**
     * Best-effort copy of the database file (and its `-wal`/`-shm` siblings, if present) into a
     * timestamped folder under [recoveryDir], so the bytes survive even though the live database is
     * about to be deleted and rebuilt. Never throws — a failed backup must not block recovery from
     * proceeding; it just means nothing was preserved this time.
     *
     * @return the backup directory's absolute path, or null if nothing could be backed up.
     */
    fun backUpBeforeReset(context: Context, dbName: String): String? = runCatching {
        val live = context.getDatabasePath(dbName)
        if (!live.exists()) return@runCatching null

        val dest = File(recoveryDir(context), System.currentTimeMillis().toString()).apply { mkdirs() }
        var copiedAny = false
        for (suffix in listOf("", "-wal", "-shm")) {
            val src = File(live.parentFile, dbName + suffix)
            if (src.exists()) {
                src.copyTo(File(dest, dbName + suffix), overwrite = true)
                copiedAny = true
            }
        }
        if (copiedAny) dest.absolutePath else null
    }.getOrNull()

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
