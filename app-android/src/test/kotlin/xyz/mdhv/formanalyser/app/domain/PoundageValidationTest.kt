package xyz.mdhv.formanalyser.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoundageValidationTest {
    @Test
    fun optionalValuesAllowBlankAndRequirePositiveFiniteNumbers() {
        assertTrue(PoundageValidation.isValid(null))
        assertTrue(PoundageValidation.isValid(1.0))
        assertFalse(PoundageValidation.isValid(0.0))
        assertFalse(PoundageValidation.isValid(-1.0))
        assertFalse(PoundageValidation.isValid(Double.NaN))
        assertFalse(PoundageValidation.isValid(Double.POSITIVE_INFINITY))
    }

    @Test
    fun inputAllowsBlankButRejectsInvalidOrNonPositiveText() {
        assertTrue(PoundageValidation.isValidInput(""))
        assertTrue(PoundageValidation.isValidInput("  "))
        assertTrue(PoundageValidation.isValidInput("24.5"))
        assertFalse(PoundageValidation.isValidInput("0"))
        assertFalse(PoundageValidation.isValidInput("-2"))
        assertFalse(PoundageValidation.isValidInput("."))
        assertFalse(PoundageValidation.isValidInput("not a number"))
    }
}
