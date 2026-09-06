package com.tbdfit.phone.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Pure mapping tests using real androidx.credentials exception types — no Credential Manager
// mocking involved, and none of this proves the real on-device flow works (see the
// phone-authentication research report for what still requires live verification).
class GoogleIdTokenProviderTest {
    @Test
    fun cancellationProducesNoMessage() {
        assertNull(googleCredentialFailureMessage(GetCredentialCancellationException()))
    }

    @Test
    fun noCredentialProducesASpecificMessage() {
        assertEquals(
            "No Google account is available on this device.",
            googleCredentialFailureMessage(NoCredentialException()),
        )
    }

    @Test
    fun unexpectedCredentialTypeProducesAGenericSafeMessage() {
        // Never exposes anything about the unexpected credential itself.
        assertEquals(
            "Something went wrong. Please try again.",
            googleCredentialFailureMessage(UnexpectedCredentialTypeException()),
        )
    }
}
