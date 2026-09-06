package com.tbdfit.phone.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Pure logic only — no Compose. Proves backTargetFor is the single source of truth the system
// BackHandler and the top bar's back arrow both resolve through (see EmailAuthScreen.kt's
// BackHandler and TbdfitAuthTopBar), so there is exactly one navigation semantics, not two.
class EmailAuthNavigationTest {
    @Test
    fun placeholderBackReturnsToCredentialsWithSameMode() {
        val placeholder = EmailScreenStep.Placeholder(
            title = "Terms of Service",
            message = "...",
            returnTo = EmailMode.CREATE_ACCOUNT,
        )

        assertEquals(EmailScreenStep.Credentials(EmailMode.CREATE_ACCOUNT), backTargetFor(placeholder))
    }

    @Test
    fun awaitingVerificationBackReturnsToLoginCredentials() {
        val awaitingVerification = EmailScreenStep.AwaitingVerification(email = "alice@example.com")

        assertEquals(EmailScreenStep.Credentials(EmailMode.LOGIN), backTargetFor(awaitingVerification))
    }

    @Test
    fun credentialsRootHasNoInternalBackTarget() {
        // Back from the root credentials screen exits to Landing via the onBack callback passed
        // in from SignedOutScreen — not an internal EmailScreenStep transition.
        assertNull(backTargetFor(EmailScreenStep.Credentials(EmailMode.LOGIN)))
        assertNull(backTargetFor(EmailScreenStep.Credentials(EmailMode.CREATE_ACCOUNT)))
    }
}
