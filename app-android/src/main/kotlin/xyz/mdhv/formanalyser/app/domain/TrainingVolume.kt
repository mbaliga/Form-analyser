package xyz.mdhv.formanalyser.app.domain

/**
 * Arrows shot per training session, counted the way Progress counts them.
 *
 * Extracted from `ProgressViewModel` so the home-screen "This week" widget and the Progress screen
 * share one rule instead of two copies that could drift apart: the same athlete must never see one
 * arrow count on the home screen and another inside the app.
 *
 * The rule: every capture session is one point at its start time. A finished scorecard the athlete
 * attached to a capture session that still exists is the same afternoon on the shooting line, so it
 * folds into that capture's point, and the larger of the two counts wins (the capture may have
 * stopped early, or only some ends were scored; taking the larger keeps a link from removing arrows
 * the athlete shot). Every other finished scorecard is its own point at its completion time.
 */
object TrainingVolume {
    /** A capture session; [arrows] is its reconciled count, or its recorded shots when none was. */
    data class Capture(val id: String, val atMs: Long, val arrows: Int)

    /** A finished scorecard; [linkedCaptureId] is the capture session it was attached to, if any. */
    data class Card(val atMs: Long, val linkedCaptureId: String?, val arrows: Int)

    data class Point(val atMs: Long, val arrows: Int)

    /** True when [card] folds into one of [captureIds] rather than standing on its own. */
    fun isAbsorbed(card: Card, captureIds: Set<String>): Boolean =
        card.linkedCaptureId != null && card.linkedCaptureId in captureIds

    /** Capture points first (in [captures] order), then unlinked scorecards (in [cards] order). */
    fun points(captures: List<Capture>, cards: List<Card>): List<Point> {
        val captureIds = captures.mapTo(HashSet()) { it.id }
        val linked =
            cards
                .filter { isAbsorbed(it, captureIds) }
                .groupBy({ requireNotNull(it.linkedCaptureId) }, { it.arrows })
                .mapValues { (_, arrows) -> arrows.sum() }
        return captures.map { Point(it.atMs, maxOf(it.arrows, linked[it.id] ?: 0)) } +
            cards.filterNot { isAbsorbed(it, captureIds) }.map { Point(it.atMs, it.arrows) }
    }
}
