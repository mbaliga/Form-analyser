package xyz.mdhv.formanalyser.app.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import xyz.mdhv.formanalyser.app.data.AppPrefs

/**
 * The strength tiers offered in Settings → Appearance and persisted as [AppPrefs.hapticStrength].
 *
 * These existed as four strings in a segmented control that were written to DataStore and read by
 * nothing — no Vibrator, no VIBRATE permission, no call site. The setting has been a promise the
 * app never kept.
 */
enum class HapticStrength(val amplitude: Int) {
    OFF(0),
    LOW(60),
    MED(140),
    HIGH(255);

    companion object {
        fun parse(raw: String?): HapticStrength =
            entries.firstOrNull { it.name == raw?.uppercase() } ?: MED
    }
}

/**
 * What happened, not how it should feel — the tier decides the amplitude, the cue decides only the
 * shape. Kept deliberately small: this is a confirmation channel for a shooting line where the
 * phone is often not being looked at, not a general effects library.
 */
enum class HapticCue(val millis: Long) {
    /** An arrow was recorded. The common case, so the lightest. */
    ARROW(12),
    /** An arrow was retracted — distinguishable from recording one. */
    UNDO(28),
    /** An end or a round closed out. */
    COMPLETE(45),
}

/**
 * Returns a function that fires a cue at the athlete's chosen strength, or does nothing if they
 * chose OFF or the device has no vibrator.
 *
 * Reads the preference as state, so changing the setting takes effect without a restart.
 */
@Composable
fun rememberHaptics(): (HapticCue) -> Unit {
    val context = LocalContext.current
    val prefs = remember(context) { AppPrefs(context) }
    val strength by prefs.hapticStrength.collectAsState(initial = HapticStrength.MED.name)
    val vibrator = remember(context) { context.vibrator() }
    return { cue ->
        val tier = HapticStrength.parse(strength)
        if (tier != HapticStrength.OFF && vibrator?.hasVibrator() == true) {
            runCatching {
                vibrator.vibrate(VibrationEffect.createOneShot(cue.millis, tier.amplitude))
            }
        }
    }
}

private fun Context.vibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
