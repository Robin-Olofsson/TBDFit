package com.tbdfit.phone.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tbdfit.phone.workout.ExercisePickerScreen
import com.tbdfit.phone.workout.RoutineAttachedExercise
import com.tbdfit.phone.workout.RoutineEntity
import com.tbdfit.phone.workout.RoutinePlannedSetEntity
import com.tbdfit.phone.workout.SetFieldInput
import com.tbdfit.phone.workout.StartWorkoutResult
import com.tbdfit.phone.workout.WorkoutRepository
import com.tbdfit.phone.workout.parseRepsInput
import com.tbdfit.phone.workout.parseWeightInput
import kotlinx.coroutines.launch

// Slice A of docs/architecture/program-routine-first-slice-design.md — see
// docs/product/frontend-prototype-notes.md for the current REAL/MIXED/PROTOTYPE classification.
// Routine persistence and Routine → Workout execution below are PRODUCTION-BACKED (real
// RoutineDao/RoutineExerciseDao/RoutinePlannedSetDao, real WorkoutRepository.startRoutine). The
// sample routine CONTENT seeded below is MIXED: it is real, persisted, user-editable/deletable
// RoutineEntity data — not a hardcoded Kotlin constant rendered directly the way it was before this
// slice — but its starting values are still representative sample content chosen for continuity
// with the earlier prototype, not something a real user authored.
//
// Deliberately composed from the REAL built-in exercise catalog (see BuiltInExerciseCatalog.kt) —
// the same choice the pre-Slice-A prototype made, preserved here.
private data class SampleRoutineExercise(val exerciseId: String, val plannedSets: Int, val plannedReps: Int)
private data class SampleRoutine(val name: String, val exercises: List<SampleRoutineExercise>)

private val SAMPLE_ROUTINES: List<SampleRoutine> = listOf(
    SampleRoutine(
        name = "Push Day",
        exercises = listOf(
            SampleRoutineExercise("builtin_bench_press", plannedSets = 4, plannedReps = 8),
            SampleRoutineExercise("builtin_overhead_press", plannedSets = 3, plannedReps = 10),
        ),
    ),
    SampleRoutine(
        name = "Pull Day",
        exercises = listOf(
            SampleRoutineExercise("builtin_deadlift", plannedSets = 3, plannedReps = 5),
            SampleRoutineExercise("builtin_barbell_row", plannedSets = 4, plannedReps = 8),
            SampleRoutineExercise("builtin_pull_up", plannedSets = 3, plannedReps = 6),
        ),
    ),
    SampleRoutine(
        name = "Leg Day",
        exercises = listOf(
            SampleRoutineExercise("builtin_back_squat", plannedSets = 5, plannedReps = 5),
        ),
    ),
)

// One-time, idempotent, per-owner bootstrap — see SampleRoutineSeedMarker's own doc comment for why
// this is not simply "seed if the owner currently has zero routines" (that would silently resurrect
// routines the user deliberately deleted). Safe to call on every composition that shows My Routines.
internal suspend fun seedSampleRoutinesIfAbsent(repository: WorkoutRepository, marker: SampleRoutineSeedMarker, ownerId: String) {
    if (marker.hasSeeded(ownerId)) return
    for (sample in SAMPLE_ROUTINES) {
        val routine = repository.createRoutine(ownerId, sample.name)
        for (exercise in sample.exercises) {
            val routineExercise = repository.addExerciseToRoutine(routine.id, exercise.exerciseId)
            repeat(exercise.plannedSets) {
                repository.addPlannedSetToRoutineExercise(routineExercise.id, plannedReps = exercise.plannedReps, plannedWeight = null)
            }
        }
    }
    marker.markSeeded(ownerId)
}

// The "My Routines" hub — Routine UX completion slice. This is deliberately an always-visible
// section of the Workout tab's root screen (see WorkoutTabScreen), not a destination hidden behind
// a conditional text button: the earlier prototype-bridge placement made Routines effectively
// undiscoverable (a small "Browse Routines" button shown only when no workout was active). My
// Routines is now visible regardless of active-workout state — starting a Routine while one is
// already active simply routes back to the existing active workout untouched (see
// WorkoutRepository.startRoutine's own doc comment), so there is no harm in always offering it.
@Composable
internal fun MyRoutinesSection(
    repository: WorkoutRepository,
    ownerId: String,
    onOpenRoutine: (RoutineEntity) -> Unit,
    onCreateRoutine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(ownerId) {
        seedSampleRoutinesIfAbsent(repository, SampleRoutineSeedMarker(context), ownerId)
    }
    val routines by repository.observeRoutinesFor(ownerId).collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxWidth()) {
        Text("My Routines", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (routines.isEmpty()) {
            Text(
                "No routines yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        } else {
            for (routine in routines) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpenRoutine(routine) },
                ) {
                    Text(routine.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onCreateRoutine) {
            Text("+ Create Routine")
        }
    }
}

// A single name prompt, then straight into RoutineEditorScreen — the actual "create your own
// routine" action the pre-completion UI never offered despite the repository fully supporting it.
@Composable
internal fun NewRoutineScreen(
    repository: WorkoutRepository,
    ownerId: String,
    onCreated: (RoutineEntity) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth().padding(24.dp)) {
        Text("New Routine", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Routine name") },
            singleLine = true,
            enabled = !isCreating,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !isCreating && name.isNotBlank(),
            onClick = {
                val trimmed = name
                isCreating = true
                scope.launch {
                    val created = repository.createRoutine(ownerId, trimmed)
                    isCreating = false
                    onCreated(created)
                }
            },
        ) {
            Text("Create")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
        if (isCreating) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator()
        }
    }
}

// Real Routine Detail — upgraded from a read-only "{count} × {first set's reps}" summary row to a
// genuine per-set list, plus Start Workout / Edit Routine actions (the two actions the earlier
// prototype-bridge screen never offered beyond Start).
@Composable
internal fun RoutineDetailScreen(
    routine: RoutineEntity,
    repository: WorkoutRepository,
    ownerId: String,
    onStarted: () -> Unit,
    onEdit: (RoutineEntity) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isStarting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val attachedExercises by repository.observeRoutineExercises(routine.id).collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack) { Text("< Routines") }
        Spacer(Modifier.height(8.dp))
        Text(routine.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        if (attachedExercises.isEmpty()) {
            Text(
                "No exercises yet — tap Edit Routine to add some.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            for (attached in attachedExercises) {
                RoutinePlannedSetsList(routineExerciseId = attached.routineExerciseId, name = attached.name, repository = repository)
                Spacer(Modifier.height(20.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                enabled = !isStarting,
                onClick = {
                    isStarting = true
                    scope.launch {
                        // Real Routine → Workout start (WorkoutRepository.startRoutine) — if a
                        // workout is already active for this owner, planned content is not attached
                        // to it; the caller is simply routed back to the existing active workout,
                        // untouched.
                        when (repository.startRoutine(ownerId, routine.id)) {
                            is StartWorkoutResult.Started, is StartWorkoutResult.AlreadyActive -> Unit
                        }
                        isStarting = false
                        onStarted()
                    }
                },
            ) {
                Text("Start Workout")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { onEdit(routine) }) {
                Text("Edit Routine")
            }
        }
        if (isStarting) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator()
        }
    }
}

// One exercise's planned sets, each individually visible (target reps/weight) — not a collapsed
// summary. Independently observes its own planned sets, the same per-row-observation pattern
// ActiveWorkoutScreen's ExerciseSetsSection already uses for executed sets.
@Composable
private fun RoutinePlannedSetsList(routineExerciseId: String, name: String, repository: WorkoutRepository, modifier: Modifier = Modifier) {
    val plannedSets by repository.observePlannedSets(routineExerciseId).collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        if (plannedSets.isEmpty()) {
            Text(
                "No planned sets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            plannedSets.forEachIndexed { index, set ->
                val weightLabel = set.plannedWeight?.let { "$it kg" } ?: "bodyweight"
                val repsLabel = set.plannedReps?.toString() ?: "-"
                Text(
                    "Set ${index + 1}: $repsLabel reps @ $weightLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// The real Create/Edit Routine UI — the actual missing central action: a user can rename a
// Routine, add/remove exercises (reusing the existing real ExercisePickerScreen, not a second
// picker), and add/remove/edit each planned set's target reps/weight. Reuses parseWeightInput/
// parseRepsInput (SetInputParsing.kt) — same "blank is intentional null, malformed is never
// silently coerced to zero" contract already proven for ActiveWorkoutScreen's actual-value fields,
// applied here to planned targets instead.
@Composable
internal fun RoutineEditorScreen(
    routine: RoutineEntity,
    repository: WorkoutRepository,
    ownerId: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showingPicker by remember { mutableStateOf(false) }
    var nameText by remember(routine.id) { mutableStateOf(routine.name) }
    val scope = rememberCoroutineScope()

    if (showingPicker) {
        ExercisePickerScreen(
            repository = repository,
            ownerId = ownerId,
            onExerciseChosen = { exercise ->
                scope.launch { repository.addExerciseToRoutine(routine.id, exercise.id) }
                showingPicker = false
            },
            onCancel = { showingPicker = false },
            modifier = modifier,
        )
    } else {
        val attachedExercises by repository.observeRoutineExercises(routine.id).collectAsState(initial = emptyList())

        Column(modifier = modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState())) {
            Text("Edit Routine", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = nameText,
                onValueChange = { newText ->
                    nameText = newText
                    if (newText.isNotBlank()) {
                        scope.launch { repository.renameRoutine(routine.id, newText) }
                    }
                },
                label = { Text("Routine name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            if (attachedExercises.isEmpty()) {
                Text(
                    "No exercises yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                for (attached in attachedExercises) {
                    RoutineExerciseEditorSection(attached = attached, repository = repository)
                    Spacer(Modifier.height(20.dp))
                }
            }
            Button(onClick = { showingPicker = true }) {
                Text("+ Add Exercise")
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDone) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun RoutineExerciseEditorSection(attached: RoutineAttachedExercise, repository: WorkoutRepository, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val plannedSets by repository.observePlannedSets(attached.routineExerciseId).collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(attached.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { repository.removeExerciseFromRoutine(attached.routineExerciseId) } }) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove ${attached.name}")
            }
        }
        Spacer(Modifier.height(4.dp))
        plannedSets.forEachIndexed { index, set ->
            RoutinePlannedSetEditorRow(displayNumber = index + 1, set = set, repository = repository)
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = {
            // New planned sets always begin with no target (both null) — position assigned
            // atomically by the existing appendPlannedSet transaction, never computed here.
            scope.launch { repository.addPlannedSetToRoutineExercise(attached.routineExerciseId, plannedReps = null, plannedWeight = null) }
        }) {
            Text("+ Add Set")
        }
    }
}

// Local text-field state exists only for what is currently displayed while typing (remember keyed
// on set.id) — not the source of truth for what gets saved: every keystroke attempts to parse, and
// only a successfully parsed value (including a deliberate blank, parsed as null) is ever
// persisted, exactly matching ActiveWorkoutScreen's SetRow contract for actual values.
@Composable
private fun RoutinePlannedSetEditorRow(displayNumber: Int, set: RoutinePlannedSetEntity, repository: WorkoutRepository, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var weightText by remember(set.id) { mutableStateOf(set.plannedWeight?.toString() ?: "") }
    var repsText by remember(set.id) { mutableStateOf(set.plannedReps?.toString() ?: "") }

    Row(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$displayNumber", modifier = Modifier.width(24.dp))
        OutlinedTextField(
            value = weightText,
            onValueChange = { newText ->
                weightText = newText
                when (val parsed = parseWeightInput(newText)) {
                    is SetFieldInput.Persist -> scope.launch { repository.updatePlannedSetWeight(set.id, parsed.value) }
                    SetFieldInput.Invalid -> Unit
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
                when (val parsed = parseRepsInput(newText)) {
                    is SetFieldInput.Persist -> scope.launch { repository.updatePlannedSetReps(set.id, parsed.value) }
                    SetFieldInput.Invalid -> Unit
                }
            },
            label = { Text("reps") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(90.dp),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { scope.launch { repository.removePlannedSet(set.id) } }) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete planned set $displayNumber")
        }
    }
}
