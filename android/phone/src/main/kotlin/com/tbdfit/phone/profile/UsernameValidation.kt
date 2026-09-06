package com.tbdfit.phone.profile

// UX-only mirror of the database's authoritative CHECK constraint
// (supabase/migrations/20260906120000_create_profiles.sql, profiles_username_syntax) — kept in
// sync by hand; PostgreSQL remains the actual authority regardless of what this function allows.
// Case-insensitivity is a login/uniqueness concern (handled by the database's generated
// normalized_username column), not something this function needs to enforce. Unicode/confusable
// handling is deliberately out of scope for V1 in both places — see the profile/username research
// report.
private val USERNAME_PATTERN = Regex("^[A-Za-z0-9_.]+$")

internal fun usernameValidationError(rawUsername: String): String? {
    val trimmed = rawUsername.trim()
    return when {
        trimmed.isEmpty() -> "Enter a username."
        trimmed.length < 3 -> "Username must be at least 3 characters."
        trimmed.length > 30 -> "Username must be 30 characters or fewer."
        !USERNAME_PATTERN.matches(trimmed) ->
            "Usernames can only contain letters, numbers, underscores, and periods."
        else -> null
    }
}
