package xyz.mdhv.formanalyser.app

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.mdhv.formanalyser.app.data.ScoringRepository

/**
 * Small on-device contract for the release-critical app boundary.
 *
 * Robolectric covers database and migration behavior. This suite intentionally exercises the real
 * Android activity/WebView stack so a CI emulator catches manifest, WebView, asset-origin, or
 * release-packaging regressions that JVM tests cannot observe.
 */
@RunWith(AndroidJUnit4::class)
class ProductionSmokeTest {
    @Test
    fun mainActivityLaunchesWithoutProcessFailure() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.isFinishing.not())
            }
        }
    }

    @Test
    fun onboardedAthleteCanVisitEveryPrimaryTab() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = xyz.mdhv.formanalyser.app.data.AppPrefs(appContext)
        val wasOnboarded = runBlocking { prefs.onboarded.first() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            runBlocking { prefs.setOnboarded(true) }
            ActivityScenario.launch(MainActivity::class.java).use {
                assertTextEventually(instrumentation, "Crocodyl")

                clickBottomTabEventually(instrumentation, "Train")
                assertTextEventually(instrumentation, "New session")

                clickBottomTabEventually(instrumentation, "Progress")
                assertTextEventually(instrumentation, "Understand what changes performance · local evidence only")

                clickBottomTabEventually(instrumentation, "Body")
                assertTextEventually(instrumentation, "Context")

                clickBottomTabEventually(instrumentation, "Calendar")
                assertTextEventually(instrumentation, "← prev")

                clickBottomTabEventually(instrumentation, "Home")
                assertTextEventually(instrumentation, "Crocodyl")

                clickTextEventually(instrumentation, "Score")
                assertTextEventually(instrumentation, "Quick score")
                clickTextEventually(instrumentation, "Choose round")
                assertTextEventually(instrumentation, "WA Recurve 70 m · 72 arrows")
                instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                assertTextEventually(instrumentation, "Quick score")
            }
        } finally {
            runBlocking { prefs.setOnboarded(wasOnboarded) }
        }
    }

    @Test
    fun quickScoreResumesAfterActivityRecreationAndCleansUpItsEmptyCard() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = xyz.mdhv.formanalyser.app.data.AppPrefs(appContext)
        val repository = ScoringRepository(appContext)
        val wasOnboarded = runBlocking { prefs.onboarded.first() }
        val startedAt = System.currentTimeMillis()
        var createdSessionId: String? = null
        var expectedSummary: String? = null
        var quickScoreRequested = false
        try {
            runBlocking { prefs.setOnboarded(true) }
            ActivityScenario.launch(MainActivity::class.java).use {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                assertTextEventually(instrumentation, "Crocodyl")
                waitForAthlete(repository)
                // Avoid resuming or changing an athlete's pre-existing card on shared/local test devices.
                assumeTrue("Quick Score smoke requires no pre-existing active scorecard", runBlocking { repository.resumeActive() == null })

                clickTextEventually(instrumentation, "Score")
                clickTextEventually(instrumentation, "Quick score")
                quickScoreRequested = true
                val created = waitForActiveScorecard(repository)
                createdSessionId = created.session.id
                expectedSummary = "${created.card.total} · ${created.card.xCount} X · " +
                    "${created.card.arrowCount}/${created.card.round.maxArrows}"
                assertTextEventually(instrumentation, expectedSummary!!)
            }

            // A new Activity gets a new ViewModel; the screen must resume the persisted card.
            ActivityScenario.launch(MainActivity::class.java).use {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                assertTextEventually(instrumentation, "Crocodyl")
                clickTextEventually(instrumentation, "Score")
                assertTextEventually(instrumentation, expectedSummary!!)
            }
        } finally {
            try {
                // If an assertion failed immediately after Quick Score, still remove only the new,
                // untouched card created by this test. The start-time guard prevents touching older data.
                val cleanupId = createdSessionId ?: if (quickScoreRequested) runBlocking {
                    repository.resumeActive()
                        ?.takeIf { it.session.startedAt >= startedAt && it.card.arrowCount == 0 }
                        ?.session?.id
                } else null
                cleanupId?.let { id -> runBlocking { repository.deleteScorecard(id) } }
            } finally {
                runBlocking { prefs.setOnboarded(wasOnboarded) }
            }
        }
    }

    @Test
    fun sportAndDirectoryShellsLaunchWithoutProcessFailure() {
        ActivityScenario.launch(FencingActivity::class.java).use { fencing ->
            fencing.onActivity { activity -> assertTrue(activity.isFinishing.not()) }
        }
        ActivityScenario.launch(CommunityDirectoryActivity::class.java).use { directory ->
            directory.onActivity { activity -> assertTrue(activity.isFinishing.not()) }
        }
    }

    @Test
    fun closingMediaPipeImageReleasesItsOwnedBitmap() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val image = BitmapImageBuilder(bitmap).build()

        assertFalse("The test bitmap should be live before MPImage close", bitmap.isRecycled)
        image.close()
        assertTrue("MPImage close should recycle the BitmapImageContainer's bitmap", bitmap.isRecycled)
    }

    @Test
    fun equipmentAtelierUsesBundledOfflineStudioContract() {
        ActivityScenario.launch(EquipmentAtelierActivity::class.java).use { scenario ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            var webView: WebView? = null
            var url: String? = null
            var javascriptEnabled = false
            var fileAccessEnabled = true
            var contentAccessEnabled = true
            var networkLoadsBlocked = false

            for (attempt in 0 until 100) {
                scenario.onActivity { activity ->
                    webView = findWebView(activity.window.decorView)
                    url = webView?.url
                    webView?.let { view ->
                        javascriptEnabled = view.settings.javaScriptEnabled
                        fileAccessEnabled = view.settings.allowFileAccess
                        contentAccessEnabled = view.settings.allowContentAccess
                        networkLoadsBlocked = view.settings.blockNetworkLoads
                    }
                }
                if (url?.startsWith("https://crocodyl.local/atelier/") == true) break
                instrumentation.waitForIdleSync()
                SystemClock.sleep(50)
            }

            val loaded = webView
            assertNotNull("The Atelier activity did not create a WebView", loaded)
            assertTrue("The Atelier must load only its local HTTPS origin: $url", url?.startsWith("https://crocodyl.local/atelier/") == true)
            assertTrue("JavaScript is required by the local renderer", javascriptEnabled)
            assertFalse("The local renderer must not read file URLs", fileAccessEnabled)
            assertFalse("The local renderer must not read content providers", contentAccessEnabled)
            assertTrue("The local renderer must block network loads", networkLoadsBlocked)
        }
    }

    private fun findWebView(root: View): WebView? {
        if (root is WebView) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            findWebView(root.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun assertTextEventually(
        instrumentation: android.app.Instrumentation,
        expected: String,
    ) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null && containsText(root, expected)) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for visible UI text: $expected")
    }

    private fun clickBottomTabEventually(
        instrumentation: android.app.Instrumentation,
        label: String,
    ) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val minBottom = (context.resources.displayMetrics.heightPixels * 0.72f).toInt()
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val target = root?.findClickableTextInBottomArea(label, minBottom)
            if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Could not click bottom navigation tab: $label")
    }

    private fun clickTextEventually(
        instrumentation: android.app.Instrumentation,
        label: String,
    ) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val target = root?.findClickableText(label)
            if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Could not click visible UI text: $label")
    }

    private fun waitForAthlete(repository: ScoringRepository) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (runBlocking { repository.currentAthlete() } != null) return
            SystemClock.sleep(50)
        }
        throw AssertionError("The main shell did not initialize an athlete profile")
    }

    private fun waitForActiveScorecard(repository: ScoringRepository): ScoringRepository.Snapshot {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            runBlocking { repository.resumeActive() }?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("Quick Score did not create an active scorecard")
    }

    private fun containsText(node: AccessibilityNodeInfo, expected: String): Boolean {
        if (node.text?.toString() == expected || node.contentDescription?.toString() == expected) return true
        return (0 until node.childCount).any { index -> node.getChild(index)?.let { containsText(it, expected) } == true }
    }

    private fun AccessibilityNodeInfo.findClickableTextInBottomArea(
        expected: String,
        minBottom: Int,
    ): AccessibilityNodeInfo? {
        val bounds = Rect()
        getBoundsInScreen(bounds)
        if (isClickable && bounds.bottom >= minBottom && containsText(this, expected)) return this
        for (index in 0 until childCount) {
            getChild(index)?.findClickableTextInBottomArea(expected, minBottom)?.let { return it }
        }
        return null
    }

    private fun AccessibilityNodeInfo.findClickableText(expected: String): AccessibilityNodeInfo? {
        if (isClickable && containsText(this, expected)) return this
        for (index in 0 until childCount) {
            getChild(index)?.findClickableText(expected)?.let { return it }
        }
        return null
    }
}
