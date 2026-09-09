package com.tbdfit.phone.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tbdfit.phone.localaccount.LocalAccountEntity
import com.tbdfit.phone.localstorage.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

// A close-instance-then-reopen-a-fresh-instance-against-the-same-file test, following the exact
// pattern already established and documented in LocalRecordDaoTest — the strongest automated
// proxy for process death available without a real Wear OS/Android emulator or device in this
// environment. It is NOT a substitute for real on-device process-death verification (see the
// design doc's Test Strategy: "Manual process-death tests").
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutDurabilityTest {
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

    @Test
    fun anEntireWorkoutGraphSurvivesCloseAndReopenAgainstTheSameFile() = runTest {
        val workoutId: String
        val workoutExerciseId: String
        val setId: String

        // Instance A: build the graph a real active-workout session would produce, then close —
        // simulates the object graph going away.
        run {
            val dbA = AppDatabase.build(context, dbName)
            val now = System.currentTimeMillis()

            val exerciseId = UUID.randomUUID().toString()
            dbA.exerciseDao().insert(
                ExerciseEntity(id = exerciseId, name = "Bench Press", normalizedName = "bench press", source = ExerciseSource.CUSTOM, ownerId = null, createdAt = now),
            )
            dbA.localAccountDao().insertIfAbsent(LocalAccountEntity(id = "owner-a", createdAt = now))

            val startResult = dbA.workoutDao().startWorkoutIfNoneActive(
                WorkoutEntity(id = UUID.randomUUID().toString(), ownerId = "owner-a", status = WorkoutStatus.ACTIVE, startedAt = now, createdAt = now),
            )
            workoutId = (startResult as StartWorkoutResult.Started).workout.id

            workoutExerciseId = dbA.workoutExerciseDao()
                .appendExercise(UUID.randomUUID().toString(), workoutId, exerciseId).id

            setId = dbA.workoutSetDao()
                .appendSet(UUID.randomUUID().toString(), workoutExerciseId, weight = 100.0, reps = 8).id
            dbA.workoutSetDao().markCompleted(setId, now)

            dbA.close()
        }

        // Instance B: a brand-new database object opened against the same on-disk file.
        val dbB = AppDatabase.build(context, dbName)

        val restoredWorkout = dbB.workoutDao().getActiveWorkout("owner-a")
        assertEquals(workoutId, restoredWorkout?.id)
        assertEquals(WorkoutStatus.ACTIVE, restoredWorkout?.status)

        val restoredExercises = dbB.workoutExerciseDao().getForWorkout(workoutId)
        assertEquals(1, restoredExercises.size)
        assertEquals(workoutExerciseId, restoredExercises.single().id)

        val restoredSets = dbB.workoutSetDao().getForWorkoutExercise(workoutExerciseId)
        assertEquals(1, restoredSets.size)
        val restoredSet = restoredSets.single()
        assertEquals(setId, restoredSet.id)
        assertEquals(100.0, restoredSet.weight)
        assertEquals(8, restoredSet.reps)
        assertEquals(true, restoredSet.isCompleted)

        dbB.close()
    }
}
