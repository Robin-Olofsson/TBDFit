package com.tbdfit.phone.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tbdfit.phone.localstorage.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        repository = WorkoutRepository(db.workoutDao(), db.workoutExerciseDao(), db.workoutSetDao(), db.exerciseDao(), db.localAccountDao(), db.routineDao(), db.routineExerciseDao(), db.routinePlannedSetDao(), db)
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

    // --- Slice A: Routine → Workout (program-routine-first-slice-design.md) ---------------------

    private suspend fun createBenchPressRoutine(owner: String = ownerId, plannedReps: Int? = 8, plannedWeight: Double? = 80.0): RoutineEntity {
        val routine = repository.createRoutine(owner, "Push Day")
        val routineExercise = repository.addExerciseToRoutine(routine.id, repository.createCustomExercise(owner, "Bench Press").id)
        repository.addPlannedSetToRoutineExercise(routineExercise.id, plannedReps, plannedWeight)
        repository.addPlannedSetToRoutineExercise(routineExercise.id, plannedReps, plannedWeight)
        repository.addPlannedSetToRoutineExercise(routineExercise.id, plannedReps, plannedWeight)
        return routine
    }

    @Test
    fun aRoutineCanBeUsedWithZeroProgramInvolvement() = runTest {
        val routine = createBenchPressRoutine()

        val result = repository.startRoutine(ownerId, routine.id)

        assertTrue(result is StartWorkoutResult.Started)
        val workout = (result as StartWorkoutResult.Started).workout
        assertEquals(routine.id, workout.originRoutineId)
        val attached = repository.observeAttachedExercises(workout.id).first()
        assertEquals(listOf("Bench Press"), attached.map { it.name })
        assertEquals(3, repository.observeSetsForWorkoutExercise(attached.single().workoutExerciseId).first().size)
    }

    // The single most important test in this slice: an in-progress edit to the source Routine must
    // never retroactively change an already-started Workout's target snapshot.
    @Test
    fun editingTheRoutineAfterStartDoesNotChangeTheActiveWorkoutsTargetSnapshot() = runTest {
        val routine = createBenchPressRoutine(plannedReps = 8, plannedWeight = 80.0)
        val started = repository.startRoutine(ownerId, routine.id) as StartWorkoutResult.Started
        val workoutExerciseId = repository.observeAttachedExercises(started.workout.id).first().single().workoutExerciseId
        val setsAtStart = repository.observeSetsForWorkoutExercise(workoutExerciseId).first()
        assertEquals(3, setsAtStart.size)
        assertTrue(setsAtStart.all { it.targetReps == 8 && it.targetWeight == 80.0 })

        // Edit the Routine's planned sets to a completely different prescription after the Workout
        // already exists.
        val routineExercise = db.routineExerciseDao().getForRoutine(routine.id).single()
        for (plannedSet in db.routinePlannedSetDao().getForRoutineExercise(routineExercise.id)) {
            db.routinePlannedSetDao().updatePlannedReps(plannedSet.id, 5)
            db.routinePlannedSetDao().updatePlannedWeight(plannedSet.id, 90.0)
        }

        val setsAfterEdit = repository.observeSetsForWorkoutExercise(workoutExerciseId).first()
        assertEquals(listOf(8, 8, 8), setsAfterEdit.map { it.targetReps })
        assertEquals(listOf(80.0, 80.0, 80.0), setsAfterEdit.map { it.targetWeight })
    }

    @Test
    fun targetAndActualRemainIndependentThroughACompletedSet() = runTest {
        val routine = createBenchPressRoutine(plannedReps = 8, plannedWeight = 80.0)
        val started = repository.startRoutine(ownerId, routine.id) as StartWorkoutResult.Started
        val workoutExerciseId = repository.observeAttachedExercises(started.workout.id).first().single().workoutExerciseId
        val set = repository.observeSetsForWorkoutExercise(workoutExerciseId).first().first()

        // Before performing: target is populated, actual is unperformed.
        assertEquals(8, set.targetReps)
        assertEquals(80.0, set.targetWeight)
        assertNull(set.reps)
        assertNull(set.weight)
        assertTrue(!set.isCompleted)
        assertNull(set.completedAt)

        // Perform with DIFFERENT actual values than target.
        repository.updateSetReps(set.id, 7)
        repository.updateSetWeight(set.id, 82.5)
        repository.completeSet(set.id)

        val completed = repository.observeSetsForWorkoutExercise(workoutExerciseId).first().first()
        assertEquals(8, completed.targetReps)
        assertEquals(80.0, completed.targetWeight)
        assertEquals(7, completed.reps)
        assertEquals(82.5, completed.weight)
        assertTrue(completed.isCompleted)
    }

    @Test
    fun aManuallyAddedSetHasNoTarget() = runTest {
        val workout = (repository.startWorkout(ownerId) as StartWorkoutResult.Started).workout
        val exercise = repository.createCustomExercise(ownerId, "Overhead Press")
        val workoutExercise = repository.addExercise(workout.id, exercise.id)

        val set = repository.addSet(workoutExercise.id, weight = 40.0, reps = 10)

        assertNull(set.targetReps)
        assertNull(set.targetWeight)
        assertEquals(40.0, set.weight)
        assertEquals(10, set.reps)
    }

    @Test
    fun deletingTheRoutineAfterCompletionLeavesTheWorkoutIntactWithProvenanceCleared() = runTest {
        val routine = createBenchPressRoutine()
        val started = repository.startRoutine(ownerId, routine.id) as StartWorkoutResult.Started
        repository.completeWorkout(started.workout.id)

        repository.deleteRoutine(routine.id, ownerId)

        val history = repository.observeCompletedWorkouts().first()
        val survivingWorkout = history.single { it.id == started.workout.id }
        assertEquals(null, survivingWorkout.originRoutineId)
        assertTrue(survivingWorkout.status == WorkoutStatus.COMPLETED)
    }

    @Test
    fun aDifferentOwnerCannotStartAnotherOwnersRoutine() = runTest {
        val routine = createBenchPressRoutine(owner = "owner-a")

        val result = runCatching { repository.startRoutine("owner-b", routine.id) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun aDifferentOwnerCannotDeleteAnotherOwnersRoutine() = runTest {
        val routine = createBenchPressRoutine(owner = "owner-a")

        val deleted = repository.deleteRoutine(routine.id, "owner-b")

        assertTrue(!deleted)
        assertTrue(repository.observeRoutinesFor("owner-a").first().any { it.id == routine.id })
    }

    @Test
    fun startingARoutineWhileAWorkoutIsAlreadyActiveLeavesTheExistingWorkoutUntouched() = runTest {
        val existing = (repository.startWorkout(ownerId) as StartWorkoutResult.Started).workout
        val routine = createBenchPressRoutine()

        val result = repository.startRoutine(ownerId, routine.id)

        assertTrue(result is StartWorkoutResult.AlreadyActive)
        assertEquals(existing.id, (result as StartWorkoutResult.AlreadyActive).existing.id)
        // No exercises were attached to the existing workout as a side effect.
        assertEquals(emptyList<AttachedExercise>(), repository.observeAttachedExercises(existing.id).first())
    }

    // --- Routine UX completion slice: the new repository-level edit wrappers ---------------------

    @Test
    fun updatingAPlannedSetsRepsAndWeightThroughTheRepositoryPersists() = runTest {
        val routine = repository.createRoutine(ownerId, "Push Day")
        val routineExercise = repository.addExerciseToRoutine(routine.id, repository.createCustomExercise(ownerId, "Bench Press").id)
        val plannedSet = repository.addPlannedSetToRoutineExercise(routineExercise.id, plannedReps = 8, plannedWeight = 80.0)

        repository.updatePlannedSetReps(plannedSet.id, 5)
        repository.updatePlannedSetWeight(plannedSet.id, 90.0)

        val updated = repository.observePlannedSets(routineExercise.id).first().single()
        assertEquals(5, updated.plannedReps)
        assertEquals(90.0, updated.plannedWeight)
    }

    @Test
    fun removingAPlannedSetThroughTheRepositoryDeletesOnlyThatSet() = runTest {
        val routine = repository.createRoutine(ownerId, "Push Day")
        val routineExercise = repository.addExerciseToRoutine(routine.id, repository.createCustomExercise(ownerId, "Bench Press").id)
        val first = repository.addPlannedSetToRoutineExercise(routineExercise.id, plannedReps = 8, plannedWeight = 80.0)
        val second = repository.addPlannedSetToRoutineExercise(routineExercise.id, plannedReps = 8, plannedWeight = 80.0)

        repository.removePlannedSet(first.id)

        assertEquals(listOf(second), repository.observePlannedSets(routineExercise.id).first())
    }

    @Test
    fun removingAnExerciseFromARoutineThroughTheRepositoryCascadesItsPlannedSets() = runTest {
        val routine = repository.createRoutine(ownerId, "Push Day")
        val benchExercise = repository.addExerciseToRoutine(routine.id, repository.createCustomExercise(ownerId, "Bench Press").id)
        val squatExercise = repository.addExerciseToRoutine(routine.id, repository.createCustomExercise(ownerId, "Squat").id)
        repository.addPlannedSetToRoutineExercise(benchExercise.id, plannedReps = 8, plannedWeight = 80.0)

        repository.removeExerciseFromRoutine(benchExercise.id)

        val remaining = repository.observeRoutineExercises(routine.id).first()
        assertEquals(listOf(squatExercise.id), remaining.map { it.routineExerciseId })
        assertEquals(emptyList<RoutinePlannedSetEntity>(), repository.observePlannedSets(benchExercise.id).first())
    }

    // Mirrors editingTheRoutineAfterStartDoesNotChangeTheActiveWorkoutsTargetSnapshot above, but
    // exercises the actual repository-level edit methods the new Edit Routine UI calls (not raw
    // DAO access) — proving the UI's own edit path cannot retroactively mutate an already-started
    // Workout's frozen target snapshot either.
    @Test
    fun editingAPlannedSetThroughTheRepositoryAfterStartDoesNotChangeTheActiveWorkoutsTargetSnapshot() = runTest {
        val routine = createBenchPressRoutine(plannedReps = 8, plannedWeight = 80.0)
        val started = repository.startRoutine(ownerId, routine.id) as StartWorkoutResult.Started
        val workoutExerciseId = repository.observeAttachedExercises(started.workout.id).first().single().workoutExerciseId

        val routineExercise = repository.observeRoutineExercises(routine.id).first().single()
        for (plannedSet in repository.observePlannedSets(routineExercise.routineExerciseId).first()) {
            repository.updatePlannedSetReps(plannedSet.id, 5)
            repository.updatePlannedSetWeight(plannedSet.id, 90.0)
        }

        val setsAfterEdit = repository.observeSetsForWorkoutExercise(workoutExerciseId).first()
        assertEquals(listOf(8, 8, 8), setsAfterEdit.map { it.targetReps })
        assertEquals(listOf(80.0, 80.0, 80.0), setsAfterEdit.map { it.targetWeight })
    }

    // Removing an exercise from the Routine (e.g. via Edit Routine) after a Workout has already
    // been started from it must not touch that Workout's already-copied WorkoutExercise/WorkoutSet
    // rows — there is no live reference for the removal to follow.
    @Test
    fun removingARoutineExerciseAfterStartDoesNotAffectTheActiveWorkout() = runTest {
        val routine = createBenchPressRoutine()
        val started = repository.startRoutine(ownerId, routine.id) as StartWorkoutResult.Started
        val attachedBefore = repository.observeAttachedExercises(started.workout.id).first()
        assertEquals(1, attachedBefore.size)

        val routineExercise = repository.observeRoutineExercises(routine.id).first().single()
        repository.removeExerciseFromRoutine(routineExercise.routineExerciseId)

        val attachedAfter = repository.observeAttachedExercises(started.workout.id).first()
        assertEquals(attachedBefore.map { it.workoutExerciseId }, attachedAfter.map { it.workoutExerciseId })
    }
}
