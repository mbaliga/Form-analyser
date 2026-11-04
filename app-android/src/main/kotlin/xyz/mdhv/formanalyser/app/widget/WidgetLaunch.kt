package xyz.mdhv.formanalyser.app.widget

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Carries a widget tap into the app. A widget's PendingIntent is explicit (WidgetTapActivity, which
 * forwards to MainActivity), uses a fixed action rather than a URL scheme, and names its
 * destination; MainActivity hands the intent here and MainShell navigates once its graph exists.
 */
object WidgetLaunch {
    const val ACTION_OPEN = "xyz.mdhv.formanalyser.app.action.OPEN_FROM_WIDGET"
    const val EXTRA_DESTINATION = "xyz.mdhv.formanalyser.app.extra.WIDGET_DESTINATION"

    private val requested = MutableStateFlow<WidgetDestination?>(null)

    /** The destination waiting for MainShell, if a widget tap brought the app here. */
    val pending: StateFlow<WidgetDestination?> = requested

    /** One PendingIntent per widget and destination: the destination's ordinal fits in 4 bits. */
    fun requestCode(appWidgetId: Int, destination: WidgetDestination): Int =
        appWidgetId * 16 + destination.ordinal

    /**
     * Accepts [intent] if it is a widget tap with a known destination. A relaunch from Recents
     * replays the original intent, so it is ignored, and the extra is removed once read so a
     * recreated activity does not navigate twice.
     */
    fun accept(intent: Intent?) {
        if (intent == null || intent.action != ACTION_OPEN) return
        val replayed = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        val destination = WidgetDestination.fromExtra(intent.getStringExtra(EXTRA_DESTINATION))
        intent.removeExtra(EXTRA_DESTINATION)
        if (!replayed && destination != null) requested.value = destination
    }

    /** Takes the pending destination, leaving none behind. */
    fun take(): WidgetDestination? = requested.value.also { requested.value = null }

    /** What is on top of Crocodyl's task when a widget is tapped. */
    enum class TaskTop {
        /** No task: the app is not open (or only its restored root is left after a reboot). */
        NONE,

        /** MainActivity: the tap can navigate inside it. */
        MAIN,

        /**
         * Anything else: a Fencing bout, a Muay Thai or Bouldering entry, the Atelier, the
         * Community Directory, or another app's screen in the task (the camera taking a document
         * photo, app settings).
         */
        OTHER,
    }

    enum class TapAction {
        /** Start or reach MainActivity with the destination (it is not on top of anything). */
        OPEN,

        /** Bring the task to the front exactly as it was, without navigating. */
        BRING_TO_FRONT,
    }

    /**
     * A tap never finishes a screen with unsaved work: it navigates only when MainActivity is on top
     * (or no task exists), and otherwise brings the app back as it was. MainShell adds the same rule
     * for the Capture screen inside MainActivity.
     */
    fun tapAction(top: TaskTop): TapAction =
        when (top) {
            TaskTop.NONE, TaskTop.MAIN -> TapAction.OPEN
            TaskTop.OTHER -> TapAction.BRING_TO_FRONT
        }
}
