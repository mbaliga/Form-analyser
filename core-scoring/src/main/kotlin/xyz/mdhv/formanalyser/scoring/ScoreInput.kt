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
}
