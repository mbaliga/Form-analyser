package xyz.mdhv.formanalyser.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.room.InvalidationTracker
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import xyz.mdhv.formanalyser.app.data.AppDatabase

/**
 * Keeps the home-screen widgets current by push, never by polling (`updatePeriodMillis` is 0).
 *
 * - A database write: Room's invalidation tracker reports the tables it touched, and only the
 *   widgets that read those tables are redrawn, debounced so a burst of arrow taps is one redraw.
 *   The observer lives in the app process and is registered only while a widget is placed.
 * - The app opening, onboarding or a data wipe: [onAppReady] redraws everything.
 * - Time: one inexact, non-waking alarm at the next boundary (local midnight, or the moment the
 *   latest check-in goes stale for readiness), plus the providers' own time, time-zone and locale
 *   broadcasts. No exact-alarm permission, no WorkManager, no network.
 * - Font size: the layouts are chosen for the current font scale, so a change redraws them (while
 *   the app process is alive; otherwise at the next of the triggers above).
 *
 * Redraws of one widget kind run one at a time, in the order they were asked for, so an older,
 * slower read can never overwrite a newer one.
 */
object WidgetRefresh {
    private const val ACTION_REFRESH = "xyz.mdhv.formanalyser.app.action.WIDGET_REFRESH"
    private const val DEBOUNCE_MS = 1_500L
    /** goAsync() allows about ten seconds; the redraws get seven, the next alarm one and a half. */
    private const val WORK_BUDGET_MS = 7_000L
    private const val BOUNDARY_BUDGET_MS = 1_500L
    /** How late the inexact boundary alarm may fire, on every API level (setWindow). */
    internal const val ALARM_WINDOW_MS = 10 * 60_000L

    /**
     * A redraw that fails must never take the app down; the widget keeps its last views until the
     * next one. Nothing is reported anywhere (no telemetry), so the handler simply drops it.
     */
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })
    private val lock = Any()
    private var watched: AppDatabase? = null
    private var observer: InvalidationTracker.Observer? = null
    private val dirty = mutableSetOf<WidgetKind>()
    private var debounce: Job? = null
    private val kindLocks: Map<WidgetKind, Mutex> = WidgetKind.entries.associateWith { Mutex() }
    private var configuration: ComponentCallbacks? = null
    private var fontScale: Float? = null

    internal fun provider(kind: WidgetKind): Class<out CrocodylWidgetProvider> =
        when (kind) {
            WidgetKind.LAST_SCORECARD -> LastScorecardWidget::class.java
            WidgetKind.THIS_WEEK -> ThisWeekWidget::class.java
            WidgetKind.NEXT_PHYSIO -> NextPhysioWidget::class.java
            WidgetKind.READINESS -> ReadinessWidget::class.java
            WidgetKind.ACTIVE_RIG -> ActiveRigWidget::class.java
            WidgetKind.QUICK_START -> QuickStartWidget::class.java
        }

    fun ids(context: Context, kind: WidgetKind): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider(kind)))

    private fun placedKinds(context: Context): Set<WidgetKind> =
        WidgetKind.entries.filter { ids(context, it).isNotEmpty() }.toSet()

    /**
     * The app is past its database gate (MainShell is showing): clear any remembered open failure,
     * make sure database writes reach the widgets, and redraw them with what the app now sees.
     */
    fun onAppReady(context: Context) {
        val app = context.applicationContext
        scope.launch {
            WidgetDatabase.clearFailure(app)
            refresh(app, WidgetKind.entries.toSet())
        }
    }

    /** For a provider's goAsync(): redraws [ids] of [kind], then calls [done], within the budget. */
    internal fun updateAsync(context: Context, kind: WidgetKind, ids: IntArray, done: () -> Unit) {
        val app = context.applicationContext
        scope.launch {
            try {
                withTimeoutOrNull(WORK_BUDGET_MS) { redraw(app, kind, ids) }
            } finally {
                nextBoundary(app)
                done()
            }
        }
    }

    internal fun refreshAsync(context: Context, done: () -> Unit) {
        val app = context.applicationContext
        scope.launch {
            try {
                withTimeoutOrNull(WORK_BUDGET_MS) { redrawAll(app, WidgetKind.entries.toSet()) }
            } finally {
                nextBoundary(app)
                done()
            }
        }
    }

    private suspend fun refresh(context: Context, kinds: Set<WidgetKind>) {
        try {
            redrawAll(context, kinds)
        } finally {
            nextBoundary(context)
        }
    }

    private suspend fun redrawAll(context: Context, kinds: Set<WidgetKind>) {
        // One widget failing to redraw must not stop the others.
        kinds.forEach { kind ->
            try {
                redraw(context, kind, ids(context, kind))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                // Keep the widget's last views; the next write or boundary tries again.
            }
        }
    }

    private suspend fun redraw(context: Context, kind: WidgetKind, ids: IntArray) {
        if (ids.isEmpty()) return
        // Reads start in the order the redraws were asked for (Mutex is fair), and each one reads
        // the database and the clock only once it holds the lock.
        kindLocks.getValue(kind).withLock {
            val now = ZonedDateTime.now()
            val content = WidgetSource.load(context, kind, now)
            val manager = AppWidgetManager.getInstance(context)
            ids.forEach { id ->
                manager.updateAppWidget(id, WidgetRenderer.views(context, manager, id, kind, content, now))
            }
        }
    }

    /**
     * Sets the next boundary alarm (or clears everything once no widget is placed). It runs even
     * when the redraw before it ran out of time or failed: without it, every dated widget would
     * wait for the app to be opened.
     */
    private suspend fun nextBoundary(context: Context) {
        withContext(NonCancellable) {
            withTimeoutOrNull(BOUNDARY_BUDGET_MS) {
                runCatching { afterUpdate(context) }
            }
        }
    }

    private suspend fun afterUpdate(context: Context) {
        val placed = placedKinds(context)
        if (placed.isEmpty()) {
            cancelAlarm(context)
            unwatch(context)
            return
        }
        // Midnight first: it needs no database, so it is set even if the read below is slow.
        schedule(context, WidgetDates.nextRefreshAtMs(ZonedDateTime.now(), null))
        watchConfiguration(context)
        watch(context)
        if (WidgetKind.READINESS in placed) {
            val stale = WidgetSource.readinessStaleAtMs(context)
            // FLAG_UPDATE_CURRENT: the earlier boundary replaces the midnight alarm.
            if (stale != null) schedule(context, WidgetDates.nextRefreshAtMs(ZonedDateTime.now(), stale))
        }
    }

    /**
     * The layouts are chosen for the font scale they were drawn at (WidgetSizing.keys), so a
     * change of the system font size redraws every widget. Registered once while widgets are
     * placed; it only lives as long as the app process.
     */
    private fun watchConfiguration(context: Context) {
        val app = context.applicationContext
        val added =
            synchronized(lock) {
                if (configuration != null) return
                fontScale = app.resources.configuration.fontScale
                object : ComponentCallbacks {
                        override fun onConfigurationChanged(newConfig: Configuration) {
                            val changed =
                                synchronized(lock) {
                                    (fontScale != newConfig.fontScale).also { fontScale = newConfig.fontScale }
                                }
                            if (changed) requestSoon(app, WidgetKind.entries.toSet())
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onLowMemory() = Unit
                    }
                    .also { configuration = it }
            }
        app.registerComponentCallbacks(added)
    }

    /**
     * Registers the invalidation observer on the current database instance, once per instance (a
     * full data wipe replaces the instance). Room is called outside [lock], so its notifier threads,
     * which take [lock] in [requestSoon], can never wait on a Room call made while holding it.
     */
    private fun watch(context: Context) {
        val db = WidgetDatabase.openOrNull(context) ?: return
        val added =
            object : InvalidationTracker.Observer(WidgetTables.all.toTypedArray()) {
                override fun onInvalidated(tables: Set<String>) {
                    requestSoon(context, WidgetTables.kindsFor(tables))
                }
            }
        val previous =
            synchronized(lock) {
                if (watched === db) return
                (watched to observer).also {
                    watched = db
                    observer = added
                }
            }
        remove(previous)
        db.invalidationTracker.addObserver(added)
    }

    /** With no widget placed, stop observing so Room drops its triggers for these tables. */
    private fun unwatch(context: Context) {
        val (previous, callbacks) =
            synchronized(lock) {
                ((watched to observer) to configuration).also {
                    watched = null
                    observer = null
                    configuration = null
                }
            }
        remove(previous)
        callbacks?.let { runCatching { context.applicationContext.unregisterComponentCallbacks(it) } }
    }

    private fun remove(registration: Pair<AppDatabase?, InvalidationTracker.Observer?>) {
        val (db, current) = registration
        if (db != null && current != null) runCatching { db.invalidationTracker.removeObserver(current) }
    }

    private fun requestSoon(context: Context, kinds: Set<WidgetKind>) {
        if (kinds.isEmpty()) return
        synchronized(lock) {
            dirty += kinds
            if (debounce?.isActive == true) return
            debounce =
                scope.launch {
                    delay(DEBOUNCE_MS)
                    // Writes that land while this redraw runs start the next debounce window.
                    val due =
                        synchronized(lock) {
                            debounce = null
                            dirty.toSet().also { dirty.clear() }
                        }
                    refresh(context, due)
                }
        }
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * One inexact RTC alarm: it does not wake the device and needs no exact-alarm permission. A
     * window, not set(): on API 26-30 set() may defer an alarm by up to 75% of the time until it
     * is due, so one set at 09:00 for midnight could fire mid-morning the next day.
     */
    private fun schedule(context: Context, atMs: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.setWindow(AlarmManager.RTC, atMs, ALARM_WINDOW_MS, alarmIntent(context))
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context))
    }

    internal fun onWidgetsRemoved(context: Context) {
        val app = context.applicationContext
        scope.launch {
            if (placedKinds(app).isEmpty()) {
                cancelAlarm(app)
                unwatch(app)
            }
        }
    }

    internal fun isRefreshAction(intent: Intent?) = intent?.action == ACTION_REFRESH
}

/** Receives the app's own boundary alarm. Not exported: only this app's PendingIntent reaches it. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!WidgetRefresh.isRefreshAction(intent)) return
        val pending = goAsync()
        WidgetRefresh.refreshAsync(context) { pending.finish() }
    }
}
