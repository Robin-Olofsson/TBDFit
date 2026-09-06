package com.tbdfit.wear.replication

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tbdfit.wear.localstorage.AppDatabase
import com.tbdfit.wear.localstorage.LocalRecordEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

// Tests the application responsibility (WearReplicationCoordinator) against the narrow
// WearRecordTransport boundary via a fake — never against real Play Services Wearable classes.
// Whether a real DataClient/MessageClient actually deliver anything across a paired connection can
// only be verified on a real emulator/device pair (see the project report's manual verification
// steps) — that platform behavior is deliberately out of scope for this test.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WearReplicationCoordinatorTest {
    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "replication-test-${UUID.randomUUID()}.db"
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
    fun pendingRecordIsSubmittedToTransport() = runTest {
        val record = newRecord()
        db.localRecordDao().insert(record)
        val transport = FakeWearRecordTransport()

        WearReplicationCoordinator(db.localRecordDao(), transport).replicate()

        assertEquals(listOf(record.id), transport.sendAttempts.map { it.id })
    }

    @Test
    fun coordinatorDoesNotMarkSyncedItself() = runTest {
        // Only PhoneAckListenerService (driven by an actual ack) may mark phoneSyncedAt — a
        // successful send only means "queued for delivery," not "Phone has it." This is the
        // opposite of :phone's LocalRecordSyncCoordinator, where the HTTP response itself was the
        // confirmation, and the distinction matters enough to assert explicitly.
        val record = newRecord()
        db.localRecordDao().insert(record)
        val transport = FakeWearRecordTransport()

        WearReplicationCoordinator(db.localRecordDao(), transport).replicate()

        val stored = db.localRecordDao().getPendingReplication()
        assertEquals(1, stored.size)
        assertNull(stored.first().phoneSyncedAt)
    }

    @Test
    fun failedSendLeavesRecordEligibleForRetry() = runTest {
        val record = newRecord()
        db.localRecordDao().insert(record)
        val transport = FakeWearRecordTransport(shouldSucceed = { false })

        WearReplicationCoordinator(db.localRecordDao(), transport).replicate()
        WearReplicationCoordinator(db.localRecordDao(), transport).replicate()

        assertEquals(2, transport.sendAttempts.size)
        assertEquals(1, db.localRecordDao().getPendingReplication().size)
    }
}
