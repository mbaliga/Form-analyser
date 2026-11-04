package xyz.mdhv.formanalyser.app.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mdhv.formanalyser.app.domain.ArrowDto
import xyz.mdhv.formanalyser.app.domain.RigTuning
import xyz.mdhv.formanalyser.app.domain.TrainingVolume
import xyz.mdhv.formanalyser.equipment.PoundageSource
import xyz.mdhv.formanalyser.model.BowType
import xyz.mdhv.formanalyser.wellness.PrivacyRegistry
import xyz.mdhv.formanalyser.wellness.ReadinessLevel

/** The pure rules behind every home-screen widget. Plain JUnit: no Android, no Robolectric. */
class WidgetModelTest {
    private val utc = ZoneOffset.UTC

    private fun ms(date: LocalDate, hour: Int = 12, zone: ZoneId = utc) =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    // ---------------------------------------------------------------------------------------
    // Sizing
    // ---------------------------------------------------------------------------------------

    @Test
    fun layoutFollowsTheCellsALauncherReportsAndTheTemplate() {
        val stat = WidgetTemplate.STAT
        // Cell sizes from the platform's guide for a 4x5 phone grid (portrait / landscape).
        assertEquals(WidgetSize.COMPACT, WidgetSizing.of(stat, 110f, 40f, 1f)) // 2x1 at its minimum
        assertEquals(WidgetSize.COMPACT, WidgetSizing.of(stat, 554f, 51f, 1f)) // 4x1 in landscape
        assertEquals(WidgetSize.SMALL, WidgetSizing.of(stat, 130f, 102f, 1f)) // 2x1 in portrait
        assertEquals(WidgetSize.SMALL, WidgetSizing.of(stat, 130f, 220f, 1f)) // 2x2, narrow
        assertEquals(WidgetSize.SMALL, WidgetSizing.of(stat, 130f, 455f, 1f)) // 2x4: narrow stays small
        assertEquals(WidgetSize.MEDIUM, WidgetSizing.of(stat, 276f, 102f, 1f)) // 4x1 in portrait
        assertEquals(WidgetSize.MEDIUM, WidgetSizing.of(stat, 276f, 220f, 1f)) // 4x2
        assertEquals(WidgetSize.LARGE, WidgetSizing.of(stat, 276f, 337f, 1f)) // 4x3
        // Four rows and the 48dp Log check-in action need more than a 230dp tall widget.
        assertEquals(WidgetSize.MEDIUM, WidgetSizing.of(stat, 276f, 230f, 1f))
        // The card's medium layout stacks five lines, too many for a 4x1.
        assertEquals(WidgetSize.SMALL, WidgetSizing.of(WidgetTemplate.CARD, 276f, 102f, 1f))
        assertEquals(WidgetSize.MEDIUM, WidgetSizing.of(WidgetTemplate.CARD, 276f, 220f, 1f))
        // The progress layouts fit a 4x1 and, with the day strip, a 4x2.
        assertEquals(WidgetSize.SMALL, WidgetSizing.of(WidgetTemplate.PROGRESS, 130f, 102f, 1f))
        assertEquals(WidgetSize.MEDIUM, WidgetSizing.of(WidgetTemplate.PROGRESS, 276f, 102f, 1f))
        // Pads: one target on a short widget, three across a 4x1, six from 4x2.
        assertEquals(WidgetSize.COMPACT, WidgetSizing.of(WidgetTemplate.PADS, 554f, 51f, 1f))
        assertEquals(WidgetSize.MEDIUM, WidgetSizing.of(WidgetTemplate.PADS, 276f, 102f, 1f))
        assertEquals(WidgetSize.LARGE, WidgetSizing.of(WidgetTemplate.PADS, 276f, 220f, 1f))
    }

    @Test
    fun aLargerFontNeedsMoreHeightForTheSameLayout() {
        // 4x1 portrait: the stat's block fits at 1x but not at 1.3x, so it collapses to one line.
        assertEquals(WidgetSize.MEDIUM, WidgetSizing.of(WidgetTemplate.STAT, 276f, 102f, 1f))
        assertEquals(WidgetSize.COMPACT, WidgetSizing.of(WidgetTemplate.STAT, 276f, 102f, 1.3f))
        // 4x3: large at 1x and 1.3x, medium at 2x.
        assertEquals(WidgetSize.LARGE, WidgetSizing.of(WidgetTemplate.STAT, 276f, 337f, 1.3f))
        assertEquals(WidgetSize.MEDIUM, WidgetSizing.of(WidgetTemplate.STAT, 276f, 337f, 2f))
        for (template in WidgetTemplate.entries) {
            for (size in WidgetSize.entries) {
                val need = WidgetSizing.need(template, size)
                assertTrue("$template $size", need.heightDp(1.3f) > need.heightDp(1f))
            }
        }
    }

    @Test
    fun sizedLayoutKeysAreAChainThatAgreesWithTheFallbackRule() {
        for (template in WidgetTemplate.entries) {
            for (scale in listOf(0.85f, 1f, 1.3f, 2f)) {
                val keys = WidgetSizing.keys(template, scale)
                assertEquals(WidgetSize.entries, keys.keys.toList())
                assertEquals(1f to 1f, keys.getValue(WidgetSize.COMPACT))
                assertEquals("one layout per size", keys.size, keys.values.toSet().size)
                // Each key covers the one before it, so the launcher's nearest fitting key is the
                // largest layout that fits...
                keys.values.zipWithNext().forEach { (a, b) ->
                    assertTrue("$template $scale", b.first >= a.first && b.second >= a.second)
                }
                for ((size, key) in keys) assertEquals(size, WidgetSizing.of(template, key.first, key.second, scale))
                // ...and is never smaller than what that layout needs at this font scale.
                for ((size, key) in keys) {
                    if (size == WidgetSize.COMPACT) continue
                    val need = WidgetSizing.need(template, size)
                    assertTrue("$template $size $scale", key.second >= need.heightDp(scale))
                    assertTrue("$template $size $scale", key.first >= need.minWidthDp)
                }
            }
        }
    }

    @Test
    fun belowApi31PortraitAndLandscapeAreChosenSeparately() {
        // A 4x1: 276 x 102 in portrait, 554 x 51 in landscape.
        assertEquals(
            WidgetSize.MEDIUM to WidgetSize.COMPACT,
            WidgetSizing.portraitAndLandscape(WidgetTemplate.STAT, 276f, 554f, 51f, 102f, 1f),
        )
        // A 4x3: 276 x 337 in portrait, 554 x 184 in landscape.
        assertEquals(
            WidgetSize.LARGE to WidgetSize.MEDIUM,
            WidgetSizing.portraitAndLandscape(WidgetTemplate.STAT, 276f, 554f, 184f, 337f, 1f),
        )
    }

    @Test
    fun everyWidgetHasATemplate() {
        assertEquals(WidgetTemplate.STAT, WidgetKind.LAST_SCORECARD.template)
        assertEquals(WidgetTemplate.STAT, WidgetKind.READINESS.template)
        assertEquals(WidgetTemplate.PROGRESS, WidgetKind.THIS_WEEK.template)
        assertEquals(WidgetTemplate.NEXT, WidgetKind.NEXT_PHYSIO.template)
        assertEquals(WidgetTemplate.CARD, WidgetKind.ACTIVE_RIG.template)
        assertEquals(WidgetTemplate.PADS, WidgetKind.QUICK_START.template)
    }

    // ---------------------------------------------------------------------------------------
    // Taps
    // ---------------------------------------------------------------------------------------

    @Test
    fun onlyKnownDestinationsAreAccepted() {
        assertEquals(WidgetDestination.SCORE, WidgetDestination.fromExtra("SCORE"))
        assertEquals(WidgetDestination.RIGS, WidgetDestination.fromExtra("RIGS"))
        assertNull(WidgetDestination.fromExtra("score"))
        assertNull(WidgetDestination.fromExtra("S_EXPORT"))
        assertNull(WidgetDestination.fromExtra(""))
        assertNull(WidgetDestination.fromExtra(null))
    }

    @Test
    fun requestCodesAreUniquePerWidgetAndDestination() {
        val codes =
            (1..50).flatMap { id -> WidgetDestination.entries.map { WidgetLaunch.requestCode(id, it) } }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun aTapNavigatesOnlyWhenNoOtherScreenIsOnTop() {
        assertEquals(WidgetLaunch.TapAction.OPEN, WidgetLaunch.tapAction(WidgetLaunch.TaskTop.NONE))
        assertEquals(WidgetLaunch.TapAction.OPEN, WidgetLaunch.tapAction(WidgetLaunch.TaskTop.MAIN))
        // A Fencing bout, a journal entry or the camera: come back as it was, never finish it.
        assertEquals(WidgetLaunch.TapAction.BRING_TO_FRONT, WidgetLaunch.tapAction(WidgetLaunch.TaskTop.OTHER))
    }

    @Test
    fun everyPadOpensItsOwnPlaceAndSmallCollapsesToOneTarget() {
        assertEquals(listOf(Pad.SCORE), QuickStart.pads(WidgetSize.COMPACT))
        assertEquals(listOf(Pad.SCORE), QuickStart.pads(WidgetSize.SMALL))
        assertEquals(listOf(Pad.SCORE, Pad.TRAIN, Pad.LOG), QuickStart.pads(WidgetSize.MEDIUM))
        assertEquals(6, QuickStart.pads(WidgetSize.LARGE).size)
        assertEquals(6, Pad.entries.map { it.destination }.toSet().size)
    }

    // ---------------------------------------------------------------------------------------
    // Readiness: a shape and a word, never the cascade's reasons
    // ---------------------------------------------------------------------------------------

    @Test
    fun everyReadinessLevelHasItsOwnShape() {
        assertEquals(ReadinessMark.READY, ReadinessMark.of(ReadinessLevel.READY))
        assertEquals(ReadinessMark.CAUTION, ReadinessMark.of(ReadinessLevel.CAUTION))
        assertEquals(ReadinessMark.REST_ADVISED, ReadinessMark.of(ReadinessLevel.REST_ADVISED))
        assertEquals(ReadinessMark.ON_PAUSE, ReadinessMark.of(ReadinessLevel.QUIET))
        val glyphs = ReadinessMark.entries.map { it.glyph }
        assertTrue(glyphs.all { it.isNotBlank() })
        assertEquals("shape alone must tell the levels apart", glyphs.size, glyphs.toSet().size)
    }

    @Test
    fun loadRatioAppearsOnlyAfterTheAppsOwnWarmUp() {
        val warming = ReadinessModel.of(ReadinessLevel.READY, null, 1.1, acwrWarmupComplete = false, computedAtMs = 0)
        assertNull(warming.acwr)
        val ready = ReadinessModel.of(ReadinessLevel.READY, null, 1.1, acwrWarmupComplete = true, computedAtMs = 0)
        assertEquals(1.1, ready.acwr!!, 1e-9)
        val undefined = ReadinessModel.of(ReadinessLevel.READY, null, Double.NaN, true, 0)
        assertNull(undefined.acwr)
    }

    @Test
    fun readinessModelHasNoFieldForReasonText() {
        // The cascade's reasons can name an injured region or stand in for a private life event.
        // The model keeps only the level, check-in measurements, the load ratio and a timestamp.
        val fields = ReadinessModel::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("$") }
        assertEquals(setOf("mark", "checkin", "acwr", "computedAtMs"), fields.filterNot { it == "Companion" }.toSet())
        assertTrue(CheckinFacts::class.java.declaredFields.none { it.type == String::class.java })
    }

    @Test
    fun checkinGoesStaleAtTheAppsThreshold() {
        assertEquals(36 * 3_600_000L + 1_000L, ReadinessTiming.staleAtMs(1_000L, 36))
        assertNull(ReadinessTiming.staleAtMs(null, 36))
    }

    // ---------------------------------------------------------------------------------------
    // Refresh boundaries
    // ---------------------------------------------------------------------------------------

    @Test
    fun nextRedrawIsJustAfterMidnightUnlessACheckinGoesStaleFirst() {
        val zone = ZoneId.of("Europe/London")
        val now = LocalDateTime.of(2026, 11, 4, 15, 30).atZone(zone)
        val midnight = LocalDateTime.of(2026, 11, 5, 0, 1).atZone(zone).toInstant().toEpochMilli()
        assertEquals(midnight, WidgetDates.nextRefreshAtMs(now, null))
        val soon = now.plusHours(2).toInstant().toEpochMilli()
        assertEquals(soon, WidgetDates.nextRefreshAtMs(now, soon))
        val past = now.minusHours(2).toInstant().toEpochMilli()
        assertEquals(midnight, WidgetDates.nextRefreshAtMs(now, past))
    }

    @Test
    fun midnightBoundaryHoldsAcrossADaylightSavingChange() {
        // Clocks go back at 02:00 on 25 Oct 2026 in London; the next local day still starts at 00:00.
        val zone = ZoneId.of("Europe/London")
        val now = LocalDateTime.of(2026, 10, 24, 23, 0).atZone(zone)
        val expected = LocalDateTime.of(2026, 10, 25, 0, 1).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, WidgetDates.nextRefreshAtMs(now, null))
    }

    // ---------------------------------------------------------------------------------------
    // Last scorecard
    // ---------------------------------------------------------------------------------------

    private fun card(
        id: String,
        status: String = "FINISHED",
        completed: Long? = 1_000,
        updated: Long = completed ?: 1_000,
        kind: String = "CUMULATIVE",
        total: Int = 612,
        complete: Boolean = true,
        sets: Pair<Int, Int>? = null,
    ) =
        ScorecardRow(
            id, status, "wa70", "WA 70 m", 70, kind, 6, 12, total, 14, complete, completed, updated,
            sets?.first, sets?.second, "rig-1",
        )

    @Test
    fun lastScorecardIsTheMostRecentlyFinishedCard() {
        val picked =
            LastScorecard.pick(
                listOf(
                    card("old", completed = 1_000),
                    card("newest", completed = 3_000),
                    card("open", status = "ACTIVE", completed = null, updated = 9_000),
                    card("middle", completed = 2_000, updated = 8_000),
                ),
            )
        assertEquals("newest", picked?.id)
        assertNull(LastScorecard.pick(listOf(card("open", status = "ACTIVE", completed = null))))
    }

    @Test
    fun scorecardShowsTotalAgainstMaximumAndArrowsShot() {
        val m = LastScorecard.model(card("a"), arrowsShot = 72, roundBest = 640, rigName = "Indoor", zone = utc)
        assertEquals(612, m.total)
        assertEquals(720, m.maxScore)
        assertEquals(72, m.arrowsPlanned)
        assertEquals(8.5, m.arrowAverage!!, 1e-9)
        assertEquals(640, m.roundBest)
        assertNull(m.setPoints)
    }

    @Test
    fun incompleteRoundHasNoRoundBestAndCountsArrowsHonestly() {
        val m = LastScorecard.model(card("a", total = 300, complete = false), 36, 640, null, utc)
        assertNull(m.roundBest)
        assertEquals(36, m.arrowsShot)
        assertFalse(m.complete)
    }

    @Test
    fun setMatchShowsTheSetScoreNotATotalOrAverage() {
        val m = LastScorecard.model(card("a", kind = "SET_MATCH", sets = 6 to 4), 15, 640, null, utc)
        assertEquals(6 to 4, m.setPoints)
        assertNull(m.maxScore)
        assertNull(m.arrowAverage)
        assertNull(m.roundBest)
    }

    // ---------------------------------------------------------------------------------------
    // This week
    // ---------------------------------------------------------------------------------------

    private fun plan(
        title: String,
        start: LocalDate?,
        end: LocalDate? = null,
        target: Int? = 300,
        state: String = "ACTIVE",
        updated: Long = 0,
    ) = PlanRow(title, state, start, end, target, updated)

    @Test
    fun activePlanIsTheLatestStartedActivePlanThatHasNotEnded() {
        val today = LocalDate.of(2026, 11, 4)
        val plans =
            listOf(
                plan("Old", LocalDate.of(2026, 9, 1)),
                plan("Current", LocalDate.of(2026, 10, 20)),
                plan("Future", LocalDate.of(2026, 12, 1)),
                plan("Ended", LocalDate.of(2026, 10, 25), end = LocalDate.of(2026, 11, 3)),
                plan("Paused", LocalDate.of(2026, 11, 1), state = "PAUSED"),
            )
        assertEquals("Current", WeekVolume.activePlan(plans, today)?.title)
        assertNull(WeekVolume.activePlan(listOf(plan("Archived", today, state = "ARCHIVED")), today))
    }

    @Test
    fun weekCountsOnlyThisWeeksArrowsInTheLocalesWeek() {
        val today = LocalDate.of(2026, 11, 4) // a Wednesday
        val points =
            listOf(
                TrainingVolume.Point(ms(LocalDate.of(2026, 11, 2)), 72), // Monday
                TrainingVolume.Point(ms(LocalDate.of(2026, 11, 4)), 36), // today
                TrainingVolume.Point(ms(LocalDate.of(2026, 11, 4), 18), 30), // today again
                TrainingVolume.Point(ms(LocalDate.of(2026, 11, 1)), 99), // Sunday before
                TrainingVolume.Point(ms(LocalDate.of(2026, 11, 9)), 99), // next Monday
            )
        val monday = WeekVolume.model(points, utc, today, DayOfWeek.MONDAY, plan("P", LocalDate.of(2026, 10, 1)))
        assertEquals(LocalDate.of(2026, 11, 2), monday.weekStart)
        assertEquals(listOf(72, 0, 66, 0, 0, 0, 0), monday.days)
        assertEquals(2, monday.todayIndex)
        assertEquals(138, monday.total)
        assertEquals(3, monday.sessions)
        assertEquals(300, monday.target)
        assertEquals(162, monday.remaining)
        assertNull(monday.over)
        assertEquals(0.46, monday.fraction!!, 1e-9)

        // A Sunday-first locale moves the boundary: the Sunday session now counts.
        val sunday = WeekVolume.model(points, utc, today, DayOfWeek.SUNDAY, null)
        assertEquals(LocalDate.of(2026, 11, 1), sunday.weekStart)
        assertEquals(237, sunday.total)
        assertNull(sunday.target)
        assertNull(sunday.fraction)
    }

    @Test
    fun weekBeyondTargetSaysByHowMuchAndCapsTheBar() {
        val today = LocalDate.of(2026, 11, 4)
        val m =
            WeekVolume.model(
                listOf(TrainingVolume.Point(ms(today), 350)),
                utc,
                today,
                DayOfWeek.MONDAY,
                plan("P", today, target = 300),
            )
        assertEquals(50, m.over)
        assertEquals(0, m.remaining)
        assertEquals(1.0, m.fraction!!, 1e-9)
    }

    @Test
    fun aZeroTargetIsNoTarget() {
        val today = LocalDate.of(2026, 11, 4)
        val m = WeekVolume.model(emptyList(), utc, today, DayOfWeek.MONDAY, plan("P", today, target = 0))
        assertNull(m.target)
        assertEquals(0, m.total)
        assertEquals(List(7) { 0 }, m.days)
    }

    // ---------------------------------------------------------------------------------------
    // Next physio day
    // ---------------------------------------------------------------------------------------

    private val wednesday = LocalDate.of(2026, 11, 4)

    private fun physio(
        schedule: Set<DayOfWeek>,
        start: LocalDate = wednesday.minusDays(30),
        end: LocalDate? = null,
        exercises: Int = 4,
        logged: Set<LocalDate> = emptySet(),
    ) = PhysioPlanRow(start, end, schedule, exercises, logged)

    @Test
    fun nextPhysioDayIsTodayUntilEveryPlanDueTodayIsLogged() {
        val plan = physio(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val open = NextPhysio.model(listOf(plan), wednesday)!!
        assertEquals(wednesday, open.next.date)
        assertFalse(open.todayLogged)

        val done = NextPhysio.model(listOf(plan.copy(loggedDates = setOf(wednesday))), wednesday)!!
        assertTrue(done.todayLogged)
        assertEquals(LocalDate.of(2026, 11, 6), done.next.date) // Friday
    }

    @Test
    fun aPartlyLoggedDayStaysNextWithOnlyTheOpenPlansCounted() {
        val a = physio(setOf(DayOfWeek.WEDNESDAY), exercises = 3, logged = setOf(wednesday))
        val b = physio(setOf(DayOfWeek.WEDNESDAY), exercises = 5)
        val m = NextPhysio.model(listOf(a, b), wednesday)!!
        assertEquals(wednesday, m.next.date)
        assertEquals(5, m.next.exercises)
        assertEquals(1, m.next.plans)
        assertFalse(m.todayLogged)
    }

    @Test
    fun plansThatHaveNotStartedOrHaveEndedAreNotScheduled() {
        val future = physio(setOf(DayOfWeek.WEDNESDAY), start = wednesday.plusDays(7))
        assertEquals(wednesday.plusDays(7), NextPhysio.model(listOf(future), wednesday)!!.next.date)
        val ending = physio(setOf(DayOfWeek.MONDAY), end = wednesday.plusDays(1))
        assertNull(NextPhysio.model(listOf(ending), wednesday))
        assertNull(NextPhysio.model(listOf(physio(emptySet())), wednesday))
        assertNull(NextPhysio.model(emptyList(), wednesday))
    }

    @Test
    fun upcomingListsThreeDaysWithTheirExerciseCounts() {
        val a = physio(setOf(DayOfWeek.THURSDAY, DayOfWeek.SATURDAY), exercises = 2)
        val b = physio(setOf(DayOfWeek.THURSDAY), exercises = 3)
        val m = NextPhysio.model(listOf(a, b), wednesday)!!
        assertEquals(
            listOf(
                PhysioDay(LocalDate.of(2026, 11, 5), 5, 2),
                PhysioDay(LocalDate.of(2026, 11, 7), 2, 1),
                PhysioDay(LocalDate.of(2026, 11, 12), 5, 2),
            ),
            m.upcoming,
        )
    }

    @Test
    fun adherenceCountsScheduledDaysAttendedOverTwentyEightDays() {
        // Mondays in the 28 days to Wed 4 Nov: 12, 19, 26 Oct and 2 Nov. Two were attended; a
        // session on an unscheduled Tuesday must not count.
        val plan =
            physio(
                setOf(DayOfWeek.MONDAY),
                logged =
                    setOf(
                        LocalDate.of(2026, 10, 12),
                        LocalDate.of(2026, 10, 26),
                        LocalDate.of(2026, 10, 27),
                    ),
            )
        val w = NextPhysio.model(listOf(plan), wednesday)!!.adherence!!
        assertEquals(4, w.expected)
        assertEquals(2, w.completed)
    }

    @Test
    fun physioRowCarriesNoTitleRegionOrNote() {
        assertTrue(PhysioPlanRow::class.java.declaredFields.none { it.type == String::class.java })
    }

    // ---------------------------------------------------------------------------------------
    // Active rig
    // ---------------------------------------------------------------------------------------

    @Test
    fun rigShowsOnlyWhatWasRecordedInAFixedOrder() {
        val tuning =
            RigTuning(
                otfLbs = 36.0,
                braceHeightMm = 220.0,
                tillerTopMm = 180.0,
                arrow = ArrowDto(spine = 600, lengthMm = 750.0, balancePointMm = 465.0, totalMassGrains = 360.0),
            )
        val m = RigCard.model("Indoor", BowType.RECURVE, tuning, drawLengthMm = 700)
        assertEquals(36.0, m.poundageLbs!!, 1e-9)
        assertEquals(PoundageSource.MEASURED, m.poundageSource)
        // Tiller needs both limbs, so a lone top measurement is not shown.
        assertEquals(
            listOf(
                RigFactKey.BRACE_HEIGHT,
                RigFactKey.ARROW_SPINE,
                RigFactKey.ARROW_LENGTH,
                RigFactKey.ARROW_MASS,
                RigFactKey.FOC,
                RigFactKey.GPP,
            ),
            m.facts.map { it.key },
        )
        assertEquals(10.0, m.facts.first { it.key == RigFactKey.GPP }.values.single(), 1e-9)
    }

    @Test
    fun anUntunedRigHasNoFactsAndNoInventedPoundage() {
        val m = RigCard.model("Club bow", BowType.BAREBOW, RigTuning(), drawLengthMm = null)
        assertTrue(m.facts.isEmpty())
        assertNull(m.poundageLbs)
        assertNull(m.poundageSource)
    }

    // ---------------------------------------------------------------------------------------
    // Push refresh and privacy classes
    // ---------------------------------------------------------------------------------------

    @Test
    fun aWriteRedrawsOnlyTheWidgetsThatReadIt() {
        assertEquals(
            setOf(WidgetKind.LAST_SCORECARD, WidgetKind.THIS_WEEK, WidgetKind.QUICK_START),
            WidgetTables.kindsFor(setOf("score_arrow")),
        )
        assertEquals(setOf(WidgetKind.READINESS), WidgetTables.kindsFor(setOf("checkin")))
        assertEquals(setOf(WidgetKind.NEXT_PHYSIO), WidgetTables.kindsFor(setOf("physio_session")))
        assertEquals(setOf(WidgetKind.ACTIVE_RIG, WidgetKind.LAST_SCORECARD), WidgetTables.kindsFor(setOf("rig")))
        assertEquals(WidgetKind.entries.toSet(), WidgetTables.kindsFor(setOf("athletes")))
        assertTrue(WidgetTables.kindsFor(setOf("mood_entry", "cycle_entry", "document")).isEmpty())
        assertEquals(WidgetKind.entries.toSet(), WidgetTables.byKind.keys)
    }

    @Test
    fun noWidgetReadsMedicalTablesAndOnlyReadinessTouchesAPrivateOne() {
        assertTrue(WidgetTables.all.intersect(PrivacyRegistry.medicalTables()).isEmpty())
        // life_event feeds the app's own readiness cascade ("high-stress window"); the widget reads
        // the resulting level only, never the event.
        assertEquals(setOf("life_event"), WidgetTables.all.intersect(PrivacyRegistry.privateTables()))
        val readers = WidgetTables.byKind.filterValues { "life_event" in it }.keys
        assertEquals(setOf(WidgetKind.READINESS), readers)
    }
}
