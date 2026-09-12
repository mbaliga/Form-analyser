package xyz.mdhv.formanalyser.app

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Block the test thread until this [StateFlow] holds a value matching [predicate], or fail after
 * [timeoutMs].
 *
 * The ViewModels under test dispatch their mutating work via `viewModelScope.launch { withContext
 * (Dispatchers.IO) { ... } }` — real IO, not a virtual-time test dispatcher — so the state a test
 * cares about lands asynchronously on a real background thread. `Flow.first` is the
 * dispatcher-agnostic way to wait for that: it suspends the calling coroutine until an emission
 * satisfies [predicate] and works the same whether the emission came from the Main dispatcher
 * (`Dispatchers.setMain(UnconfinedTestDispatcher())` in these tests) or a real IO thread, so it does
 * not need Robolectric's (paused-by-default) main Looper pumped by hand. [runBlocking] here blocks
 * only the JUnit test thread, not any dispatcher these ViewModels use.
 */
fun <T> StateFlow<T>.awaitUntil(timeoutMs: Long = 5_000, predicate: (T) -> Boolean): T =
    runBlocking { withTimeout(timeoutMs) { first(predicate) } }
