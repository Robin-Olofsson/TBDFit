package com.tbdfit.phone.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbdfit.phone.workout.RootUiState
import com.tbdfit.phone.workout.WorkoutRepository
import com.tbdfit.phone.workout.deriveRootUiState
import kotlinx.coroutines.launch

// The "Home" destination of the corrected Phone IA. Given a DISTINCT job from "Workout" per
// product-information-architecture.md §1's adversarial-review correction: the original 4-tab
// recommendation gave Home identical content to Workout's own root screen ("active workout or a
// start prompt"), which would have made the two tabs indistinguishable at launch. This dashboard
// answers "what should I do now / what did I recently do" instead — active-workout status uses REAL
// production-backed state (WorkoutRepository.observeActiveWorkout); "recent activity" is
// PROTOTYPE-ONLY representative content (see HistoryPrototype.kt — no real completed-workout query
// has anything to show yet).
@Composable
internal fun HomeTabScreen(
    repository: WorkoutRepository,
    ownerId: String,
    onGoToWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state by produceState<RootUiState>(initialValue = RootUiState.Loading, repository, ownerId) {
        repository.observeActiveWorkout(ownerId).collect { workout -> value = deriveRootUiState(workout) }
    }

    Column(modifier = modifier.fillMaxWidth().padding(24.dp)) {
        Text("Home", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        when (val current = state) {
            RootUiState.Loading -> CircularProgressIndicator()
            is RootUiState.Active -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Workout in progress", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onGoToWorkout) { Text("Resume Workout") }
                }
            }
            RootUiState.NoActiveWorkout -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("No active workout", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        scope.launch {
                            repository.startWorkout(ownerId)
                            onGoToWorkout()
                        }
                    }) { Text("Start Workout") }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Recent activity",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Prototype content — no real completed-history query has anything to show yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        for (entry in PROTOTYPE_HISTORY.take(2)) {
            Text("• ${entry.title} — ${entry.dateLabel}", modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}
