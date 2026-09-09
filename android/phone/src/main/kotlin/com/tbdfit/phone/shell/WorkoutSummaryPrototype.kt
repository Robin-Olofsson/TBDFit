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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbdfit.phone.workout.WorkoutRepository

// PROTOTYPE-ONLY completion interaction (see ActiveWorkoutScreen.onFinishWorkout's own doc comment
// and frontend-prototype-notes.md) — MIXED, not fake: the exercise/set counts shown here are read
// live from the REAL, already-implemented WorkoutRepository (observeAttachedExercises /
// observeSetsForWorkoutExercise), so the numbers are genuine. What is NOT real: pressing "Done"
// below does not call WorkoutRepository.completeWorkout — the underlying Workout row is left
// exactly as it was (still ACTIVE) when this screen closes. Whether to actually wire real
// completion is deliberately left to human evaluation of this prototype, not decided here.
@Composable
internal fun WorkoutSummaryScreen(
    workoutId: String,
    repository: WorkoutRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val attachedExercises by repository.observeAttachedExercises(workoutId).collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxWidth().padding(24.dp)) {
        Text("Workout Summary", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Prototype — this screen does not mark the workout complete. The workout remains active.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text("${attachedExercises.size} exercise(s) attached", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        for (attached in attachedExercises) {
            Text("• ${attached.name}")
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone) {
            Text("Done")
        }
    }
}
