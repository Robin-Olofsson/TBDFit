package com.tbdfit.phone.profile

// Narrow, purpose-scoped state for the profile-completeness concern — deliberately separate from
// AuthState (Unknown/SignedOut/SignedIn/SessionUnavailable stay exactly as they are; see the
// profile/username research report for why authentication and profile completeness must not be
// conflated into one state type).
//
// Unavailable is the profile-side analogue of AuthState.SessionUnavailable: a genuine
// network/backend failure while loading or creating a profile must never masquerade as
// AuthState.SignedOut — the user is still authenticated, their profile is just temporarily
// unreachable. Nothing here decides whether local-first product features require an online
// profile; that remains a separate, not-yet-made product decision.
sealed interface ProfileState {
    data object Loading : ProfileState
    data class Missing(val suggestedUsername: String?) : ProfileState
    data class Complete(val profile: Profile) : ProfileState
    data object Unavailable : ProfileState
}
