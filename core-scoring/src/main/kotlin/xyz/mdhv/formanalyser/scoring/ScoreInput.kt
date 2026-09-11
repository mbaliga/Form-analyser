package xyz.mdhv.formanalyser.scoring

/** Cold-thumb keypad tokens. Parsing stays in the pure core so every future surface agrees. */
object ScoreInput {
    val keypad: List<String> = listOf("X", "10", "9", "8", "7", "6", "5", "4", "3", "2", "1", "M")

    fun parse(token: String): ArrowScore = when (token.trim().uppercase()) {
        "X" -> ArrowScore.X
        "M", "MISS", "0" -> ArrowScore.MISS
        else -> token.toIntOrNull()?.takeIf { it in 1..10 }?.let(ArrowScore::points)
            ?: throw IllegalArgumentException("Unsupported score token: $token")
    }

    /**
     * Parse one hands-free observer declaration such as "eight bottom left", "ten high" or
     * "X centre". The score remains authoritative only after the speech recognizer returns this
     * deterministic parse; unrecognised words never silently become an arrow.
     */
    fun parseSpoken(utterance: String): SpokenScore {
        val words = utterance.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
        require(words.isNotEmpty()) { "No score heard" }
        val valueWords = mapOf(
            "one" to "1", "two" to "2", "three" to "3", "four" to "4",
            "five" to "5", "six" to "6", "seven" to "7", "eight" to "8",
            "nine" to "9", "ten" to "10", "x" to "X", "ex" to "X",
            "miss" to "M", "missed" to "M", "zero" to "M",
        )
        val token = words.firstNotNullOfOrNull { word ->
            valueWords[word] ?: word.toIntOrNull()?.takeIf { it in 0..10 }?.toString()
        } ?: throw IllegalArgumentException("No valid score in: $utterance")
        val vertical = when {
            words.any { it in setOf("top", "high", "upper") } -> "top"
            words.any { it in setOf("bottom", "low", "lower") } -> "bottom"
            else -> null
        }
        val horizontal = when {
            "left" in words -> "left"
            "right" in words -> "right"
            else -> null
        }
        val sector = listOfNotNull(vertical, horizontal).joinToString(" ").ifBlank {
            if (words.any { it in setOf("centre", "center", "middle") }) "centre" else "unspecified"
        }
        return SpokenScore(parse(token), sector, utterance.trim())
    }
}

data class SpokenScore(val score: ArrowScore, val sector: String, val declaredText: String)
