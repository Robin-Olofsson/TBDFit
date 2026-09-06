package com.tbdfit.phone.profile

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// The Android-side mirror of the database's authoritative CHECK constraint
// (profiles_username_syntax) — see UsernameValidation.kt's doc comment. This proves the mirror
// itself, not that PostgreSQL agrees; that requires the live verification steps in the report.
class UsernameValidationTest {
    @Test
    fun rejectsEmptyOrBlank() {
        assertNotNull(usernameValidationError(""))
        assertNotNull(usernameValidationError("   "))
    }

    @Test
    fun rejectsTooShortOrTooLong() {
        assertNotNull(usernameValidationError("ab"))
        assertNotNull(usernameValidationError("a".repeat(31)))
        assertNull(usernameValidationError("abc"))
        assertNull(usernameValidationError("a".repeat(30)))
    }

    @Test
    fun rejectsDisallowedCharacters() {
        assertNotNull(usernameValidationError("röbin"))
        assertNotNull(usernameValidationError("robin!"))
        assertNotNull(usernameValidationError("rob in"))
        assertNotNull(usernameValidationError("robin@92"))
    }

    @Test
    fun allowsUnderscoreAndPeriod() {
        assertNull(usernameValidationError("robin_92"))
        assertNull(usernameValidationError("robin.92"))
    }

    @Test
    fun trimsSurroundingWhitespaceBeforeValidating() {
        assertNull(usernameValidationError("  robin  "))
    }
}
