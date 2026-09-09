package com.tbdfit.phone.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tbdfit.phone.localaccount.LocalAccountEntity
import com.tbdfit.phone.localstorage.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
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
class WorkoutDaoTest {
    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "test-${UUID.randomUUID()}.db"
        db = AppDatabase.build(context, dbName)
        // Local-account-ownership correction: workouts.ownerId is now a real foreign key against
        // local_accounts — every owner this test file uses must exist first.
        runBlocking {
            db.localAccountDao().insertIfAbsent(LocalAccountEntity(id = "owner-a", createdAt = 0L))
            db.localAccountDao().insertIfAbsent(LocalAccountEntity(id = "owner-b", createdAt = 0L))
        }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun newActiveWorkout(startedAt: Long = System.currentTimeMillis(), ownerId: String = "owner-a") = WorkoutEntity(
        id = UUID.randomUUID().toString(),
        ownerId = ownerId,
        status = WorkoutStatus.ACTIVE,
        startedAt = startedAt,
        createdAt = startedAt,
    )

    @Test
    fun startingAWorkoutCreatesAnActiveRowWithStartedAtAndNoCompletedAt() = runTest {
        val workout = newActiveWorkout(startedAt = 1_700_000_000_000L)

        val result = db.workoutDao().startWorkoutIfNoneActive(workout)

        assertTrue(result is StartWorkoutResult.Started)
        val persisted = db.workoutDao().getById(workout.id)
        assertNotNull(persisted)
        assertEquals(WorkoutStatus.ACTIVE, persisted?.status)
        assertEquals(1_700_000_000_000L, persisted?.startedAt)
        assertNull(persisted?.completedAt)
    }

    @Test
    fun secondSequentialStartIsRejectedWhileFirstIsActive() = runTest {
        val first = db.workoutDao().startWorkoutIfNoneActive(newActiveWorkout())
        assertTrue(first is StartWorkoutResult.Started)
        val firstWorkout = (first as StartWorkoutResult.Started).workout

        val second = db.workoutDao().startWorkoutIfNoneActive(newActiveWorkout())

        assertTrue(second is StartWorkoutResult.AlreadyActive)
        assertEquals(firstWorkout.id, (second as StartWorkoutResult.AlreadyActive).existing.id)
        // No second row was silently created.
        assertEquals(1, countActiveRows())
    }

    // The load-bearing concurrency proof: launches many real, concurrently-executing attempts
    // through the supported path (startWorkoutIfNoneActive) on real threads (Dispatchers.IO, not
    // runTest's single-threaded virtual scheduler) against the same database, and asserts exactly
    // one succeeds. This exercises the @Transaction atomicity itself, not just its happy path — see
    // WorkoutDao's doc comment for why this must not depend on the UI, one repository instance, or
    // a process-local Mutex.
    @Test
    fun concurrentStartAttemptsThroughTheSupportedPathProduceExactlyOneActiveWorkout() = runBlocking {
        val attempts = 25

        val results = (1..attempts).map {
            async(Dispatchers.IO) {
                db.workoutDao().startWorkoutIfNoneActive(newActiveWorkout())
            }
        }.awaitAll()

        assertEquals(1, results.count { it is StartWorkoutResult.Started })
        assertEquals(attempts - 1, results.count { it is StartWorkoutResult.AlreadyActive })
        assertEquals(1, countActiveRows())
    }

    // Deterministic recovery behavior, not enforcement (see WorkoutDao's doc comment): if
    // unexpected/corrupt data somehow contains more than one ACTIVE row — inserted here directly,
    // bypassing startWorkoutIfNoneActive entirely, since that path is what prevents this in
    // practice — the restore query must still resolve to exactly one row, deterministically.
    @Test
    fun restoreQueryDeterministicallyResumesTheMostRecentlyStartedRowIfMultipleActiveRowsExist() = runTest {
        val older = newActiveWorkout(startedAt = 1_700_000_000_000L)
        val newer = newActiveWorkout(startedAt = 1_700_000_005_000L)
        db.workoutDao().insert(older)
        db.workoutDao().insert(newer)

        val resumed = db.workoutDao().getActiveWorkout(requireNotNull(older.ownerId))

        assertEquals(newer.id, resumed?.id)
    }

    // Local-workout-ownership audit: the single-active-workout invariant is per-owner, not
    // per-device — a different account must be able to start their own workout even while another
    // account's is still ACTIVE, and must never see it.
    @Test
    fun differentOwnersCanEachHaveTheirOwnActiveWorkoutSimultaneously() = runTest {
        val ownerAWorkout = (db.workoutDao().startWorkoutIfNoneActive(newActiveWorkout(ownerId = "owner-a")) as StartWorkoutResult.Started).workout
        val ownerBResult = db.workoutDao().startWorkoutIfNoneActive(newActiveWorkout(ownerId = "owner-b"))

        assertTrue(ownerBResult is StartWorkoutResult.Started)
        val ownerBWorkout = (ownerBResult as StartWorkoutResult.Started).workout

        assertEquals(ownerAWorkout.id, db.workoutDao().getActiveWorkout("owner-a")?.id)
        assertEquals(ownerBWorkout.id, db.workoutDao().getActiveWorkout("owner-b")?.id)
        // Neither owner's query ever surfaces the other's row.
        assertTrue(db.workoutDao().getActiveWorkout("owner-a")?.id != ownerBWorkout.id)
    }

    @Test
    fun startingAsOneOwnerIsNotBlockedByAnotherOwnersStillActiveWorkout() = runTest {
        db.workoutDao().insert(newActiveWorkout(ownerId = "owner-a"))

        val result = db.workoutDao().startWorkoutIfNoneActive(newActiveWorkout(ownerId = "owner-b"))

        assertTrue("owner-b must be able to start their own workout despite owner-a's still being ACTIVE", result is StartWorkoutResult.Started)
    }

    @Test
    fun completingAnActiveWorkoutSetsCompletedAtAndLeavesLastModifiedAtNull() = runTest {
        val workout = newActiveWorkout()
        db.workoutDao().insert(workout)
        val completedAt = 1_700_000_010_000L

        val rowsUpdated = db.workoutDao().completeIfActive(workout.id, completedAt)

        assertEquals(1, rowsUpdated)
        val persisted = db.workoutDao().getById(workout.id)
        assertEquals(WorkoutStatus.COMPLETED, persisted?.status)
        assertEquals(completedAt, persisted?.completedAt)
        // lastModifiedAt tracks post-completion corrections only, per the design doc's
        // Completed-Workout Editing section — ordinary completion is not itself a "correction",
        // so it must remain null here.
        assertNull(persisted?.lastModifiedAt)
    }

    @Test
    fun completingAnAlreadyCompletedWorkoutIsRejectedAsADeterministicNoOp() = runTest {
        val workout = newActiveWorkout()
        db.workoutDao().insert(workout)
        db.workoutDao().completeIfActive(workout.id, 1_700_000_010_000L)

        // A second completion attempt must not silently mutate completedAt again.
        val secondAttemptRows = db.workoutDao().completeIfActive(workout.id, 1_700_000_099_999L)

        assertEquals(0, secondAttemptRows)
        val persisted = db.workoutDao().getById(workout.id)
        assertEquals(1_700_000_010_000L, persisted?.completedAt)
    }

    @Test
    fun discardingAnActiveWorkoutDeletesItTransactionally() = runTest {
        val workout = newActiveWorkout()
        db.workoutDao().insert(workout)

        val rowsDeleted = db.workoutDao().deleteIfActive(workout.id)

        assertEquals(1, rowsDeleted)
        assertNull(db.workoutDao().getById(workout.id))
    }

    @Test
    fun discardingACompletedWorkoutIsRejectedNotDeleted() = runTest {
        val workout = newActiveWorkout()
        db.workoutDao().insert(workout)
        db.workoutDao().completeIfActive(workout.id, 1_700_000_010_000L)

        val rowsDeleted = db.workoutDao().deleteIfActive(workout.id)

        assertEquals(0, rowsDeleted)
        assertNotNull(db.workoutDao().getById(workout.id))
    }

    private suspend fun countActiveRows(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM workouts WHERE status = 'ACTIVE'").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
