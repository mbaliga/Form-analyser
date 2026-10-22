package xyz.mdhv.formanalyser.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStartPolicyTest {
    @Test
    fun acceptsOnlyWhenNoStartOrCaptureIsAlreadyInFlight() {
        assertTrue(canAcceptSessionStart(sessionStarting = false, recording = false))
        assertFalse(canAcceptSessionStart(sessionStarting = true, recording = false))
        assertFalse(canAcceptSessionStart(sessionStarting = false, recording = true))
        assertFalse(canAcceptSessionStart(sessionStarting = true, recording = true))
    }
}
