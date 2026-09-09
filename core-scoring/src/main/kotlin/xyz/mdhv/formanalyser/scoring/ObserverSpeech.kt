package xyz.mdhv.formanalyser.scoring

/**
 * The 3×3 target sector an observer may declare alongside a ring.
 *
 * Persisted by [name] into `observer_score_event.sector`, the same way [ScoreSource] and
 * [ObservationResolution] are persisted — the column has existed since the Live Observer schema
 * landed and has never had a writer.
 *
 * Deliberately nine coarse cells rather than twelve clock positions: the spoken grammar the
 * blueprint fixes is "8 bottom left" / "9 top middle", and the on-screen sector picker has to write
 * the *same* vocabulary into the *same* column, or nothing downstream can read it. A clock face
 * would also mean twelve sub-48 dp targets on a glove-thumb layout.
 *
 * A sector is a description of where the arrow landed, never a coordinate: it must never be turned
 * into a [PlotPoint]. Nine boxes cannot be promoted to a point, and inventing one would be exactly
 * the fabricated precision the join rules forbid.
 */
enum class ObserverSector(val label: String) {
    TOP_LEFT("top left"),
    TOP_CENTER("top middle"),
    TOP_RIGHT("top right"),
    MID_LEFT("middle left"),
    CENTER("middle"),
    MID_RIGHT("middle right"),
    BOTTOM_LEFT("bottom left"),
    BOTTOM_CENTER("bottom middle"),
    BOTTOM_RIGHT("bottom right");

    companion object {
        /** Row/column lookup used by both the phrase parser and the on-screen 3×3 picker. */
        fun of(row: Row, column: Column): ObserverSector =
            when (row) {
                Row.TOP ->
                    when (column) {
                        Column.LEFT -> TOP_LEFT
                        Column.CENTER -> TOP_CENTER
                        Column.RIGHT -> TOP_RIGHT
                    }
                Row.MIDDLE ->
                    when (column) {
                        Column.LEFT -> MID_LEFT
                        Column.CENTER -> CENTER
                        Column.RIGHT -> MID_RIGHT
                    }
                Row.BOTTOM ->
                    when (column) {
                        Column.LEFT -> BOTTOM_LEFT
                        Column.CENTER -> BOTTOM_CENTER
                        Column.RIGHT -> BOTTOM_RIGHT
                    }
            }
    }

    enum class Row {
        TOP,
        MIDDLE,
        BOTTOM,
    }

    enum class Column {
        LEFT,
        CENTER,
        RIGHT,
    }
}

/**
 * Turns what a speech recogniser *heard* into a keypad token, or refuses.
 *
 * Lives in the pure core for the same reason [ScoreInput] does — every surface has to agree on the
 * vocabulary — and because this is the one piece of the voice path that can be tested without a
 * device. The Android layer owns the microphone and the recogniser; it owns no scoring rules.
 *
 * The governing rule is **reject over guess**. A misheard word must cost the athlete one repetition,
 * never a wrong arrow on an authoritative scorecard, so:
 * - every word of the utterance must be in the vocabulary below — an unknown word rejects the whole
 *   phrase rather than being skipped as filler;
 * - nothing is matched by proximity: there is no edit distance, no "nearest number", no phonetic
 *   fallback. `"fine"` is not a nine;
 * - homophones of score words that are common English function words (`for`, `to`/`too`, `won`,
 *   `ate`) are deliberately absent. They are the largest false-accept surface on a noisy shooting
 *   line, and a rejection there costs one repeat while an acceptance costs a corrupted end;
 * - two score words in one phrase ("nine or ten") reject; there is no first-wins rule.
 *
 * That strictness has a second effect worth stating: because only vocabulary words can survive
 * parsing, an accepted transcript is *structurally incapable* of containing anything the athlete
 * said other than a score and a sector. That is what makes it safe to persist the transcript
 * verbatim as `observer_score_event.declaredText`.
 *
 * Accepting a phrase is not the same as scoring it. This object decides only what was said; whether
 * a spoken score may become an authoritative arrow is a separate, human decision (see the
 * propose/confirm/reject flow in the repository).
 */
object ObserverSpeech {

    /**
     * Below this, a hypothesis the recogniser itself doubts is refused rather than shown.
     *
     * Not device-validated — no range recording exists to calibrate it against, and it cannot be
     * calibrated in a headless build. It is a conservative backstop behind the grammar gate and the
     * n-best agreement rule, both of which do the real work. A recogniser that reports no
     * confidence at all (many on-device engines report `0f` for every hypothesis) is treated as
     * having said nothing about confidence rather than as having reported no confidence — see
     * [Hypothesis.reportedConfidence].
     */
    const val MIN_CONFIDENCE: Float = 0.4f

    /** One entry of a recogniser's n-best list. */
    data class Hypothesis(val text: String, val confidence: Float? = null) {
        /**
         * The confidence to actually gate on, or null when the recogniser did not report one.
         *
         * `0f` means "not reported" on most engines rather than "certainly wrong"; treating it
         * literally would reject every utterance on those devices and make voice look broken.
         */
        val reportedConfidence: Float?
            get() = confidence?.takeIf { it > 0f }
    }

    /** Why an utterance was refused. Rendered to the athlete verbatim — never swallowed. */
    enum class RejectReason(val message: String) {
        NOTHING_HEARD("Nothing was heard."),
        NO_SCORE_WORD("No score in that. Say a ring, X or miss."),
        TWO_SCORE_WORDS("More than one score in that. Say one arrow at a time."),
        UNKNOWN_WORD("Some of that is not a scoring word."),
        BAD_SECTOR("That sector is not one of the nine boxes."),
        DISAGREEMENT("Heard more than one possible score. Say it again, or tap it."),
        LOW_CONFIDENCE("Not heard clearly enough to be sure."),
    }

    sealed interface Result {
        /**
         * A phrase understood without ambiguity.
         *
         * [token] is a [ScoreInput] keypad token, so it feeds the existing observer path verbatim —
         * this introduces no second scoring vocabulary.
         *
         * [heard] is the winning hypothesis exactly as the recogniser returned it, so the
         * acknowledgement can show the athlete what the phone actually understood rather than a
         * tidied-up version of it.
         *
         * [sectorDropped] means the observer named only half a sector ("eight left") or the n-best
         * hypotheses agreed on the score but not on the sector. The score stands, the sector is
         * discarded, and the UI must say so: half a sector is never rounded up into a whole one.
         */
        data class Accepted(
            val token: String,
            val sector: ObserverSector? = null,
            val heard: String,
            val sectorDropped: Boolean = false,
        ) : Result

        /** [heard] is the top hypothesis, kept so the athlete can be shown what went wrong. */
        data class Rejected(val heard: String, val reason: RejectReason) : Result
    }

    /** Phrases the on-screen hint offers, sourced here so hint and grammar cannot drift apart. */
    val examples: List<String> = listOf("nine", "ten", "X", "miss", "eight bottom left")

    /**
     * Resolve a recogniser's whole n-best list into one decision.
     *
     * Agreement, not rank, is what authorises a result. Recognisers routinely return several
     * spellings of one utterance ("9", "nine", "nine.") — those agree and are fine. When two
     * hypotheses parse to *different* scores the recogniser is genuinely unsure between two
     * numbers, and taking the top-ranked one would be the guess this whole path exists to avoid.
     *
     * Sector disagreement is weaker: the score is still unanimous, so the arrow survives with its
     * sector dropped rather than costing the athlete a repetition.
     */
    fun resolve(hypotheses: List<Hypothesis>): Result {
        val usable = hypotheses.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return Result.Rejected("", RejectReason.NOTHING_HEARD)
        val parsed = usable.map { it to parse(it.text) }
        val accepted = parsed.filter { it.second is Result.Accepted }
        // Nothing parsed: report the top hypothesis's own reason, which is the one that describes
        // what the athlete actually said rather than what some lower-ranked alternative said.
        if (accepted.isEmpty()) return parsed.first().second
        val top = accepted.first()
        val topConfidence = top.first.reportedConfidence
        if (topConfidence != null && topConfidence < MIN_CONFIDENCE)
            return Result.Rejected(top.first.text, RejectReason.LOW_CONFIDENCE)
        val results = accepted.map { it.second as Result.Accepted }
        if (results.map { it.token }.distinct().size > 1)
            return Result.Rejected(top.first.text, RejectReason.DISAGREEMENT)
        val sectors = results.map { it.sector }.distinct()
        val winner = results.first()
        return if (sectors.size > 1) winner.copy(sector = null, sectorDropped = true) else winner
    }

    /**
     * Parse one transcript. Public because the recogniser is not the only possible source (a
     * keyboard fallback or a test fixture is), and because [resolve] is only this plus agreement.
     */
    fun parse(transcript: String): Result {
        val heard = transcript.trim()
        val words = normalise(heard)
        if (words.isEmpty()) return Result.Rejected(heard, RejectReason.NOTHING_HEARD)

        val tokens = words.mapNotNull(SCORE_WORDS::get)
        val token =
            when {
                tokens.isEmpty() -> return Result.Rejected(heard, RejectReason.NO_SCORE_WORD)
                tokens.size == 1 -> tokens.single()
                // "ten X" is one arrow named twice over, not two arrows — an X already scores ten,
                // so the two words cannot contradict each other. Every other repetition ("nine
                // nine") is refused: under a one-arrow-at-a-time grammar that is far more likely to
                // be two arrows called in one breath than one arrow said twice.
                tokens.toSet() == setOf("X", "10") -> "X"
                else -> return Result.Rejected(heard, RejectReason.TWO_SCORE_WORDS)
            }
        val rest = words.filter { it !in SCORE_WORDS }
        if (rest.any { it !in SECTOR_WORDS })
            return Result.Rejected(heard, RejectReason.UNKNOWN_WORD)

        return when (val sector = sector(rest)) {
            is SectorResult.None -> Result.Accepted(token, null, heard)
            is SectorResult.Cell -> Result.Accepted(token, sector.value, heard)
            // Half a sector is information the observer did not give. The ring is unaffected by it,
            // so the arrow is kept and the missing half is reported, never filled in.
            is SectorResult.Partial -> Result.Accepted(token, null, heard, sectorDropped = true)
            is SectorResult.Invalid -> Result.Rejected(heard, RejectReason.BAD_SECTOR)
        }
    }

    private sealed interface SectorResult {
        data object None : SectorResult

        data class Cell(val value: ObserverSector) : SectorResult

        /** One axis named, the other not — "eight left". */
        data object Partial : SectorResult

        /** Two words on the same axis, or more words than a cell can have. */
        data object Invalid : SectorResult
    }

    /**
     * Read at most one 3×3 cell out of the non-score words.
     *
     * Position within the phrase is not load-bearing; which axis each word belongs to is. "top
     * middle" is a row and a column, "middle left" is the same pair the other way round, and
     * "middle" alone is the centre box. "top bottom" names one axis twice and is refused.
     */
    private fun sector(words: List<String>): SectorResult {
        if (words.isEmpty()) return SectorResult.None
        if (words.size > 2) return SectorResult.Invalid
        val rows = words.mapNotNull(ROW_WORDS::get)
        val columns = words.mapNotNull(COLUMN_WORDS::get)
        val centres = words.count { it in CENTRE_WORDS }
        return when {
            // "middle" / "centre" alone, or said twice: the centre box, no axis ambiguity.
            centres == words.size -> SectorResult.Cell(ObserverSector.CENTER)
            rows.size > 1 || columns.size > 1 -> SectorResult.Invalid
            rows.size == 1 && columns.size == 1 ->
                SectorResult.Cell(ObserverSector.of(rows.single(), columns.single()))
            // A row plus "middle": the centre column of that row ("nine top middle").
            rows.size == 1 && centres == 1 ->
                SectorResult.Cell(ObserverSector.of(rows.single(), ObserverSector.Column.CENTER))
            // "middle left" — the middle row, that column.
            columns.size == 1 && centres == 1 ->
                SectorResult.Cell(ObserverSector.of(ObserverSector.Row.MIDDLE, columns.single()))
            else -> SectorResult.Partial
        }
    }

    /**
     * Lower-case, strip the punctuation recognisers add, and split.
     *
     * Hyphens become spaces so a recogniser that returns "bottom-left" is treated the same as one
     * that returns "bottom left"; that is a transcription style difference, not a different phrase.
     * Nothing else is rewritten — normalising further would start guessing.
     */
    private fun normalise(transcript: String): List<String> =
        transcript
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }

    /**
     * The whole score vocabulary, mapped straight onto [ScoreInput] keypad tokens.
     *
     * Both the numerals and the words appear because recognisers differ on which they emit for an
     * isolated digit, and that choice is not something the athlete controls. "X" is the letter only:
     * "ex" is left out until range recordings show it is needed, because guessing at aliases is how
     * a false accept gets in.
     */
    private val SCORE_WORDS: Map<String, String> =
        buildMap {
            put("x", "X")
            put("miss", "M")
            put("missed", "M")
            put("m", "M")
            put("0", "M")
            put("zero", "M")
            val names =
                listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
            names.forEachIndexed { i, name ->
                val value = (i + 1).toString()
                put(value, value)
                put(name, value)
            }
        }

    private val ROW_WORDS: Map<String, ObserverSector.Row> =
        mapOf("top" to ObserverSector.Row.TOP, "bottom" to ObserverSector.Row.BOTTOM)

    private val COLUMN_WORDS: Map<String, ObserverSector.Column> =
        mapOf("left" to ObserverSector.Column.LEFT, "right" to ObserverSector.Column.RIGHT)

    /** Both spellings, because the recogniser's locale decides which one comes back. */
    private val CENTRE_WORDS: Set<String> = setOf("middle", "centre", "center")

    private val SECTOR_WORDS: Set<String> = ROW_WORDS.keys + COLUMN_WORDS.keys + CENTRE_WORDS
}
