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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

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
            }
        } finally {
            runBlocking { prefs.setOnboarded(wasOnboarded) }
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
}
