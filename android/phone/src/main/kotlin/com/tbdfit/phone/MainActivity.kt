package com.tbdfit.phone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.tbdfit.phone.auth.LastSignedInAccountCache
import com.tbdfit.phone.auth.SessionUnavailableScreen
import com.tbdfit.phone.auth.SignedOutScreen
import com.tbdfit.phone.auth.performLogout
import com.tbdfit.phone.auth.shouldShowLocalWorkoutInsteadOfSessionRetry
import com.tbdfit.phone.backend.auth.SupabaseAuthGateway
import com.tbdfit.phone.backend.localrecords.SupabaseLocalRecordRemoteStore
import com.tbdfit.phone.backend.profile.SupabaseProfileGateway
import com.tbdfit.phone.localstorage.AppDatabase
import com.tbdfit.phone.localstorage.LocalRecordDao
import com.tbdfit.phone.profile.ProfileCompletionScreen
import com.tbdfit.phone.profile.ProfileGateway
import com.tbdfit.phone.profile.ProfileState
import com.tbdfit.phone.profile.ProfileUnavailableScreen
import com.tbdfit.phone.profile.loadProfileState
import com.tbdfit.phone.shell.AppShell
import com.tbdfit.phone.sync.LocalRecordSyncCoordinator
import com.tbdfit.phone.ui.theme.TbdfitTheme
import com.tbdfit.phone.wearreplication.WearReplicaDao
import com.tbdfit.phone.workout.ActiveWorkoutScreen
import com.tbdfit.phone.workout.RootUiState
import com.tbdfit.phone.workout.WorkoutRepository
import com.tbdfit.phone.workout.deriveRootUiState
import com.tbdfit.phone.workout.seedBuiltInExercisesIfAbsent
import kotlinx.coroutines.launch

// Foundation-only entry point; no workout feature exists yet. This screen exists only to prove the
// local-persistence, local-to-Supabase sync, Wear-replica-receiving, and phone-authentication
// slices, not as product UI. Auth gates the technical-proof screen below purely as the smallest
// way to demonstrate the auth slice end to end — this is NOT a product rule that workouts require
// login (see the phone-authentication report's open decisions).
class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var syncCoordinator: LocalRecordSyncCoordinator
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var authGateway: AuthGateway
    private lateinit var profileGateway: ProfileGateway
    private lateinit var googleIdTokenProvider: GoogleIdTokenProvider
    private lateinit var lastSignedInAccountCache: LastSignedInAccountCache

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AppDatabase.build(applicationContext)
        syncCoordinator = LocalRecordSyncCoordinator(
            dao = database.localRecordDao(),
            remoteStore = SupabaseLocalRecordRemoteStore(),
        )
        // The sole application-layer composition point over the four workout DAOs — see
        // WorkoutRepository's own doc comment for why this is a concrete class, not a generic
        // repository abstraction. Nothing else in this file (or anywhere in app code) is permitted
        // to hold a raw WorkoutDao reference — see WorkoutDao.insert's own doc comment for why that
        // matters for the single-active-workout guarantee.
        workoutRepository = WorkoutRepository(
            workoutDao = database.workoutDao(),
            workoutExerciseDao = database.workoutExerciseDao(),
            workoutSetDao = database.workoutSetDao(),
            exerciseDao = database.exerciseDao(),
            localAccountDao = database.localAccountDao(),
            routineDao = database.routineDao(),
            routineExerciseDao = database.routineExerciseDao(),
            routinePlannedSetDao = database.routinePlannedSetDao(),
            appDatabase = database,
        )
        authGateway = SupabaseAuthGateway()
        profileGateway = SupabaseProfileGateway()
        googleIdTokenProvider = GoogleIdTokenProvider(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        lastSignedInAccountCache = LastSignedInAccountCache(applicationContext)

        // Handles the email-confirmation deep link (tbdfit://auth-callback) if this launch came
        // from one; a no-op for a normal launcher start. Delegated to AuthGateway rather than
        // called on Supabase directly — MainActivity must not import Supabase Auth SDK types or
        // SupabaseClientProvider itself (see AuthGateway.handleAuthDeepLink).
        authGateway.handleAuthDeepLink(intent)

        setContent {
            TbdfitTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Local-only, needs no session: safe and correct to run regardless of auth
                    // state (see seedBuiltInExercisesIfAbsent's own doc comment for why this is
                    // idempotent and safe to call on every launch).
                    LaunchedEffect(Unit) {
                        seedBuiltInExercisesIfAbsent(database.exerciseDao())
                    }
                    val authState by authGateway.state.collectAsState(initial = AuthState.Unknown)
                    when (val state = authState) {
                        AuthState.Unknown -> LoadingScreen()
                        AuthState.SignedOut -> SignedOutScreen(
                            authGateway = authGateway,
                            googleIdTokenProvider = googleIdTokenProvider,
                        )
                        AuthState.SessionUnavailable -> {
                            val scope = rememberCoroutineScope()
                            SessionUnavailableOrLocalWorkoutScreen(
                                workoutRepository = workoutRepository,
                                lastSignedInAccountCache = lastSignedInAccountCache,
                                onRetry = { scope.launch { authGateway.retryRestoringSession() } },
                            )
                        }
                        is AuthState.SignedIn -> AuthenticatedScreen(
                            session = state.session,
                            authGateway = authGateway,
                            profileGateway = profileGateway,
                            dao = database.localRecordDao(),
                            syncCoordinator = syncCoordinator,
                            wearReplicaDao = database.wearReplicaDao(),
                            workoutRepository = workoutRepository,
                            lastSignedInAccountCache = lastSignedInAccountCache,
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

// Offline/auth-gate audit fix: SessionUnavailable means a persisted session exists but could not
// currently be verified (e.g. no network) — not that the user signed out (see AuthState's own doc
// comment). Unconditionally showing the retry-only SessionUnavailableScreen here previously made a
// durably-ACTIVE local workout completely unreachable whenever the account happened to be
// unverifiable, which contradicts the local-first direction: local workout execution and remote
// account authorization are separate concerns, and the Room database needs no live session to be
// read. See shouldShowLocalWorkoutInsteadOfSessionRetry's own doc comment for the exact, narrow
// scope of this exception (does not extend to SignedOut; does not allow starting a NEW workout).
//
// Local-workout-ownership audit: this branch has no AuthSession (SessionUnavailable carries none),
// so it cannot query "the current account's" workout the normal way. It deliberately does NOT fall
// back to an unscoped "any active workout" query either — that would reopen the exact cross-account
// leak the ownership audit closed, e.g. if account A's stale still-ACTIVE workout exists locally
// and account B's session (with no active workout of its own) goes SessionUnavailable, an unscoped
// query would incorrectly hand B a view of A's workout. Instead this uses
// LastSignedInAccountCache — the account last actually confirmed SignedIn on this device — which
// is always B in that scenario (recorded the moment B's SignedIn was first observed), never A.
@Composable
private fun SessionUnavailableOrLocalWorkoutScreen(
    workoutRepository: WorkoutRepository,
    lastSignedInAccountCache: LastSignedInAccountCache,
    onRetry: () -> Unit,
) {
    val ownerId = lastSignedInAccountCache.lastKnownUserId()

    if (ownerId == null) {
        // No account has ever been confirmed signed in on this device — nothing safe to scope a
        // workout lookup to, so there is nothing to show but the ordinary retry screen.
        SessionUnavailableScreen(onRetry = onRetry)
        return
    }

    val workoutState by produceState<RootUiState>(initialValue = RootUiState.Loading, workoutRepository, ownerId) {
        workoutRepository.observeActiveWorkout(ownerId).collect { workout -> value = deriveRootUiState(workout) }
    }

    if (shouldShowLocalWorkoutInsteadOfSessionRetry(workoutState)) {
        val activeWorkout = (workoutState as RootUiState.Active).workout
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "Account temporarily unavailable — this workout is saved locally and will keep working.",
                modifier = Modifier.padding(16.dp),
            )
            HorizontalDivider()
            ActiveWorkoutScreen(workout = activeWorkout, repository = workoutRepository, ownerId = ownerId)
        }
    } else {
        SessionUnavailableScreen(onRetry = onRetry)
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
    workoutRepository: WorkoutRepository,
    lastSignedInAccountCache: LastSignedInAccountCache,
) {
    val scope = rememberCoroutineScope()
    var profileState by remember { mutableStateOf<ProfileState>(ProfileState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(session.userId, reloadKey) {
        profileState = ProfileState.Loading
        profileState = loadProfileState(profileGateway)
    }

    // Local-workout-ownership audit: records the account SessionUnavailable's local-workout
    // exception should later resume, if this session ever becomes temporarily unverifiable. Keyed
    // on session.userId so it re-runs (harmlessly, idempotently) if the signed-in account changes
    // without a full process recreation.
    //
    // Also ensures the corresponding LocalAccount identity row exists (local-account-ownership
    // correction) — "On SignedIn(userId): ensure LocalAccount(userId) exists" — done explicitly and
    // directly here, in addition to WorkoutRepository's own internal defensive guarantee at every
    // owner-scoped write, so the FK is satisfied the moment a session begins, not only lazily at
    // first workout/exercise creation.
    LaunchedEffect(session.userId) {
        lastSignedInAccountCache.recordSignedIn(session.userId)
        workoutRepository.ensureLocalAccountExists(session.userId)
    }

    val onLogout: () -> Unit = {
        scope.launch { performLogout(authGateway, dao, wearReplicaDao, lastSignedInAccountCache) }
    }

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
        is ProfileState.Complete -> AppShell(
            workoutRepository = workoutRepository,
            ownerId = session.userId,
            profile = state.profile,
            email = session.email,
            onLogout = onLogout,
            dao = dao,
            syncCoordinator = syncCoordinator,
            wearReplicaDao = wearReplicaDao,
        )
    }
}
