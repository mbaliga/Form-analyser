package xyz.mdhv.formanalyser.app

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real [ArcNavigationDock] at phone, landscape-phone and desktop-window sizes. The
 * window size comes from the Robolectric qualifiers, which is what `LocalConfiguration` reports.
 * The sizing rules themselves are pinned by `DockGeometryTest` in core-model; these tests check
 * that the composable applies them (48 dp targets, rail fallback, capped stage, scrubbing).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ArcNavigationDockTest {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    // createAndroidComposeRule() launches ComponentActivity before any @Before runs, and only the
    // debug manifest (ui-test-manifest) declares it, so register it for every variant first.
    private val registerHostActivity = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val application = ApplicationProvider.getApplicationContext<Application>()
                shadowOf(application.packageManager).addActivityIfNotPresent(
                    ComponentName(application.packageName, ComponentActivity::class.java.name),
                )
                base.evaluate()
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(registerHostActivity).around(composeRule)

    private val items = listOf(
        "home" to (Icons.Filled.Home to "Home"),
        "train" to (Icons.Filled.CameraAlt to "Train"),
        "progress" to (Icons.Filled.Timeline to "Progress"),
        "body" to (Icons.Filled.Accessibility to "Body"),
        "calendar" to (Icons.Filled.CalendarMonth to "Calendar"),
    )
    private val selections = mutableListOf<String>()

    private fun showDock(current: String? = "home") {
        composeRule.setContent {
            ArcNavigationDock(
                items = items,
                current = current,
                badge = false,
                onSelect = { selections += it },
                onQuickAdd = {},
                onSettings = {},
            )
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-port")
    fun tabsMeet48dpTouchTarget() {
        showDock()
        val tags = items.map { "nav-tab-${it.first}" } + listOf("dock-action-quick-add", "dock-action-settings")
        tags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    @Config(qualifiers = "w895dp-h412dp-land")
    fun landscapePhoneShowsRail() {
        showDock()
        composeRule.onNodeWithTag("nav-rail").assertExists()
        composeRule.onNodeWithTag("nav-context-recurve").assertDoesNotExist()
        composeRule.onNodeWithTag("nav-tab-calendar").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("calendar"), selections)
    }

    @Test
    @Config(qualifiers = "w1600dp-h1000dp")
    fun wideWindowCapsStage() {
        showDock()
        val stage = composeRule.onNodeWithTag("nav-context-recurve").getUnclippedBoundsInRoot()
        assertTrue("stage is ${stage.height} tall, expected at most 166.dp", stage.height <= 166.dp)
        assertTrue("stage is ${stage.width} wide, expected at most 441.dp", stage.width <= 441.dp)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-port")
    fun arcStageIsActuallyDrawnIncludingItsTextOnPath() {
        showDock()
        composeRule.waitForIdle()
        // Draw the window into a bitmap: this runs the Canvas (paths, gradients, drawTextOnPath)
        // through Robolectric's native graphics, which composeRule's own assertions never do.
        val content = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        content.draw(Canvas(bitmap))
        var darkPath = 0
        var greenLabel = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val argb = bitmap.getPixel(x, y)
            val r = argb shr 16 and 0xff
            val g = argb shr 8 and 0xff
            val b = argb and 0xff
            if (argb ushr 24 == 0xff && r < 0x40 && g < 0x40 && b < 0x40) darkPath++
            if (g > 120 && g > r + 20 && g > b + 40) greenLabel++
        }
        assertTrue("no dock path pixels were drawn (dark=$darkPath)", darkPath > 500)
        assertTrue("the drawTextOnPath label drew no pixels (green=$greenLabel)", greenLabel > 0)
    }

    @Test
    @Config(qualifiers = "w1600dp-h1000dp")
    fun scrubOnAWideWindowStillMapsTheFingerToTheSeatBeneathIt() {
        showDock()
        // The stage is 440 dp wide in a 1580 dp slot: the gesture must be measured against the stage.
        composeRule.onNodeWithTag("nav-context-recurve").performTouchInput {
            fun at(stageX: Float, stageY: Float) = Offset(width * stageX / 400f, height * stageY / 150f)
            down(at(46.8f, 107.4f))
            moveTo(at(200f, 100f))
            moveTo(at(353.2f, 107.4f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(listOf("calendar"), selections)
    }
}
