package xyz.mdhv.formanalyser.app.widget

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppTask
import xyz.mdhv.formanalyser.app.MainActivity
import xyz.mdhv.formanalyser.app.R
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.data.AthleteEntity
import xyz.mdhv.formanalyser.app.data.RigEntity
import xyz.mdhv.formanalyser.model.BowType
import xyz.mdhv.formanalyser.wellness.ReadinessLevel

/**
 * The paths that only run on a launcher: which layout the platform picks from the sized views at a
 * given widget size, the refresh wiring (a database write reaching a placed widget, the boundary
 * alarm), and where a tap lands depending on what is open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetRuntimeTest {
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private val now: ZonedDateTime = ZonedDateTime.now()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        AppDatabase.resetForTests()
        app.deleteDatabase("form-analyser.db")
        WidgetDatabase.clearFailure(app)
        db = AppDatabase.get(app)
    }

    @After
    fun tearDown() {
        AppDatabase.resetForTests()
        WidgetLaunch.take()
    }

    // ---------------------------------------------------------------------------------------
    // Layout choice on API 31+: the platform's own pick from the sized RemoteViews
    // ---------------------------------------------------------------------------------------

    /**
     * RemoteViews' hidden apply(context, parent, handler, size), the overload a host calls. Looked
     * up by its exact signature: other hidden overloads take a SizeF too but need a non-null
     * params object, and getMethods() returns them in no fixed order.
     */
    private fun applyAt(views: RemoteViews, widthDp: Float, heightDp: Float): View {
        val handler = Class.forName("android.widget.RemoteViews\$InteractionHandler")
        val method =
            RemoteViews::class.java.getMethod(
                "apply",
                Context::class.java,
                ViewGroup::class.java,
                handler,
                SizeF::class.java,
            )
        return method.invoke(views, app, FrameLayout(app), null, SizeF(widthDp, heightDp)) as View
    }

    private fun layoutOf(view: View): WidgetSize =
        when {
            view.findViewById<View>(R.id.widget_row1) != null -> WidgetSize.LARGE
            view.findViewById<View>(R.id.widget_detail1) != null -> WidgetSize.MEDIUM
            view.findViewById<View>(R.id.widget_label) != null -> WidgetSize.SMALL
            else -> WidgetSize.COMPACT
        }

    private fun sized(kind: WidgetKind, content: WidgetContent): RemoteViews {
        val manager = AppWidgetManager.getInstance(app)
        return WidgetRenderer.views(app, manager, 5, kind, content, now)
    }

    @Test
    fun theLauncherPicksTheLargestLayoutThatFitsTheWidget() {
        val readiness =
            WidgetContent.Readiness(ReadinessModel.of(ReadinessLevel.CAUTION, CheckinFacts(0, 3, 3, 1), 1.2, true, 0))
        val stat = sized(WidgetKind.READINESS, readiness)
        assertEquals(WidgetSize.COMPACT, layoutOf(applyAt(stat, 554f, 51f))) // 4x1 landscape
        assertEquals(WidgetSize.SMALL, layoutOf(applyAt(stat, 130f, 102f))) // 2x1 portrait
        assertEquals(WidgetSize.MEDIUM, layoutOf(applyAt(stat, 276f, 102f))) // 4x1 portrait
        assertEquals(WidgetSize.MEDIUM, layoutOf(applyAt(stat, 276f, 230f))) // too short for large
        assertEquals(WidgetSize.LARGE, layoutOf(applyAt(stat, 276f, 337f))) // 4x3 portrait

        val rig =
            WidgetContent.ActiveRig(RigModel("Rig", BowType.RECURVE, null, null, emptyList()))
        val card = sized(WidgetKind.ACTIVE_RIG, rig)
        // Five stacked lines do not fit a 4x1, so the card stays small there.
        assertEquals(WidgetSize.SMALL, layoutOf(applyAt(card, 276f, 102f)))
        assertEquals(WidgetSize.MEDIUM, layoutOf(applyAt(card, 276f, 220f)))
    }

    @Test
    fun aStateIsSizedToo() {
        val views = sized(WidgetKind.READINESS, WidgetContent.FirstRun)
        val short = applyAt(views, 554f, 51f)
        assertNull("one line, no label", short.findViewById<View>(R.id.widget_label))
        assertEquals("Set up Crocodyl", short.findViewById<TextView>(R.id.widget_value).text.toString())
        val tall = applyAt(views, 130f, 220f)
        assertEquals("Set up Crocodyl", tall.findViewById<TextView>(R.id.widget_title).text.toString())
    }

    // ---------------------------------------------------------------------------------------
    // Refresh wiring
    // ---------------------------------------------------------------------------------------

    private fun drawn(view: View?): List<String> =
        when (view) {
            null -> emptyList()
            is TextView -> listOf(view.text.toString())
            is ViewGroup -> (0 until view.childCount).flatMap { drawn(view.getChildAt(it)) }
            else -> emptyList()
        }

    private fun waitFor(what: String, timeoutMs: Long = 15_000, check: () -> Boolean) {
        val until = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < until) {
            if (runCatching(check).getOrDefault(false)) return
            Thread.sleep(50)
        }
        throw AssertionError("timed out waiting for $what")
    }

    @Test
    fun aDatabaseWriteRedrawsAPlacedWidgetAndTheNextBoundaryIsSet() {
        runBlocking {
            db.athleteDao().upsert(AthleteEntity("a1", "Private Person", 65.0, drawLengthMm = 700))
            db.rigDao().upsert(RigEntity("r1", "a1", "First rig", "RECURVE", null, active = true, createdAt = 1))
        }
        val manager = AppWidgetManager.getInstance(app)
        val widgets = shadowOf(manager)
        widgets.bindAppWidgetId(41, ComponentName(app, ActiveRigWidget::class.java))

        WidgetRefresh.onAppReady(app)
        waitFor("the first draw") { drawn(widgets.getViewFor(41)).contains("First rig") }
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
        waitFor("the boundary alarm") { alarms.scheduledAlarms.isNotEmpty() }
        val alarm = alarms.scheduledAlarms.single()
        assertEquals(AlarmManager.RTC, alarm.type)
        assertEquals("inexact, but bounded on every API level", WidgetRefresh.ALARM_WINDOW_MS, alarm.windowLengthMs)
        assertEquals(WidgetDates.nextRefreshAtMs(ZonedDateTime.now(), null), alarm.triggerAtMs)

        // A write to a table the widget reads reaches it through Room's invalidation tracker.
        runBlocking {
            db.rigDao().upsert(RigEntity("r1", "a1", "Renamed rig", "RECURVE", null, active = true, createdAt = 1))
        }
        waitFor("the redraw after a write") { drawn(widgets.getViewFor(41)).contains("Renamed rig") }
    }

    @Test
    fun theBoundaryAlarmIsSetEvenWhenTheDatabaseCannotBeRead() {
        val flag = java.io.File(app.noBackupFilesDir, "widgets/database-open-failed")
        flag.parentFile?.mkdirs()
        flag.writeText(xyz.mdhv.formanalyser.app.BuildConfig.VERSION_CODE.toString())
        val widgets = shadowOf(AppWidgetManager.getInstance(app))
        widgets.bindAppWidgetId(42, ComponentName(app, ReadinessWidget::class.java))

        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        WidgetRefresh.updateAsync(app, WidgetKind.READINESS, intArrayOf(42)) { done.set(true) }
        waitFor("the provider's work to finish") { done.get() }
        assertTrue(drawn(widgets.getViewFor(42)).contains("Open Crocodyl"))
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertEquals(1, alarms.size)
    }

    // ---------------------------------------------------------------------------------------
    // Taps
    // ---------------------------------------------------------------------------------------

    private fun tap(destination: String) =
        Intent(app, WidgetTapActivity::class.java)
            .setAction(WidgetLaunch.ACTION_OPEN)
            .putExtra(WidgetLaunch.EXTRA_DESTINATION, destination)

    private fun appTask(top: String): ActivityManager.AppTask {
        val task = ShadowAppTask.newInstance()
        shadowOf(task).setTaskInfo(
            ActivityManager.RecentTaskInfo().apply {
                baseActivity = ComponentName(app, MainActivity::class.java)
                topActivity = ComponentName(app.packageName, top)
            },
        )
        shadowOf(app.getSystemService(ActivityManager::class.java)).setAppTasks(listOf(task))
        return task
    }

    @Test
    fun aTapWithTheAppClosedOpensTheDestination() {
        val activity = Robolectric.buildActivity(WidgetTapActivity::class.java, tap("SCORE")).create().get()
        val started = shadowOf(app).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertEquals(WidgetLaunch.ACTION_OPEN, started.action)
        assertEquals("SCORE", started.getStringExtra(WidgetLaunch.EXTRA_DESTINATION))
        assertEquals(0, started.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun aTapWithTheMainScreenOnTopNavigatesInIt() {
        appTask(MainActivity::class.java.name)
        Robolectric.buildActivity(WidgetTapActivity::class.java, tap("BODY")).create()
        val started = shadowOf(app).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertEquals("BODY", started.getStringExtra(WidgetLaunch.EXTRA_DESTINATION))
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertEquals(0, started.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    @Test
    fun aTapWithAnotherScreenOnTopBringsTheAppBackAsItWas() {
        val task = appTask("xyz.mdhv.formanalyser.app.FencingActivity")
        val activity = Robolectric.buildActivity(WidgetTapActivity::class.java, tap("PROGRESS")).create().get()
        assertNull("nothing is started, nothing is finished", shadowOf(app).nextStartedActivity)
        assertTrue(shadowOf(task).hasMovedToFront())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun anUnknownDestinationFallsBackToHome() {
        Robolectric.buildActivity(WidgetTapActivity::class.java, tap("S_EXPORT")).create()
        assertEquals("HOME", shadowOf(app).nextStartedActivity.getStringExtra(WidgetLaunch.EXTRA_DESTINATION))
    }
}
