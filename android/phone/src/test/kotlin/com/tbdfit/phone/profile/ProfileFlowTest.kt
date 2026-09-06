package com.tbdfit.phone.profile

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Proves the pressure-tested branches from the profile/username research report, against a fake
// ProfileGateway (no real Supabase/Postgres involved — database constraints themselves are only
// proven by the live verification steps in the report, never claimed as tested here).
//
// Deliberately does not construct any AuthSession: loadProfileState only takes a ProfileGateway —
// see the profile/username hardening report for why the desired-username hint was moved off
// AuthSession and behind ProfileGateway.loadDesiredUsernameHint() instead.
class ProfileFlowTest {
    @Test
    fun signedInWithExistingProfileIsComplete() = runTest {
        val profile = Profile(userId = "uid-1", username = "robin")
        val gateway = FakeProfileGateway(profile = profile)

        val state = loadProfileState(gateway)

        assertEquals(ProfileState.Complete(profile), state)
    }

    @Test
    fun signedInWithNoProfileIsMissingWithSuggestedUsername() = runTest {
        val gateway = FakeProfileGateway(profile = null, desiredUsernameHint = "robin")

        val state = loadProfileState(gateway)

        assertEquals(ProfileState.Missing(suggestedUsername = "robin"), state)
    }

    @Test
    fun existingAccountWithNoDesiredUsernameGetsAnEmptySuggestion() = runTest {
        // Google-first accounts, and any account created before this feature existed — see the
        // report's "existing account behavior" pressure test. Null, not a fabricated value.
        val gateway = FakeProfileGateway(profile = null, desiredUsernameHint = null)

        val state = loadProfileState(gateway) as ProfileState.Missing

        assertNull(state.suggestedUsername)
    }

    @Test
    fun backendFailureIsUnavailableNotMissingOrComplete() = runTest {
        val gateway = FakeProfileGateway(loadFailure = RuntimeException("network error"))

        val state = loadProfileState(gateway)

        assertEquals(ProfileState.Unavailable, state)
    }

    @Test
    fun uniqueUsernameConflictProducesSafeMessageNotRawException() {
        val message = usernameConflictMessage(UsernameUnavailableException())

        assertEquals(
            "Your account is verified, but that username is no longer available. Choose another username.",
            message,
        )
    }

    @Test
    fun conflictMessageNeverLeaksOtherFailureDetails() {
        val message = usernameConflictMessage(RuntimeException("relation \"profiles\" violates constraint xyz"))

        assertEquals("Something went wrong. Please try again.", message)
    }
}
