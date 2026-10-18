package xyz.mdhv.formanalyser.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportViewModelQueryTest {
    @Test
    fun retractedRowsAreFilteredFromEveryRetractableTableDump() {
        val retractableTables =
            setOf("sessions", "score_session", "sport_session", "checkin", "pain_log", "injury")

        retractableTables.forEach { table ->
            assertEquals(
                "SELECT * FROM `$table` WHERE deletedAt IS NULL",
                ExportViewModel.tableDumpQuery(table),
            )
        }
    }

    @Test
    fun tablesWithoutRetractionColumnsAreDumpedWithoutTombstonePredicate() {
        assertEquals("SELECT * FROM `athletes`", ExportViewModel.tableDumpQuery("athletes"))
    }

    @Test
    fun tableNameCannotInjectSqlIntoTheArchiveQuery() {
        assertThrows(IllegalArgumentException::class.java) {
            ExportViewModel.tableDumpQuery("sessions WHERE 1=1; DROP TABLE athletes")
        }
    }
}
