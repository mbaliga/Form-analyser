package xyz.mdhv.formanalyser.exchange

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImportColumnGuardTest {

    private val athleteColumns =
        setOf("id", "displayName", "bodyMassKg", "handedness", "drawLengthMm", "avatarSeed", "club", "pubkey")

    private fun rejected(vararg keys: String, columns: Set<String> = athleteColumns): ImportColumnGuard.Result.Rejected {
        val result = ImportColumnGuard.validateRow("athletes", columns, keys.toList())
        return assertIs<ImportColumnGuard.Result.Rejected>(result, "expected ${keys.toList()} to be rejected")
    }

    // --- the payloads that were shown to work against the unquoted INSERT -----------------------

    @Test
    fun rejectsOnConflictUpsertInColumnName() {
        val hostile = "id) VALUES (?) ON CONFLICT(id) DO UPDATE SET displayName=? --"
        val result = rejected("id", hostile)
        assertEquals("athletes", result.table)
        assertTrue("ON CONFLICT" !in result.reason, "hostile text must not be echoed: ${result.reason}")
    }

    @Test
    fun rejectsSubselectExfiltrationColumnName() {
        rejected("id", "displayName) SELECT id, note, 0 FROM mood_entry --")
        rejected("(SELECT note FROM mood_entry)")
    }

    @Test
    fun rejectsStatementTerminatorInColumnName() {
        rejected("id; DROP TABLE athletes")
        rejected("id;")
    }

    // --- anything that is not a plain identifier ------------------------------------------------

    @Test
    fun rejectsEmptyColumnName() {
        rejected("")
    }

    @Test
    fun rejectsBacktick() {
        rejected("a`b")
        rejected("id`")
    }

    @Test
    fun rejectsDoubleQuote() {
        rejected("id\"")
        rejected("\"id\"")
    }

    @Test
    fun rejectsSingleQuote() {
        rejected("id'")
        rejected("'id'")
    }

    @Test
    fun rejectsSpace() {
        rejected("display Name")
        rejected(" id")
    }

    @Test
    fun rejectsUnicode() {
        rejected("idé")
        rejected("id．")
        rejected("ｉｄ")
    }

    @Test
    fun rejectsTrailingNewlineAndControlCharacters() {
        rejected("id\n")
        rejected("id\u0000")
        rejected("id\t")
    }

    // --- must be a real column, spelled exactly -------------------------------------------------

    @Test
    fun rejectsUnknownColumn() {
        val result = rejected("id", "notAColumn")
        assertTrue("notAColumn" in result.reason)
    }

    @Test
    fun rejectsCaseOnlyDifference() {
        rejected("ID")
        rejected("DisplayName")
    }

    @Test
    fun rejectsDuplicateKeysDifferingByCase() {
        // Both spellings would need to be real columns to get past membership, so make the table
        // genuinely contain them: the case-collision rule is independent of the membership rule.
        rejected("id", "Id", columns = athleteColumns + "Id")
    }

    @Test
    fun rejectsExactDuplicateKeys() {
        rejected("id", "club", "id")
    }

    // --- legitimate archives --------------------------------------------------------------------

    @Test
    fun acceptsSubsetOfRealColumns() {
        // An archive written by an older build carries fewer columns than today's table.
        val result = ImportColumnGuard.validateRow("athletes", athleteColumns, listOf("id", "displayName", "bodyMassKg"))
        assertEquals(ImportColumnGuard.Result.Ok(listOf("id", "displayName", "bodyMassKg")), result)
    }

    @Test
    fun acceptsAllValidColumns() {
        val keys = listOf("pubkey", "id", "club", "displayName", "bodyMassKg", "handedness", "drawLengthMm", "avatarSeed")
        val result = ImportColumnGuard.validateRow("athletes", athleteColumns, keys)
        assertEquals(ImportColumnGuard.Result.Ok(keys), result, "the row's own key order is preserved")
    }

    @Test
    fun acceptsUnderscoreAndDigitColumns() {
        val result = ImportColumnGuard.validateRow("t", setOf("a_1", "B2"), listOf("a_1", "B2"))
        assertIs<ImportColumnGuard.Result.Ok>(result)
    }

    // --- whole tables ---------------------------------------------------------------------------

    @Test
    fun validateTableFailsOnTheFirstBadRowEvenWhenEarlierRowsAreFine() {
        val rows =
            listOf(
                listOf("id", "displayName"),
                listOf("id", "displayName) SELECT 1 --"),
                listOf("id", "alsoNotAColumn"),
            )
        val result = ImportColumnGuard.validateTable("athletes", athleteColumns, rows)
        val rejected = assertIs<ImportColumnGuard.Result.Rejected>(result)
        assertTrue("alsoNotAColumn" !in rejected.reason, "the first rejection wins")
    }

    @Test
    fun validateTableAcceptsGoodRowsAndAnEmptyTable() {
        assertIs<ImportColumnGuard.Result.Ok>(
            ImportColumnGuard.validateTable("athletes", athleteColumns, listOf(listOf("id"), listOf("id", "club")))
        )
        assertEquals(
            ImportColumnGuard.Result.Ok(emptyList()),
            ImportColumnGuard.validateTable("athletes", athleteColumns, emptyList()),
        )
    }
}
