package com.tbdfit.phone.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Proves the post-email-signup copy stays non-committal: Supabase returns an obfuscated success
// response and sends no email at all when signup targets an address that already belongs to a
// confirmed (e.g. Google-originated) account, to prevent account enumeration. This copy must never
// assert that an email definitely exists or was definitely sent — see the phone-authentication
// research report. Deliberately not a Compose UI test: these are just the exact strings shown, so
// asserting on the constants directly is enough to lock in the required semantics.
class VerificationCopyTest {
    @Test
    fun awaitingVerificationCopyIsConditionalNotAssertive() {
        assertTrue(AWAITING_VERIFICATION_COPY.startsWith("If "))
        assertFalse(AWAITING_VERIFICATION_COPY.contains("we sent", ignoreCase = true))
        assertFalse(AWAITING_VERIFICATION_COPY.contains("we've created", ignoreCase = true))
    }

    @Test
    fun resendVerificationCopyIsConditionalNotAssertive() {
        assertTrue(RESEND_VERIFICATION_COPY.startsWith("If "))
        assertFalse(RESEND_VERIFICATION_COPY.contains("sent", ignoreCase = true))
    }
}
