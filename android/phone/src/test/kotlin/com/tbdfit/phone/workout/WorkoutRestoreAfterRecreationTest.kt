package com.tbdfit.phone.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tbdfit.phone.localstorage.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

// The most realistic supported lifecycle-restoration proxy available in this environment (no
// emulator/device — see WorkoutDurabilityTest's own doc comment for the same limitation at the
// DAO level). Exercises the actual application-layer path Slice 2's UI depends on
// (WorkoutRepository, not raw DAOs): a fresh AppDatabase + WorkoutRepository instance opened
// against the same on-disk file simulates "the app process was recreated and MainActivity.onCreate
// ran again," the same way LocalRecordDaoTest's close/reopen pattern already does for the
// technical-proof slice.
//
// This is APP RECREATION VERIFIED (an automated DB-reopen proxy), not PHYSICAL DEVICE
// PROCESS-DEATH VERIFIED — see the Slice 2 report for why those are kept distinct.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutRestoreAfterRecreationTest {
    private val ownerId = "owner-a"
    private lateinit var context: Context
    private lateinit var dbName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "test-${UUID.randomUUID()}.db"
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun repositoryFor(db: AppDatabase) =
        WorkoutRepository(db.workoutDao(), db.workoutExerciseDao(), db.workoutSetDao(), db.exerciseDao(), db.localAccountDao())

    @Test
    fun anActiveWorkoutStartedBeforeRecreationIsRestoredAfterwardsAsTheActiveUiState() = runTest {
        val startedWorkoutId: String

        // "Before" — a real session starts a workout, then the process goes away.
        run {
            val dbBefore = AppDatabase.build(context, dbName)
            val repoBefore = repositoryFor(dbBefore)
            val result = repoBefore.startWorkout(ownerId)
            startedWorkoutId = (result as StartWorkoutResult.Started).workout.id
            dbBefore.close()
        }

        // "After" — a brand-new AppDatabase/WorkoutRepository pair against the same file, exactly
        // as MainActivity.onCreate constructs on every launch. Nothing here is in-memory state
        // carried over from before — this instance has never seen the workout above except via
        // Room.
        val dbAfter = AppDatabase.build(context, dbName)
        val repoAfter = repositoryFor(dbAfter)

        val restored = repoAfter.getActiveWorkout(ownerId)
        assertEquals(startedWorkoutId, restored?.id)
        assertEquals(RootUiState.Active(restored!!), deriveRootUiState(restored))

        dbAfter.close()
    }

    @Test
    fun startingAgainAfterRecreationDoesNotCreateASecondActiveWorkoutAndSurfacesTheExistingOne() = runTest {
        val originalId: String
        run {
            val dbBefore = AppDatabase.build(context, dbName)
            originalId = (repositoryFor(dbBefore).startWorkout(ownerId) as StartWorkoutResult.Started).workout.id
            dbBefore.close()
        }

        val dbAfter = AppDatabase.build(context, dbName)
        val repoAfter = repositoryFor(dbAfter)

        // Simulates a user reaching the home/start UI again after relaunch (e.g. a stale cached
        // screen, or simply re-attempting) before the Flow-driven router has navigated them to the
        // active screen — the repository, not the UI, is what must refuse to double-create here.
        val secondAttempt = repoAfter.startWorkout(ownerId)

        assertTrue(secondAttempt is StartWorkoutResult.AlreadyActive)
        assertEquals(originalId, (secondAttempt as StartWorkoutResult.AlreadyActive).existing.id)

        dbAfter.close()
    }

    // Slice 3 — B8: "same WorkoutExercise rows, same order" after recreation. Extends the same
    // close/reopen-against-the-same-file proxy to attached exercises specifically, going through
    // the repository (what ActiveWorkoutScreen actually observes), not raw DAOs.
    @Test
    fun attachedExercisesAndTheirOrderSurviveRecreation() = runTest {
        val workoutId: String
        val benchPressId: String
        val squatId: String

        run {
            val dbBefore = AppDatabase.build(context, dbName)
            val repoBefore = repositoryFor(dbBefore)
            workoutId = (repoBefore.startWorkout(ownerId) as StartWorkoutResult.Started).workout.id
            benchPressId = repoBefore.createCustomExercise(ownerId, "Bench Press").id
            squatId = repoBefore.createCustomExercise(ownerId, "Squat").id
            repoBefore.addExercise(workoutId, benchPressId)
            repoBefore.addExercise(workoutId, squatId)
            dbBefore.close()
        }

        val dbAfter = AppDatabase.build(context, dbName)
        val repoAfter = repositoryFor(dbAfter)

        val attached = repoAfter.observeAttachedExercises(workoutId).first()

        assertEquals(listOf("Bench Press", "Squat"), attached.map { it.name })
        assertEquals(listOf(benchPressId, squatId), attached.map { it.exerciseId })
        assertEquals(listOf(0, 1), attached.map { it.position })

        dbAfter.close()
    }

    // Slice 4 (C7) — extends the same proxy one level deeper: reps, weight, completion state, and
    // order for logged sets, restored through the repository exactly as ActiveWorkoutScreen
    // observes them.
    @Test
    fun loggedSetsWithMixedCompletionStateSurviveRecreationInOrder() = runTest {
        val workoutExerciseId: String
        val completedSetId: String

        run {
            val dbBefore = AppDatabase.build(context, dbName)
            val repoBefore = repositoryFor(dbBefore)
            val workoutId = (repoBefore.startWorkout(ownerId) as StartWorkoutResult.Started).workout.id
            val exerciseId = repoBefore.createCustomExercise(ownerId, "Bench Press").id
            workoutExerciseId = repoBefore.addExercise(workoutId, exerciseId).id

            val firstSet = repoBefore.addSet(workoutExerciseId, weight = 100.0, reps = 8)
            completedSetId = firstSet.id
            repoBefore.completeSet(completedSetId)
            repoBefore.addSet(workoutExerciseId, weight = null, reps = null) // still in progress

            dbBefore.close()
        }

        val dbAfter = AppDatabase.build(context, dbName)
        val repoAfter = repositoryFor(dbAfter)

        val restoredSets = repoAfter.observeSetsForWorkoutExercise(workoutExerciseId).first()

        assertEquals(2, restoredSets.size)
        val restoredCompleted = restoredSets.first { it.id == completedSetId }
        assertTrue(restoredCompleted.isCompleted)
        assertEquals(100.0, restoredCompleted.weight)
        assertEquals(8, restoredCompleted.reps)
        val restoredIncomplete = restoredSets.first { it.id != completedSetId }
        assertTrue(!restoredIncomplete.isCompleted)
        assertEquals(listOf(0, 1), restoredSets.map { it.position })

        dbAfter.close()
    }

    // Local-workout-ownership audit, at the exact granularity the audit's scenario describes:
    // User A starts a workout and attaches an exercise → the app is recreated (simulating logout +
    // a different user signing in on the same installation) → User B must not be able to observe
    // or resume User A's workout, and User B starting their own must succeed independently.
    @Test
    fun aDifferentOwnerAfterRecreationCannotResumeThePreviousOwnersWorkoutAndCanStartTheirOwn() = runTest {
        val userAWorkoutId: String

        run {
            val dbBefore = AppDatabase.build(context, dbName)
            val repoBefore = repositoryFor(dbBefore)
            userAWorkoutId = (repoBefore.startWorkout("user-a") as StartWorkoutResult.Started).workout.id
            val exercise = repoBefore.createCustomExercise("user-a", "Bench Press")
            repoBefore.addExercise(userAWorkoutId, exercise.id)
            dbBefore.close()
        }

        // Simulates: user-a logs out (workout rows are never cleared on logout — see
        // AccountTransition.kt), then user-b signs in on the same installation, and the app is
        // recreated (or simply re-queried) under user-b's identity.
        val dbAfter = AppDatabase.build(context, dbName)
        val repoAfter = repositoryFor(dbAfter)

        assertEquals(null, repoAfter.getActiveWorkout("user-b"))

        val userBResult = repoAfter.startWorkout("user-b")
        assertTrue(userBResult is StartWorkoutResult.Started)
        assertTrue((userBResult as StartWorkoutResult.Started).workout.id != userAWorkoutId)

        // user-a's workout still exists, untouched, and remains exclusively theirs.
        assertEquals(userAWorkoutId, repoAfter.getActiveWorkout("user-a")?.id)

        dbAfter.close()
    }
}
