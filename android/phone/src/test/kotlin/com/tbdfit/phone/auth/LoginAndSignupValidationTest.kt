package com.tbdfit.phone.auth

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// Pure logic only — no Compose, no Credential Manager, no Supabase. Login is email-only (see the
// repository truth audit's username-login findings — username login has never existed, and no
// username->auth-identity resolution exists anywhere in the backend), so loginValidationError is
// the sole gate before AuthGateway.signIn is ever called: a blank field or a non-email value must
// never reach it.
class LoginAndSignupValidationTest {
    @Test
    fun loginRequiresEmailAndPassword() {
        assertNotNull(loginValidationError("", "password"))
        assertNotNull(loginValidationError("alice@example.com", ""))
        assertNull(loginValidationError("alice@example.com", "password"))
    }

    @Test
    fun loginRejectsNonEmailIdentifiers() {
        // Username login has never existed (no backend resolution path) — a username-shaped
        // identifier must be rejected locally with a validation error, never passed to
        // AuthGateway.signIn.
        for (identifier in listOf("robin", "robin92", "robin_92")) {
            assertNotNull("expected a validation error for '$identifier'", loginValidationError(identifier, "password"))
        }
    }

    @Test
    fun loginRejectsMalformedEmail() {
        assertNotNull(loginValidationError("foo@", "password"))
    }

    @Test
    fun loginAcceptsRealWorldEmailShapes() {
        val cases = listOf(
            "test@example.com",
            "robin@example.com",
            "Robin@example.com",
            "first.last@example.co.uk",
            "name+tag@example.com",
        )
        for (email in cases) {
            assertNull("expected no validation error for '$email'", loginValidationError(email, "password"))
        }
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
