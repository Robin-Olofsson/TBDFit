package com.tbdfit.phone.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure logic only — no Compose. Proves the exact contract ActiveWorkoutScreen relies on: blank is
// an intentional null, a valid non-negative number persists as itself, and anything malformed is
// rejected outright — never silently coerced to zero.
class SetInputParsingTest {
    @Test
    fun blankWeightIsIntentionalNull() {
        assertEquals(SetFieldInput.Persist(null), parseWeightInput(""))
        assertEquals(SetFieldInput.Persist(null), parseWeightInput("   "))
    }

    @Test
    fun blankRepsIsIntentionalNull() {
        assertEquals(SetFieldInput.Persist(null), parseRepsInput(""))
    }

    @Test
    fun validWeightParsesToItself() {
        assertEquals(SetFieldInput.Persist(102.5), parseWeightInput("102.5"))
        assertEquals(SetFieldInput.Persist(0.0), parseWeightInput("0"))
    }

    @Test
    fun validRepsParsesToItself() {
        assertEquals(SetFieldInput.Persist(8), parseRepsInput("8"))
        assertEquals(SetFieldInput.Persist(0), parseRepsInput("0"))
    }

    @Test
    fun malformedWeightIsInvalidNeverZero() {
        val result = parseWeightInput("12.3.4")
        assertTrue(result is SetFieldInput.Invalid)
        assertTrue(result != SetFieldInput.Persist(0.0))
    }

    @Test
    fun malformedRepsIsInvalidNeverZero() {
        val result = parseRepsInput("abc")
        assertTrue(result is SetFieldInput.Invalid)
        assertTrue(result != SetFieldInput.Persist(0))
    }

    @Test
    fun negativeWeightIsInvalid() {
        assertEquals(SetFieldInput.Invalid, parseWeightInput("-5"))
    }

    @Test
    fun negativeRepsIsInvalid() {
        assertEquals(SetFieldInput.Invalid, parseRepsInput("-1"))
    }

    @Test
    fun trailingLetterMakesWeightInvalidNotZero() {
        // e.g. the user has typed "12k" (a stray key) — must not be persisted as 0 or as 12, just
        // left un-persisted until the text becomes a complete valid number again.
        assertEquals(SetFieldInput.Invalid, parseWeightInput("12k"))
    }
}
