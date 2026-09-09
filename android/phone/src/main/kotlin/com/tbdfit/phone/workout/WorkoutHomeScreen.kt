package com.tbdfit.phone.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Shown only once WorkoutRootScreen has confirmed via Room that no ACTIVE workout currently
// exists. Tapping Start calls WorkoutRepository.startWorkout() — the sole supported start path
// (see WorkoutDao.startWorkoutIfNoneActive) — this screen never performs a raw insert itself.
//
// If two rapid taps (or two separate UI entry points) race, the data layer decides, not this UI:
// both calls go through the same atomic check-then-insert, so at most one can ever actually be
// StartWorkoutResult.Started (see WorkoutDaoTest's concurrency test for the proof). Disabling the
// button while a call is in flight below is a courtesy against accidental double-taps, not the
// actual correctness mechanism — correctness holds even if that were removed.
@Composable
fun WorkoutHomeScreen(repository: WorkoutRepository, ownerId: String, modifier: Modifier = Modifier) {
    var isStarting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No active workout", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        Button(
            enabled = !isStarting,
            onClick = {
                isStarting = true
                scope.launch {
                    // Both outcomes are legitimate, non-error results — WorkoutRootScreen's own
                    // Flow observation is what actually routes to ActiveWorkoutScreen once the
                    // row exists, regardless of which of these two this call returns.
                    when (repository.startWorkout(ownerId)) {
                        is StartWorkoutResult.Started -> Unit
                        is StartWorkoutResult.AlreadyActive -> Unit
                    }
                    isStarting = false
                }
            },
        ) {
            Text("Start Workout")
        }
        if (isStarting) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator()
        }
    }
}
