package xyz.mdhv.formanalyser.app.domain

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
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
            setOf("sessions", "score_session", "sport_session", "checkin", "pain_log", "injury", "document")

        SQLiteDatabase.create(null).use { database ->
            retractableTables.forEach { table ->
                assertEquals(
                    "SELECT * FROM `$table` WHERE deletedAt IS NULL",
                    ExportViewModel.tableDumpQuery(table),
                )
                database.execSQL("CREATE TABLE `$table` (id TEXT NOT NULL, deletedAt INTEGER)")
                database.insertOrThrow(table, null, ContentValues().apply {
                    put("id", "active")
                    putNull("deletedAt")
                })
                database.insertOrThrow(table, null, ContentValues().apply {
                    put("id", "retracted")
                    put("deletedAt", 123L)
                })

                database.rawQuery(ExportViewModel.tableDumpQuery(table), null).use { cursor ->
                    assertEquals("$table should export only its active row", 1, cursor.count)
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals("active", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                }
            }
        }
    }

    @Test
    fun tablesWithoutRetractionColumnsAreDumpedWithoutTombstonePredicate() {
        assertEquals("SELECT * FROM `athletes`", ExportViewModel.tableDumpQuery("athletes"))
        SQLiteDatabase.create(null).use { database ->
            database.execSQL("CREATE TABLE `athletes` (id TEXT NOT NULL)")
            database.execSQL("INSERT INTO `athletes` VALUES ('athlete-1')")
            database.rawQuery(ExportViewModel.tableDumpQuery("athletes"), null).use { cursor ->
                assertEquals(1, cursor.count)
            }
        }
    }

    @Test
    fun ownedChildrenOfRetractedParentsAreExcludedByTheDumpQuery() {
        assertEquals(
            "SELECT * FROM `shots` WHERE NOT EXISTS " +
                "(SELECT 1 FROM `sessions` p WHERE p.id = `shots`.`sessionId` AND p.deletedAt IS NOT NULL)",
            ExportViewModel.tableDumpQuery("shots"),
        )
        assertEquals(
            "SELECT * FROM `soreness` WHERE NOT EXISTS " +
                "(SELECT 1 FROM `checkin` p WHERE p.id = `soreness`.`checkinId` AND p.deletedAt IS NOT NULL)",
            ExportViewModel.tableDumpQuery("soreness"),
        )
        assertEquals(
            "SELECT * FROM `score_arrow` WHERE NOT EXISTS " +
                "(SELECT 1 FROM `score_session` p WHERE p.id = `score_arrow`.`scoreSessionId` AND p.deletedAt IS NOT NULL)",
            ExportViewModel.tableDumpQuery("score_arrow"),
        )
        // document is a parent and only a LINK child of injury: its own tombstone is the only filter.
        assertEquals(
            "SELECT * FROM `document` WHERE deletedAt IS NULL",
            ExportViewModel.tableDumpQuery("document"),
        )
        // PRIVATE raw-video table: never dumped, whatever the caller thinks the consent filter did.
        assertThrows(IllegalArgumentException::class.java) {
            ExportViewModel.tableDumpQuery("capture_media")
        }
    }

    @Test
    fun tableNameCannotInjectSqlIntoTheArchiveQuery() {
        assertThrows(IllegalArgumentException::class.java) {
            ExportViewModel.tableDumpQuery("sessions WHERE 1=1; DROP TABLE athletes")
        }
    }
}
