package xyz.mdhv.formanalyser.model.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockGeometryTest {
    private fun assertNear(expected: Float, actual: Float, tolerance: Float = 0.01f, what: String = "value") {
        assertTrue(abs(expected - actual) <= tolerance, "$what: expected $expected but was $actual")
    }

    @Test
    fun phonePortraitKeepsNaturalArc() {
        val layout = DockGeometry.resolve(392f, 915f, 1f)
        assertEquals(DockMode.ARC, layout.mode)
        assertNear(392f, layout.stageWidthDp, what = "width")
        assertNear(147f, layout.stageHeightDp, tolerance = 0.5f, what = "height")
        assertFalse(layout.compactLabels)
    }

    @Test
    fun phoneLandscapeFallsBackToRail() {
        val layout = DockGeometry.resolve(895f, 412f, 1f)
        assertEquals(DockMode.RAIL, layout.mode)
        assertNear(56f, layout.stageHeightDp, what = "height")
    }

    @Test
    fun foldableInnerScreenCapsWidth() {
        val layout = DockGeometry.resolve(820f, 900f, 1f)
        assertEquals(DockMode.ARC, layout.mode)
        assertNear(440f, layout.stageWidthDp, what = "width")
        assertNear(165f, layout.stageHeightDp, what = "height")
    }

    @Test
    fun tabletLandscapeCapsWidth() {
        val layout = DockGeometry.resolve(1260f, 800f, 1f)
        assertEquals(DockMode.ARC, layout.mode)
        assertNear(440f, layout.stageWidthDp, what = "width")
        assertTrue(layout.stageHeightDp <= 0.30f * 800f, "height ${layout.stageHeightDp} exceeds 30% of the window")
    }

    @Test
    fun desktopWindowNeverExceedsCap() {
        val layout = DockGeometry.resolve(1900f, 1000f, 1f)
        assertEquals(DockMode.ARC, layout.mode)
        assertNear(440f, layout.stageWidthDp, what = "width")
        assertNear(165f, layout.stageHeightDp, what = "height")
    }

    @Test
    fun shortMediumWindowIsHeightLimited() {
        val layout = DockGeometry.resolve(800f, 500f, 1f)
        assertEquals(DockMode.ARC, layout.mode)
        assertNear(150f, layout.stageHeightDp, what = "height (30% of 500)")
        assertNear(400f, layout.stageWidthDp, what = "width")
    }

    @Test
    fun largeFontForcesCompactLabels() {
        assertTrue(DockGeometry.resolve(392f, 915f, 1.3f).compactLabels)
        assertTrue(DockGeometry.resolve(392f, 915f, 1.2f).compactLabels)
        assertFalse(DockGeometry.resolve(392f, 915f, 1.19f).compactLabels)
    }

    @Test
    fun narrowPhoneCompactsLabels() {
        val layout = DockGeometry.resolve(300f, 640f, 1f)
        assertTrue(layout.compactLabels)
        assertEquals(DockMode.ARC, layout.mode)
        assertFalse(DockGeometry.resolve(320f, 640f, 1f).compactLabels)
    }

    @Test
    fun breakpointBoundaryIsInclusive() {
        assertEquals(DockMode.ARC, DockGeometry.resolve(900f, 480f, 1f).mode)
        assertEquals(DockMode.RAIL, DockGeometry.resolve(900f, 479.9f, 1f).mode)
    }

    @Test
    fun scaleIsStageWidthOverDesignWidth() {
        assertNear(1f, DockGeometry.resolve(400f, 915f, 1f).scale, what = "scale at design width")
        assertNear(1.1f, DockGeometry.resolve(1900f, 1000f, 1f).scale, what = "scale at the cap")
    }

    @Test
    fun arcAspectRatioIsAlwaysTheDesignRatio() {
        for (width in listOf(280f, 360f, 392f, 440f, 700f, 1900f)) {
            for (height in listOf(480f, 500f, 640f, 915f, 1400f)) {
                val layout = DockGeometry.resolve(width, height, 1f)
                assertEquals(DockMode.ARC, layout.mode)
                assertNear(0.375f, layout.stageHeightDp / layout.stageWidthDp, 0.0001f, "aspect at ${width}x$height")
                assertTrue(layout.stageWidthDp <= width + 0.001f, "stage wider than the space at ${width}x$height")
                assertTrue(layout.stageHeightDp <= 0.30f * height + 0.001f, "stage taller than 30% at ${width}x$height")
            }
        }
    }

    @Test
    fun negativeAndNanInputsAreTreatedAsZero() {
        val negative = DockGeometry.resolve(-5f, -1f, -2f)
        assertEquals(DockMode.RAIL, negative.mode)
        assertNear(0f, negative.stageWidthDp, what = "width")
        val nan = DockGeometry.resolve(Float.NaN, Float.NaN, Float.NaN)
        assertEquals(DockMode.RAIL, nan.mode)
        assertNear(0f, nan.stageWidthDp, what = "width")
        val zeroWidth = DockGeometry.resolve(-10f, 900f, 1f)
        assertEquals(DockMode.ARC, zeroWidth.mode)
        assertNear(0f, zeroWidth.stageWidthDp, what = "width")
        assertNear(0f, zeroWidth.stageHeightDp, what = "height")
    }
}
