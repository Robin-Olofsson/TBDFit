package com.tbdfit.phone.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbdfit.phone.workout.RootUiState
import com.tbdfit.phone.workout.WorkoutRepository
import com.tbdfit.phone.workout.WorkoutRootScreen
import com.tbdfit.phone.workout.deriveRootUiState

// The "Workout" destination of the corrected Phone IA (Home | Workout | You) — training-focused,
// per product-information-architecture.md §2. Wraps the REAL, unmodified WorkoutRootScreen (start →
// attach exercise → log sets, all production-backed) and adds two PROTOTYPE-ONLY entry points on
// top: Routine Library/Detail (see RoutinePrototype.kt) and a Workout Summary shown after pressing
// the real ActiveWorkoutScreen's new "Finish Workout" affordance (see WorkoutSummaryPrototype.kt).
// Internal nav is a plain sealed-state switch, matching this codebase's existing manual-nav style
// (see ActiveWorkoutScreen's own `showingPicker` toggle) rather than introducing a navigation
// library for a prototype.
private sealed interface WorkoutTabDestination {
    data object Root : WorkoutTabDestination
    data object RoutineLibrary : WorkoutTabDestination
    data class RoutineDetail(val routine: PrototypeRoutine) : WorkoutTabDestination
    data class Summary(val workoutId: String) : WorkoutTabDestination
}

@Composable
internal fun WorkoutTabScreen(repository: WorkoutRepository, ownerId: String, modifier: Modifier = Modifier) {
    var destination by remember { mutableStateOf<WorkoutTabDestination>(WorkoutTabDestination.Root) }

    when (val current = destination) {
        WorkoutTabDestination.Root -> Column(modifier = modifier.fillMaxWidth()) {
            WorkoutRootScreen(
                repository = repository,
                ownerId = ownerId,
                onFinishWorkout = { workout -> destination = WorkoutTabDestination.Summary(workout.id) },
            )
            // Independently observes the same real Flow WorkoutRootScreen already observes, purely
            // to decide whether the "Browse Routines" prototype entry point makes sense right now —
            // it would be misleading to offer starting a new routine while one workout is already
            // active. Room Flows support multiple independent collectors, so this adds no real cost.
            val state by produceState<RootUiState>(initialValue = RootUiState.Loading, repository, ownerId) {
                repository.observeActiveWorkout(ownerId).collect { workout -> value = deriveRootUiState(workout) }
            }
            if (state is RootUiState.NoActiveWorkout) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Or start from a routine:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Button(
                    onClick = { destination = WorkoutTabDestination.RoutineLibrary },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Text("Browse Routines")
                }
            }
        }
        WorkoutTabDestination.RoutineLibrary -> RoutineLibraryScreen(
            onOpenRoutine = { routine -> destination = WorkoutTabDestination.RoutineDetail(routine) },
            modifier = modifier,
        )
        is WorkoutTabDestination.RoutineDetail -> RoutineDetailScreen(
            routine = current.routine,
            repository = repository,
            ownerId = ownerId,
            onStarted = { destination = WorkoutTabDestination.Root },
            onBack = { destination = WorkoutTabDestination.RoutineLibrary },
            modifier = modifier,
        )
        is WorkoutTabDestination.Summary -> WorkoutSummaryScreen(
            workoutId = current.workoutId,
            repository = repository,
            onDone = { destination = WorkoutTabDestination.Root },
            modifier = modifier,
        )
    }
}
