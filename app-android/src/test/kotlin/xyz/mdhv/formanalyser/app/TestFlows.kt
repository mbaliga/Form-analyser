package xyz.mdhv.formanalyser.app

import android.os.Looper
import kotlinx.coroutines.flow.StateFlow
import org.robolectric.Shadows.shadowOf

/**
 * Block the test thread until this [StateFlow] holds a value matching [predicate], or fail after
 * [timeoutMs].
 *
 * The ViewModels under test dispatch their mutating work via `viewModelScope.launch { withContext
 * (Dispatchers.IO) { ... } }` -- real IO, not a virtual-time test dispatcher -- and resume on
 * `Dispatchers.Main`, which under Robolectric is the (paused-by-default) main Looper that the JUnit
 * thread itself is running on. Blocking that thread without draining the Looper would therefore
 * deadlock the resumption the test is waiting for, so this polls instead: drain whatever the IO
 * threads have posted back to Main, check the predicate, and sleep briefly. It needs no test-only
 * coroutine dependency (the module's strict dependency lockfile deliberately carries none).
 */
fun <T> StateFlow<T>.awaitUntil(timeoutMs: Long = 5_000, predicate: (T) -> Boolean): T {
    val deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000L
    while (true) {
        shadowOf(Looper.getMainLooper()).idle()
        val current = value
        if (predicate(current)) return current
        check(System.nanoTime() < deadlineNanos) {
            "Timed out after ${timeoutMs}ms waiting for a matching state; last value was: $current"
        }
        Thread.sleep(10)
    }
}
