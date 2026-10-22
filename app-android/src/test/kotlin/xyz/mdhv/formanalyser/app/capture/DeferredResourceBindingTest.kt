package xyz.mdhv.formanalyser.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeferredResourceBindingTest {
    @Test
    fun completionAfterCloseNeverBindsOrTakesOwnership() {
        val released = mutableListOf<String>()
        var bound = false
        val owner = DeferredResourceBinding<String>(released::add)

        owner.close()
        val accepted = owner.bindIfOpen("provider") { bound = true }

        assertFalse(accepted)
        assertFalse(bound)
        assertTrue(released.isEmpty())
    }

    @Test
    fun closeReleasesAnAlreadyBoundResourceExactlyOnce() {
        val released = mutableListOf<String>()
        val owner = DeferredResourceBinding<String>(released::add)

        assertTrue(owner.bindIfOpen("provider") {})
        owner.close()
        owner.close()

        assertEquals(listOf("provider"), released)
        assertFalse(owner.bindIfOpen("late provider") {})
    }

    @Test
    fun resourceIsReleasedIfBindingThrows() {
        val released = mutableListOf<String>()
        val owner = DeferredResourceBinding<String>(released::add)

        try {
            owner.bindIfOpen("provider") { throw IllegalStateException("bind failed") }
            fail("Expected binding failure")
        } catch (expected: IllegalStateException) {
            assertEquals("bind failed", expected.message)
        }
        owner.close()

        assertEquals(listOf("provider"), released)
    }

    @Test
    fun closeWaitsForAnInProgressBindingThenReleasesIt() {
        val released = mutableListOf<String>()
        val events = mutableListOf<String>()
        val owner = DeferredResourceBinding<String>(released::add)
        val binderStarted = java.util.concurrent.CountDownLatch(1)
        val allowBinderToFinish = java.util.concurrent.CountDownLatch(1)
        val closeStarted = java.util.concurrent.CountDownLatch(1)

        val binderThread = Thread {
            owner.bindIfOpen("provider") {
                events += "bound"
                binderStarted.countDown()
                check(allowBinderToFinish.await(2, java.util.concurrent.TimeUnit.SECONDS))
            }
        }
        binderThread.start()
        assertTrue(binderStarted.await(2, java.util.concurrent.TimeUnit.SECONDS))

        val closeThread = Thread {
            closeStarted.countDown()
            owner.close()
            events += "closed"
        }
        closeThread.start()
        assertTrue(closeStarted.await(2, java.util.concurrent.TimeUnit.SECONDS))
        val closeDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2)
        while (closeThread.state != Thread.State.BLOCKED && System.nanoTime() < closeDeadline) {
            Thread.sleep(1)
        }
        assertEquals(Thread.State.BLOCKED, closeThread.state)
        allowBinderToFinish.countDown()
        binderThread.join(2_000)
        closeThread.join(2_000)

        assertFalse(binderThread.isAlive)
        assertFalse(closeThread.isAlive)
        assertEquals(listOf("bound", "closed"), events)
        assertEquals(listOf("provider"), released)
    }
}
