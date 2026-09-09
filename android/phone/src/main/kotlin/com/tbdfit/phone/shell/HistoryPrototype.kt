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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// PROTOTYPE-ONLY — see frontend-prototype-notes.md. WorkoutRepository.observeCompletedWorkouts()
// IS real and already wired to Room (WorkoutDao.observeCompletedWorkouts), but since no UI anywhere
// in the app currently calls WorkoutRepository.completeWorkout(), that query will only ever return
// an empty list on a real device today — there is no real completed-history product flow to render
// yet (see product-information-architecture.md Journey C's own gap note). Representative sample
// content is used instead, per the review brief's explicit permission to do so for History
// specifically until that flow exists.
internal data class PrototypeHistoryEntry(
    val id: String,
    val title: String,
    val dateLabel: String,
    val summary: String,
    val exerciseNames: List<String>,
)

internal val PROTOTYPE_HISTORY: List<PrototypeHistoryEntry> = listOf(
    PrototypeHistoryEntry(
        id = "proto-history-1",
        title = "Push Day",
        dateLabel = "Yesterday",
        summary = "2 exercises · 7 sets",
        exerciseNames = listOf("Bench Press — 4×8 @ 80kg", "Overhead Press — 3×10 @ 40kg"),
    ),
    PrototypeHistoryEntry(
        id = "proto-history-2",
        title = "Pull Day",
        dateLabel = "3 days ago",
        summary = "3 exercises · 10 sets",
        exerciseNames = listOf("Deadlift — 3×5 @ 140kg", "Barbell Row — 4×8 @ 60kg", "Pull-Up — 3×6 (bodyweight)"),
    ),
    PrototypeHistoryEntry(
        id = "proto-history-3",
        title = "Leg Day",
        dateLabel = "1 week ago",
        summary = "1 exercise · 5 sets",
        exerciseNames = listOf("Back Squat — 5×5 @ 100kg"),
    ),
)

@Composable
internal fun HistoryListScreen(onOpenEntry: (PrototypeHistoryEntry) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(24.dp)) {
        Text("History", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Prototype content — no completed-workout UI exists yet to produce real history.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (PROTOTYPE_HISTORY.isEmpty()) {
            Text("No workouts yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn {
                items(PROTOTYPE_HISTORY, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onOpenEntry(entry) },
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                Text(entry.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(entry.dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkoutDetailScreen(entry: PrototypeHistoryEntry, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(24.dp)) {
        TextButton(onClick = onBack) { Text("< History") }
        Spacer(Modifier.height(8.dp))
        Text(entry.title, style = MaterialTheme.typography.headlineSmall)
        Text(entry.dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        for (name in entry.exerciseNames) {
            Text(name, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}
