package xyz.mdhv.formanalyser.app

import android.app.Application
import dev.aarso.crashrecovery.CrashRecovery

/**
 * Installs crash capture as early as possible, so a device-only crash (which CI never sees —
 * CI runs unit tests + assembles, but never launches the app) is diagnosable on the next
 * launch instead of the app silently dying. Crocodyl is the constellation's real-world crasher
 * — the camera / pose-estimation / session paths do fail in the field — which makes the
 * recovery screen genuinely useful here, not just a safety net. See [MainActivity.onCreate].
 */
class CrocodylApplication : Application() {
    override fun onCreate() {
        CrashRecovery.install(this, appLabel = "Crocodyl")
        super.onCreate()
    }
}
