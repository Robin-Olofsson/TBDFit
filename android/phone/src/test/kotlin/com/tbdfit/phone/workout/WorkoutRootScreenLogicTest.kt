package com.tbdfit.phone.workout

import org.junit.Assert.assertEquals
import org.junit.Test

// Pure logic only — no Compose, no Robolectric. Proves the state-derivation mapping
// WorkoutRootScreen actually routes on: no active workout means "Start Workout available"
// (NoActiveWorkout), and an existing active workout — however it got there, freshly started or
// restored from a prior session — means the active state, never a third "not sure yet" outcome
// once a real value is in hand.
class WorkoutRootScreenLogicTest {
    @Test
    fun noActiveWorkoutMapsToStartWorkoutAvailableState() {
        assertEquals(RootUiState.NoActiveWorkout, deriveRootUiState(null))
    }

    @Test
    fun anExistingActiveWorkoutMapsToTheActiveState() {
        val workout = WorkoutEntity(
            id = "workout-1",
            ownerId = "owner-a",
            status = WorkoutStatus.ACTIVE,
            startedAt = 1_700_000_000_000L,
            createdAt = 1_700_000_000_000L,
        )

        assertEquals(RootUiState.Active(workout), deriveRootUiState(workout))
    }
}
