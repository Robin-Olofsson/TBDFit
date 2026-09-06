package com.tbdfit.phone.profile

// The narrow boundary application/UI code depends on for profile behavior — mirrors AuthGateway's
// role for authentication (see CLAUDE.md's Supabase boundary rule). Whichever backend implements
// this owns all Supabase SDK/table/query details; nothing in this package, or in the UI, may know
// Supabase exists. Deliberately separate from AuthGateway: profile is product-owned data with its
// own lifecycle, not identity/session state — see ProfileState.
interface ProfileGateway {
    // null means "no profile exists yet" (ProfileState.Missing), not an error. A genuine
    // load failure is a Result.failure, mapped by the caller to ProfileState.Unavailable.
    suspend fun loadOwnProfile(): Result<Profile?>

    // The signup-time onboarding hint (see AuthGateway.signUp's desiredUsername param) — wherever
    // and however the backend actually stores it (Supabase user metadata today) stays entirely
    // inside the implementation; nothing but a plain nullable String crosses this boundary. This
    // deliberately does NOT live on AuthSession/AuthState — it's profile-onboarding intent, not
    // identity/session state (see the profile/username hardening report). Null for accounts with
    // no such hint (Google-first, or any account predating this feature) — a normal case, not an
    // error, so this never needs a Result wrapper.
    suspend fun loadDesiredUsernameHint(): String?

    // Username availability before this call is advisory only (the hint above included) — the
    // database's UNIQUE constraint on normalized_username is the sole authority. See
    // UsernameUnavailableException.
    suspend fun createOwnProfile(username: String): Result<Profile>
}

// Thrown by createOwnProfile specifically when the database's uniqueness constraint rejects the
// requested username — never a raw PostgREST/Postgres exception, and never anything that reveals
// which other account holds the username.
class UsernameUnavailableException : Exception("That username is no longer available")
