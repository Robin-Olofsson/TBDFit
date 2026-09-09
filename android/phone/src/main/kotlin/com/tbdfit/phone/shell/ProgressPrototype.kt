package com.tbdfit.phone.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// PROTOTYPE-ONLY, layout/IA only — no real analytics backend (see
// multi-client-product-vision.md: analytics is OPEN, not a committed capability). Nested under You
// per the corrected phone IA (§1/§2): Progress is deliberately NOT a top-level tab — see that
// document's adversarial-review correction for why the original 4-tab version was wrong to promote
// it without a screen behind it.
private data class PrototypeProgressStat(val label: String, val value: String)

private val PROTOTYPE_STATS = listOf(
    PrototypeProgressStat("Workouts this week", "3"),
    PrototypeProgressStat("Estimated Bench Press 1RM", "~95 kg"),
    PrototypeProgressStat("Estimated Back Squat 1RM", "~125 kg"),
)

@Composable
internal fun ProgressScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(24.dp)) {
        androidx.compose.material3.TextButton(onClick = onBack) { Text("< You") }
        Spacer(Modifier.height(8.dp))
        Text("Progress", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Prototype layout only — no analytics engine exists yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        for (stat in PROTOTYPE_STATS) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stat.label, modifier = Modifier.fillMaxWidth().weight(1f))
                    Text(stat.value, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
