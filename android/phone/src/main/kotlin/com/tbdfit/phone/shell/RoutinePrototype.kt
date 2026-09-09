package com.tbdfit.phone.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbdfit.phone.workout.StartWorkoutResult
import com.tbdfit.phone.workout.WorkoutRepository
import kotlinx.coroutines.launch

// PROTOTYPE-ONLY data — see frontend-prototype-notes.md. No Routine/RoutineExercise Room entity
// exists (see product-information-architecture.md §5: Routine Library/Detail are POST-MVP for
// backend, prototype-only for this first clickable pass). This list is representative sample
// content, not persisted anywhere, and resets every process launch.
//
// Deliberately composed from the REAL built-in exercise catalog (see BuiltInExerciseCatalog.kt)
// rather than made-up exercise names: this is what makes "Start Routine" below able to produce a
// genuinely real Workout (via the real, already-tested WorkoutRepository.startWorkout +
// addExercise) instead of a fabricated one — see product-information-architecture.md §"Phone
// Workout" / the review brief's section 11 instruction not to invent domain behavior to make this
// work. If a future real Routine needed exercises outside the built-in catalog, this exact
// integration would need real Exercise rows to reference — that gap is intentionally left visible,
// not papered over.
internal data class PrototypeRoutineExercise(
    val exerciseId: String,
    val name: String,
    val plannedSets: Int,
    val plannedReps: Int,
)

internal data class PrototypeRoutine(
    val id: String,
    val name: String,
    val exercises: List<PrototypeRoutineExercise>,
)

internal val PROTOTYPE_ROUTINES: List<PrototypeRoutine> = listOf(
    PrototypeRoutine(
        id = "proto-routine-push",
        name = "Push Day",
        exercises = listOf(
            PrototypeRoutineExercise("builtin_bench_press", "Bench Press", plannedSets = 4, plannedReps = 8),
            PrototypeRoutineExercise("builtin_overhead_press", "Overhead Press", plannedSets = 3, plannedReps = 10),
        ),
    ),
    PrototypeRoutine(
        id = "proto-routine-pull",
        name = "Pull Day",
        exercises = listOf(
            PrototypeRoutineExercise("builtin_deadlift", "Deadlift", plannedSets = 3, plannedReps = 5),
            PrototypeRoutineExercise("builtin_barbell_row", "Barbell Row", plannedSets = 4, plannedReps = 8),
            PrototypeRoutineExercise("builtin_pull_up", "Pull-Up", plannedSets = 3, plannedReps = 6),
        ),
    ),
    PrototypeRoutine(
        id = "proto-routine-legs",
        name = "Leg Day",
        exercises = listOf(
            PrototypeRoutineExercise("builtin_back_squat", "Back Squat", plannedSets = 5, plannedReps = 5),
        ),
    ),
)

// Starts a REAL Workout and attaches REAL WorkoutExercise rows for each planned exercise, using the
// existing production WorkoutRepository — this is the "MIXED" integration described in
// frontend-prototype-notes.md: the Routine's content is prototype data, but pressing Start produces
// a genuinely durable, ownership-scoped Workout, not a fabricated one. If a workout is already
// active for this owner, exercises are NOT attached to it (avoids silently mutating a workout the
// routine didn't start) — the caller is simply routed to the existing active workout.
internal suspend fun startPrototypeRoutine(repository: WorkoutRepository, ownerId: String, routine: PrototypeRoutine) {
    val result = repository.startWorkout(ownerId)
    if (result is StartWorkoutResult.Started) {
        for (exercise in routine.exercises) {
            repository.addExercise(result.workout.id, exercise.exerciseId)
        }
    }
    // StartWorkoutResult.AlreadyActive: intentionally left untouched — see doc comment above.
}

@Composable
internal fun RoutineLibraryScreen(onOpenRoutine: (PrototypeRoutine) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(24.dp)) {
        Text("Routines", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Prototype content — not yet backed by a real Routine model.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn {
            items(PROTOTYPE_ROUTINES, key = { it.id }) { routine ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onOpenRoutine(routine) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(routine.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${routine.exercises.size} exercises",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RoutineDetailScreen(
    routine: PrototypeRoutine,
    repository: WorkoutRepository,
    ownerId: String,
    onStarted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isStarting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth().padding(24.dp)) {
        TextButton(onClick = onBack) { Text("< Routines") }
        Spacer(Modifier.height(8.dp))
        Text(routine.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        for (exercise in routine.exercises) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(exercise.name, modifier = Modifier.fillMaxWidth().weight(1f))
                Text(
                    "${exercise.plannedSets} × ${exercise.plannedReps}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            enabled = !isStarting,
            onClick = {
                isStarting = true
                scope.launch {
                    startPrototypeRoutine(repository, ownerId, routine)
                    isStarting = false
                    onStarted()
                }
            },
        ) {
            Text("Start Routine")
        }
        if (isStarting) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator()
        }
    }
}
