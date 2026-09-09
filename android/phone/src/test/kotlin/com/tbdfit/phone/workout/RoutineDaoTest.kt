package com.tbdfit.phone.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tbdfit.phone.localaccount.LocalAccountEntity
import com.tbdfit.phone.localstorage.AppDatabase
import kotlinx.coroutines.flow.first
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

// Slice A of docs/architecture/program-routine-first-slice-design.md — DAO-level coverage for the
// real Routine/RoutineExercise/RoutinePlannedSet persistence, independent of any Program concept
// (which does not exist yet).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutineDaoTest {
    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var db: AppDatabase

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        dbName = "test-${UUID.randomUUID()}.db"
        db = AppDatabase.build(context, dbName)
        db.localAccountDao().insertIfAbsent(LocalAccountEntity(id = "owner-a", createdAt = 0L))
        db.localAccountDao().insertIfAbsent(LocalAccountEntity(id = "owner-b", createdAt = 0L))
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun newRoutine(ownerId: String = "owner-a", name: String = "Push Day") = RoutineEntity(
        id = UUID.randomUUID().toString(),
        ownerId = ownerId,
        name = name,
        createdAt = System.currentTimeMillis(),
    )

    @Test
    fun aRoutinePersistsWithNoProgramInvolvementWhatsoever() = runTest {
        val routine = newRoutine()
        db.routineDao().insert(routine)

        assertEquals(routine, db.routineDao().getById(routine.id))
    }

    @Test
    fun getVisibleToIsScopedToTheOwner() = runTest {
        db.routineDao().insert(newRoutine(ownerId = "owner-a", name = "Owner A's Routine"))
        db.routineDao().insert(newRoutine(ownerId = "owner-b", name = "Owner B's Routine"))

        val visibleToA = db.routineDao().getVisibleTo("owner-a").first()

        assertEquals(listOf("Owner A's Routine"), visibleToA.map { it.name })
    }

    @Test
    fun renameUpdatesNameAndLastModifiedAt() = runTest {
        val routine = newRoutine()
        db.routineDao().insert(routine)
        assertNull(routine.lastModifiedAt)

        db.routineDao().rename(routine.id, "Push Day (Heavy)", 1_700_000_000_000L)

        val renamed = db.routineDao().getById(routine.id)
        assertEquals("Push Day (Heavy)", renamed?.name)
        assertEquals(1_700_000_000_000L, renamed?.lastModifiedAt)
    }

    @Test
    fun deleteOwnedByRejectsAMismatchedOwnerAndLeavesTheRoutineIntact() = runTest {
        val routine = newRoutine(ownerId = "owner-a")
        db.routineDao().insert(routine)

        val rowsDeleted = db.routineDao().deleteOwnedBy(routine.id, "owner-b")

        assertEquals(0, rowsDeleted)
        assertEquals(routine, db.routineDao().getById(routine.id))
    }

    @Test
    fun deleteOwnedBySucceedsForTheRealOwner() = runTest {
        val routine = newRoutine(ownerId = "owner-a")
        db.routineDao().insert(routine)

        val rowsDeleted = db.routineDao().deleteOwnedBy(routine.id, "owner-a")

        assertEquals(1, rowsDeleted)
        assertNull(db.routineDao().getById(routine.id))
    }

    @Test
    fun deletingARoutineCascadesToItsExercisesAndPlannedSets() = runTest {
        val routine = newRoutine()
        db.routineDao().insert(routine)
        val exerciseId = UUID.randomUUID().toString()
        db.exerciseDao().insert(
            ExerciseEntity(id = exerciseId, name = "Bench Press", normalizedName = "bench press", source = ExerciseSource.CUSTOM, ownerId = null, createdAt = 0L),
        )
        val routineExercise = db.routineExerciseDao().appendExercise(UUID.randomUUID().toString(), routine.id, exerciseId)
        db.routinePlannedSetDao().appendPlannedSet(UUID.randomUUID().toString(), routineExercise.id, plannedReps = 8, plannedWeight = 80.0)

        db.routineDao().deleteOwnedBy(routine.id, "owner-a")

        assertEquals(emptyList<RoutineExerciseEntity>(), db.routineExerciseDao().getForRoutine(routine.id))
        assertEquals(emptyList<RoutinePlannedSetEntity>(), db.routinePlannedSetDao().getForRoutineExercise(routineExercise.id))
    }

    @Test
    fun routineExercisesReceiveDeterministicSequentialPositions() = runTest {
        val routine = newRoutine()
        db.routineDao().insert(routine)
        val benchId = UUID.randomUUID().toString()
        val squatId = UUID.randomUUID().toString()
        db.exerciseDao().insert(ExerciseEntity(id = benchId, name = "Bench Press", normalizedName = "bench press", source = ExerciseSource.CUSTOM, ownerId = null, createdAt = 0L))
        db.exerciseDao().insert(ExerciseEntity(id = squatId, name = "Squat", normalizedName = "squat", source = ExerciseSource.CUSTOM, ownerId = null, createdAt = 0L))

        val first = db.routineExerciseDao().appendExercise(UUID.randomUUID().toString(), routine.id, benchId)
        val second = db.routineExerciseDao().appendExercise(UUID.randomUUID().toString(), routine.id, squatId)

        assertEquals(0, first.position)
        assertEquals(1, second.position)
        assertEquals(listOf(first, second), db.routineExerciseDao().getForRoutine(routine.id))
    }

    @Test
    fun observeAttachedExercisesJoinsInTheCurrentExerciseNameAndReflectsARenameNotASnapshot() = runTest {
        val routine = newRoutine()
        db.routineDao().insert(routine)
        val exerciseId = UUID.randomUUID().toString()
        db.exerciseDao().insert(ExerciseEntity(id = exerciseId, name = "Bench Press", normalizedName = "bench press", source = ExerciseSource.CUSTOM, ownerId = null, createdAt = 0L))
        db.routineExerciseDao().appendExercise(UUID.randomUUID().toString(), routine.id, exerciseId)

        assertEquals("Bench Press", db.routineExerciseDao().observeAttachedExercises(routine.id).first().single().name)

        db.exerciseDao().rename(exerciseId, "Barbell Bench Press", "barbell bench press")

        assertEquals("Barbell Bench Press", db.routineExerciseDao().observeAttachedExercises(routine.id).first().single().name)
    }

    @Test
    fun plannedSetsReceiveDeterministicSequentialPositionsAndPersistTargets() = runTest {
        val routine = newRoutine()
        db.routineDao().insert(routine)
        val exerciseId = UUID.randomUUID().toString()
        db.exerciseDao().insert(ExerciseEntity(id = exerciseId, name = "Bench Press", normalizedName = "bench press", source = ExerciseSource.CUSTOM, ownerId = null, createdAt = 0L))
        val routineExercise = db.routineExerciseDao().appendExercise(UUID.randomUUID().toString(), routine.id, exerciseId)

        val first = db.routinePlannedSetDao().appendPlannedSet(UUID.randomUUID().toString(), routineExercise.id, plannedReps = 8, plannedWeight = 80.0)
        val second = db.routinePlannedSetDao().appendPlannedSet(UUID.randomUUID().toString(), routineExercise.id, plannedReps = 6, plannedWeight = 85.0)

        assertEquals(0, first.position)
        assertEquals(1, second.position)
        assertEquals(listOf(first, second), db.routinePlannedSetDao().getForRoutineExercise(routineExercise.id))
    }

    @Test
    fun aPlannedSetMayOmitEitherOrBothValues() = runTest {
        val routine = newRoutine()
        db.routineDao().insert(routine)
        val exerciseId = UUID.randomUUID().toString()
        db.exerciseDao().insert(ExerciseEntity(id = exerciseId, name = "Pull-Up", normalizedName = "pull-up", source = ExerciseSource.CUSTOM, ownerId = null, createdAt = 0L))
        val routineExercise = db.routineExerciseDao().appendExercise(UUID.randomUUID().toString(), routine.id, exerciseId)

        val bodyweightSet = db.routinePlannedSetDao().appendPlannedSet(UUID.randomUUID().toString(), routineExercise.id, plannedReps = 10, plannedWeight = null)

        assertNull(bodyweightSet.plannedWeight)
        assertEquals(10, bodyweightSet.plannedReps)
    }

    @Test
    fun negativePlannedRepsIsRejected() = runTest {
        val routine = newRoutine()
        db.routineDao().insert(routine)
        val exerciseId = UUID.randomUUID().toString()
        db.exerciseDao().insert(ExerciseEntity(id = exerciseId, name = "Bench Press", normalizedName = "bench press", source = ExerciseSource.CUSTOM, ownerId = null, createdAt = 0L))
        val routineExercise = db.routineExerciseDao().appendExercise(UUID.randomUUID().toString(), routine.id, exerciseId)

        val result = runCatching {
            db.routinePlannedSetDao().appendPlannedSet(UUID.randomUUID().toString(), routineExercise.id, plannedReps = -1, plannedWeight = null)
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun negativePlannedWeightIsRejected() = runTest {
        val routine = newRoutine()
        db.routineDao().insert(routine)
        val exerciseId = UUID.randomUUID().toString()
        db.exerciseDao().insert(ExerciseEntity(id = exerciseId, name = "Bench Press", normalizedName = "bench press", source = ExerciseSource.CUSTOM, ownerId = null, createdAt = 0L))
        val routineExercise = db.routineExerciseDao().appendExercise(UUID.randomUUID().toString(), routine.id, exerciseId)

        val result = runCatching {
            db.routinePlannedSetDao().appendPlannedSet(UUID.randomUUID().toString(), routineExercise.id, plannedReps = null, plannedWeight = -5.0)
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    // Cross-account isolation — a custom Exercise belonging to owner-b must not be attachable to
    // owner-a's Routine via the same appendExercise path a real UI would use. The FK alone cannot
    // express this (see program-routine-first-slice-design.md's Cross-account isolation section) —
    // this proves the actual mitigation (the exercise picker only ever offering
    // ExerciseDao.getVisibleTo(ownerId) results) by demonstrating owner-b's custom exercise is
    // simply never visible to owner-a in the first place.
    @Test
    fun anotherOwnersCustomExerciseIsNeverOfferedToThisOwnersRoutineAuthoring() = runTest {
        val ownerBExerciseId = UUID.randomUUID().toString()
        db.exerciseDao().insert(
            ExerciseEntity(id = ownerBExerciseId, name = "Owner B's Move", normalizedName = "owner b's move", source = ExerciseSource.CUSTOM, ownerId = "owner-b", createdAt = 0L),
        )

        val visibleToA = db.exerciseDao().getVisibleTo("owner-a").first()

        assertTrue(visibleToA.none { it.id == ownerBExerciseId })
    }

    @Test
    fun builtInExercisesRemainUsableInAnyOwnersRoutine() = runTest {
        db.exerciseDao().insertBuiltInIfAbsent(
            ExerciseEntity(id = "builtin_bench_press", name = "Bench Press", normalizedName = "bench press", source = ExerciseSource.BUILT_IN, ownerId = null, createdAt = 0L),
        )

        val visibleToA = db.exerciseDao().getVisibleTo("owner-a").first()
        val visibleToB = db.exerciseDao().getVisibleTo("owner-b").first()

        assertTrue(visibleToA.any { it.id == "builtin_bench_press" })
        assertTrue(visibleToB.any { it.id == "builtin_bench_press" })
    }
}
