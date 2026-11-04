package xyz.mdhv.formanalyser.app.widget

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import xyz.mdhv.formanalyser.app.MainActivity

/**
 * Every widget tap arrives here first. It has no window (Theme.NoDisplay), runs in its own empty
 * task (`taskAffinity=""`, excluded from Recents) and is not exported, so only the widgets' own
 * immutable PendingIntents reach it. It looks at Crocodyl's task and applies
 * [WidgetLaunch.tapAction]: with MainActivity on top, or no task, it opens the destination; with
 * any other screen on top it brings the task back as it was, so a tap cannot finish a running
 * bout, a half-filled journal entry or a document photo in progress (which `CLEAR_TOP` would).
 */
class WidgetTapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            route()
        } finally {
            // Theme.NoDisplay requires finishing before onResume.
            finish()
        }
    }

    private fun route() {
        // Validated by name; anything unknown falls back to Home.
        val destination =
            WidgetDestination.fromExtra(intent?.getStringExtra(WidgetLaunch.EXTRA_DESTINATION))
                ?: WidgetDestination.HOME
        val main = ComponentName(this, MainActivity::class.java)
        val task =
            runCatching {
                    getSystemService(ActivityManager::class.java)?.appTasks?.firstOrNull { t ->
                        runCatching { t.taskInfo.baseActivity == main }.getOrDefault(false)
                    }
                }
                .getOrNull()
        val top =
            when {
                task == null -> WidgetLaunch.TaskTop.NONE
                else ->
                    when (runCatching { task.taskInfo.topActivity }.getOrNull()) {
                        // A task restored after a reboot keeps only its root.
                        null, main -> WidgetLaunch.TaskTop.MAIN
                        else -> WidgetLaunch.TaskTop.OTHER
                    }
            }
        when (WidgetLaunch.tapAction(top)) {
            WidgetLaunch.TapAction.OPEN -> startActivity(openMain(destination))
            WidgetLaunch.TapAction.BRING_TO_FRONT -> {
                val moved = task != null && runCatching { task.moveToFront() }.isSuccess
                // The launcher's own intent also resumes the task as it was.
                if (!moved) packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
            }
        }
    }

    private fun openMain(destination: WidgetDestination): Intent =
        Intent(this, MainActivity::class.java)
            .setAction(WidgetLaunch.ACTION_OPEN)
            .putExtra(WidgetLaunch.EXTRA_DESTINATION, destination.name)
            // No CLEAR_TOP: MainActivity is on top (it receives onNewIntent) or not running at all.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
