package xyz.mdhv.formanalyser.app

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Base64
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
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.room.withTransaction
import com.google.mediapipe.framework.image.BitmapImageBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.mdhv.formanalyser.app.data.ScoringRepository
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.app.data.AppDataReset
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.data.AppPrefs
import xyz.mdhv.formanalyser.app.data.AthleteEntity
import xyz.mdhv.formanalyser.app.data.CheckinEntity
import xyz.mdhv.formanalyser.app.data.InjuryEntity
import xyz.mdhv.formanalyser.app.data.PainLogEntity
import xyz.mdhv.formanalyser.app.data.ScoreSessionEntity
import xyz.mdhv.formanalyser.app.data.SessionEntity
import xyz.mdhv.formanalyser.app.data.SportSessionEntity
import xyz.mdhv.formanalyser.app.ai.AiSettings
import xyz.mdhv.formanalyser.app.domain.ExportViewModel
import xyz.mdhv.formanalyser.app.exchange.AndroidKeyProvider

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
    fun signedExportOmitsRetractedRowsFromEveryRetractableTable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val application = context.applicationContext as android.app.Application
        val suffix = UUID.randomUUID().toString()
        val testDatabaseName = "export-privacy-$suffix.db"
        val database = Room.databaseBuilder(context, AppDatabase::class.java, testDatabaseName).build()
        val athleteId = "export-qa-athlete-$suffix"
        val timestamp = System.currentTimeMillis()
        val pairs =
            mapOf(
                "sessions" to ("session-active-$suffix" to "session-deleted-$suffix"),
                "score_session" to ("score-active-$suffix" to "score-deleted-$suffix"),
                "sport_session" to ("sport-active-$suffix" to "sport-deleted-$suffix"),
                "checkin" to ("checkin-active-$suffix" to "checkin-deleted-$suffix"),
                "pain_log" to ("pain-active-$suffix" to "pain-deleted-$suffix"),
                "injury" to ("injury-active-$suffix" to "injury-deleted-$suffix"),
            )
        val shareDirectory = File(context.cacheDir, "share").apply { mkdirs() }
        val destination = File(shareDirectory, "retraction-$suffix.croc")
        val uri: Uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destination,
            )
        val viewModel = ExportViewModel(application, database)

        try {
            database.sessionDao().insert(
                SessionEntity(pairs.getValue("sessions").first, athleteId, timestamp, 25.0, 18),
            )
            database.sessionDao().insert(
                SessionEntity(
                    pairs.getValue("sessions").second,
                    athleteId,
                    timestamp + 1,
                    25.0,
                    18,
                    deletedAt = timestamp,
                ),
            )
            listOf(false, true).forEach { retracted ->
                val id = if (retracted) pairs.getValue("score_session").second else pairs.getValue("score_session").first
                database.scoringDao().upsertSession(
                    ScoreSessionEntity(
                        id = id,
                        athleteId = athleteId,
                        rigId = null,
                        roundId = "wa-18m",
                        roundName = "Export QA",
                        distanceMeters = 18,
                        targetFaceCm = 40,
                        arrowsPerEnd = 3,
                        endCount = 2,
                        scoringKind = "CUMULATIVE",
                        faceLayout = "SINGLE",
                        startedAt = timestamp,
                        updatedAt = timestamp,
                        deletedAt = if (retracted) timestamp else null,
                    ),
                )
                val sportId = if (retracted) pairs.getValue("sport_session").second else pairs.getValue("sport_session").first
                database.sportSessionDao().insert(
                    SportSessionEntity(
                        id = sportId,
                        athleteId = athleteId,
                        sportId = "recurve",
                        startedAtEpochMs = timestamp,
                        durationSeconds = 600,
                        perceivedEffortRpe = 3,
                        summary = "Synthetic export QA",
                        detailsJson = "{}",
                        featuresJson = "{}",
                        deletedAt = if (retracted) timestamp else null,
                    ),
                )
                val checkinId = if (retracted) pairs.getValue("checkin").second else pairs.getValue("checkin").first
                database.wellnessDao().insertCheckin(
                    CheckinEntity(
                        id = checkinId,
                        athleteId = athleteId,
                        ts = timestamp,
                        kind = "STANDALONE",
                        energy = 3,
                        deletedAt = if (retracted) timestamp else null,
                    ),
                )
                val painId = if (retracted) pairs.getValue("pain_log").second else pairs.getValue("pain_log").first
                database.bodyDao().insertPain(
                    PainLogEntity(
                        id = painId,
                        athleteId = athleteId,
                        ts = timestamp,
                        regionId = "shoulder_r",
                        intensity = 2,
                        tagsJson = "[]",
                        deletedAt = if (retracted) timestamp else null,
                    ),
                )
                val injuryId = if (retracted) pairs.getValue("injury").second else pairs.getValue("injury").first
                database.bodyDao().upsertInjury(
                    InjuryEntity(
                        id = injuryId,
                        athleteId = athleteId,
                        onset = "2026-10-18",
                        regionsJson = "[\"shoulder_r\"]",
                        severity = 1,
                        mechanism = "UNKNOWN",
                        status = "ACTIVE",
                        deletedAt = if (retracted) timestamp else null,
                    ),
                )
            }

            viewModel.export(uri)
            val outcome = withTimeout(30_000) { viewModel.outcome.first { it != null }!! }
            assertTrue("Synthetic signed export failed: ${outcome.message}", outcome.ok)

            val payloads = mutableMapOf<String, JSONArray>()
            ZipInputStream(destination.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name.startsWith("tables/") && entry.name.endsWith(".json")) {
                        payloads[entry.name.removePrefix("tables/").removeSuffix(".json")] =
                            JSONArray(zip.readBytes().toString(Charsets.UTF_8))
                    }
                    zip.closeEntry()
                }
            }
            pairs.forEach { (table, ids) ->
                val rows = payloads[if (table == "sessions") "session" else table]
                assertNotNull("Signed archive omitted $table", rows)
                val exportedIds = (0 until rows!!.length()).map { rows.getJSONObject(it).getString("id") }
                assertTrue("Active $table row was not exported", ids.first in exportedIds)
                assertFalse("Retracted $table row was exported", ids.second in exportedIds)
            }
            val manifest = ZipInputStream(destination.inputStream()).use { zip ->
                assertEquals("manifest.json", zip.nextEntry?.name)
                JSONObject(zip.readBytes().toString(Charsets.UTF_8))
            }
            assertEquals("SHA256withECDSA/CrocodylEnvelopeV2", manifest.getString("signatureAlgorithm"))
            val publicKey =
                KeyFactory.getInstance("EC").generatePublic(
                    X509EncodedKeySpec(Base64.decode(manifest.getString("publicKeyBase64"), Base64.DEFAULT)),
                )
            val includedTables =
                manifest.getJSONArray("includedTables").let { tables ->
                    (0 until tables.length()).map(tables::getString)
                }
            val envelope =
                AndroidKeyProvider.envelopePayload(
                    manifest.getString("contentChecksum"),
                    includedTables,
                    manifest.getString("tierName"),
                )
            val signature = Signature.getInstance(AndroidKeyProvider.SIGNATURE_ALGORITHM)
            signature.initVerify(publicKey)
            signature.update(envelope.toByteArray(Charsets.UTF_8))
            assertTrue(
                "Signed .croc consent envelope did not verify",
                signature.verify(Base64.decode(manifest.getString("signatureBase64"), Base64.DEFAULT)),
            )
        } finally {
            destination.delete()
            database.close()
            context.deleteDatabase(testDatabaseName)
        }
    }

    @Test
    fun fullLocalDataWipeRemovesDatabaseFilesAndResetsPreferencesOnDevice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = AppPrefs(context)
        val aiSettings = AiSettings(context)
        val database = AppDatabase.get(context)
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            database.athleteDao().upsert(AthleteEntity("wipe-smoke-athlete", "Wipe smoke", 70.0))
        }
        prefs.setOnboarded(true)
        prefs.setThemeMode("DARK")
        aiSettings.setSelectedModelId("wipe-smoke-model")
        aiSettings.setMedicalGrantDefault(true)
        aiSettings.setKeepPrivate(false)
        aiSettings.setOnDeviceModelPath("/private/wipe-smoke.task")
        val cachedPrivateExport = File(context.cacheDir, ".wipe-smoke.partial").apply {
            writeText("private")
        }
        val vaultMarker = File(context.filesDir, "vault/wipe-smoke").apply {
            parentFile?.mkdirs()
            writeText("private")
        }

        AppDataReset.wipe(context)

        assertFalse("Wipe left the Room database file", context.getDatabasePath("form-analyser.db").exists())
        assertFalse("Wipe left private export cache data", cachedPrivateExport.exists())
        assertFalse("Wipe left encrypted vault data", vaultMarker.exists())
        assertFalse("Wipe did not reset onboarding", prefs.onboarded.first())
        assertEquals("SYSTEM", prefs.themeMode.first())
        assertEquals(AiSettings.DEFAULT_MODEL_ID, aiSettings.selectedModelId.first())
        assertFalse(aiSettings.medicalGrantDefault.first())
        assertTrue(aiSettings.keepPrivate.first())
        assertNull(aiSettings.onDeviceModelPath.first())
        val freshDatabase = withContext(kotlinx.coroutines.Dispatchers.IO) { AppDatabase.get(context) }
        assertEquals(
            "Wipe left the seeded athlete in the replacement database",
            null,
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                freshDatabase.athleteDao().firstOrNull()
            },
        )
    }

    @Test
    fun settingsWipeRequiresTypedConfirmationAndReturnsToOnboarding() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = AppPrefs(context)
        val aiSettings = AiSettings(context)
        val wasOnboarded = prefs.onboarded.first()
        val previousTheme = prefs.themeMode.first()
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            AppDatabase.get(context).athleteDao()
                .upsert(AthleteEntity("wipe-ui-athlete", "Wipe UI", 70.0))
        }
        prefs.setOnboarded(true)
        prefs.setThemeMode("DARK")
        aiSettings.setMedicalGrantDefault(true)
        val cachedPrivateExport = File(context.cacheDir, ".wipe-ui.partial").apply {
            writeText("private")
        }
        val vaultMarker = File(context.filesDir, "vault/wipe-ui").apply {
            parentFile?.mkdirs()
            writeText("private")
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                assertTextEventually(instrumentation, "Find your rhythm.")
                clickBottomTabEventually(instrumentation, "Settings")
                assertTextEventually(instrumentation, "Settings")
                scrollScreenUntilText(instrumentation, "Data", forward = true)
                clickTextEventually(instrumentation, "Data")
                assertTextEventually(instrumentation, "Everything runs on this device — nothing is stored anywhere else, so a wipe has no undo.")
                scrollScreenUntilText(instrumentation, "Wipe everything", forward = true)
                clickTextEventually(instrumentation, "Wipe everything")
                assertTextEventually(instrumentation, "Type WIPE to confirm")
                scrollScreenUntilText(instrumentation, "Erase all data", forward = true)
                assertTextEventually(instrumentation, "Erase all data")
                clickTextEventually(instrumentation, "Erase all data")
                assertTextEventually(instrumentation, "Type WIPE to confirm")

                setEditableTextEventually(instrumentation, "WIPE")
                assertTextEventually(instrumentation, "WIPE")
                scrollScreenUntilText(instrumentation, "Erase all data", forward = true)
                clickTextEventually(instrumentation, "Erase all data")

                assertTextEventually(instrumentation, "What do you do?", timeoutMs = 20_000)
                val replacementDatabase = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    AppDatabase.get(context).athleteDao().firstOrNull()
                }
                assertNull("Settings wipe retained the seeded athlete", replacementDatabase)
                assertFalse("Settings wipe left private vault content", vaultMarker.exists())
                assertFalse("Settings wipe left cached export data", cachedPrivateExport.exists())
                assertFalse("Settings wipe did not reset onboarding", prefs.onboarded.first())
                assertEquals("SYSTEM", prefs.themeMode.first())
                assertFalse(aiSettings.medicalGrantDefault.first())
            }
        } finally {
            prefs.setOnboarded(wasOnboarded)
            prefs.setThemeMode(previousTheme)
        }
    }

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
    fun bocalStyleDockNavigationSelectsCalendar() {
        val prefs = xyz.mdhv.formanalyser.app.data.AppPrefs(
            ApplicationProvider.getApplicationContext(),
        )
        val wasOnboarded = runBlocking { prefs.onboarded.first() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            runBlocking { prefs.setOnboarded(true) }
            ActivityScenario.launch(MainActivity::class.java).use {
                assertAnyTextEventually(
                    instrumentation,
                    listOf("Opening your training space", "Find your rhythm.", "Saved data needs recovery"),
                    timeoutMs = 30_000,
                )
                assertTextEventually(instrumentation, "Find your rhythm.", timeoutMs = 30_000)
                assertTextEventually(instrumentation, "Home")
                assertTextEventually(instrumentation, "Train")
                assertTextEventually(instrumentation, "Score")
                assertTextEventually(instrumentation, "Current sport: Recurve")
                assertTextEventually(instrumentation, "Quick add")
                assertTextEventually(instrumentation, "Settings")
                assertTextEventually(instrumentation, "Progress")
                assertTextEventually(instrumentation, "Body")
                assertTextEventually(instrumentation, "Calendar")
                if (InstrumentationRegistry.getArguments().getString("captureDockVisual") == "true") {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "crocodyl-bocal-floating-dock-home.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Crocodyl QA")
                    }
                    val resolver = instrumentation.targetContext.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("Could not create the Bocal dock visual-QA media record")
                    val screenshot = instrumentation.uiAutomation.takeScreenshot()
                        ?: error("Could not capture the Bocal dock visual-QA screenshot")
                    try {
                        resolver.openOutputStream(uri)?.use { output ->
                            assertTrue(
                                "Could not write the Bocal dock visual-QA screenshot",
                                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output),
                            )
                        } ?: error("Could not open the Bocal dock visual-QA media stream")
                    } finally {
                        screenshot.recycle()
                    }
                }
                swipePrimaryNavigationRail(instrumentation, "Home", "Calendar")
                assertTextEventually(instrumentation, "← prev")
                clickBottomTabEventually(instrumentation, "Calendar")
                assertTextEventually(instrumentation, "← prev")
                clickBottomTabEventually(instrumentation, "Home")
                assertTextEventually(instrumentation, "Find your rhythm.")
                clickBottomTabEventually(instrumentation, "Quick add")
                assertTextEventually(instrumentation, "Local actions · no account required")
                instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                assertTextEventually(instrumentation, "Find your rhythm.")
                clickBottomTabEventually(instrumentation, "Settings")
                assertTextEventually(instrumentation, "Settings")
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
                scrollScreenUntilText(instrumentation, "Arrow, down to each part.", forward = true)
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
                scrollScreenUntilText(instrumentation, "Find your rhythm.", forward = false)
                assertTextEventually(instrumentation, "CROCODYL  /  RECURVE")

                scrollScreenUntilText(instrumentation, "Score", forward = false)
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
    fun unsavedInjuryChangesRequireConfirmationForSystemAndToolbarBack() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = xyz.mdhv.formanalyser.app.data.AppPrefs(appContext)
        val wasOnboarded = runBlocking { prefs.onboarded.first() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            runBlocking { prefs.setOnboarded(true) }
            ActivityScenario.launch(MainActivity::class.java).use {
                assertTextEventually(instrumentation, "CROCODYL  /  RECURVE")
                clickBottomTabEventually(instrumentation, "Body")
                assertTextEventually(instrumentation, "Body & recovery")
                clickTextEventually(instrumentation, "Injuries")
                scrollScreenUntilText(instrumentation, "Log injury", forward = true)
                clickTextEventually(instrumentation, "Log injury")
                scrollScreenUntilText(instrumentation, "Notes", forward = true)
                setEditableTextEventually(instrumentation, "unsaved QA note")
                assertTextEventually(instrumentation, "unsaved QA note")

                clickTextEventually(instrumentation, "Back to previous screen")
                assertTextEventually(instrumentation, "Discard changes?")
                assertTextEventually(instrumentation, "What you have filled in here has not been saved.")
                clickTextEventually(instrumentation, "Keep editing")
                assertTextEventually(instrumentation, "unsaved QA note")

                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                assertTextEventually(instrumentation, "Discard changes?")
                clickTextEventually(instrumentation, "Discard")
                assertTextEventually(instrumentation, "Body & recovery")
            }
        } finally {
            runBlocking { prefs.setOnboarded(wasOnboarded) }
        }
    }

    @Test
    fun unsavedPhysioPlanChangesRequireConfirmationForSystemAndToolbarBack() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = xyz.mdhv.formanalyser.app.data.AppPrefs(appContext)
        val wasOnboarded = runBlocking { prefs.onboarded.first() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            runBlocking { prefs.setOnboarded(true) }
            ActivityScenario.launch(MainActivity::class.java).use {
                assertTextEventually(instrumentation, "CROCODYL  /  RECURVE")
                clickBottomTabEventually(instrumentation, "Body")
                assertTextEventually(instrumentation, "Body & recovery")
                clickTextEventually(instrumentation, "Physio")
                scrollScreenUntilText(instrumentation, "New physio plan", forward = true)
                clickTextEventually(instrumentation, "New physio plan")
                assertTextEventually(instrumentation, "New physio plan")
                setEditableTextEventually(instrumentation, "Unsent rehab plan")
                assertTextEventually(instrumentation, "Unsent rehab plan")

                clickTextEventually(instrumentation, "Back to previous screen")
                assertTextEventually(instrumentation, "Discard changes?")
                clickTextEventually(instrumentation, "Keep editing")
                assertTextEventually(instrumentation, "Unsent rehab plan")

                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                assertTextEventually(instrumentation, "Discard changes?")
                clickTextEventually(instrumentation, "Discard")
                assertTextEventually(instrumentation, "Body & recovery")
            }
        } finally {
            runBlocking { prefs.setOnboarded(wasOnboarded) }
        }
    }

    @Test
    fun unsavedPhysioScheduleSurvivesActivityRecreation() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = xyz.mdhv.formanalyser.app.data.AppPrefs(appContext)
        val wasOnboarded = runBlocking { prefs.onboarded.first() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            runBlocking { prefs.setOnboarded(true) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertTextEventually(instrumentation, "CROCODYL  /  RECURVE")
                clickBottomTabEventually(instrumentation, "Body")
                clickTextEventually(instrumentation, "Physio")
                scrollScreenUntilText(instrumentation, "New physio plan", forward = true)
                clickTextEventually(instrumentation, "New physio plan")
                scrollScreenUntilText(instrumentation, "MO", forward = true)
                clickTextEventually(instrumentation, "MO")

                scenario.recreate()

                scrollScreenUntilText(instrumentation, "MO", forward = true)
                clickTextEventually(instrumentation, "Back to previous screen")
                assertTextEventually(instrumentation, "Discard changes?")
                clickTextEventually(instrumentation, "Discard")
                assertTextEventually(instrumentation, "Body & recovery")
            }
        } finally {
            runBlocking { prefs.setOnboarded(wasOnboarded) }
        }
    }

    @Test
    fun unsavedPhysioExerciseSurvivesActivityRecreation() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = xyz.mdhv.formanalyser.app.data.AppPrefs(appContext)
        val wasOnboarded = runBlocking { prefs.onboarded.first() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            runBlocking { prefs.setOnboarded(true) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertTextEventually(instrumentation, "CROCODYL  /  RECURVE")
                clickBottomTabEventually(instrumentation, "Body")
                clickTextEventually(instrumentation, "Physio")
                scrollScreenUntilText(instrumentation, "New physio plan", forward = true)
                clickTextEventually(instrumentation, "New physio plan")
                scrollScreenUntilText(instrumentation, "Exercise 1", forward = true)
                setEditableTextEventually(instrumentation, "Unsent exercise")
                assertTextEventually(instrumentation, "Unsent exercise")

                scenario.recreate()

                scrollScreenUntilText(instrumentation, "Exercise 1", forward = true)
                assertTextEventually(instrumentation, "Unsent exercise")
                clickTextEventually(instrumentation, "Back to previous screen")
                assertTextEventually(instrumentation, "Discard changes?")
                clickTextEventually(instrumentation, "Discard")
                assertTextEventually(instrumentation, "Body & recovery")
            }
        } finally {
            runBlocking { prefs.setOnboarded(wasOnboarded) }
        }
    }

    @Test
    fun unsavedInjuryRegionSurvivesActivityRecreation() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = xyz.mdhv.formanalyser.app.data.AppPrefs(appContext)
        val wasOnboarded = runBlocking { prefs.onboarded.first() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            runBlocking { prefs.setOnboarded(true) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertTextEventually(instrumentation, "CROCODYL  /  RECURVE")
                clickBottomTabEventually(instrumentation, "Body")
                clickTextEventually(instrumentation, "Injuries")
                scrollScreenUntilText(instrumentation, "Log injury", forward = true)
                clickTextEventually(instrumentation, "Log injury")
                scrollScreenUntilText(instrumentation, "Choose regions from list", forward = false)
                clickTextEventually(instrumentation, "Choose regions from list")
                clickTextEventually(instrumentation, "Back of neck")
                clickTextEventually(instrumentation, "Done")
                assertTextAbsent(instrumentation, "Choose body regions")

                scenario.recreate()

                clickTextEventually(instrumentation, "Back to previous screen")
                assertTextEventually(instrumentation, "Discard changes?")
                clickTextEventually(instrumentation, "Discard")
                assertTextEventually(instrumentation, "Body & recovery")
            }
        } finally {
            runBlocking { prefs.setOnboarded(wasOnboarded) }
        }
    }

    @Test
    fun unsavedPhysioRegionSurvivesActivityRecreation() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = xyz.mdhv.formanalyser.app.data.AppPrefs(appContext)
        val wasOnboarded = runBlocking { prefs.onboarded.first() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            runBlocking { prefs.setOnboarded(true) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertTextEventually(instrumentation, "CROCODYL  /  RECURVE")
                clickBottomTabEventually(instrumentation, "Body")
                clickTextEventually(instrumentation, "Physio")
                scrollScreenUntilText(instrumentation, "New physio plan", forward = true)
                clickTextEventually(instrumentation, "New physio plan")
                scrollScreenUntilText(instrumentation, "Choose regions from list", forward = true)
                clickTextEventually(instrumentation, "Choose regions from list")
                clickTextEventually(instrumentation, "Back of neck")
                clickTextEventually(instrumentation, "Done")

                scenario.recreate()

                clickTextEventually(instrumentation, "Back to previous screen")
                assertTextEventually(instrumentation, "Discard changes?")
                clickTextEventually(instrumentation, "Discard")
                assertTextEventually(instrumentation, "Body & recovery")
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
        val hasCameraHardware =
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val cameraPermissionWasGranted =
            appContext.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
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
                if (hasCameraHardware && !cameraPermissionWasGranted) {
                    dismissCameraPermissionPromptIfShown(instrumentation)
                    assertAnyTextEventually(
                        instrumentation,
                        listOf(
                            "Camera permission is required to analyse form.",
                            "Camera access is blocked. Allow it in app settings to analyse form.",
                        ),
                    )
                } else if (hasCameraHardware) {
                    assertTextEventually(
                        instrumentation,
                        "No archer detected — frame the full body, side-on.",
                    )
                    assertTextAbsent(instrumentation, "Camera permission is required to analyse form.")
                    assertTextAbsent(
                        instrumentation,
                        "Camera access is blocked. Allow it in app settings to analyse form.",
                    )
                } else {
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
            try {
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
            } finally {
                if (hasCameraHardware) {
                    assertEquals(
                        "The training flow must not change camera permission state",
                        cameraPermissionWasGranted,
                        appContext.checkSelfPermission(Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED,
                    )
                }
            }
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
                // Compose exposes the destination heading in the active Home accessibility tree
                // before its hit-test bounds settle during system-Back navigation on API-36.
                assertTextExposedEventually(instrumentation, "Find your rhythm.")
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
            scrollForwardUntilText(instrumentation, "Prêt")
            assertTextEventually(instrumentation, "Prêt")
            scrollForwardUntilText(instrumentation, "Allez")
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

    private fun accessibilityRoots(instrumentation: android.app.Instrumentation): List<AccessibilityNodeInfo> {
        val automation = instrumentation.uiAutomation
        return runCatching {
            val info = automation.serviceInfo
            val retrieveWindows =
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            if (info.flags and retrieveWindows == 0) {
                info.flags = info.flags or retrieveWindows
                automation.serviceInfo = info
            }
            automation.windows.mapNotNull { it.root }
        }.getOrElse { listOfNotNull(rootInActiveWindowOrNull(instrumentation)) }
    }

    private fun assertTextEventually(
        instrumentation: android.app.Instrumentation,
        expected: String,
        timeoutMs: Long = 8_000,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var consecutiveVisibleSamples = 0
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val root = rootInActiveWindowOrNull(instrumentation)
            if (root?.findVisibleTextNode(expected) != null) {
                consecutiveVisibleSamples++
                if (consecutiveVisibleSamples >= 2) return
                SystemClock.sleep(75)
                continue
            }
            consecutiveVisibleSamples = 0
            SystemClock.sleep(50)
        }
        val visibleText = rootInActiveWindowOrNull(instrumentation)?.let(::collectVisibleText).orEmpty()
        throw AssertionError("Timed out waiting for visible UI text: $expected. Visible: $visibleText")
    }

    private fun assertTextExposedEventually(
        instrumentation: android.app.Instrumentation,
        expected: String,
        timeoutMs: Long = 8_000,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (rootInActiveWindowOrNull(instrumentation)?.findTextNode(expected) != null) return
            SystemClock.sleep(50)
        }
        val visibleText = rootInActiveWindowOrNull(instrumentation)?.let(::collectVisibleText).orEmpty()
        throw AssertionError("Timed out waiting for foreground accessibility text: $expected. Visible: $visibleText")
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
        timeoutMs: Long = 8_000,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
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
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val root = rootInActiveWindowOrNull(instrumentation)
            val target = root?.findClickableText(label)
            if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            val named = root?.findVisibleTextNode(label)
            if (named != null) {
                val bounds = Rect()
                named.getBoundsInScreen(bounds)
                tap(instrumentation, bounds.centerX().toFloat(), bounds.centerY().toFloat())
                return
            }
            SystemClock.sleep(50)
        }
        val visibleText = rootInActiveWindowOrNull(instrumentation)?.let(::collectVisibleText).orEmpty()
        throw AssertionError("Could not click bottom navigation tab: $label. Visible: $visibleText")
    }

    private fun swipePrimaryNavigationRail(
        instrumentation: android.app.Instrumentation,
        from: String,
        to: String,
    ) {
        val root = rootInActiveWindowOrNull(instrumentation)
            ?: throw AssertionError("Primary navigation is not accessible")
        val dock = root.findVisibleTextNode("Current sport: Recurve")
            ?: throw AssertionError("Could not locate the Bocal arc navigation stage")
        val bounds = Rect().also(dock::getBoundsInScreen)
        val seatX = mapOf("Home" to 46.8f, "Train" to 123.6f, "Progress" to 200f, "Body" to 276.4f, "Calendar" to 353.2f)
        val startX = bounds.left + bounds.width() * seatX.getValue(from) / 400f
        val endX = bounds.left + bounds.width() * seatX.getValue(to) / 400f
        val railY = bounds.top + bounds.width() * 107.4f / 400f
        val downTime = SystemClock.uptimeMillis()
        val automation = instrumentation.uiAutomation
        fun inject(action: Int, fraction: Float) {
            val event = MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                startX + (endX - startX) * fraction,
                railY,
                0,
            ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try {
                assertTrue("Could not inject primary navigation scrub", automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
        inject(MotionEvent.ACTION_DOWN, 0f)
        repeat(12) { step ->
            inject(MotionEvent.ACTION_MOVE, (step + 1) / 12f)
            SystemClock.sleep(40)
        }
        inject(MotionEvent.ACTION_UP, 1f)
    }

    private fun scrollScreenUntilText(
        instrumentation: android.app.Instrumentation,
        expected: String,
        forward: Boolean,
    ) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val width = context.resources.displayMetrics.widthPixels.toFloat()
        val height = context.resources.displayMetrics.heightPixels.toFloat()
        // Small, deliberate swipes avoid skipping a specific card at larger accessibility sizes.
        val startY = height * if (forward) .66f else .52f
        val endY = height * if (forward) .52f else .66f
        val x = width * .5f
        val automation = instrumentation.uiAutomation
        // Slow emulators (the 16 KB page-size image boots in ~2 min) and large font scales need a
        // longer budget; the assertion itself depends on the text appearing, not on swipe counts.
        val deadline = SystemClock.uptimeMillis() + 45_000
        var consecutiveVisibleSamples = 0
        // Start in the requested direction, but when that edge of the list is reached sweep back the
        // other way: a viewport-sized accessibility scroll can step past a card that only a
        // straddling frame would have shown.
        var direction = forward
        var edgeFlips = 0
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val root = rootInActiveWindowOrNull(instrumentation)
            if (root?.findVisibleTextNode(expected) != null) {
                consecutiveVisibleSamples++
                if (consecutiveVisibleSamples >= 2) return
                SystemClock.sleep(75)
                continue
            }
            consecutiveVisibleSamples = 0
            // Compose's scrollable semantics are more reliable than coordinate swipes in the
            // minified build at large font scales. Keep the touch fallback for screens whose
            // accessibility tree does not expose a scroll action.
            if (root?.scrollOnce(direction) == true) {
                SystemClock.sleep(250)
                continue
            }
            if (edgeFlips < 4 && root?.scrollOnce(!direction) == true) {
                direction = !direction
                edgeFlips++
                SystemClock.sleep(250)
                continue
            }
            val downTime = SystemClock.uptimeMillis()
            fun inject(action: Int, y: Float) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                }
                try {
                    assertTrue("Could not inject screen scroll", automation.injectInputEvent(event, true))
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
        val visible = rootInActiveWindowOrNull(instrumentation)?.let(::collectVisibleText).orEmpty().distinct()
        throw AssertionError("Could not scroll to visible UI text: $expected. Visible: $visible")
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
            val named = root?.findVisibleTextNode(label)
            if (named != null) {
                val bounds = Rect()
                named.getBoundsInScreen(bounds)
                tap(instrumentation, bounds.centerX().toFloat(), bounds.centerY().toFloat())
                return
            }
            SystemClock.sleep(50)
        }
        val visible = rootInActiveWindowOrNull(instrumentation)?.let(::collectVisibleText).orEmpty().distinct()
        throw AssertionError("Could not click visible UI text: $label. Visible: $visible")
    }

    private fun setEditableTextEventually(
        instrumentation: android.app.Instrumentation,
        value: String,
    ) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val editable = rootInActiveWindowOrNull(instrumentation)?.findEditableNode()
            if (editable != null) {
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                }
                if (editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) return
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("Could not enter text into the visible confirmation field")
    }

    private fun dismissCameraPermissionPromptIfShown(
        instrumentation: android.app.Instrumentation,
    ) {
        // The API-36 permission controller can take several seconds to create its window on a
        // software-rendered 16 KB emulator. Inspect all accessibility windows and allow that
        // controller time to settle instead of treating an empty active-window tree as dismissal.
        val deadline = SystemClock.uptimeMillis() + 20_000
        var denyAttempts = 0
        var lastDenyAttemptAt = 0L
        var backSent = false
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val roots = accessibilityRoots(instrumentation)
            val activeRoot = rootInActiveWindowOrNull(instrumentation)
            if (
                activeRoot != null && (
                    containsText(activeRoot, "Grant camera") ||
                        containsText(activeRoot, "Camera access is blocked. Allow it in app settings to analyse form.") ||
                        containsText(activeRoot, "Camera permission is required to analyse form.") ||
                        containsText(
                            activeRoot,
                            "This device has no camera, so form capture is unavailable here. " +
                                "Scoring, training logs and everything else still work.",
                        )
                    )
            ) return

            val systemPromptVisible = roots.any {
                containsText(it, "Allow Crocodyl to take pictures and record video?")
            }
            val now = SystemClock.uptimeMillis()
            val deny = listOf("Don't allow", "Don’t allow")
                .firstNotNullOfOrNull { label -> roots.firstNotNullOfOrNull { it.findTextNode(label) } }
            if (
                systemPromptVisible && deny != null && denyAttempts < 2 &&
                (denyAttempts == 0 || now - lastDenyAttemptAt >= 2_000)
            ) {
                // The permission controller's text nodes are not always marked clickable. A touch
                // at the label's bounds activates the actual button; allow time for the window to
                // transition before trying again (some Android versions require a second denial).
                val bounds = Rect().also(deny::getBoundsInScreen)
                tap(instrumentation, bounds.centerX().toFloat(), bounds.centerY().toFloat())
                denyAttempts++
                lastDenyAttemptAt = now
                SystemClock.sleep(500)
                continue
            }
            if (
                systemPromptVisible && !backSent && denyAttempts >= 2 &&
                now - lastDenyAttemptAt >= 2_000
            ) {
                // If the dialog remains after two explicit denials, Back cancels it. Acceptance
                // still requires the app's own denial UI below.
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                backSent = true
                SystemClock.sleep(250)
                continue
            }
            SystemClock.sleep(50)
        }
        val visible = listOfNotNull(rootInActiveWindowOrNull(instrumentation))
            .plus(accessibilityRoots(instrumentation))
            .flatMap(::collectVisibleText)
            .distinct()
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
            if (root?.findVisibleTextNode(expected) != null) return
            if (root != null) root.scrollOnce(forward = true)
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

    private fun AccessibilityNodeInfo.scrollBackwardOnce(): Boolean {
        if (isScrollable && performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return true
        for (index in 0 until childCount) {
            if (getChild(index)?.scrollBackwardOnce() == true) return true
        }
        return false
    }

    private fun AccessibilityNodeInfo.scrollOnce(forward: Boolean): Boolean =
        if (forward) scrollForwardOnce() else scrollBackwardOnce()

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

    private fun AccessibilityNodeInfo.findClickableText(expected: String): AccessibilityNodeInfo? {
        if (
            isVisiblyOnScreen() && isClickable &&
                (text?.toString() == expected || contentDescription?.toString() == expected)
        ) return this
        for (index in 0 until childCount) {
            getChild(index)?.findClickableText(expected)?.let { return it }
        }
        return null
    }

    private fun AccessibilityNodeInfo.findVisibleTextNode(expected: String): AccessibilityNodeInfo? {
        if (isVisiblyOnScreen() && (text?.toString() == expected || contentDescription?.toString() == expected)) return this
        for (index in 0 until childCount) {
            getChild(index)?.findVisibleTextNode(expected)?.let { return it }
        }
        return null
    }

    private fun AccessibilityNodeInfo.isVisiblyOnScreen(): Boolean {
        if (!isVisibleToUser) return false
        val metrics = ApplicationProvider.getApplicationContext<android.content.Context>()
            .resources.displayMetrics
        val bounds = Rect()
        getBoundsInScreen(bounds)
        return bounds.intersect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun AccessibilityNodeInfo.findTextNode(expected: String): AccessibilityNodeInfo? {
        if (text?.toString() == expected || contentDescription?.toString() == expected) return this
        for (index in 0 until childCount) {
            getChild(index)?.findTextNode(expected)?.let { return it }
        }
        return null
    }

    private fun AccessibilityNodeInfo.findEditableNode(): AccessibilityNodeInfo? {
        if (isEditable && isVisiblyOnScreen()) return this
        for (index in 0 until childCount) {
            getChild(index)?.findEditableNode()?.let { return it }
        }
        return null
    }

    private fun tap(instrumentation: android.app.Instrumentation, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(
            downTime,
            downTime,
            android.view.MotionEvent.ACTION_DOWN,
            x,
            y,
            0,
        )
        val up = android.view.MotionEvent.obtain(
            downTime,
            downTime + 80,
            android.view.MotionEvent.ACTION_UP,
            x,
            y,
            0,
        )
        try {
            instrumentation.uiAutomation.injectInputEvent(down, true)
            instrumentation.uiAutomation.injectInputEvent(up, true)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun AccessibilityNodeInfo.findContentDescriptionPrefix(expected: String): AccessibilityNodeInfo? {
        if (contentDescription?.toString()?.startsWith(expected) == true) return this
        for (index in 0 until childCount) {
            getChild(index)?.findContentDescriptionPrefix(expected)?.let { return it }
        }
        return null
    }
}
