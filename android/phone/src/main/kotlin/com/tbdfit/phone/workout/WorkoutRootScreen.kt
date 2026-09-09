package com.tbdfit.phone.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// The small router between "no active workout" and "an active workout exists" — mirrors
// com.tbdfit.phone.auth.SignedOutScreen's role as a thin router between its own two states. Driven
// entirely by WorkoutRepository.observeActiveWorkout(), a Flow backed directly by Room: there is no
// separate in-memory "restore" step here — the same query that drives ordinary navigation also
// drives recovery after process death/recreation (see the design doc's "Process death / restore"
// section and WorkoutDao.observeActiveWorkout). Deliberately does not trust an in-memory flag on
// its own — RootUiState is derived fresh from the Flow every time this composable (re)enters
// composition, e.g. after activity recreation.
//
// Deliberately no separate "Resume Workout" screen distinct from the active screen itself: the
// moment an ACTIVE workout exists — whether just created or restored from a prior session — this
// router shows ActiveWorkoutScreen directly, matching the design doc's own "routes straight into
// it instead of offering to start a new one" precedent and this slice's stop condition (restored
// means back in the active screen, not a button to press again).
// No `modifier` default of fillMaxSize here on purpose: this slice embeds the workout section
// alongside other content in MainActivity's existing single screen (see that file), not as its
// own dedicated navigation destination yet — sizing to content keeps it from fighting a sibling
// for all remaining vertical space. A future slice that gives this its own screen/back-stack entry
// can pass Modifier.fillMaxSize() explicitly at that call site.
// `ownerId` (local-workout-ownership audit): the account whose workout this screen is allowed to
// observe/start — always a real, currently-known account id (AuthSession.userId while SignedIn;
// the cached last-known account while SessionUnavailable). This screen never queries "any" active
// workout regardless of owner — see WorkoutDao for why that guarantee lives at the DAO level too.
@Composable
fun WorkoutRootScreen(
    repository: WorkoutRepository,
    ownerId: String,
    modifier: Modifier = Modifier,
    onFinishWorkout: ((WorkoutEntity) -> Unit)? = null,
) {
    val state by produceState<RootUiState>(initialValue = RootUiState.Loading, repository, ownerId) {
        repository.observeActiveWorkout(ownerId).collect { workout -> value = deriveRootUiState(workout) }
    }

    when (val current = state) {
        RootUiState.Loading -> Box(
            modifier = modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        RootUiState.NoActiveWorkout -> WorkoutHomeScreen(repository = repository, ownerId = ownerId, modifier = modifier)
        is RootUiState.Active -> ActiveWorkoutScreen(
            workout = current.workout,
            repository = repository,
            ownerId = ownerId,
            modifier = modifier,
            onFinishWorkout = onFinishWorkout,
        )
    }
}

// Loading exists only for the brief window before Room's first Flow emission arrives — without it,
// the screen would need to treat "no value yet" the same as "confirmed no active workout," which is
// exactly the kind of trusting-an-assumption-instead-of-Room mistake this slice exists to avoid.
//
// internal, not private: exercised directly by WorkoutRootScreenLogicTest without Compose, the same
// pattern already used for e.g. EmailScreenStep/backTargetFor in EmailAuthScreen.kt — a pure
// mapping function is the testable unit here, not the composable itself.
internal sealed interface RootUiState {
    data object Loading : RootUiState
    data object NoActiveWorkout : RootUiState
    data class Active(val workout: WorkoutEntity) : RootUiState
}

internal fun deriveRootUiState(workout: WorkoutEntity?): RootUiState =
    if (workout == null) RootUiState.NoActiveWorkout else RootUiState.Active(workout)
