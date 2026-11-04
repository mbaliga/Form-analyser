package xyz.mdhv.formanalyser.app.widget

import android.app.Application
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.room.InvalidationTracker
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.mdhv.formanalyser.app.MainActivity
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.data.AthleteEntity
import xyz.mdhv.formanalyser.app.data.CheckinEntity
import xyz.mdhv.formanalyser.app.data.InjuryEntity
import xyz.mdhv.formanalyser.app.data.LifeEventEntity
import xyz.mdhv.formanalyser.app.data.PhysioExerciseEntity
import xyz.mdhv.formanalyser.app.data.PhysioPlanEntity
import xyz.mdhv.formanalyser.app.data.RigEntity
import xyz.mdhv.formanalyser.app.data.ScoringRepository
import xyz.mdhv.formanalyser.app.data.SessionEntity
import xyz.mdhv.formanalyser.app.data.TrainingPlanEntity
import xyz.mdhv.formanalyser.scoring.ArrowScore
import xyz.mdhv.formanalyser.scoring.RoundPack

/**
 * The widgets end to end on the real Room schema: what each one loads, that every layout inflates
 * under RemoteViews' own rules at every size, and that private or medical words never reach a
 * home screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetSourceTest {
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private val now: ZonedDateTime = ZonedDateTime.now()
    private val today: LocalDate = now.toLocalDate()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        AppDatabase.resetForTests()
        app.deleteDatabase("form-analyser.db")
        WidgetDatabase.clearFailure(app)
        db = AppDatabase.get(app)
    }

    @After
    fun tearDown() {
        AppDatabase.resetForTests()
        WidgetLaunch.take()
    }

    private fun load(kind: WidgetKind) = runBlocking { WidgetSource.load(app, kind, now) }

    private fun seedAthlete() = runBlocking {
        db.athleteDao().upsert(AthleteEntity("a1", "Private Person", 65.0, drawLengthMm = 700))
    }

    /** Every string a launcher would draw or read aloud for these views. */
    private fun texts(content: WidgetContent, kind: WidgetKind, size: WidgetSize): List<String> {
        val views = WidgetRenderer.build(app, 7, kind, content, size, now)
        val root = views.apply(app, FrameLayout(app))
        fun collect(v: View): List<String> =
            listOfNotNull(v.contentDescription?.toString()) +
                when (v) {
                    is TextView -> listOf(v.text.toString())
                    is ViewGroup -> (0 until v.childCount).flatMap { collect(v.getChildAt(it)) }
                    else -> emptyList()
                }
        return collect(root)
    }

    private fun allTexts(content: WidgetContent, kind: WidgetKind) =
        WidgetSize.entries.flatMap { texts(content, kind, it) }

    @Test
    fun beforeOnboardingEveryWidgetShowsItsFirstRunState() {
        for (kind in WidgetKind.entries) {
            val content = load(kind)
            assertEquals(kind.name, WidgetContent.FirstRun, content)
            assertTrue(allTexts(content, kind).any { it.contains("Set up Crocodyl") })
        }
    }

    @Test
    fun lastScorecardShowsTheFinishedRoundAndItsArrows() = runBlocking {
        seedAthlete()
        db.rigDao().upsert(RigEntity("r1", "a1", "Indoor recurve", "RECURVE", null, active = true, createdAt = 1))
        assertEquals(WidgetContent.Empty(WidgetKind.LAST_SCORECARD), load(WidgetKind.LAST_SCORECARD))

        val scoring = ScoringRepository(db)
        val card = scoring.start(RoundPack.customPractice("practice-6", "Practice six", 18, 40, 3, 2))
        repeat(6) { scoring.recordNumeric(card.session.id, ArrowScore(9)) }
        scoring.finish(card.session.id)
        // An open card afterwards is not a "last" result.
        scoring.start(RoundPack.customPractice("practice-3", "Open card", 18, 40, 3, 1))

        val content = load(WidgetKind.LAST_SCORECARD) as WidgetContent.LastScorecard
        assertEquals("Practice six", content.model.roundName)
        assertEquals(54, content.model.total)
        assertEquals(60, content.model.maxScore)
        assertEquals(6, content.model.arrowsShot)
        assertEquals(54, content.model.roundBest)
        assertEquals("Indoor recurve", content.model.rigName)
        val small = texts(content, WidgetKind.LAST_SCORECARD, WidgetSize.SMALL)
        assertTrue(small.containsAll(listOf("54", "/60", "6 arrows")))
        assertTrue(texts(content, WidgetKind.LAST_SCORECARD, WidgetSize.LARGE).contains("◆ native · computed on this phone"))
    }

    @Test
    fun thisWeekCountsArrowsAgainstTheActivePlansTarget() = runBlocking {
        seedAthlete()
        db.sessionDao().insert(
            SessionEntity("s1", "a1", now.toInstant().toEpochMilli(), 38.0, 18, arrowsActual = 30),
        )
        db.athleteFeatureDao().upsertTrainingPlan(
            TrainingPlanEntity(
                "p1", "a1", "Indoor build", "BUILD", "Volume", today.minusDays(10).toString(),
                weeklyArrowTarget = 300, createdAtMs = 1, updatedAtMs = 1,
            ),
        )
        val content = load(WidgetKind.THIS_WEEK) as WidgetContent.ThisWeek
        assertEquals(30, content.model.total)
        assertEquals(300, content.model.target)
        assertEquals(1, content.model.sessions)
        assertTrue(texts(content, WidgetKind.THIS_WEEK, WidgetSize.MEDIUM).contains("10% of weekly target"))
        assertTrue(texts(content, WidgetKind.THIS_WEEK, WidgetSize.LARGE).contains("Plan · Indoor build"))
    }

    @Test
    fun readinessShowsTheLevelButNeverTheInjuryOrTheLifeEventBehindIt() = runBlocking {
        seedAthlete()
        db.wellnessDao().insertCheckin(
            CheckinEntity("c1", "a1", now.toInstant().toEpochMilli() - 3_600_000, "STANDALONE", energy = 4, sleep = 4),
        )
        db.bodyDao().upsertInjury(
            InjuryEntity(
                "i1", "a1", today.minusDays(3).toString(), "[\"rotator_cuff_r\"]", severity = 2,
                mechanism = "OVERUSE", status = "ACTIVE", notes = "Physio says ease off",
            ),
        )
        db.wellnessDao().insertLifeEvent(
            LifeEventEntity("l1", today.minusDays(1).toString(), null, "BEREAVEMENT", impact = 3, title = "Grandmother"),
        )

        val content = load(WidgetKind.READINESS) as WidgetContent.Readiness
        assertEquals(ReadinessMark.CAUTION, content.model.mark)
        val drawn = allTexts(content, WidgetKind.READINESS)
        assertTrue(drawn.any { it.contains("◐ Caution") })
        for (secret in listOf("rotator", "injury", "Injury", "Grandmother", "BEREAVEMENT", "stress", "Physio says")) {
            assertFalse("'$secret' reached the widget: $drawn", drawn.any { it.contains(secret) })
        }
        assertTrue(drawn.contains("4/5"))
    }

    @Test
    fun nextPhysioDayShowsTheDayAndCountsButNotThePlanName() = runBlocking {
        seedAthlete()
        val code = today.dayOfWeek.name.take(2)
        db.bodyDao().upsertPlan(
            PhysioPlanEntity(
                "pp1", "a1", "Left shoulder rehab", "[\"rotator_cuff_l\"]", "[\"$code\"]",
                today.minusDays(14).toString(), notes = "After the MRI",
            ),
        )
        db.bodyDao().upsertExercise(PhysioExerciseEntity("e1", "pp1", "Band external rotation", sets = 3))
        db.bodyDao().upsertExercise(PhysioExerciseEntity("e2", "pp1", "Scapular setting", sets = 2))

        val content = load(WidgetKind.NEXT_PHYSIO) as WidgetContent.NextPhysio
        assertEquals(today, content.model.next.date)
        assertEquals(2, content.model.next.exercises)
        val drawn = allTexts(content, WidgetKind.NEXT_PHYSIO)
        assertTrue(drawn.contains("Today"))
        assertTrue(drawn.any { it.contains("2 exercises") })
        for (secret in listOf("shoulder", "rotator", "MRI", "Band", "Scapular")) {
            assertFalse("'$secret' reached the widget", drawn.any { it.contains(secret, ignoreCase = true) })
        }
    }

    @Test
    fun activeRigAndQuickStartReadTheirOwnRows() = runBlocking {
        seedAthlete()
        assertEquals(WidgetContent.Empty(WidgetKind.ACTIVE_RIG), load(WidgetKind.ACTIVE_RIG))
        db.rigDao().upsert(
            RigEntity("r1", "a1", "Indoor recurve", "RECURVE", "{\"v\":2,\"otfLbs\":36.0,\"braceHeightMm\":220.0}", true, 1),
        )
        val rig = load(WidgetKind.ACTIVE_RIG) as WidgetContent.ActiveRig
        assertEquals("Indoor recurve", rig.model.name)
        assertTrue(texts(rig, WidgetKind.ACTIVE_RIG, WidgetSize.LARGE).containsAll(listOf("Brace height", "220 mm")))

        assertNull((load(WidgetKind.QUICK_START) as WidgetContent.QuickStart).open)
        val scoring = ScoringRepository(db)
        val open = scoring.start(RoundPack.customPractice("p", "Practice", 18, 40, 3, 2))
        scoring.recordNumeric(open.session.id, ArrowScore(10))
        val quick = load(WidgetKind.QUICK_START) as WidgetContent.QuickStart
        assertEquals(OpenScorecard("Practice", 1, 6), quick.open)
        assertTrue(texts(quick, WidgetKind.QUICK_START, WidgetSize.SMALL).contains("Resume"))
        assertTrue(texts(quick, WidgetKind.QUICK_START, WidgetSize.LARGE).contains("Open scorecard · Practice · 1 of 6 arrows"))
    }

    @Test
    fun everyLayoutInflatesUnderRemoteViewsRulesAtEverySize() {
        val samples =
            listOf(
                WidgetKind.LAST_SCORECARD to
                    WidgetContent.LastScorecard(
                        LastScorecardModel("WA 70 m", 70, 612, 720, 72, 72, 14, true, null, today, 8.5, 640, "Rig"),
                    ),
                WidgetKind.THIS_WEEK to
                    WidgetContent.ThisWeek(WeekModel(today, List(7) { it * 10 }, 3, 210, 4, 300, "Plan")),
                WidgetKind.NEXT_PHYSIO to
                    WidgetContent.NextPhysio(
                        NextPhysioModel(
                            PhysioDay(today.plusDays(2), 5, 1),
                            todayLogged = true,
                            upcoming = listOf(PhysioDay(today.plusDays(2), 5, 1), PhysioDay(today.plusDays(4), 3, 1)),
                            adherence = xyz.mdhv.formanalyser.app.domain.PhysioAdherence.Window(8, 6),
                        ),
                    ),
                WidgetKind.READINESS to
                    WidgetContent.Readiness(
                        ReadinessModel(ReadinessMark.REST_ADVISED, CheckinFacts(0, 1, 2, 3), 1.62, 0),
                    ),
                WidgetKind.ACTIVE_RIG to
                    WidgetContent.ActiveRig(
                        RigModel("Rig", xyz.mdhv.formanalyser.model.BowType.RECURVE, 36.0, null, emptyList()),
                    ),
                WidgetKind.QUICK_START to WidgetContent.QuickStart(null),
            )
        for ((kind, content) in samples) {
            for (size in WidgetSize.entries) {
                val drawn = texts(content, kind, size)
                assertTrue("$kind $size drew nothing", drawn.any { it.isNotBlank() })
            }
        }
        for (state in listOf(WidgetContent.NeedsApp, WidgetContent.Error, WidgetContent.Empty(WidgetKind.NEXT_PHYSIO))) {
            assertTrue(allTexts(state, WidgetKind.NEXT_PHYSIO).isNotEmpty())
        }
    }

    @Test
    fun theWidgetsTablesAllExistSoTheInvalidationObserverCanRegister() = runBlocking(Dispatchers.IO) {
        fun observer(tables: Collection<String>) =
            object : InvalidationTracker.Observer(tables.toTypedArray()) {
                override fun onInvalidated(tables: Set<String>) = Unit
            }
        // Room rejects an observer naming a table it does not have...
        assertThrows(IllegalArgumentException::class.java) {
            db.invalidationTracker.addObserver(observer(listOf("no_such_table")))
        }
        // ...so this registration (the one WidgetRefresh makes) proves every name is real.
        db.invalidationTracker.addObserver(observer(WidgetTables.all))
    }

    @Test
    fun aWidgetTapIsAcceptedOnlyWithItsOwnActionAndAKnownDestination() {
        fun tap(destination: String?, action: String = WidgetLaunch.ACTION_OPEN) =
            Intent(app, MainActivity::class.java).setAction(action).putExtra(WidgetLaunch.EXTRA_DESTINATION, destination)

        val good = tap("SCORE")
        WidgetLaunch.accept(good)
        assertEquals(WidgetDestination.SCORE, WidgetLaunch.take())
        assertNull("read once", WidgetLaunch.take())
        assertNull("extra removed after reading", good.getStringExtra(WidgetLaunch.EXTRA_DESTINATION))

        WidgetLaunch.accept(tap("S_EXPORT"))
        WidgetLaunch.accept(tap("SCORE", action = Intent.ACTION_MAIN))
        WidgetLaunch.accept(tap("SCORE").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        WidgetLaunch.accept(null)
        assertNull(WidgetLaunch.pending.value)
    }

    @Test
    fun tapIntentsAreExplicitImmutableAndGoThroughTheTapActivity() {
        val pending = WidgetRenderer.openIntent(app, 3, WidgetDestination.LOG)
        assertTrue(pending.isImmutable)
        val shadow = org.robolectric.Shadows.shadowOf(pending)
        assertTrue(shadow.isActivity)
        val intent = shadow.savedIntent
        assertEquals(WidgetTapActivity::class.java.name, intent.component?.className)
        assertEquals(WidgetLaunch.ACTION_OPEN, intent.action)
        assertEquals("LOG", intent.getStringExtra(WidgetLaunch.EXTRA_DESTINATION))
        assertNull("no URL scheme", intent.data)
        // CLEAR_TOP would finish a Fencing bout or a journal entry open above the main screen.
        assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    @Test
    fun readinessWithNoCheckinSaysSoWhereverTheLevelIsShown() = runBlocking {
        seedAthlete()
        val content = load(WidgetKind.READINESS) as WidgetContent.Readiness
        // The cascade has nothing to lower the level with, so it reads Ready...
        assertEquals(ReadinessMark.READY, content.model.mark)
        assertNull(content.model.checkin)
        // ...and every layout that shows it says that no check-in is behind it.
        for (size in WidgetSize.entries) {
            val drawn = texts(content, WidgetKind.READINESS, size)
            assertTrue("$size: $drawn", drawn.any { it.contains("● Ready") })
            assertTrue("$size: $drawn", drawn.any { it.contains("No check-in logged") })
        }
    }

    @Test
    fun aDatabaseThatFailedToOpenIsNotRetriedFromTheHomeScreen() {
        val flag = java.io.File(app.noBackupFilesDir, "widgets/database-open-failed")
        flag.parentFile?.mkdirs()
        flag.writeText(xyz.mdhv.formanalyser.app.BuildConfig.VERSION_CODE.toString())
        assertEquals(WidgetContent.NeedsApp, load(WidgetKind.READINESS))
        WidgetDatabase.clearFailure(app)
        assertFalse(flag.exists())
        assertNotNull(WidgetDatabase.openOrNull(app))
    }
}
