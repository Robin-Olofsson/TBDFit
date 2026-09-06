package com.tbdfit.phone.auth

// Auth-only identity — deliberately not the product Profile model (see CLAUDE.md's Supabase
// boundary rule and the phone-authentication report). Do not add product-shaped fields here
// (display name, preferences, onboarding intent, etc.) merely because a Profile table doesn't
// exist yet, or because Supabase happens to store something else on the same session object.
//
// desired_username signup metadata (see AuthGateway.signUp) deliberately does NOT live here, even
// though it originates from a signUp call — it is profile/onboarding intent, not
// identity/session state. Retrieving it is the Profile capability's job:
// ProfileGateway.loadDesiredUsernameHint() — see the profile/username hardening report for why
// this was corrected out of AuthSession after an earlier pass.
data class AuthSession(
    val userId: String,
    val email: String?,
)
