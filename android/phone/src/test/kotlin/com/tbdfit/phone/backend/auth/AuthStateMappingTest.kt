package com.tbdfit.phone.backend.auth

import com.tbdfit.phone.auth.AuthState
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

// Tests the pure SessionStatus -> AuthState translation using REAL Supabase SDK types (real
// SessionStatus/UserSession/UserInfo values, not fakes/mocks of Supabase behavior) — this proves
// our own deterministic mapping logic is correct, not that the real network/auth flow works. The
// live flow itself can only be proven by the manual verification steps in the project report; do
// not read these tests as verifying that.
class AuthStateMappingTest {
    private fun userSession(userId: String = "uid-1", email: String? = "user@example.com") = UserSession(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresIn = 3600,
        tokenType = "bearer",
        user = UserInfo(aud = "authenticated", id = userId, email = email),
    )

    @Test
    fun initializingMapsToUnknown() {
        assertEquals(AuthState.Unknown, toAuthState(SessionStatus.Initializing))
    }

    @Test
    fun notAuthenticatedMapsToSignedOut() {
        assertEquals(AuthState.SignedOut, toAuthState(SessionStatus.NotAuthenticated(isSignOut = false)))
    }

    @Test
    fun notAuthenticatedAfterSignOutStillMapsToSignedOut() {
        assertEquals(AuthState.SignedOut, toAuthState(SessionStatus.NotAuthenticated(isSignOut = true)))
    }

    @Test
    fun authenticatedFromStorageMapsToSignedInWithSameIdentity() {
        val session = userSession(userId = "uid-42", email = "restored@example.com")
        val result = toAuthState(SessionStatus.Authenticated(session, source = SessionSource.Storage))

        assertEquals(AuthState.SignedIn(com.tbdfit.phone.auth.AuthSession("uid-42", "restored@example.com")), result)
    }

    @Test
    fun authenticatedFromRefreshMapsToSignedIn() {
        val session = userSession()
        val result = toAuthState(SessionStatus.Authenticated(session, source = SessionSource.Refresh(session)))

        assertEquals(AuthState.SignedIn(com.tbdfit.phone.auth.AuthSession("uid-1", "user@example.com")), result)
    }

    @Test
    fun refreshFailureFromNetworkErrorMapsToSessionUnavailableNotSignedOut() {
        val networkFailure = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(RuntimeException("no network")))
        val result = toAuthState(networkFailure)

        assertEquals(AuthState.SessionUnavailable, result)
        assertNotEquals(AuthState.SignedOut, result)
    }

    @Test
    fun sessionUnavailableRecoversToSignedInOnceAuthenticatedArrives() {
        // Sequenced deliberately to demonstrate the scenario the correction is about: a prior
        // RefreshFailure must not prevent a later, real Authenticated status from mapping normally.
        // toAuthState is a pure function of its single input, so this also follows from the
        // individual-state tests above, but this test states the recovery scenario explicitly.
        val afterFailure = toAuthState(SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(RuntimeException("no network"))))
        assertEquals(AuthState.SessionUnavailable, afterFailure)

        val session = userSession(userId = "uid-7", email = "recovered@example.com")
        val afterRecovery = toAuthState(SessionStatus.Authenticated(session, source = SessionSource.Refresh(session)))

        assertEquals(AuthState.SignedIn(com.tbdfit.phone.auth.AuthSession("uid-7", "recovered@example.com")), afterRecovery)
    }

    @Test
    fun googleAndEmailSignInsConvergeToSameSignedInShape() {
        // toAuthState never inspects which provider proved the identity — only the resulting
        // session's user — so Google and email sign-ins must produce an identical AuthState.SignedIn
        // for the same identity. Proves "authentication provider is how identity is proven, not
        // what kind of TBDFit user someone is" (no GoogleUser/EmailUser split exists).
        val session = userSession(userId = "uid-99", email = "alice@example.com")

        val viaGoogle = toAuthState(SessionStatus.Authenticated(session, source = SessionSource.SignIn(Google)))
        val viaEmail = toAuthState(SessionStatus.Authenticated(session, source = SessionSource.SignIn(Email)))

        assertEquals(viaGoogle, viaEmail)
        assertEquals(AuthState.SignedIn(com.tbdfit.phone.auth.AuthSession("uid-99", "alice@example.com")), viaGoogle)
    }

    @Test
    fun signUpMetadataIsNotCarriedIntoAuthSessionEvenWhenPresent() {
        // Load-bearing boundary assertion for the profile/username hardening correction: even when
        // real signup metadata (desired_username) is present on the underlying Supabase user, the
        // identity/session mapping must produce exactly the same AuthSession as it would without
        // any metadata at all — AuthSession carries only userId/email, never onboarding intent.
        // Reading that hint back is ProfileGateway.loadDesiredUsernameHint()'s job (see
        // ProfileFlowTest), never this mapping's.
        val userWithMetadata = UserInfo(
            aud = "authenticated",
            id = "uid-5",
            email = "alice@example.com",
            userMetadata = buildJsonObject { put("desired_username", "alice_lifts") },
        )
        val session = UserSession(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresIn = 3600,
            tokenType = "bearer",
            user = userWithMetadata,
        )

        val result = toAuthState(SessionStatus.Authenticated(session, source = SessionSource.SignIn(Email)))

        assertEquals(AuthState.SignedIn(com.tbdfit.phone.auth.AuthSession("uid-5", "alice@example.com")), result)
    }
}
