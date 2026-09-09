package com.tbdfit.phone.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Exercise.id is identity, not its display name (see the strength-workout design doc's Exercise
// Identity section) — tapping a row here passes the whole ExerciseEntity (its stable id) to
// onExerciseChosen, never just a name string. normalizedName is not consulted here at all: it is a
// search/hint field only, never a uniqueness gate, so similar or identical names are shown exactly
// as they exist, with no de-duplication or merging.
//
// Minimal on purpose: no search filtering, no categories, no equipment metadata — this slice only
// needs enough to pick an existing Exercise or create a new one.
@Composable
fun ExercisePickerScreen(
    repository: WorkoutRepository,
    ownerId: String,
    onExerciseChosen: (ExerciseEntity) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercises by repository.observeAllExercises(ownerId).collectAsState(initial = emptyList())
    var newExerciseName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth().padding(24.dp)) {
        Text("Add Exercise", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
            items(exercises, key = { it.id }) { exercise ->
                Text(
                    exercise.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExerciseChosen(exercise) }
                        .padding(vertical = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = newExerciseName,
            onValueChange = { newExerciseName = it },
            label = { Text("New exercise name") },
            singleLine = true,
            enabled = !isCreating,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = !isCreating && newExerciseName.isNotBlank(),
            onClick = {
                val name = newExerciseName
                isCreating = true
                scope.launch {
                    val created = repository.createCustomExercise(ownerId, name)
                    isCreating = false
                    newExerciseName = ""
                    onExerciseChosen(created)
                }
            },
        ) {
            Text("Create new exercise")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}
