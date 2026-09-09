package com.tbdfit.phone.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tbdfit.phone.localstorage.LocalRecordDao
import com.tbdfit.phone.profile.Profile
import com.tbdfit.phone.sync.LocalRecordSyncCoordinator
import com.tbdfit.phone.wearreplication.WearReplicaDao
import com.tbdfit.phone.workout.WorkoutRepository

// The corrected Phone information architecture's top-level shell — see
// docs/product/product-information-architecture.md §1/§2. `Home | Workout | You`, revised from an
// earlier 4-tab `Home | Workout | Progress | You` version after adversarial review found Progress
// had no MVP screen behind it (now nested under You instead — see ProgressPrototype.kt).
//
// This is the ONE new composable AuthenticatedScreen (MainActivity.kt) delegates to once a profile
// is Complete — it does not replace or fork any real production logic, only reorganizes how the
// existing real WorkoutRootScreen/ProfileSummaryHeader/DeveloperSyncProofScreen are presented,
// alongside new PROTOTYPE-ONLY screens (Home dashboard content, Routines, History, Progress,
// Settings body) — see docs/product/frontend-prototype-notes.md for the full
// production-backed/prototype-only/mixed classification.
private enum class ShellTab(val label: String) {
    Home("Home"),
    Workout("Workout"),
    You("You"),
}

@Composable
internal fun AppShell(
    workoutRepository: WorkoutRepository,
    ownerId: String,
    profile: Profile,
    email: String?,
    onLogout: () -> Unit,
    dao: LocalRecordDao,
    syncCoordinator: LocalRecordSyncCoordinator,
    wearReplicaDao: WearReplicaDao,
) {
    var selectedTab by remember { mutableStateOf(ShellTab.Home) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == ShellTab.Home,
                    onClick = { selectedTab = ShellTab.Home },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(ShellTab.Home.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == ShellTab.Workout,
                    onClick = { selectedTab = ShellTab.Workout },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(ShellTab.Workout.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == ShellTab.You,
                    onClick = { selectedTab = ShellTab.You },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text(ShellTab.You.label) },
                )
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (selectedTab) {
            ShellTab.Home -> HomeTabScreen(
                repository = workoutRepository,
                ownerId = ownerId,
                onGoToWorkout = { selectedTab = ShellTab.Workout },
                modifier = contentModifier,
            )
            ShellTab.Workout -> WorkoutTabScreen(
                repository = workoutRepository,
                ownerId = ownerId,
                modifier = contentModifier,
            )
            ShellTab.You -> YouTabScreen(
                profile = profile,
                email = email,
                onLogout = onLogout,
                dao = dao,
                syncCoordinator = syncCoordinator,
                wearReplicaDao = wearReplicaDao,
                modifier = contentModifier,
            )
        }
    }
}
