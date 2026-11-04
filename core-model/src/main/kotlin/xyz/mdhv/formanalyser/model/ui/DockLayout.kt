package xyz.mdhv.formanalyser.model.ui

/** How the primary navigation dock is drawn: the curved two-level stage or a flat bottom rail. */
enum class DockMode { ARC, RAIL }

/**
 * Resolved size of the navigation dock. [stageWidthDp] x [stageHeightDp] is the box the dock
 * occupies; in [DockMode.ARC] its drawing is laid out on a 400 x 150 design stage scaled by [scale].
 */
data class DockLayout(
    val mode: DockMode,
    val stageWidthDp: Float,
    val stageHeightDp: Float,
    val compactLabels: Boolean,
) {
    val scale: Float get() = stageWidthDp / DockGeometry.DESIGN_W
}

/**
 * Pure sizing rules for the navigation dock, kept free of Android types so they run on the JVM.
 *
 * The arc is designed on a 400 x 150 stage whose tabs are 68 dp wide on seats 76.8 stage units
 * apart, so scaling it below about 354 dp makes neighbouring tabs overlap. A "compact arc" for a
 * short window is therefore not viable, and windows shorter than [MIN_WINDOW_HEIGHT_FOR_ARC] (a
 * phone in landscape) get a flat [RAIL_HEIGHT] rail instead. Taller windows keep the arc, but
 * capped at [MAX_STAGE_W] wide (165 dp tall) and at [MAX_HEIGHT_FRACTION] of the window height, so
 * a tablet or desktop window never produces a dock several hundred dp tall.
 */
object DockGeometry {
    const val DESIGN_W = 400f
    const val DESIGN_H = 150f
    const val MAX_STAGE_W = 440f
    const val MAX_HEIGHT_FRACTION = 0.30f

    /** Material "medium" window-height breakpoint: below it the dock becomes a rail. */
    const val MIN_WINDOW_HEIGHT_FOR_ARC = 480f
    const val RAIL_HEIGHT = 56f

    /** Stage widths under this, or system font scales from [LARGE_FONT_SCALE], abbreviate labels. */
    const val COMPACT_STAGE_W = 320f
    const val LARGE_FONT_SCALE = 1.2f

    private const val ASPECT = DESIGN_H / DESIGN_W

    /**
     * @param availableWidthDp width the dock may use, after any outer padding
     * @param windowHeightDp height of the app window, not of the physical display
     * @param fontScale system font scale
     *
     * Negative and NaN inputs are treated as 0.
     */
    fun resolve(availableWidthDp: Float, windowHeightDp: Float, fontScale: Float): DockLayout {
        val width = nonNegative(availableWidthDp)
        val height = nonNegative(windowHeightDp)
        val font = nonNegative(fontScale)
        if (height < MIN_WINDOW_HEIGHT_FOR_ARC) {
            return DockLayout(DockMode.RAIL, width, RAIL_HEIGHT, compactLabels(width, font))
        }
        val stageWidth = minOf(width, MAX_STAGE_W, height * MAX_HEIGHT_FRACTION / ASPECT)
        return DockLayout(DockMode.ARC, stageWidth, stageWidth * ASPECT, compactLabels(stageWidth, font))
    }

    private fun compactLabels(stageWidthDp: Float, fontScale: Float): Boolean =
        stageWidthDp < COMPACT_STAGE_W || fontScale >= LARGE_FONT_SCALE

    private fun nonNegative(value: Float): Float = if (value.isNaN()) 0f else value.coerceAtLeast(0f)
}
