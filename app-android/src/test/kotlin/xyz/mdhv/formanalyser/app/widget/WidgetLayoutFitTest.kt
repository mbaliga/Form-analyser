package xyz.mdhv.formanalyser.app.widget

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xyz.mdhv.formanalyser.app.domain.PhysioAdherence
import xyz.mdhv.formanalyser.equipment.PoundageSource
import xyz.mdhv.formanalyser.model.BowType
import xyz.mdhv.formanalyser.wellness.ReadinessLevel

/**
 * Every widget layout, filled with long real-looking content, is measured with real font metrics
 * (native graphics) at 1x, 1.3x and 2x font scale, at the narrowest width it is used at. Its height
 * must not exceed what [WidgetSizing.need] says it needs: those needs are the keys a launcher uses to
 * pick a layout, so a layout taller than its need would be clipped on a widget of exactly that size.
 *
 * SDK 31 scales text linearly; SDK 35 scales large text less (non-linear font scaling). The density
 * is a phone's (xxhdpi), so per-line rounding is what a device sees.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31, 35], qualifiers = "xxhdpi")
class WidgetLayoutFitTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 4, 9, 30, 0, 0, ZoneId.systemDefault())
    private val today: LocalDate = now.toLocalDate()

    private fun samples(): List<Pair<WidgetKind, WidgetContent>> {
        val nowMs = now.toInstant().toEpochMilli()
        return listOf(
            WidgetKind.LAST_SCORECARD to
                WidgetContent.LastScorecard(
                    LastScorecardModel(
                        "WA 1440 (90 m) Gents Recurve", 90, 1287, 1440, 140, 144, 33, false, null, today,
                        9.19, null, "Outdoor competition recurve",
                    ),
                ),
            WidgetKind.LAST_SCORECARD to
                WidgetContent.LastScorecard(
                    LastScorecardModel(
                        "Olympic round set match", 70, 0, null, 15, 15, 0, true, 6 to 4, today, null, null, "Rig",
                    ),
                ),
            WidgetKind.THIS_WEEK to
                WidgetContent.ThisWeek(
                    WeekModel(today.minusDays(2), listOf(144, 0, 216, 0, 0, 0, 0), 2, 1360, 12, 1500, "Indoor build, phase two"),
                ),
            WidgetKind.THIS_WEEK to
                WidgetContent.ThisWeek(WeekModel(today, List(7) { 0 }, 0, 0, 0, null, null)),
            WidgetKind.NEXT_PHYSIO to
                WidgetContent.NextPhysio(
                    NextPhysioModel(
                        PhysioDay(today.plusDays(3), 12, 3),
                        todayLogged = true,
                        upcoming =
                            listOf(PhysioDay(today.plusDays(3), 12, 3), PhysioDay(today.plusDays(5), 4, 1), PhysioDay(today.plusDays(7), 12, 3)),
                        adherence = PhysioAdherence.Window(28, 21),
                    ),
                ),
            WidgetKind.READINESS to
                WidgetContent.Readiness(
                    ReadinessModel.of(ReadinessLevel.REST_ADVISED, CheckinFacts(nowMs - 3_600_000, 2, 3, 4), 1.43, true, nowMs),
                ),
            WidgetKind.READINESS to
                WidgetContent.Readiness(ReadinessModel.of(ReadinessLevel.READY, null, null, false, nowMs)),
            WidgetKind.ACTIVE_RIG to
                WidgetContent.ActiveRig(
                    RigModel(
                        "Indoor competition recurve, 2026 season",
                        BowType.RECURVE,
                        36.5,
                        PoundageSource.ESTIMATED,
                        listOf(
                            RigFact(RigFactKey.BRACE_HEIGHT, listOf(222.0)),
                            RigFact(RigFactKey.TILLER, listOf(181.0, 177.0)),
                            RigFact(RigFactKey.ARROW_SPINE, listOf(700.0)),
                            RigFact(RigFactKey.ARROW_LENGTH, listOf(724.0)),
                            RigFact(RigFactKey.POINT_MASS, listOf(100.0)),
                            RigFact(RigFactKey.ARROW_MASS, listOf(312.0)),
                            RigFact(RigFactKey.FOC, listOf(13.4)),
                            RigFact(RigFactKey.GPP, listOf(8.6)),
                        ),
                    ),
                ),
            WidgetKind.QUICK_START to WidgetContent.QuickStart(OpenScorecard("WA 1440 (90 m) Gents Recurve", 36, 144)),
            WidgetKind.QUICK_START to WidgetContent.QuickStart(null),
            WidgetKind.READINESS to WidgetContent.FirstRun,
            WidgetKind.NEXT_PHYSIO to WidgetContent.NeedsApp,
            WidgetKind.LAST_SCORECARD to WidgetContent.Error,
            WidgetKind.NEXT_PHYSIO to WidgetContent.Empty(WidgetKind.NEXT_PHYSIO),
        )
    }

    private fun scaled(fontScale: Float): Context =
        app.createConfigurationContext(Configuration(app.resources.configuration).apply { this.fontScale = fontScale })

    private fun heightDp(context: Context, root: View, widthDp: Float): Float {
        val density = context.resources.displayMetrics.density
        root.measure(
            View.MeasureSpec.makeMeasureSpec((widthDp * density).roundToInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return root.measuredHeight / density
    }

    @Test
    fun everyLayoutFitsTheHeightItIsChosenFor() {
        val failures = mutableListOf<String>()
        val report = StringBuilder()
        for (scale in listOf(1f, 1.3f, 2f)) {
            val context = scaled(scale)
            for ((kind, content) in samples()) {
                val template = WidgetRenderer.templateOf(kind, content)
                for (size in WidgetSize.entries) {
                    val need = WidgetSizing.need(template, size)
                    // The narrowest width this layout is drawn at: its own minimum, or the
                    // providers' minResizeWidth for the layouts any width may get.
                    val width = if (need.minWidthDp > 1f) need.minWidthDp else 110f
                    val views = WidgetRenderer.build(context, 7, kind, content, size, now)
                    val root = views.apply(context, FrameLayout(context))
                    val measured = heightDp(context, root, width)
                    val allowed = need.heightDp(scale)
                    val line = "$kind/${content::class.simpleName} $size @${scale}x ${width}dp wide: " +
                        "measured %.1f dp, need %.1f dp".format(measured, allowed)
                    report.appendLine(line)
                    if (measured > allowed + 0.5f) failures += line
                }
            }
        }
        println(report)
        assertTrue("Layouts taller than their declared need:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
