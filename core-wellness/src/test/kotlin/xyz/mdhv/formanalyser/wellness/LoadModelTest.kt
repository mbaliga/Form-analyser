package xyz.mdhv.formanalyser.wellness

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadModelTest {
    private val d0 = LocalDate.of(2026, 1, 5)

    @Test fun shotLoadPoundageResolutionChain() {
        // session poundage wins
        assertEquals(40.0, LoadModel.resolvePoundageLbs(SessionLoad(d0, 60, sessionPoundageLbs = 40.0, athleteActivePoundageLbs = 38.0)))
        // falls back to athlete active
        assertEquals(38.0, LoadModel.resolvePoundageLbs(SessionLoad(d0, 60, sessionPoundageLbs = null, athleteActivePoundageLbs = 38.0)))
        // neither → null (excluded)
        assertNull(LoadModel.resolvePoundageLbs(SessionLoad(d0, 60, sessionPoundageLbs = null, athleteActivePoundageLbs = null)))
        // kg conversion in shot load
        assertEquals(72 * 40.0 * 0.45359237, LoadModel.shotLoadKg(72, 40.0), 1e-9)
    }

    @Test fun incompleteSessionsExcludedAndFlagged() {
        val day = listOf(
            SessionLoad(d0, arrows = 60, sessionPoundageLbs = 40.0),   // complete
            SessionLoad(d0, arrows = 30, sessionPoundageLbs = null),   // no poundage → excluded
        )
        val daily = LoadModel.dailyLoads(day)
        assertEquals(1, daily.size)
        assertEquals(LoadModel.shotLoadKg(60, 40.0), daily[0].shotLoad, 1e-9) // only the complete session
        assertFalse(daily[0].complete)                                        // day flagged
    }

    @Test fun srpeAndCompleteDay() {
        val daily = LoadModel.dailyLoads(listOf(SessionLoad(d0, 60, 40.0, durationMin = 90.0, rpe = 6.0)))
        assertEquals(90.0 * 6.0, daily[0].srpeLoad, 1e-9)
        assertTrue(daily[0].complete)
    }
}
