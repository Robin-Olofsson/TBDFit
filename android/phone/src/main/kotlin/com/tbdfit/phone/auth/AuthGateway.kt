package com.tbdfit.phone.auth

import android.content.Intent
import kotlinx.coroutines.flow.Flow

// The narrow boundary application/UI code depends on for identity/session behavior (see
// CLAUDE.md's Supabase boundary rule). Whichever backend implements this owns all Supabase Auth
// SDK details — nothing in this package, or in the UI, may know Supabase exists.
//
// Deliberately separate from LocalRecordRemoteStore: authentication/session and per-capability
// data sync are different responsibilities. Conflating them into one interface (or a generic
// BackendProvider covering both) would hide that distinction rather than express it.
interface AuthGateway {
    val state: Flow<AuthState>

    // desiredUsername, if given, is carried as signup metadata purely to survive the
    // email-verification boundary — it is client-controlled/untrusted input, not proof the
    // username is available, and not the product profile. See com.tbdfit.phone.profile for what
    // happens with it after a real session exists (ProfileGateway.createOwnProfile is the only
    // thing that can actually claim a username, gated by the database's own uniqueness
    // constraint).
    suspend fun signUp(email: String, password: String, desiredUsername: String? = null): Result<Unit>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signOut(): Result<Unit>

    // Minimum sensible retry for AuthState.SessionUnavailable: attempts to re-establish a usable
    // online session from whatever is already persisted, without discarding it first (unlike
    // signIn/signUp, this does not require the user to re-enter credentials). Not a generic
    // retry/backoff framework — one explicit action for one explicit state.
    suspend fun retryRestoringSession(): Result<Unit>

    // idToken/rawNonce are plain strings, not Google SDK types — obtaining them is a separate,
    // purpose-scoped responsibility (see GoogleIdTokenProvider). Google and email both converge on
    // this same AuthGateway boundary and the same AuthState.SignedIn shape on success; the
    // provider used to prove identity is not a different kind of TBDFit user.
    suspend fun signInWithGoogle(idToken: String, rawNonce: String): Result<Unit>

    // Resends the signup confirmation email. A no-op-shaped failure (e.g. rate limited) is
    // reported the same safe way as any other auth failure — never by exposing raw exception text.
    suspend fun resendEmailVerification(email: String): Result<Unit>

    // Handles an inbound auth deep link (email confirmation / PKCE callback) from
    // MainActivity.onCreate/onNewIntent, if the given intent is one — a safe no-op otherwise (the
    // implementation checks the intent's own scheme/host before doing anything). Takes a plain
    // platform Intent, not a Supabase type: MainActivity already legitimately owns one from the
    // Activity lifecycle, and the only thing any implementation needs from it is its data URI —
    // reconstructing a narrower representation (e.g. just a Uri) here would be pure indirection
    // around what the real implementation requires anyway, not a meaningful boundary improvement.
    // This is what keeps MainActivity itself from importing SupabaseClientProvider or any Supabase
    // Auth SDK type directly (see the phone-authentication audit report).
    fun handleAuthDeepLink(intent: Intent)
}
