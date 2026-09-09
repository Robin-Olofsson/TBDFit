package com.tbdfit.phone.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tbdfit.phone.localstorage.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

// A thin integration smoke test over the repository facade itself (ID/timestamp generation +
// delegation) — the underlying persistence behavior is covered exhaustively by the DAO-level
// tests; this only proves the facade is wired correctly end to end.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutRepositoryTest {
    private val ownerId = "owner-a"
    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var db: AppDatabase
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "test-${UUID.randomUUID()}.db"
        db = AppDatabase.build(context, dbName)
        repository = WorkoutRepository(db.workoutDao(), db.workoutExerciseDao(), db.workoutSetDao(), db.exerciseDao(), db.localAccountDao())
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun fullHappyPathFromStartToCompletionPersistsCorrectly() = runTest {
        val startResult = repository.startWorkout(ownerId)
        assertTrue(startResult is StartWorkoutResult.Started)
        val workout = (startResult as StartWorkoutResult.Started).workout

        val exercise = repository.createCustomExercise(ownerId, "Bench Press")
        val workoutExercise = repository.addExercise(workout.id, exercise.id)
        val set = repository.addSet(workoutExercise.id, weight = 100.0, reps = 8)
        repository.completeSet(set.id)

        val completed = repository.completeWorkout(workout.id)

        assertTrue(completed)
        val persistedWorkout = repository.getActiveWorkout(ownerId)
        assertEquals(null, persistedWorkout) // no longer active
        val persistedSet = db.workoutSetDao().getForWorkoutExercise(workoutExercise.id).single()
        assertTrue(persistedSet.isCompleted)
    }

    @Test
    fun secondStartAttemptViaRepositoryReturnsExistingActiveWorkout() = runTest {
        val first = repository.startWorkout(ownerId)
        val existing = (first as StartWorkoutResult.Started).workout

        val second = repository.startWorkout(ownerId)

        assertTrue(second is StartWorkoutResult.AlreadyActive)
        assertEquals(existing.id, (second as StartWorkoutResult.AlreadyActive).existing.id)
    }

    @Test
    fun discardingAnActiveWorkoutRemovesItAndItsChildren() = runTest {
        val workout = (repository.startWorkout(ownerId) as StartWorkoutResult.Started).workout
        val exercise = repository.createCustomExercise(ownerId, "Squat")
        val workoutExercise = repository.addExercise(workout.id, exercise.id)
        repository.addSet(workoutExercise.id, weight = 60.0, reps = 5)

        val discarded = repository.discardWorkout(workout.id)

        assertTrue(discarded)
        assertNotNull(db.exerciseDao().getById(exercise.id)) // exercise itself is untouched
        assertEquals(emptyList<WorkoutExerciseEntity>(), db.workoutExerciseDao().getForWorkout(workout.id))
        assertEquals(emptyList<WorkoutSetEntity>(), db.workoutSetDao().getForWorkoutExercise(workoutExercise.id))
    }

    // Slice 3 — B9: "exercise picker reflects persisted Exercise rows", "custom Exercise creation
    // persists", "same/similar names can coexist", exercised through the repository facade the UI
    // actually depends on, not just the DAO directly.
    @Test
    fun createCustomExercisePersistsAndIsVisibleThroughObserveAllExercises() = runTest {
        val exercise = repository.createCustomExercise(ownerId, "  Nordic Curl  ")

        val all = repository.observeAllExercises(ownerId).first()
        assertTrue(all.any { it.id == exercise.id && it.name == "Nordic Curl" })
    }

    @Test
    fun similarAndIdenticalNamedCustomExercisesCanCoexistThroughTheRepository() = runTest {
        val first = repository.createCustomExercise(ownerId, "Bench Press")
        val second = repository.createCustomExercise(ownerId, "Bench Press")
        val third = repository.createCustomExercise(ownerId, "Incline Bench Press")

        val all = repository.observeAllExercises(ownerId).first()
        assertTrue(setOf(first.id, second.id, third.id).all { id -> all.any { it.id == id } })
        assertTrue(first.id != second.id)
    }

    @Test
    fun attachingAnExerciseThroughTheRepositoryIsVisibleViaObserveAttachedExercises() = runTest {
        val workout = (repository.startWorkout(ownerId) as StartWorkoutResult.Started).workout
        val benchPress = repository.createCustomExercise(ownerId, "Bench Press")
        val squat = repository.createCustomExercise(ownerId, "Squat")

        repository.addExercise(workout.id, benchPress.id)
        repository.addExercise(workout.id, squat.id)

        val attached = repository.observeAttachedExercises(workout.id).first()
        assertEquals(listOf("Bench Press", "Squat"), attached.map { it.name })
        assertEquals(listOf(0, 1), attached.map { it.position })
    }

    // B9: "no duplicate ACTIVE workout is created during this flow" — the attach flow itself never
    // touches WorkoutDao at all, but this proves that explicitly rather than by omission.
    @Test
    fun attachingExercisesDoesNotCreateAnyAdditionalWorkout() = runTest {
        val workout = (repository.startWorkout(ownerId) as StartWorkoutResult.Started).workout
        val exercise = repository.createCustomExercise(ownerId, "Bench Press")

        repository.addExercise(workout.id, exercise.id)
        repository.addExercise(workout.id, exercise.id)

        val activeCount = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM workouts").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        assertEquals(1, activeCount)
    }

    // Local-workout-ownership audit — the core regression test: a different account must never be
    // able to observe or resume this account's ACTIVE workout through the repository facade the
    // UI actually uses (WorkoutRootScreen).
    @Test
    fun aDifferentOwnerNeverObservesThisOwnersActiveWorkout() = runTest {
        repository.startWorkout(ownerId)

        val otherOwnerId = "owner-b"
        val otherOwnersView = repository.getActiveWorkout(otherOwnerId)

        assertEquals(null, otherOwnersView)
    }

    @Test
    fun logoutThenADifferentOwnerSigningInCannotResumeThePreviousOwnersWorkout() = runTest {
        val userAWorkout = (repository.startWorkout("user-a") as StartWorkoutResult.Started).workout

        // "Logout" here is exactly what performLogout leaves behind for workout data: the row is
        // untouched (see AccountTransition.kt) — only the routing/ownerId used to query changes,
        // which is what this test actually proves.
        val userBView = repository.getActiveWorkout("user-b")

        assertEquals(null, userBView)
        // user-a's workout is not lost — it's simply correctly invisible to user-b.
        assertEquals(userAWorkout.id, repository.getActiveWorkout("user-a")?.id)
    }

    // Local-account-ownership correction — the same regression coverage extended to custom
    // exercises: a CUSTOM exercise created by one account must never appear in another account's
    // exercise picker (observeAllExercises), through the repository facade the UI actually uses.
    @Test
    fun aCustomExerciseCreatedByOneOwnerIsNeverVisibleToAnotherOwner() = runTest {
        val ownerBExercise = repository.createCustomExercise("owner-b", "Owner B's Move")

        val visibleToA = repository.observeAllExercises(ownerId).first()

        assertTrue(visibleToA.none { it.id == ownerBExercise.id })
        // ...but it IS visible to the account that actually owns it.
        val visibleToB = repository.observeAllExercises("owner-b").first()
        assertTrue(visibleToB.any { it.id == ownerBExercise.id })
    }
}
