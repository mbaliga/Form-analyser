package xyz.mdhv.formanalyser.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.withTransaction
import com.google.mediapipe.framework.image.BitmapImageBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.mdhv.formanalyser.app.data.ScoringRepository
import xyz.mdhv.formanalyser.app.data.Repository

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
    fun bocalStyleArcNavigationSelectsCalendar() {
        val prefs = xyz.mdhv.formanalyser.app.data.AppPrefs(
            ApplicationProvider.getApplicationContext(),
        )
        val wasOnboarded = runBlocking { prefs.onboarded.first() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            runBlocking { prefs.setOnboarded(true) }
            ActivityScenario.launch(MainActivity::class.java).use {
                assertTextEventually(instrumentation, "Find your rhythm.")
                assertTextEventually(instrumentation, "Home")
                clickBottomTabEventually(instrumentation, "Calendar")
                assertTextEventually(instrumentation, "← prev")
                clickBottomTabEventually(instrumentation, "Home")
                assertTextEventually(instrumentation, "Find your rhythm.")
                val navRoot = rootInActiveWindowOrNull(instrumentation) ?: error("Missing Home accessibility tree")
                val contextRail = navRoot.findContentDescriptionPrefix("Swipe across the upper arc")
                    ?: error("Bocal upper-rail interaction is not exposed to accessibility")
                val railBounds = Rect().also(contextRail::getBoundsInScreen)
                contextRail.recycle()
                navRoot.recycle()
                val downTime = SystemClock.uptimeMillis()
                val xStart = railBounds.left + railBounds.width() * .12f
                val xEnd = railBounds.left + railBounds.width() * .88f
                val y = railBounds.centerY().toFloat()
                fun injectRail(action: Int, x: Float) {
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0).apply {
                        source = InputDevice.SOURCE_TOUCHSCREEN
                    }
                    try {
                        assertTrue("Could not inject upper-rail navigation gesture", instrumentation.uiAutomation.injectInputEvent(event, true))
                    } finally {
                        event.recycle()
                    }
                }
                injectRail(MotionEvent.ACTION_DOWN, xStart)
                repeat(8) { step ->
                    injectRail(MotionEvent.ACTION_MOVE, xStart + (xEnd - xStart) * (step + 1) / 8f)
                    SystemClock.sleep(35)
                }
                injectRail(MotionEvent.ACTION_UP, xEnd)
                assertTextEventually(instrumentation, "← prev")
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
                assertTextEventually(instrumentation, "CROCODYL  /  RECURVE")
                assertTextAbsent(instrumentation, "Crocodyl")
                assertTextEventually(instrumentation, "Find your rhythm.")
                assertTextEventually(instrumentation, "ACTIVE RIG")
                assertTextEventually(instrumentation, "Train")
                assertTextEventually(instrumentation, "Score")
                assertTextEventually(instrumentation, "Settings")
                scrollHomeUntilText(instrumentation, "Arrow, down to each part.", forward = true)
                assertTextEventually(instrumentation, "Arrow, down to each part.")
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
                scrollHomeUntilText(instrumentation, "CROCODYL  /  RECURVE", forward = false)
                assertTextEventually(instrumentation, "CROCODYL  /  RECURVE")

                scrollHomeUntilText(instrumentation, "Score", forward = false)
                clickTextEventually(instrumentation, "Score")
                assertTextEventually(instrumentation, "Every arrow,\naccounted for.")
                assertTextEventually(instrumentation, "Quick score")
                clickTextEventually(instrumentation, "Choose round")
                assertTextEventually(instrumentation, "WA Recurve 70 m · 72 arrows")
                clickTextEventually(instrumentation, "Cancel")
                assertTextEventually(instrumentation, "Quick score")
            }
        } finally {
            runBlocking { prefs.setOnboarded(wasOnboarded) }
        }
    }

    @Test
    fun startingTrainingPersistsTheSelectedRoundAndSkippedPrecheckin() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = xyz.mdhv.formanalyser.app.data.AppPrefs(appContext)
        val scoringRepository = ScoringRepository(appContext)
        val repository = Repository(appContext)
        val database = xyz.mdhv.formanalyser.app.data.AppDatabase.get(appContext)
        val wasOnboarded = runBlocking { prefs.onboarded.first() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var athleteId: String? = null
        var preexistingSessionIds = emptySet<String>()
        var preexistingTrainingDefaults: xyz.mdhv.formanalyser.app.data.SessionDefaultEntity? = null
        var startedAt = 0L
        try {
            runBlocking { prefs.setOnboarded(true) }
            ActivityScenario.launch(MainActivity::class.java).use {
                assertTextEventually(instrumentation, "CROCODYL  /  RECURVE")
                waitForAthlete(scoringRepository)
                val athlete = runBlocking { repository.currentAthlete() }
                assertNotNull("The main shell did not initialize a training profile", athlete)
                athleteId = athlete!!.id
                preexistingTrainingDefaults =
                    runBlocking { database.athleteFeatureDao().defaults(athlete.id) }
                preexistingSessionIds =
                    runBlocking { repository.allSessions(athlete.id) }.map { it.id }.toSet()
                startedAt = System.currentTimeMillis()

                clickBottomTabEventually(instrumentation, "Train")
                clickTextEventually(instrumentation, "WA Recurve 70 m · 72 arrows")
                clickTextEventually(instrumentation, "Start recording")
                assertTextEventually(instrumentation, "Quick check-in")
                clickTextEventually(instrumentation, "Skip")
                if (appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) &&
                    appContext.checkSelfPermission(Manifest.permission.CAMERA) !=
                        PackageManager.PERMISSION_GRANTED
                ) {
                    dismissCameraPermissionPromptIfShown(instrumentation)
                    assertAnyTextEventually(
                        instrumentation,
                        listOf(
                            "Camera permission is required to analyse form.",
                            "Camera access is blocked. Allow it in app settings to analyse form.",
                        ),
                    )
                } else if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                    assertTextEventually(
                        instrumentation,
                        "This device has no camera, so form capture is unavailable here. Scoring, training logs and everything else still work.",
                    )
                }
                assertTextEventually(instrumentation, "Film side-on (sagittal)")
            }

            val deadline = SystemClock.uptimeMillis() + 8_000
            var created = runBlocking {
                repository.allSessions(requireNotNull(athleteId))
                    .firstOrNull { it.id !in preexistingSessionIds && it.startedAtEpochMs >= startedAt }
            }
            while (created == null && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(50)
                created = runBlocking {
                    repository.allSessions(requireNotNull(athleteId))
                        .firstOrNull { it.id !in preexistingSessionIds && it.startedAtEpochMs >= startedAt }
                }
            }
            assertNotNull("Starting the training flow did not persist its session", created)
            assertEquals(70, created!!.distanceMeters)
            val precheckinId = created.preCheckinId
            assertNotNull("Skipped pre-session check-in must remain linked to the session", precheckinId)
            val precheckin = runBlocking { repository.wellness.checkinById(precheckinId!!) }
            assertNotNull("Skipped pre-session check-in row was not persisted", precheckin)
            assertEquals("PRE", precheckin!!.kind)
            assertTrue("The check-in skip must be recorded", precheckin.skipped)
        } finally {
            athleteId?.let { id ->
                val createdSessions = runBlocking {
                    repository.allSessions(id).filter {
                        it.id !in preexistingSessionIds && it.startedAtEpochMs >= startedAt
                    }
                }
                runBlocking {
                    database.withTransaction {
                        createdSessions.forEach { session ->
                            database.openHelper.writableDatabase.execSQL(
                                "DELETE FROM session_context WHERE sessionId = ?",
                                arrayOf(session.id),
                            )
                            session.preCheckinId?.let { checkinId ->
                                database.openHelper.writableDatabase.execSQL(
                                    "DELETE FROM soreness WHERE checkinId = ?",
                                    arrayOf(checkinId),
                                )
                                database.openHelper.writableDatabase.execSQL(
                                    "DELETE FROM checkin WHERE id = ?",
                                    arrayOf(checkinId),
                                )
                            }
                            database.openHelper.writableDatabase.execSQL(
                                "DELETE FROM sessions WHERE id = ?",
                                arrayOf(session.id),
                            )
                        }
                        if (preexistingTrainingDefaults == null) {
                            database.openHelper.writableDatabase.execSQL(
                                "DELETE FROM session_default WHERE athleteId = ?",
                                arrayOf(id),
                            )
                        } else {
                            database.athleteFeatureDao().upsertDefaults(preexistingTrainingDefaults)
                        }
                    }
                }
            }
            runBlocking { prefs.setOnboarded(wasOnboarded) }
        }
    }

    @Test
    fun quickScorePersistsEnteredArrowAcrossActivityRecreationAndCleansUpItsEmptyCard() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = xyz.mdhv.formanalyser.app.data.AppPrefs(appContext)
        val repository = ScoringRepository(appContext)
        val wasOnboarded = runBlocking { prefs.onboarded.first() }
        val startedAt = System.currentTimeMillis()
        var createdSessionId: String? = null
        var expectedSummary = ""
        var quickScoreRequested = false
        try {
            runBlocking { prefs.setOnboarded(true) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                assertTextEventually(instrumentation, "CROCODYL  /  RECURVE")
                waitForAthlete(repository)
                // Avoid resuming or changing an athlete's pre-existing card on shared/local test devices.
                assumeTrue("Quick Score smoke requires no pre-existing active scorecard", runBlocking { repository.resumeActive() == null })

                clickTextEventually(instrumentation, "Score")
                assertTextEventually(instrumentation, "Every arrow,\naccounted for.")
                clickTextEventually(instrumentation, "Quick score")
                quickScoreRequested = true
                val created = waitForActiveScorecard(repository)
                createdSessionId = created.session.id
                val emptyCardSummary = "${created.card.total} · ${created.card.xCount} X · " +
                    "${created.card.arrowCount}/${created.card.round.maxArrows}"
                expectedSummary = emptyCardSummary
                assertTextEventually(instrumentation, emptyCardSummary)
                // Exercise the actual numeric keypad and undo controls, not only the Room boundary.
                clickTextEventually(instrumentation, "10")
                val scoredSummary = "10 · 0 X · 1/${created.card.round.maxArrows}"
                assertTextEventually(instrumentation, scoredSummary)

                // Recreate the real activity while the entered arrow is still committed. A fresh
                // ViewModel must reload the scored card from Room rather than only restoring an empty card.
                scenario.recreate()
                assertTextEventually(instrumentation, scoredSummary)

                // System Back must leave the active scoring destination without discarding the
                // committed arrow; reopening Score resumes the same card.
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                assertTextEventually(instrumentation, "Find your rhythm.")
                clickTextEventually(instrumentation, "Score")
                assertTextEventually(instrumentation, scoredSummary)

                clickTextEventually(instrumentation, "Undo")
                assertTextEventually(instrumentation, emptyCardSummary)
            }

            // A new Activity gets a new ViewModel; the screen must resume the persisted card.
            ActivityScenario.launch(MainActivity::class.java).use {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                assertTextEventually(instrumentation, "CROCODYL  /  RECURVE")
                clickTextEventually(instrumentation, "Score")
                assertTextEventually(instrumentation, expectedSummary)
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

    private fun rootInActiveWindowOrNull(instrumentation: android.app.Instrumentation): AccessibilityNodeInfo? =
        runCatching { instrumentation.uiAutomation.rootInActiveWindow }.getOrNull()

    private fun assertTextEventually(
        instrumentation: android.app.Instrumentation,
        expected: String,
    ) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val root = rootInActiveWindowOrNull(instrumentation)
            if (root != null && containsText(root, expected)) return
            SystemClock.sleep(50)
        }
        val visibleText = rootInActiveWindowOrNull(instrumentation)?.let(::collectVisibleText).orEmpty()
        throw AssertionError("Timed out waiting for visible UI text: $expected. Visible: $visibleText")
    }

    private fun assertTextAbsent(
        instrumentation: android.app.Instrumentation,
        unexpected: String,
    ) {
        instrumentation.waitForIdleSync()
        val root = rootInActiveWindowOrNull(instrumentation)
        assertFalse("Unexpected Home chrome is visible: $unexpected", root?.let { containsText(it, unexpected) } == true)
    }

    private fun assertAnyTextEventually(
        instrumentation: android.app.Instrumentation,
        expected: List<String>,
    ) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val root = rootInActiveWindowOrNull(instrumentation)
            if (root != null && expected.any { containsText(root, it) }) return
            SystemClock.sleep(50)
        }
        val visibleText = rootInActiveWindowOrNull(instrumentation)?.let(::collectVisibleText).orEmpty()
        throw AssertionError("Timed out waiting for any of $expected. Visible: $visibleText")
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
            val root = rootInActiveWindowOrNull(instrumentation)
            val target = root?.findClickableTextInBottomArea(label, minBottom)
            if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Could not click bottom navigation tab: $label")
    }

    private fun scrollHomeUntilText(
        instrumentation: android.app.Instrumentation,
        expected: String,
        forward: Boolean,
    ) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val width = context.resources.displayMetrics.widthPixels.toFloat()
        val height = context.resources.displayMetrics.heightPixels.toFloat()
        // Use short, deliberate swipes so a fast fling cannot skip past the target card.
        val startY = height * if (forward) .66f else .52f
        val endY = height * if (forward) .52f else .66f
        val x = width * .5f
        val automation = instrumentation.uiAutomation
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val root = rootInActiveWindowOrNull(instrumentation)
            if (root != null && containsText(root, expected)) return
            val downTime = SystemClock.uptimeMillis()
            fun inject(action: Int, y: Float) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                }
                try {
                    assertTrue("Could not inject Home feed scroll", automation.injectInputEvent(event, true))
                } finally {
                    event.recycle()
                }
            }
            inject(MotionEvent.ACTION_DOWN, startY)
            repeat(8) { step ->
                inject(MotionEvent.ACTION_MOVE, startY + (endY - startY) * (step + 1) / 8f)
                SystemClock.sleep(35)
            }
            inject(MotionEvent.ACTION_UP, endY)
            SystemClock.sleep(250)
        }
        throw AssertionError("Could not scroll Home to visible UI text: $expected")
    }

    private fun clickTextEventually(
        instrumentation: android.app.Instrumentation,
        label: String,
    ) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val root = rootInActiveWindowOrNull(instrumentation)
            val target = root?.findClickableText(label)
            if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Could not click visible UI text: $label")
    }

    private fun dismissCameraPermissionPromptIfShown(
        instrumentation: android.app.Instrumentation,
    ) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        var denyClicked = false
        var backSent = false
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val root = rootInActiveWindowOrNull(instrumentation)
            if (root == null) {
                SystemClock.sleep(50)
                continue
            }
            if (
                containsText(root, "Grant camera") ||
                containsText(root, "Camera access is blocked. Allow it in app settings to analyse form.") ||
                containsText(root, "Camera permission is required to analyse form.") ||
                containsText(
                    root,
                    "This device has no camera, so form capture is unavailable here. " +
                        "Scoring, training logs and everything else still work.",
                )
            ) return

            val systemPromptVisible =
                containsText(root, "Allow Crocodyl to take pictures and record video?")
            val deny = listOf("Don't allow", "Don’t allow")
                .firstNotNullOfOrNull { root.findClickableText(it) }
            if (!denyClicked && deny != null && deny.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                denyClicked = true
                SystemClock.sleep(250)
                continue
            }
            if (systemPromptVisible && !backSent) {
                // Some API-36 permission-controller builds report a successful accessibility click
                // without dismissing the dialog. Back cancels the request (the denied path under
                // test); keep waiting for the app's own safe-state UI before accepting the smoke.
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                backSent = true
                SystemClock.sleep(250)
                continue
            }
            SystemClock.sleep(50)
        }
        val visible = rootInActiveWindowOrNull(instrumentation)?.let(::collectVisibleText).orEmpty()
        throw AssertionError("Camera prompt or safe denial/no-camera UI was not visible: $visible")
    }

    private fun scrollForwardUntilText(
        instrumentation: android.app.Instrumentation,
        expected: String,
    ) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val root = rootInActiveWindowOrNull(instrumentation)
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

    private fun AccessibilityNodeInfo.findContentDescriptionPrefix(expected: String): AccessibilityNodeInfo? {
        if (contentDescription?.toString()?.startsWith(expected) == true) return this
        for (index in 0 until childCount) {
            getChild(index)?.findContentDescriptionPrefix(expected)?.let { return it }
        }
        return null
    }
}
