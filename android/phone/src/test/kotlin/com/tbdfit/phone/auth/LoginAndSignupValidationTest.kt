package com.tbdfit.phone.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// Pure logic only — no Compose, no Credential Manager, no Supabase. Proves the one thing that
// actually matters architecturally in this slice: a username-like login input never reaches
// AuthGateway.signIn (no lookup, no network call), while a genuine email input still does — and,
// per the login-routing-bug fix, that classification (is this email-shaped?) and validation (is
// this a valid-enough email?) are separate questions, so a malformed-but-"@"-containing identifier
// stays on the email path with a validation error rather than being silently treated as a
// username.
class LoginAndSignupValidationTest {
    @Test
    fun loginRequiresBothIdentifierAndPassword() {
        assertNotNull(loginValidationError("", "password"))
        assertNotNull(loginValidationError("alice@example.com", ""))
        assertNull(loginValidationError("alice@example.com", "password"))
    }

    // Deterministic reproduction of the reported bug: every one of these is a real, working email
    // shape and must route to EmailLogin — i.e. reach AuthGateway.signIn(email, password) via
    // LoginForm's EmailLogin branch, unchanged since before this fix.
    @Test
    fun realWorldEmailShapesAllRouteToEmailLogin() {
        val cases = listOf(
            "test@example.com",
            "robin@example.com",
            "Robin@example.com",
            "first.last@example.co.uk",
            "name+tag@example.com",
        )
        for (email in cases) {
            val route = routeLoginIdentifier(email)
            assertEquals("expected EmailLogin for '$email'", LoginIdentifierRoute.EmailLogin(email), route)
        }
    }

    @Test
    fun surroundingWhitespaceIsTrimmedBeforeRouting() {
        val route = routeLoginIdentifier("  test@example.com  ")
        assertEquals(LoginIdentifierRoute.EmailLogin("test@example.com"), route)
    }

    @Test
    fun ordinaryUsernamesRouteToUsernameDeferred() {
        // This is the load-bearing assertion for the other direction: a non-email identifier must
        // never produce an EmailLogin route, since that route is what actually calls
        // AuthGateway.signIn. No backend lookup exists for this case yet — see
        // USERNAME_LOGIN_NOT_YET_SUPPORTED_MESSAGE.
        for (username in listOf("robin", "robin92", "robin_92")) {
            assertEquals(
                "expected UsernameLoginNotYetSupported for '$username'",
                LoginIdentifierRoute.UsernameLoginNotYetSupported,
                routeLoginIdentifier(username),
            )
        }
    }

    @Test
    fun malformedEmailShapedIdentifierStaysOnEmailPathAsInvalidNotUsername() {
        // The exact bug: classification must not require full validity. "foo@" contains "@" (it's
        // an attempted email), so it must never be reclassified as a username — it must stay on
        // the email path and be reported as invalid instead.
        val route = routeLoginIdentifier("foo@")

        assertEquals(LoginIdentifierRoute.InvalidEmail, route)
        assert(route != LoginIdentifierRoute.UsernameLoginNotYetSupported)
    }

    @Test
    fun createAccountRequiresUsernameEmailAndPassword() {
        assertNotNull(createAccountValidationError("", "alice@example.com", "password"))
        assertNotNull(createAccountValidationError("alice", "", "password"))
        assertNotNull(createAccountValidationError("alice", "alice@example.com", ""))
        assertNotNull(createAccountValidationError("alice", "not-an-email", "password"))
        assertNull(createAccountValidationError("alice", "alice@example.com", "password"))
    }

    // Username syntax sanity checks themselves now live in
    // com.tbdfit.phone.profile.UsernameValidation, alongside the rest of the Profile capability
    // (see UsernameValidationTest) — createAccountValidationError above already exercises that
    // it's actually wired in.
}
