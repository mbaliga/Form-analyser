package xyz.mdhv.formanalyser.app

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
    fun sportAndDirectoryShellsLaunchWithoutProcessFailure() {
        ActivityScenario.launch(FencingActivity::class.java).use { fencing ->
            fencing.onActivity { activity -> assertTrue(activity.isFinishing.not()) }
        }
        ActivityScenario.launch(CommunityDirectoryActivity::class.java).use { directory ->
            directory.onActivity { activity -> assertTrue(activity.isFinishing.not()) }
        }
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
}
