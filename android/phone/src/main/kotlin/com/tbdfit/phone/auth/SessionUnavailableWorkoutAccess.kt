package com.tbdfit.phone.auth

import com.tbdfit.phone.workout.RootUiState

// Local-first exception to the auth gate — see the offline/auth-gate audit: a workout already
// durably ACTIVE in Room must remain resumable even when the remote session cannot currently be
// verified/refreshed (AuthState.SessionUnavailable — e.g. no network at refresh time). Local
// workout execution and remote account authorization are separate concerns; the local database
// does not require a live session to be read or written.
//
// Deliberately narrow: this does NOT extend to AuthState.SignedOut, and it does NOT let a user
// start a brand-new workout while SessionUnavailable — only an already-active one stays reachable.
// If there is no local ACTIVE workout, the ordinary SessionUnavailableScreen (retry-only) is still
// shown; nothing here invents a way to use the app fully offline with no account. Whether workouts
// should ever be startable with no account at all is a separate, broader, still-open product
// question this fix does not answer.
internal fun shouldShowLocalWorkoutInsteadOfSessionRetry(workoutState: RootUiState): Boolean =
    workoutState is RootUiState.Active
