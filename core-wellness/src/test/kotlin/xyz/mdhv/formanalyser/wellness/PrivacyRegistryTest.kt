package xyz.mdhv.formanalyser.wellness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrivacyRegistryTest {
    @Test fun privateTablesAreExactlyCycleMoodLifeEvent() {
        assertEquals(setOf("mood_entry", "life_event", "cycle_entry"), PrivacyRegistry.privateTables())
    }

    @Test fun medicationIsMedical() {
        assertEquals(PrivacyClass.MEDICAL, PrivacyRegistry.classOf("medication_entry"))
    }

    @Test fun coreTablesShareable() {
        listOf("athlete", "session", "shot", "rig", "checkin", "soreness").forEach {
            assertEquals(PrivacyClass.SHAREABLE, PrivacyRegistry.classOf(it), "$it should be shareable")
        }
    }

    @Test fun everyRegisteredTableHasAClass() {
        assertTrue(PrivacyRegistry.byTable.values.all { it in PrivacyClass.entries })
    }
}
