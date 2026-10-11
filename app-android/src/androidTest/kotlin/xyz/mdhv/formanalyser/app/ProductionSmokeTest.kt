package xyz.mdhv.formanalyser.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.File
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
    fun mainActivityRemovesOrphanedSafExportStagingOnStartup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val orphan = File(context.cacheDir, ".croc-export-interrupted.partial")
        val unrelated = File(context.cacheDir, "unrelated.partial")
        orphan.writeText("incomplete private archive")
        unrelated.writeText("unrelated cache file")
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                assertFalse("Orphaned SAF export staging survived startup", orphan.exists())
                assertTrue("Startup cleanup touched an unrelated cache file", unrelated.exists())
            }
        } finally {
            orphan.delete()
            unrelated.delete()
        }
    }

    @Test
    fun bundledSportPhotographsArePackagedAndAndroidDecodable() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val expectedDimensions = mapOf(
            "atelier/cinematic/muay-thai-training.jpg" to Pair(1400, 788),
            "atelier/cinematic/bouldering-session.jpg" to Pair(1400, 788),
            "atelier/cinematic/recurve-training.jpg" to Pair(1600, 900),
            "atelier/cinematic/fencing-training.jpg" to Pair(1600, 900),
        )

        expectedDimensions.forEach { (path, expected) ->
            val bitmap = assets.open(path).use(BitmapFactory::decodeStream)
            assertNotNull("Android could not decode bundled photo $path", bitmap)
            bitmap!!.let { decoded ->
                try {
                    assertTrue(
                        "$path has unexpected dimensions ${decoded.width}x${decoded.height}",
                        decoded.width to decoded.height == expected,
                    )
                } finally {
                    decoded.recycle()
                }
            }
        }
    }

    @Test
    fun firstRunOnboardingPresentsTheCinematicRangeEntry() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = xyz.mdhv.formanalyser.app.data.AppPrefs(context)
        val wasOnboarded = runBlocking { prefs.onboarded.first() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            runBlocking { prefs.setOnboarded(false) }
            ActivityScenario.launch(MainActivity::class.java).use {
                assertTextEventually(instrumentation, "CROCODYL  /  YOUR TRAINING SPACE")
                assertTextEventually(instrumentation, "What do you do?")
                assertTextEventually(instrumentation, "Archer")
                assertTextEventually(instrumentation, "Coach")
                assertTextEventually(instrumentation, "Both")
            }
        } finally {
            runBlocking { prefs.setOnboarded(wasOnboarded) }
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
                assertTextEventually(instrumentation, "Find your rhythm.")
                assertTextEventually(instrumentation, "ACTIVE RIG")
                assertTextEventually(instrumentation, "Train")
                assertTextEventually(instrumentation, "Score")

                clickBottomTabEventually(instrumentation, "Train")
                assertTextEventually(instrumentation, "New session")
                assertTextEventually(instrumentation, "Choose a round or enter distance to begin.")

                clickBottomTabEventually(instrumentation, "Progress")
                assertTextEventually(instrumentation, "Training, in context")
                assertTextEventually(
                    instrumentation,
                    "Understand what changes performance. No projections, no causal shortcuts.",
                )
                assertTextEventually(instrumentation, "A complete scored round will establish your first baseline.")
                assertTextEventually(instrumentation, "No comparable capture series yet.")

                clickBottomTabEventually(instrumentation, "Body")
                assertTextEventually(instrumentation, "Context")
                clickTextEventually(instrumentation, "Today")
                clickTextEventually(instrumentation, "Choose region from list")
                assertTextEventually(instrumentation, "Choose body region")
                clickTextEventually(instrumentation, "Back of neck")
                assertTextEventually(instrumentation, "Intensity")
                clickTextEventually(instrumentation, "Cancel")
                assertTextEventually(instrumentation, "Choose region from list")

                clickBottomTabEventually(instrumentation, "Calendar")
                assertTextEventually(instrumentation, "← prev")

                clickBottomTabEventually(instrumentation, "Home")
                assertTextEventually(instrumentation, "Crocodyl")

                clickTextEventually(instrumentation, "Score")
                assertTextEventually(instrumentation, "Every arrow,\naccounted for.")
                assertTextEventually(instrumentation, "Quick score")
                clickTextEventually(instrumentation, "Choose round")
                assertTextEventually(instrumentation, "WA Recurve 70 m · 72 arrows")
                // Dispatch through the modern back dispatcher: target API 36 no longer promises
                // that a legacy KEYCODE_BACK event reaches the Activity callback chain.
                scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
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
                assertTextEventually(instrumentation, "Every arrow,\naccounted for.")
                clickTextEventually(instrumentation, "Quick score")
                quickScoreRequested = true
                val created = waitForActiveScorecard(repository)
                createdSessionId = created.session.id
                expectedSummary = "${created.card.total} · ${created.card.xCount} X · " +
                    "${created.card.arrowCount}/${created.card.round.maxArrows}"
                assertTextEventually(instrumentation, expectedSummary!!)
                // Exercise the actual numeric keypad and undo controls, not only the Room boundary.
                clickTextEventually(instrumentation, "10")
                assertTextEventually(
                    instrumentation,
                    "10 · 0 X · 1/${created.card.round.maxArrows}",
                )
                clickTextEventually(instrumentation, "Undo")
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
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            fencing.onActivity { activity -> assertTrue(activity.isFinishing.not()) }
            assertTextEventually(instrumentation, "FENCING  /  THE PISTE")
            assertTextEventually(instrumentation, "Read the moment.")
            scrollForwardUntilText(instrumentation, "MANUAL REFEREE")
            assertTextEventually(instrumentation, "MANUAL REFEREE")
            assertTextEventually(instrumentation, "Prêt")
            assertTextEventually(instrumentation, "Allez")
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
        val visibleText = instrumentation.uiAutomation.rootInActiveWindow
            ?.let(::collectVisibleText)
            .orEmpty()
        throw AssertionError("Timed out waiting for visible UI text: $expected. Visible: $visibleText")
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

    private fun scrollForwardUntilText(
        instrumentation: android.app.Instrumentation,
        expected: String,
    ) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null && containsText(root, expected)) return
            if (root != null) root.scrollForwardOnce()
            SystemClock.sleep(100)
        }
        throw AssertionError("Could not scroll to visible UI text: $expected")
    }

    private fun AccessibilityNodeInfo.scrollForwardOnce(): Boolean {
        if (isScrollable && performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
        for (index in 0 until childCount) {
            if (getChild(index)?.scrollForwardOnce() == true) return true
        }
        return false
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

    private fun collectVisibleText(node: AccessibilityNodeInfo): List<String> {
        val own = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        return own + (0 until node.childCount).flatMap { index ->
            node.getChild(index)?.let(::collectVisibleText).orEmpty()
        }
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
