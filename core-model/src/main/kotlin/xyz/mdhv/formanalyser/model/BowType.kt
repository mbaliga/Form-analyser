package xyz.mdhv.formanalyser.model

/** Bow discipline. Stored as the enum name. */
enum class BowType {
    RECURVE,
    COMPOUND,
    BAREBOW,
    ;

    companion object {
        fun fromStorage(s: String?): BowType = entries.firstOrNull { it.name == s?.uppercase() } ?: RECURVE
    }
}
