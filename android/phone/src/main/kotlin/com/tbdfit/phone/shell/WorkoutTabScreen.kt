package com.tbdfit.phone.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbdfit.phone.workout.RoutineEntity
import com.tbdfit.phone.workout.WorkoutRepository
import com.tbdfit.phone.workout.WorkoutRootScreen

// The "Workout" destination of the corrected Phone IA (Home | Workout | You) — training-focused,
// per product-information-architecture.md §2. Wraps the REAL, unmodified WorkoutRootScreen (start →
// attach exercise → log sets, all production-backed) and adds My Routines as an always-visible
// section on the root screen (Routine UX completion slice — see
// docs/product/frontend-prototype-notes.md): the earlier prototype-bridge placement hid Routine
// access behind a conditional "Browse Routines" text button shown only when no workout was active,
// which made a normal user unlikely to ever discover it. My Routines (RoutineLibraryScreens.kt —
// real Routine persistence + real Routine → Workout execution as of Slice A, sample content MIXED)
// is now visible regardless of active-workout state, alongside a real Create/Edit Routine flow and
// a Workout Summary shown after pressing the real ActiveWorkoutScreen's "Finish Workout" affordance
// (see WorkoutSummaryPrototype.kt, still prototype-only). Internal nav is a plain sealed-state
// switch, matching this codebase's existing manual-nav style (see ActiveWorkoutScreen's own
// `showingPicker` toggle) rather than introducing a navigation library for a prototype.
private sealed interface WorkoutTabDestination {
    data object Root : WorkoutTabDestination
    data object NewRoutine : WorkoutTabDestination
    data class RoutineDetail(val routine: RoutineEntity) : WorkoutTabDestination
    data class RoutineEditor(val routine: RoutineEntity) : WorkoutTabDestination
    data class Summary(val workoutId: String) : WorkoutTabDestination
}

@Composable
internal fun WorkoutTabScreen(repository: WorkoutRepository, ownerId: String, modifier: Modifier = Modifier) {
    var destination by remember { mutableStateOf<WorkoutTabDestination>(WorkoutTabDestination.Root) }

    when (val current = destination) {
        WorkoutTabDestination.Root -> Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            WorkoutRootScreen(
                repository = repository,
                ownerId = ownerId,
                onFinishWorkout = { workout -> destination = WorkoutTabDestination.Summary(workout.id) },
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            MyRoutinesSection(
                repository = repository,
                ownerId = ownerId,
                onOpenRoutine = { routine -> destination = WorkoutTabDestination.RoutineDetail(routine) },
                onCreateRoutine = { destination = WorkoutTabDestination.NewRoutine },
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
        WorkoutTabDestination.NewRoutine -> NewRoutineScreen(
            repository = repository,
            ownerId = ownerId,
            onCreated = { created -> destination = WorkoutTabDestination.RoutineEditor(created) },
            onCancel = { destination = WorkoutTabDestination.Root },
            modifier = modifier,
        )
        is WorkoutTabDestination.RoutineDetail -> RoutineDetailScreen(
            routine = current.routine,
            repository = repository,
            ownerId = ownerId,
            onStarted = { destination = WorkoutTabDestination.Root },
            onEdit = { routine -> destination = WorkoutTabDestination.RoutineEditor(routine) },
            onBack = { destination = WorkoutTabDestination.Root },
            modifier = modifier,
        )
        is WorkoutTabDestination.RoutineEditor -> RoutineEditorScreen(
            routine = current.routine,
            repository = repository,
            ownerId = ownerId,
            // Returns to Root (not back to RoutineDetail with a now-possibly-stale RoutineEntity
            // snapshot, e.g. after a rename) — My Routines re-observes the real Flow, so a rename is
            // reflected immediately without needing to thread an updated Routine back through nav.
            onDone = { destination = WorkoutTabDestination.Root },
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
