package xyz.mdhv.formanalyser.exchange

/**
 * Allow-list for the column names of an archive row, checked against the real table before a single
 * byte is written.
 *
 * A `.crocbak` row is a JSON object, and its keys become SQL column names. Column names are
 * identifiers, never free text, but nothing used to check them: the framework's
 * `SQLiteDatabase.insertWithOnConflict` concatenates each `ContentValues` key into the INSERT
 * statement unquoted, so a key such as `id) VALUES (...) ON CONFLICT(id) DO UPDATE SET ... --`
 * compiles as part of the statement. That turns the import's "existing rows win, nothing is
 * overwritten" promise into an overwrite, and a sub-select in a key can copy a PRIVATE table into a
 * new row. Table names were already allow-listed; this closes the matching gap for columns.
 *
 * The rule is deliberately strict and fail-closed. A key is accepted only if it is a non-empty run of
 * `[A-Za-z0-9_]` AND is, character for character, one of the table's actual columns. There is no case
 * folding (`ID` is not `id`), so a key cannot smuggle a second spelling of a column. An unknown key is
 * either a newer-schema field this build cannot place safely or an attack; either way the whole archive
 * is refused. An older archive that carries only some of the real columns is fine.
 *
 * Pure Kotlin on purpose: the Android layer reads `PRAGMA table_info` and passes the names in, so the
 * rule is exercised by plain JVM tests, including the exact payloads that were shown to work.
 */
object ImportColumnGuard {

    sealed interface Result {
        /** Every key is a real column; [columns] keeps the row's own key order. */
        data class Ok(val columns: List<String>) : Result

        /** The archive must be refused. [reason] is safe to show; it never echoes a key that is not a plain identifier. */
        data class Rejected(val table: String, val reason: String) : Result
    }

    private val IDENTIFIER = Regex("[A-Za-z0-9_]+")

    /** Validate the keys of ONE row against [actualColumns] (exact names from `PRAGMA table_info`). */
    fun validateRow(table: String, actualColumns: Set<String>, rowKeys: List<String>): Result {
        val seenExact = HashSet<String>()
        val seenFolded = HashSet<String>()
        for (key in rowKeys) {
            if (key.isEmpty()) return Result.Rejected(table, "empty column name")
            if (!IDENTIFIER.matches(key)) {
                return Result.Rejected(table, "a column name contains characters other than letters, digits and underscore")
            }
            if (key !in actualColumns) {
                return Result.Rejected(table, "column ${shown(key)} does not exist in this version of the table")
            }
            if (!seenExact.add(key)) return Result.Rejected(table, "column ${shown(key)} appears twice in one row")
            if (!seenFolded.add(key.lowercase())) {
                return Result.Rejected(table, "column ${shown(key)} collides with another column that differs only by case")
            }
        }
        return Result.Ok(rowKeys.toList())
    }

    /** Validate every row of a table; the first rejection wins. [Result.Ok.columns] is the union of the rows' keys, first-seen order. */
    fun validateTable(table: String, actualColumns: Set<String>, rows: List<List<String>>): Result {
        for (row in rows) {
            val result = validateRow(table, actualColumns, row)
            if (result is Result.Rejected) return result
        }
        return Result.Ok(rows.flatten().distinct())
    }

    /** Only ever called for a key that already passed [IDENTIFIER], so it is safe to print; clipped for length. */
    private fun shown(key: String): String = "\"${key.take(64)}${if (key.length > 64) "..." else ""}\""
}
