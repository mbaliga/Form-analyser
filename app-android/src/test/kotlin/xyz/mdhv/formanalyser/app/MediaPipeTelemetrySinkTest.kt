package xyz.mdhv.formanalyser.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.datatransport.runtime.TransportRuntime
import com.google.mediapipe.proto.MediaPipeLoggingProto
import com.google.mediapipe.tasks.core.logging.RemoteLoggingClient
import com.google.mediapipe.tasks.core.logging.TasksStatsLoggerFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MediaPipe Tasks reports package name, version, task name/mode and latency statistics through
 * datatransport's "cct" backend. Crocodyl has no analytics in any channel: the backend and runtime
 * are excluded from every build and replaced by a no-op sink (see app-android/build.gradle.kts and
 * src/main/java/com/google/android/datatransport). These tests pin both halves: the real upload
 * machinery is absent, and MediaPipe's own logging path ends in the sink, not in a network call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaPipeTelemetrySinkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the datatransport backend, scheduler and discovery classes are not on the classpath`() {
        listOf(
            "com.google.android.datatransport.cct.CctBackendFactory",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver",
            "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
        ).forEach { name ->
            assertThrows(name, ClassNotFoundException::class.java) { Class.forName(name) }
        }
    }

    @Test
    fun `MediaPipe remote logging client hands its event to the dropping sink`() {
        val before = TransportRuntime.DROPPED_EVENTS.get()

        RemoteLoggingClient(context)
            .logEvent(MediaPipeLoggingProto.MediaPipeLogExtension.getDefaultInstance())

        assertEquals(before + 1, TransportRuntime.DROPPED_EVENTS.get())
    }

    @Test
    fun `MediaPipe task stats logger reaches the dropping sink without native code`() {
        val before = TransportRuntime.DROPPED_EVENTS.get()

        TasksStatsLoggerFactory.create(context, "pose_landmarker", "live_stream").logSessionStart()

        assertEquals(before + 1, TransportRuntime.DROPPED_EVENTS.get())
    }
}
