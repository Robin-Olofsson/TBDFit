package com.tbdfit.phone.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure logic only — no Compose. Landing must have no internal back target (system back there is
// normal Android behavior, not a transition this screen owns); Email does (back returns to
// Landing) — see SignedOutScreen's BackHandler, which resolves through this same function.
class SignedOutNavigationTest {
    @Test
    fun landingHasNoInternalBackTarget() {
        assertFalse(hasInternalBackTarget(SignedOutStep.Landing))
    }

    @Test
    fun emailHasAnInternalBackTarget() {
        assertTrue(hasInternalBackTarget(SignedOutStep.Email))
    }
}
