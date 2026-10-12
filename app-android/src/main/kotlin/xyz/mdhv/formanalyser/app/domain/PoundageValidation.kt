package xyz.mdhv.formanalyser.app.domain

/** Optional poundage is valid when absent, and must be finite and positive when supplied. */
object PoundageValidation {
    fun isValid(value: Double?): Boolean = value == null || (value.isFinite() && value > 0.0)

    fun isValidInput(raw: String): Boolean {
        if (raw.isBlank()) return true
        val value = raw.toDoubleOrNull() ?: return false
        return value.isFinite() && value > 0.0
    }
}
