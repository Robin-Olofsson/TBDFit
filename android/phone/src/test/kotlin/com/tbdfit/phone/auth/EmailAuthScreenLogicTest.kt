package com.tbdfit.phone.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Proves the "don't falsely claim SignedIn" contract for email signup requiring confirmation:
// a just-completed sign-up call must not be treated as authenticated unless the global AuthState
// actually became SignedIn. Pure logic, no Compose/Supabase involved.
class EmailAuthScreenLogicTest {
    @Test
    fun promptsForVerificationWhenStillSignedOut() {
        assertTrue(requiresEmailVerificationPrompt(AuthState.SignedOut))
    }

    @Test
    fun promptsForVerificationWhenStillUnknown() {
        assertTrue(requiresEmailVerificationPrompt(AuthState.Unknown))
    }

    @Test
    fun promptsForVerificationWhenSessionUnavailable() {
        assertTrue(requiresEmailVerificationPrompt(AuthState.SessionUnavailable))
    }

    @Test
    fun doesNotPromptWhenAlreadySignedIn() {
        assertFalse(requiresEmailVerificationPrompt(AuthState.SignedIn(AuthSession("uid-1", "a@example.com"))))
    }
}
