package xyz.mdhv.formanalyser.app.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import xyz.mdhv.formanalyser.app.R
import xyz.mdhv.formanalyser.wellness.ReadinessLevel

/**
 * Below API 31 there is no sized map: the app reads the size range the launcher reported and hands
 * it one layout for portrait and one for landscape (RemoteViews(landscape, portrait)).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class WidgetBelowApi31Test {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val content =
        WidgetContent.Readiness(ReadinessModel.of(ReadinessLevel.READY, CheckinFacts(0, 4, 4, 0), null, false, 0))

    private fun layoutOf(view: View): WidgetSize =
        when {
            view.findViewById<View>(R.id.widget_row1) != null -> WidgetSize.LARGE
            view.findViewById<View>(R.id.widget_detail1) != null -> WidgetSize.MEDIUM
            view.findViewById<View>(R.id.widget_label) != null -> WidgetSize.SMALL
            else -> WidgetSize.COMPACT
        }

    private fun applied(id: Int, orientation: Int): WidgetSize {
        val manager = AppWidgetManager.getInstance(app)
        val views = WidgetRenderer.views(app, manager, id, WidgetKind.READINESS, content, ZonedDateTime.now())
        val context =
            app.createConfigurationContext(Configuration(app.resources.configuration).apply { this.orientation = orientation })
        return layoutOf(views.apply(context, FrameLayout(context)))
    }

    private fun place(id: Int, minW: Int, maxW: Int, minH: Int, maxH: Int) {
        val manager = AppWidgetManager.getInstance(app)
        shadowOf(manager).bindAppWidgetId(id, ComponentName(app, ReadinessWidget::class.java))
        manager.updateAppWidgetOptions(
            id,
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minW)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxW)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minH)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxH)
            },
        )
    }

    @Test
    fun aFourByOneGetsTheMediumLayoutUprightAndOneLineOnItsSide() {
        place(11, 276, 554, 51, 102)
        assertEquals(WidgetSize.MEDIUM, applied(11, Configuration.ORIENTATION_PORTRAIT))
        assertEquals(WidgetSize.COMPACT, applied(11, Configuration.ORIENTATION_LANDSCAPE))
    }

    @Test
    fun aFourByThreeGetsTheLargeLayoutUprightAndTheMediumOnItsSide() {
        place(12, 276, 554, 184, 337)
        assertEquals(WidgetSize.LARGE, applied(12, Configuration.ORIENTATION_PORTRAIT))
        assertEquals(WidgetSize.MEDIUM, applied(12, Configuration.ORIENTATION_LANDSCAPE))
    }

    @Test
    fun aResizeRedrawsWithTheNewSize() {
        place(13, 130, 269, 117, 220)
        assertEquals(WidgetSize.SMALL, applied(13, Configuration.ORIENTATION_PORTRAIT))
        place(13, 276, 554, 184, 337)
        assertEquals(WidgetSize.LARGE, applied(13, Configuration.ORIENTATION_PORTRAIT))
    }
}
