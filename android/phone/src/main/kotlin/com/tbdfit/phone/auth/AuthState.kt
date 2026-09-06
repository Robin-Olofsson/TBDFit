package com.tbdfit.phone.auth

// Narrow, purpose-scoped state for this one capability — not a generic session/state-machine
// framework. Unknown is the brief window while a possibly-persisted session is still being
// resolved (see SupabaseAuthGateway.toAuthState for exactly what maps to each case).
//
// SessionUnavailable is deliberately distinct from SignedOut: a persisted session exists but
// could not currently be refreshed/verified (e.g. no network at the moment of refresh). This is
// NOT the same fact as "the user is not logged in" — treating it as SignedOut would incorrectly
// show the login/create-account screen for what may be a purely transient condition, and would
// wrongly make it eligible for logout-style account-data clearing (see LocalAccountDataReset.kt).
sealed interface AuthState {
    data object Unknown : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val session: AuthSession) : AuthState
    data object SessionUnavailable : AuthState
}
