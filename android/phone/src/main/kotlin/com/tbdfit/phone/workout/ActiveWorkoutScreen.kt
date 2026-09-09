package com.tbdfit.phone.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tbdfit.phone.BuildConfig
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// `workout` always comes from WorkoutRootScreen's Room-backed Flow — never from an in-memory value
// created directly by this screen. Same for exercises/sets below: every list here is Room-backed
// (repository.observeAttachedExercises / observeSetsForWorkoutExercise), so recreation restores
// the same state the same way Slice 2 already proved for the workout itself (see the design doc's
// "Process death / restore" section).
// `onFinishWorkout` is an optional, additive UI hook (default null preserves every existing call
// site's exact prior behavior) — see the frontend-prototype-notes.md doc for why this stays
// deliberately PROTOTYPE-ONLY: it is a UI affordance for the shell to show an intended completion
// interaction, and does NOT call WorkoutRepository.completeWorkout/WorkoutDao.completeIfActive
// itself. Those already exist and are unit-tested, but wiring them into the UI is a real product
// decision this prototype defers to human evaluation, not something this button decides on its own.
@Composable
fun ActiveWorkoutScreen(
    workout: WorkoutEntity,
    repository: WorkoutRepository,
    ownerId: String,
    modifier: Modifier = Modifier,
    onFinishWorkout: ((WorkoutEntity) -> Unit)? = null,
) {
    var showingPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showingPicker) {
        ExercisePickerScreen(
            repository = repository,
            ownerId = ownerId,
            onExerciseChosen = { exercise ->
                // Uses WorkoutRepository.addExercise, which delegates to the existing
                // transactional appendExercise — position is assigned atomically, never computed
                // here. History references the chosen exercise's stable id only; its name is never
                // copied into workout_exercises (see AttachedExercise's own doc comment).
                scope.launch { repository.addExercise(workout.id, exercise.id) }
                showingPicker = false
            },
            onCancel = { showingPicker = false },
            modifier = modifier,
        )
    } else {
        val attachedExercises by repository.observeAttachedExercises(workout.id).collectAsState(initial = emptyList())

        Column(modifier = modifier.fillMaxWidth().padding(24.dp)) {
            Text("Active Workout", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Text("Started: ${DateFormat.getDateTimeInstance().format(Date(workout.startedAt))}")
            // Debug-only, clearly temporary: exposing the raw id is a development aid for proving
            // durable identity across restarts, not polished product UI.
            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Workout ID: ${workout.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
            if (attachedExercises.isEmpty()) {
                Text(
                    "Exercises: none yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                for (attached in attachedExercises) {
                    ExerciseSetsSection(attached = attached, repository = repository)
                    Spacer(Modifier.height(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { showingPicker = true }) {
                Text("Add Exercise")
            }
            if (onFinishWorkout != null) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onFinishWorkout(workout) }) {
                    Text("Finish Workout")
                }
            }
        }
    }
}

// One attached exercise's set list. `displayNumber` (1, 2, 3, ...) is derived purely from list
// position — never the raw stored WorkoutSet.position column — so deleting a set never needs to
// renumber anything: remaining rows keep their original position values (sparse, ordered), and the
// UI simply re-derives sequential display numbers from whatever the ordered list currently
// contains. This is the smallest correct ordering behavior — see WorkoutSetDao for why no
// compaction transaction was added.
@Composable
private fun ExerciseSetsSection(attached: AttachedExercise, repository: WorkoutRepository, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val sets by repository.observeSetsForWorkoutExercise(attached.workoutExerciseId).collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxWidth()) {
        Text(attached.name, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        sets.forEachIndexed { index, set ->
            SetRow(displayNumber = index + 1, set = set, repository = repository)
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = {
            // New sets always begin incomplete (weight/reps null) — see this slice's incomplete-set
            // semantics — and get their position from the existing transactional appendSet, never
            // computed here.
            scope.launch { repository.addSet(attached.workoutExerciseId, weight = null, reps = null) }
        }) {
            Text("Add Set")
        }
    }
}

// Local text-field state exists only for what is currently displayed while typing (remember keyed
// on set.id, so switching to a different row re-seeds correctly). It is NOT the source of truth
// for what gets saved: every keystroke attempts to parse (see SetInputParsing.kt), and only a
// successfully parsed value — including a deliberate blank input, parsed as null — is ever
// persisted. A malformed in-progress value (e.g. "12." or "abc") stays visible exactly as typed but
// is never written to Room and never silently coerced to zero.
@Composable
private fun SetRow(displayNumber: Int, set: WorkoutSetEntity, repository: WorkoutRepository, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var weightText by remember(set.id) { mutableStateOf(set.weight?.toString() ?: "") }
    var repsText by remember(set.id) { mutableStateOf(set.reps?.toString() ?: "") }
    var completionRejectedMessage by remember(set.id) { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Slice A execution target snapshot (program-routine-first-slice-design.md) — target*
        // is frozen at START and never re-read from the source Routine, so this label is always
        // exactly what this Workout's own row already stores; it never re-queries planning data.
        // Null for a plain manually-added set (no source plan) — nothing shown in that case, no
        // layout change from before this slice.
        if (set.targetReps != null || set.targetWeight != null) {
            val weightLabel = set.targetWeight?.let { "$it kg" } ?: "bodyweight"
            Text(
                "Target: $weightLabel × ${set.targetReps ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$displayNumber", modifier = Modifier.width(24.dp))
            OutlinedTextField(
                value = weightText,
                onValueChange = { newText ->
                    weightText = newText
                    when (val parsed = parseWeightInput(newText)) {
                        is SetFieldInput.Persist -> scope.launch { repository.updateSetWeight(set.id, parsed.value) }
                        SetFieldInput.Invalid -> Unit // do not persist; keep showing what was typed
                    }
                },
                label = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(90.dp),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = repsText,
                onValueChange = { newText ->
                    repsText = newText
                    completionRejectedMessage = null
                    when (val parsed = parseRepsInput(newText)) {
                        is SetFieldInput.Persist -> scope.launch { repository.updateSetReps(set.id, parsed.value) }
                        SetFieldInput.Invalid -> Unit
                    }
                },
                label = { Text("reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(90.dp),
            )
            Spacer(Modifier.width(8.dp))
            // A checkbox inherently implies a two-way toggle — see WorkoutSetDao.markIncomplete's
            // own doc comment for why un-completing is supported here rather than left one-way.
            Checkbox(
                checked = set.isCompleted,
                onCheckedChange = { checked ->
                    scope.launch {
                        if (checked) {
                            when (repository.completeSet(set.id)) {
                                CompleteSetResult.Completed -> completionRejectedMessage = null
                                CompleteSetResult.RejectedMissingReps ->
                                    completionRejectedMessage = "Enter reps before marking this set complete."
                                CompleteSetResult.NotFound -> Unit
                            }
                        } else {
                            repository.uncompleteSet(set.id)
                        }
                    }
                },
            )
            IconButton(onClick = { scope.launch { repository.deleteSet(set.id) } }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete set $displayNumber")
            }
        }
        completionRejectedMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
