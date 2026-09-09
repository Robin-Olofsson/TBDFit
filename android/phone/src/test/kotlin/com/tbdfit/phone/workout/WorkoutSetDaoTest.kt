package com.tbdfit.phone.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tbdfit.phone.localaccount.LocalAccountEntity
import com.tbdfit.phone.localstorage.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutSetDaoTest {
    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var db: AppDatabase
    private lateinit var workoutExerciseId: String

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        dbName = "test-${UUID.randomUUID()}.db"
        db = AppDatabase.build(context, dbName)

        val now = System.currentTimeMillis()
        db.localAccountDao().insertIfAbsent(LocalAccountEntity(id = "owner-a", createdAt = now))
        val workoutId = UUID.randomUUID().toString()
        db.workoutDao().insert(WorkoutEntity(id = workoutId, ownerId = "owner-a", status = WorkoutStatus.ACTIVE, startedAt = now, createdAt = now))
        val exerciseId = UUID.randomUUID().toString()
        db.exerciseDao().insert(
            ExerciseEntity(id = exerciseId, name = "Bench Press", normalizedName = "bench press", source = ExerciseSource.CUSTOM, ownerId = null, createdAt = now),
        )
        workoutExerciseId = db.workoutExerciseDao().appendExercise(UUID.randomUUID().toString(), workoutId, exerciseId).id
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun addingASetCreatesAnIncompleteRow() = runTest {
        val set = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, weight = null, reps = null)

        assertTrue(!set.isCompleted)
        assertNull(set.completedAt)
    }

    @Test
    fun addingASetPersistsWeightAndReps() = runTest {
        val set = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, weight = 100.0, reps = 8)

        val persisted = db.workoutSetDao().getForWorkoutExercise(workoutExerciseId).single()
        assertEquals(set, persisted)
        assertEquals(100.0, persisted.weight)
        assertEquals(8, persisted.reps)
    }

    @Test
    fun bodyweightSetsMayHaveNullWeight() = runTest {
        val set = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, weight = null, reps = 12)

        assertNull(db.workoutSetDao().getForWorkoutExercise(workoutExerciseId).single().weight)
        assertEquals(set.id, db.workoutSetDao().getForWorkoutExercise(workoutExerciseId).single().id)
    }

    @Test
    fun editingWeightUpdatesTheExistingRowInPlace() = runTest {
        val set = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, weight = 100.0, reps = 8)

        db.workoutSetDao().updateWeight(set.id, 102.5)

        assertEquals(102.5, db.workoutSetDao().getForWorkoutExercise(workoutExerciseId).single().weight)
    }

    @Test
    fun editingRepsUpdatesTheExistingRowInPlace() = runTest {
        val set = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, weight = 100.0, reps = 8)

        db.workoutSetDao().updateReps(set.id, 10)

        assertEquals(10, db.workoutSetDao().getForWorkoutExercise(workoutExerciseId).single().reps)
    }

    @Test
    fun appendedSetsReceiveDeterministicSequentialPositions() = runTest {
        val first = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, 100.0, 8)
        val second = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, 100.0, 6)

        assertEquals(0, first.position)
        assertEquals(1, second.position)
        assertEquals(listOf(first, second), db.workoutSetDao().getForWorkoutExercise(workoutExerciseId))
    }

    @Test
    fun negativeWeightIsRejectedOnAppend() = runTest {
        val result = runCatching {
            db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, weight = -5.0, reps = 8)
        }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun negativeRepsIsRejectedOnAppend() = runTest {
        val result = runCatching {
            db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, weight = 100.0, reps = -1)
        }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun negativeWeightIsRejectedOnUpdate() = runTest {
        val set = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, 100.0, 8)

        val result = runCatching { db.workoutSetDao().updateWeight(set.id, -1.0) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun negativeRepsIsRejectedOnUpdate() = runTest {
        val set = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, 100.0, 8)

        val result = runCatching { db.workoutSetDao().updateReps(set.id, -1) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun markingASetCompletedPersistsCompletionState() = runTest {
        val set = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, 100.0, 8)

        db.workoutSetDao().markCompleted(set.id, 1_700_000_010_000L)

        val persisted = db.workoutSetDao().getForWorkoutExercise(workoutExerciseId).single()
        assertTrue(persisted.isCompleted)
        assertEquals(1_700_000_010_000L, persisted.completedAt)
    }

    // Slice 4 (C1/C5) — a completed set requires reps to be present; weight may remain null. This
    // is provisional product/domain semantics pending team review, not an Accepted decision.
    @Test
    fun completingASetWithRepsPresentSucceeds() = runTest {
        val set = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, weight = null, reps = 12)

        val result = db.workoutSetDao().completeIfRepsPresent(set.id, 1_700_000_010_000L)

        assertEquals(CompleteSetResult.Completed, result)
        val persisted = db.workoutSetDao().getForWorkoutExercise(workoutExerciseId).single()
        assertTrue(persisted.isCompleted)
        assertEquals(1_700_000_010_000L, persisted.completedAt)
        assertNull(persisted.weight) // bodyweight exercise — weight staying null is not an error
    }

    @Test
    fun completingASetWithoutRepsIsRejectedAndLeavesTheRowUnchanged() = runTest {
        val set = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, weight = 100.0, reps = null)

        val result = db.workoutSetDao().completeIfRepsPresent(set.id, 1_700_000_010_000L)

        assertEquals(CompleteSetResult.RejectedMissingReps, result)
        val persisted = db.workoutSetDao().getForWorkoutExercise(workoutExerciseId).single()
        // No mismatched/half-completed state was produced: neither field changed.
        assertTrue(!persisted.isCompleted)
        assertNull(persisted.completedAt)
    }

    @Test
    fun completingANonExistentSetIsReportedNotFound() = runTest {
        val result = db.workoutSetDao().completeIfRepsPresent("does-not-exist", 1_700_000_010_000L)

        assertEquals(CompleteSetResult.NotFound, result)
    }

    // C5 — un-completing is supported because the completion UI is a checkbox (see
    // ActiveWorkoutScreen/WorkoutSetDao.markIncomplete's own doc comment for why this is needed).
    @Test
    fun markingIncompleteClearsBothIsCompletedAndCompletedAtTogether() = runTest {
        val set = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, 100.0, 8)
        db.workoutSetDao().completeIfRepsPresent(set.id, 1_700_000_010_000L)

        db.workoutSetDao().markIncomplete(set.id)

        val persisted = db.workoutSetDao().getForWorkoutExercise(workoutExerciseId).single()
        assertTrue(!persisted.isCompleted)
        assertNull(persisted.completedAt)
    }

    // C4 — editing a completed set's values must remain possible: this DAO does not gate
    // updateWeight/updateReps on completion status, matching this slice's completed-workout
    // editing direction (see WorkoutEntity's own doc comment) rather than freezing values the
    // moment a set is checked off.
    @Test
    fun editingACompletedSetsValuesRemainsPossible() = runTest {
        val set = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, 100.0, 8)
        db.workoutSetDao().completeIfRepsPresent(set.id, 1_700_000_010_000L)

        db.workoutSetDao().updateWeight(set.id, 105.0)
        db.workoutSetDao().updateReps(set.id, 6)

        val persisted = db.workoutSetDao().getForWorkoutExercise(workoutExerciseId).single()
        assertEquals(105.0, persisted.weight)
        assertEquals(6, persisted.reps)
        // Editing values does not itself uncomplete the set.
        assertTrue(persisted.isCompleted)
    }

    @Test
    fun deletingASetRemovesItWithoutAffectingOthers() = runTest {
        val first = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, 100.0, 8)
        val second = db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, 100.0, 6)

        db.workoutSetDao().delete(first.id)

        assertEquals(listOf(second), db.workoutSetDao().getForWorkoutExercise(workoutExerciseId))
    }

    @Test
    fun deletingTheParentWorkoutExerciseCascadesToItsSets() = runTest {
        db.workoutSetDao().appendSet(UUID.randomUUID().toString(), workoutExerciseId, 100.0, 8)
        val workout = db.workoutDao().getActiveWorkout("owner-a")!!

        db.workoutDao().deleteIfActive(workout.id)

        assertEquals(emptyList<WorkoutSetEntity>(), db.workoutSetDao().getForWorkoutExercise(workoutExerciseId))
    }
}
