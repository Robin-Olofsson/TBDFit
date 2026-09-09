package com.tbdfit.phone.workout

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import com.tbdfit.phone.localaccount.LocalAccountEntity
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutExerciseDaoTest {
    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var db: AppDatabase
    private lateinit var workoutId: String
    private lateinit var exerciseId: String

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        dbName = "test-${UUID.randomUUID()}.db"
        db = AppDatabase.build(context, dbName)

        val now = System.currentTimeMillis()
        db.localAccountDao().insertIfAbsent(LocalAccountEntity(id = "owner-a", createdAt = now))
        workoutId = UUID.randomUUID().toString()
        db.workoutDao().insert(WorkoutEntity(id = workoutId, ownerId = "owner-a", status = WorkoutStatus.ACTIVE, startedAt = now, createdAt = now))
        exerciseId = UUID.randomUUID().toString()
        db.exerciseDao().insert(
            ExerciseEntity(id = exerciseId, name = "Bench Press", normalizedName = "bench press", source = ExerciseSource.CUSTOM, ownerId = null, createdAt = now),
        )
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun attachingAnExerciseToAWorkoutPersistsTheRelationship() = runTest {
        val attached = db.workoutExerciseDao().appendExercise(UUID.randomUUID().toString(), workoutId, exerciseId)

        val forWorkout = db.workoutExerciseDao().getForWorkout(workoutId)
        assertEquals(listOf(attached), forWorkout)
    }

    @Test
    fun appendedExercisesReceiveDeterministicSequentialPositions() = runTest {
        val second = UUID.randomUUID().toString()
        db.exerciseDao().insert(
            ExerciseEntity(id = second, name = "Squat", normalizedName = "squat", source = ExerciseSource.CUSTOM, ownerId = null, createdAt = System.currentTimeMillis()),
        )

        val first = db.workoutExerciseDao().appendExercise(UUID.randomUUID().toString(), workoutId, exerciseId)
        val secondEntry = db.workoutExerciseDao().appendExercise(UUID.randomUUID().toString(), workoutId, second)

        assertEquals(0, first.position)
        assertEquals(1, secondEntry.position)
        assertEquals(listOf(first, secondEntry), db.workoutExerciseDao().getForWorkout(workoutId))
    }

    @Test
    fun deletingTheParentWorkoutCascadesToItsWorkoutExercises() = runTest {
        db.workoutExerciseDao().appendExercise(UUID.randomUUID().toString(), workoutId, exerciseId)

        db.workoutDao().deleteIfActive(workoutId)

        assertEquals(emptyList<WorkoutExerciseEntity>(), db.workoutExerciseDao().getForWorkout(workoutId))
    }

    @Test
    fun exerciseRenameDoesNotAffectExistingWorkoutExerciseReferences() = runTest {
        val attached = db.workoutExerciseDao().appendExercise(UUID.randomUUID().toString(), workoutId, exerciseId)

        db.exerciseDao().rename(exerciseId, "Barbell Bench Press", "barbell bench press")

        val stillAttached = db.workoutExerciseDao().getForWorkout(workoutId).single()
        assertEquals(attached.id, stillAttached.id)
        assertEquals(exerciseId, stillAttached.exerciseId)
        assertEquals("Barbell Bench Press", db.exerciseDao().getById(exerciseId)?.name)
    }

    @Test
    fun maxPositionIsMinusOneWhenNoExercisesAttachedYet() = runTest {
        assertEquals(-1, db.workoutExerciseDao().maxPosition(workoutId))
        assertNull(db.workoutExerciseDao().getForWorkout(workoutId).firstOrNull())
    }

    // Part A hardening-pass audit (historical integrity): ExerciseDao intentionally exposes no
    // delete method — exercise deletion is not built in this slice at all. This test proves the
    // second, independent layer of protection actually fires: the exerciseId FK's
    // onDelete=RESTRICT (see WorkoutExerciseEntity) blocks a raw deletion attempt outright rather
    // than silently cascading and destroying workout history, satisfying the hard requirement that
    // removing an exercise from the catalog must never silently destroy historical workout/set
    // data. Exercised directly against the underlying SupportSQLiteDatabase, since no DAO path for
    // this exists (and should not be added merely to make this test convenient).
    @Test
    fun deletingAnExerciseReferencedByWorkoutHistoryIsRestrictedNotCascaded() = runTest {
        db.workoutExerciseDao().appendExercise(UUID.randomUUID().toString(), workoutId, exerciseId)

        val thrown = try {
            db.openHelper.writableDatabase.execSQL("DELETE FROM exercises WHERE id = '$exerciseId'")
            null
        } catch (e: SQLiteConstraintException) {
            e
        }

        assertNotNull("expected the RESTRICT foreign key to block this deletion", thrown)
        // The exercise and its historical reference both survive untouched.
        assertNotNull(db.exerciseDao().getById(exerciseId))
        assertTrue(db.workoutExerciseDao().getForWorkout(workoutId).isNotEmpty())
    }

    // The converse case: once nothing references it, RESTRICT has nothing to restrict — deletion
    // (were a DAO method to ever exist for it) would be a normal operation, not a special one. Not
    // exercised via a DAO method here since none exists; included only to make the FK's actual
    // scope explicit rather than leaving "RESTRICT always blocks deletion" as an implied overclaim.
    @Test
    fun deletingAnUnreferencedExerciseSucceeds() = runTest {
        val unreferencedId = UUID.randomUUID().toString()
        db.exerciseDao().insert(
            ExerciseEntity(id = unreferencedId, name = "Squat", normalizedName = "squat", source = ExerciseSource.CUSTOM, ownerId = null, createdAt = System.currentTimeMillis()),
        )

        db.openHelper.writableDatabase.execSQL("DELETE FROM exercises WHERE id = '$unreferencedId'")

        assertNull(db.exerciseDao().getById(unreferencedId))
    }

    // Slice 3 — attach flow (B9: "attaching Exercise creates WorkoutExercise",
    // "position/order deterministic"). observeAttachedExercises is the join projection
    // ActiveWorkoutScreen actually displays from, so it's tested directly, not just the raw
    // WorkoutExerciseEntity rows.
    @Test
    fun observeAttachedExercisesJoinsInTheCurrentExerciseNameInPersistedOrder() = runTest {
        val squatId = UUID.randomUUID().toString()
        db.exerciseDao().insert(
            ExerciseEntity(id = squatId, name = "Squat", normalizedName = "squat", source = ExerciseSource.CUSTOM, ownerId = null, createdAt = System.currentTimeMillis()),
        )
        db.workoutExerciseDao().appendExercise(UUID.randomUUID().toString(), workoutId, exerciseId)
        db.workoutExerciseDao().appendExercise(UUID.randomUUID().toString(), workoutId, squatId)

        val attached = db.workoutExerciseDao().observeAttachedExercises(workoutId).first()

        assertEquals(listOf("Bench Press", "Squat"), attached.map { it.name })
        assertEquals(listOf(0, 1), attached.map { it.position })
    }

    @Test
    fun observeAttachedExercisesReflectsARenameOnTheNextEmissionNotASnapshot() = runTest {
        db.workoutExerciseDao().appendExercise(UUID.randomUUID().toString(), workoutId, exerciseId)

        db.exerciseDao().rename(exerciseId, "Barbell Bench Press", "barbell bench press")

        val attached = db.workoutExerciseDao().observeAttachedExercises(workoutId).first()
        assertEquals("Barbell Bench Press", attached.single().name)
    }

    // Duplicate attachment semantics (B6 — open, provisional, no constraint added; see
    // observeAttachedExercises's own doc comment): proves the smallest-reversible-behavior choice
    // actually behaves as documented — the same Exercise can be attached twice.
    @Test
    fun theSameExerciseCanBeAttachedToOneWorkoutMoreThanOnce() = runTest {
        db.workoutExerciseDao().appendExercise(UUID.randomUUID().toString(), workoutId, exerciseId)
        db.workoutExerciseDao().appendExercise(UUID.randomUUID().toString(), workoutId, exerciseId)

        val attached = db.workoutExerciseDao().observeAttachedExercises(workoutId).first()

        assertEquals(2, attached.size)
        assertTrue(attached.all { it.exerciseId == exerciseId })
    }
}
