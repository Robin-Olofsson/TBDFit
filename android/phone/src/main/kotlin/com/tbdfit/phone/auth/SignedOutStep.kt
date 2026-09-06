package com.tbdfit.phone.auth

// Purely a UI-navigation concept for the signed-out experience — never confused with AuthState.
// The user's actual auth/session state is SignedOut for the entire duration of this flow,
// including while viewing the email screen or the "check your email" prompt; this only tracks
// which signed-out screen is currently shown.
sealed interface SignedOutStep {
    data object Landing : SignedOutStep
    data object Email : SignedOutStep
}
