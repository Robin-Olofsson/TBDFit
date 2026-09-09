package com.tbdfit.phone.auth

import com.tbdfit.phone.workout.RootUiState
import com.tbdfit.phone.workout.WorkoutEntity
import com.tbdfit.phone.workout.WorkoutStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure logic only — no Compose. Proves the exact routing decision the offline/auth-gate audit
// requires: a locally ACTIVE workout stays reachable when SessionUnavailable; anything else falls
// back to the existing retry-only screen.
class SessionUnavailableWorkoutAccessTest {
    @Test
    fun anActiveLocalWorkoutMeansShowTheWorkoutNotTheRetryScreen() {
        val workout = WorkoutEntity(
            id = "workout-1",
            ownerId = "uid-1",
            status = WorkoutStatus.ACTIVE,
            startedAt = 1_700_000_000_000L,
            createdAt = 1_700_000_000_000L,
        )

        assertTrue(shouldShowLocalWorkoutInsteadOfSessionRetry(RootUiState.Active(workout)))
    }

    @Test
    fun noActiveLocalWorkoutMeansShowTheRetryScreen() {
        assertFalse(shouldShowLocalWorkoutInsteadOfSessionRetry(RootUiState.NoActiveWorkout))
    }

    @Test
    fun stillLoadingMeansShowTheRetryScreenNotAFalsePositiveWorkoutView() {
        // Loading must never be treated as "show the workout" — that would risk a flash of the
        // wrong screen before Room has actually answered.
        assertFalse(shouldShowLocalWorkoutInsteadOfSessionRetry(RootUiState.Loading))
    }
}
