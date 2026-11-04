package xyz.mdhv.formanalyser.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * One AppWidgetProvider per widget so each appears on its own in every launcher's picker. They are
 * exported (a launcher binds them through the system) but protected by BIND_APPWIDGET, so only the
 * system's widget service can deliver to them; see tools/release/verify_manifest_contract.py.
 */
abstract class CrocodylWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        redraw(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // Below API 31 the layout is chosen from the reported size, so a resize needs a redraw.
        redraw(context, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Dates, "today" and the week all move with the clock, the zone and the language.
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED -> redraw(context, WidgetRefresh.ids(context, kind))
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        WidgetRefresh.onWidgetsRemoved(context)
    }

    private fun redraw(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        WidgetRefresh.updateAsync(context, kind, ids) { pending.finish() }
    }
}

class LastScorecardWidget : CrocodylWidgetProvider(WidgetKind.LAST_SCORECARD)

class ThisWeekWidget : CrocodylWidgetProvider(WidgetKind.THIS_WEEK)

class NextPhysioWidget : CrocodylWidgetProvider(WidgetKind.NEXT_PHYSIO)

class ReadinessWidget : CrocodylWidgetProvider(WidgetKind.READINESS)

class ActiveRigWidget : CrocodylWidgetProvider(WidgetKind.ACTIVE_RIG)

class QuickStartWidget : CrocodylWidgetProvider(WidgetKind.QUICK_START)
