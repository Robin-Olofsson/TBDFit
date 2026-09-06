package com.tbdfit.phone.profile

// Pure enough to unit-test without Compose: given a ProfileGateway, decide the ProfileState.
// Kept as a standalone function (not inlined into a Composable) specifically so the
// SignedIn+no-profile / SignedIn+existing-profile / backend-failure branches are all directly
// testable — see ProfileFlowTest. Deliberately takes only a ProfileGateway, no AuthSession: the
// desired-username hint is read through ProfileGateway.loadDesiredUsernameHint(), not off the
// auth session (see the profile/username hardening report).
internal suspend fun loadProfileState(profileGateway: ProfileGateway): ProfileState =
    profileGateway.loadOwnProfile().fold(
        onSuccess = { profile ->
            if (profile != null) {
                ProfileState.Complete(profile)
            } else {
                // Empty/null for Google-first accounts and for any account that predates this
                // feature — an honest default, not a bug (see the profile/username research
                // report, "existing account behavior").
                ProfileState.Missing(suggestedUsername = profileGateway.loadDesiredUsernameHint())
            }
        },
        onFailure = { ProfileState.Unavailable },
    )

// Maps a createOwnProfile failure to safe, user-facing copy — never raw PostgREST/Postgres
// exception text, never anything revealing which other account holds the username.
internal fun usernameConflictMessage(e: Throwable): String = when (e) {
    is UsernameUnavailableException ->
        "Your account is verified, but that username is no longer available. Choose another username."
    else -> "Something went wrong. Please try again."
}
