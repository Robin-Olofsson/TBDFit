package com.tbdfit.phone.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tbdfit.phone.localstorage.AppDatabase
import com.tbdfit.phone.localstorage.LocalRecordEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

// Tests the application responsibility (LocalRecordSyncCoordinator) against the narrow
// LocalRecordRemoteStore boundary via a fake — never against real Supabase classes. Room's own
// durability is already covered by LocalRecordDaoTest; these tests cover only what the
// coordinator adds: finding pending rows, marking them synced, and never touching local state on
// failure.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalRecordSyncCoordinatorTest {
    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "sync-test-${UUID.randomUUID()}.db"
        db = AppDatabase.build(context, dbName)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun newRecord() = LocalRecordEntity(
        id = UUID.randomUUID().toString(),
        createdAt = 1_700_000_000_000L,
        value = "test-value",
    )

    @Test
    fun pendingRecordIsSubmittedToRemoteStore() = runTest {
        val record = newRecord()
        db.localRecordDao().insert(record)
        val remote = FakeLocalRecordRemoteStore()

        LocalRecordSyncCoordinator(db.localRecordDao(), remote).sync()

        assertEquals(listOf(record.id), remote.upsertAttempts.map { it.id })
    }

    @Test
    fun successfulRemoteWriteMarksLocalRecordSynced() = runTest {
        val record = newRecord()
        db.localRecordDao().insert(record)
        val remote = FakeLocalRecordRemoteStore()

        LocalRecordSyncCoordinator(db.localRecordDao(), remote).sync()

        val stored = db.localRecordDao().getAll().first().first { it.id == record.id }
        assertNotNull(stored.syncedAt)
    }

    @Test
    fun failedRemoteWriteLeavesLocalRecordPending() = runTest {
        val record = newRecord()
        db.localRecordDao().insert(record)
        val remote = FakeLocalRecordRemoteStore(shouldSucceed = { false })

        LocalRecordSyncCoordinator(db.localRecordDao(), remote).sync()

        val stored = db.localRecordDao().getAll().first().first { it.id == record.id }
        assertNull(stored.syncedAt)
    }

    @Test
    fun retryingAfterFailureDoesNotCreateASecondLogicalRemoteRecord() = runTest {
        val record = newRecord()
        db.localRecordDao().insert(record)

        var attempt = 0
        val remote = FakeLocalRecordRemoteStore(shouldSucceed = { attempt++ > 0 })
        val coordinator = LocalRecordSyncCoordinator(db.localRecordDao(), remote)

        coordinator.sync() // fails
        coordinator.sync() // retries and succeeds

        assertEquals(2, remote.upsertAttempts.size) // proves the retry actually happened
        assertEquals(1, remote.remoteRowsById.size) // proves only one logical remote row exists
        assertNotNull(db.localRecordDao().getAll().first().first { it.id == record.id }.syncedAt)
    }

    @Test
    fun concurrentSyncCallsResultInOnlyOneEffectiveUpsertAttempt() = runTest {
        val record = newRecord()
        db.localRecordDao().insert(record)

        // Deterministically forces genuine overlap rather than accidental sequential execution:
        // the first upsert call blocks until the test explicitly releases it, so a second sync()
        // launched while the first is still in flight must actually wait on the coordinator's
        // Mutex, not just happen to run after the first completes.
        val upsertAttempts = mutableListOf<String>()
        val firstCallStarted = CompletableDeferred<Unit>()
        val releaseFirstCall = CompletableDeferred<Unit>()
        val blockingRemoteStore = object : LocalRecordRemoteStore {
            override suspend fun upsert(record: LocalRecordEntity): Result<Unit> {
                upsertAttempts.add(record.id)
                if (upsertAttempts.size == 1) {
                    firstCallStarted.complete(Unit)
                    releaseFirstCall.await()
                }
                return Result.success(Unit)
            }
        }
        val coordinator = LocalRecordSyncCoordinator(db.localRecordDao(), blockingRemoteStore)

        val syncA = launch { coordinator.sync() }
        firstCallStarted.await() // A is inside its remote call, still holding the coordinator's mutex
        val syncB = launch { coordinator.sync() } // must block on the mutex until A releases it

        releaseFirstCall.complete(Unit)
        syncA.join()
        syncB.join()

        // Proves single-flight, not merely idempotency: B never even attempted a second upsert,
        // because by the time it acquired the mutex and re-read pending state, A had already
        // marked the record synced.
        assertEquals(1, upsertAttempts.size)
        assertNotNull(db.localRecordDao().getAll().first().first { it.id == record.id }.syncedAt)
    }

    @Test
    fun failedSyncDoesNotDeleteOrLoseTheLocalRecord() = runTest {
        val record = newRecord()
        db.localRecordDao().insert(record)
        val remote = FakeLocalRecordRemoteStore(shouldSucceed = { false })

        LocalRecordSyncCoordinator(db.localRecordDao(), remote).sync()

        val allRecords = db.localRecordDao().getAll().first()
        assertTrue(allRecords.any { it.id == record.id })
    }
}
