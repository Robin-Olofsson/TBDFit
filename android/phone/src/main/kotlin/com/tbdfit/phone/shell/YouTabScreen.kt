package com.tbdfit.phone.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbdfit.phone.localstorage.LocalRecordDao
import com.tbdfit.phone.profile.Profile
import com.tbdfit.phone.profile.ProfileSummaryHeader
import com.tbdfit.phone.sync.LocalRecordSyncCoordinator
import com.tbdfit.phone.wearreplication.WearReplicaDao

// The "You" destination of the corrected Phone IA — Profile / History / Progress / Settings grouped
// together, per product-information-architecture.md §2 ("both read from the same completed-Workout
// data" is why History and Progress live here rather than as separate top-level tabs). This is the
// screen the adversarial review needed to judge for "catch-all" risk — kept to exactly these items
// for the first clickable prototype, no Followers/public-visibility content (both explicitly
// POST-MVP per that document).
//
// ProfileSummaryHeader (including Logout) is the REAL, unmodified, production-backed component —
// nothing about account/session handling changes here. Everything below it (History, Progress,
// Settings body) is PROTOTYPE-ONLY except the "Developer / Sync Proof" entry, which preserves
// reachability of this codebase's pre-existing real local-persistence/Supabase-sync/Wear-replica
// proof screen (previously always-visible in MainActivity; the new product IA has no place for it
// in primary navigation, but it must not be deleted or made unreachable — see MainActivity.kt).
private sealed interface YouTabDestination {
    data object Root : YouTabDestination
    data object History : YouTabDestination
    data class HistoryDetail(val entry: PrototypeHistoryEntry) : YouTabDestination
    data object Progress : YouTabDestination
    data object Settings : YouTabDestination
    data object DeveloperSyncProof : YouTabDestination
}

@Composable
internal fun YouTabScreen(
    profile: Profile,
    email: String?,
    onLogout: () -> Unit,
    dao: LocalRecordDao,
    syncCoordinator: LocalRecordSyncCoordinator,
    wearReplicaDao: WearReplicaDao,
    modifier: Modifier = Modifier,
) {
    var destination by remember { mutableStateOf<YouTabDestination>(YouTabDestination.Root) }

    when (val current = destination) {
        YouTabDestination.Root -> Column(modifier = modifier.fillMaxWidth()) {
            ProfileSummaryHeader(profile = profile, email = email, onLogout = onLogout)
            HorizontalDivider()
            YouRow("History") { destination = YouTabDestination.History }
            YouRow("Progress") { destination = YouTabDestination.Progress }
            YouRow("Followers / Following (coming soon)", enabled = false) {}
            YouRow("Settings") { destination = YouTabDestination.Settings }
        }
        YouTabDestination.History -> HistoryListScreen(
            onOpenEntry = { entry -> destination = YouTabDestination.HistoryDetail(entry) },
            modifier = modifier,
        )
        is YouTabDestination.HistoryDetail -> WorkoutDetailScreen(
            entry = current.entry,
            onBack = { destination = YouTabDestination.History },
            modifier = modifier,
        )
        YouTabDestination.Progress -> ProgressScreen(
            onBack = { destination = YouTabDestination.Root },
            modifier = modifier,
        )
        YouTabDestination.Settings -> SettingsScreen(
            onOpenDeveloperSyncProof = { destination = YouTabDestination.DeveloperSyncProof },
            onBack = { destination = YouTabDestination.Root },
            modifier = modifier,
        )
        YouTabDestination.DeveloperSyncProof -> Column(modifier = modifier.fillMaxWidth()) {
            TextButton(onClick = { destination = YouTabDestination.Settings }) { Text("< Settings") }
            DeveloperSyncProofScreen(dao = dao, syncCoordinator = syncCoordinator, wearReplicaDao = wearReplicaDao)
        }
    }
}

@Composable
private fun YouRow(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .let { if (enabled) it.clickable(onClick = onClick) else it },
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                label,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsScreen(onOpenDeveloperSyncProof: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(24.dp)) {
        TextButton(onClick = onBack) { Text("< You") }
        Spacer(Modifier.height(8.dp))
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Minimal prototype — units/notifications/etc. are not yet real settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        YouRow("Developer / Sync Proof (technical proof, unrelated to product UI)") {
            onOpenDeveloperSyncProof()
        }
    }
}
