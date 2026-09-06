package com.tbdfit.phone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.tbdfit.phone.auth.AuthGateway
import com.tbdfit.phone.auth.AuthSession
import com.tbdfit.phone.auth.AuthState
import com.tbdfit.phone.auth.GoogleIdTokenProvider
import com.tbdfit.phone.auth.SessionUnavailableScreen
import com.tbdfit.phone.auth.SignedOutScreen
import com.tbdfit.phone.auth.performLogout
import com.tbdfit.phone.backend.auth.SupabaseAuthGateway
import com.tbdfit.phone.backend.localrecords.SupabaseLocalRecordRemoteStore
import com.tbdfit.phone.backend.profile.SupabaseProfileGateway
import com.tbdfit.phone.localstorage.AppDatabase
import com.tbdfit.phone.localstorage.LocalRecordDao
import com.tbdfit.phone.localstorage.LocalRecordEntity
import com.tbdfit.phone.profile.ProfileCompletionScreen
import com.tbdfit.phone.profile.ProfileGateway
import com.tbdfit.phone.profile.ProfileState
import com.tbdfit.phone.profile.ProfileSummaryHeader
import com.tbdfit.phone.profile.ProfileUnavailableScreen
import com.tbdfit.phone.profile.loadProfileState
import com.tbdfit.phone.sync.LocalRecordSyncCoordinator
import com.tbdfit.phone.ui.theme.TbdfitTheme
import com.tbdfit.phone.wearreplication.WearReplicaDao
import kotlinx.coroutines.launch
import java.util.UUID

// Foundation-only entry point; no workout feature exists yet. This screen exists only to prove the
// local-persistence, local-to-Supabase sync, Wear-replica-receiving, and phone-authentication
// slices, not as product UI. Auth gates the technical-proof screen below purely as the smallest
// way to demonstrate the auth slice end to end — this is NOT a product rule that workouts require
// login (see the phone-authentication report's open decisions).
class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var syncCoordinator: LocalRecordSyncCoordinator
    private lateinit var authGateway: AuthGateway
    private lateinit var profileGateway: ProfileGateway
    private lateinit var googleIdTokenProvider: GoogleIdTokenProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AppDatabase.build(applicationContext)
        syncCoordinator = LocalRecordSyncCoordinator(
            dao = database.localRecordDao(),
            remoteStore = SupabaseLocalRecordRemoteStore(),
        )
        authGateway = SupabaseAuthGateway()
        profileGateway = SupabaseProfileGateway()
        googleIdTokenProvider = GoogleIdTokenProvider(BuildConfig.GOOGLE_WEB_CLIENT_ID)

        // Handles the email-confirmation deep link (tbdfit://auth-callback) if this launch came
        // from one; a no-op for a normal launcher start. Delegated to AuthGateway rather than
        // called on Supabase directly — MainActivity must not import Supabase Auth SDK types or
        // SupabaseClientProvider itself (see AuthGateway.handleAuthDeepLink).
        authGateway.handleAuthDeepLink(intent)

        setContent {
            TbdfitTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authState by authGateway.state.collectAsState(initial = AuthState.Unknown)
                    when (val state = authState) {
                        AuthState.Unknown -> LoadingScreen()
                        AuthState.SignedOut -> SignedOutScreen(
                            authGateway = authGateway,
                            googleIdTokenProvider = googleIdTokenProvider,
                        )
                        AuthState.SessionUnavailable -> {
                            val scope = rememberCoroutineScope()
                            SessionUnavailableScreen(onRetry = { scope.launch { authGateway.retryRestoringSession() } })
                        }
                        is AuthState.SignedIn -> AuthenticatedScreen(
                            session = state.session,
                            authGateway = authGateway,
                            profileGateway = profileGateway,
                            dao = database.localRecordDao(),
                            syncCoordinator = syncCoordinator,
                            wearReplicaDao = database.wearReplicaDao(),
                        )
                    }
                }
            }
        }
    }

    // singleTask (see AndroidManifest.xml) means a deep link tapped while the app is already
    // running arrives here instead of a fresh onCreate — must handle it the same way.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authGateway.handleAuthDeepLink(intent)
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// Once AuthState is SignedIn (either provider — no GoogleUser/EmailUser split), profile
// completeness is its own separate concern, checked here rather than folded into AuthState. See
// ProfileState's own doc comment and the profile/username implementation report.
@Composable
private fun AuthenticatedScreen(
    session: AuthSession,
    authGateway: AuthGateway,
    profileGateway: ProfileGateway,
    dao: LocalRecordDao,
    syncCoordinator: LocalRecordSyncCoordinator,
    wearReplicaDao: WearReplicaDao,
) {
    val scope = rememberCoroutineScope()
    var profileState by remember { mutableStateOf<ProfileState>(ProfileState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(session.userId, reloadKey) {
        profileState = ProfileState.Loading
        profileState = loadProfileState(profileGateway)
    }

    val onLogout: () -> Unit = { scope.launch { performLogout(authGateway, dao, wearReplicaDao) } }

    when (val state = profileState) {
        ProfileState.Loading -> LoadingScreen()
        ProfileState.Unavailable -> ProfileUnavailableScreen(
            onRetry = { reloadKey++ },
            onLogout = onLogout,
        )
        is ProfileState.Missing -> ProfileCompletionScreen(
            suggestedUsername = state.suggestedUsername,
            onSubmit = { username -> profileGateway.createOwnProfile(username) },
            onComplete = { profile -> profileState = ProfileState.Complete(profile) },
            onLogout = onLogout,
        )
        is ProfileState.Complete -> Column(modifier = Modifier.fillMaxSize()) {
            ProfileSummaryHeader(profile = state.profile, email = session.email, onLogout = onLogout)
            HorizontalDivider()
            MainScreen(dao = dao, syncCoordinator = syncCoordinator, wearReplicaDao = wearReplicaDao)
        }
    }
}

@Composable
private fun MainScreen(dao: LocalRecordDao, syncCoordinator: LocalRecordSyncCoordinator, wearReplicaDao: WearReplicaDao) {
    val scope = rememberCoroutineScope()
    val records by dao.getAll().collectAsState(initial = emptyList())
    val wearReplicas by wearReplicaDao.getAll().collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "TBDFit — phone local persistence + sync proof")
        Button(onClick = {
            scope.launch {
                dao.insert(
                    LocalRecordEntity(
                        id = UUID.randomUUID().toString(),
                        createdAt = System.currentTimeMillis(),
                        value = "created at ${System.currentTimeMillis()}",
                    )
                )
            }
        }) {
            Text("Create record")
        }
        Button(onClick = { scope.launch { syncCoordinator.sync() } }) {
            Text("Sync now")
        }
        LazyColumn {
            items(records) { record ->
                val status = if (record.syncedAt != null) "synced" else "pending"
                Text("${record.id.take(8)} · ${record.createdAt} · ${record.value} · $status")
            }
        }

        Text(text = "Received from Wear")
        LazyColumn {
            items(wearReplicas) { replica ->
                Text("${replica.id.take(8)} · ${replica.createdAt} · received ${replica.receivedAt}")
            }
        }
    }
}
