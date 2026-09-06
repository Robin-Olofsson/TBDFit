package com.tbdfit.phone.wearreplication

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

// Tests the application responsibility (WearReplicationReceiver) against the narrow PhoneAckSender
// boundary via a fake, and against real Room (Robolectric) — never against real Play Services
// Wearable classes. Whether a real WearReplicaListenerService actually receives a DataEvent from a
// paired watch can only be verified on a real emulator/device pair (see the project report's
// manual verification steps).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WearReplicationReceiverTest {
    private lateinit var context: Context
    private lateinit var dbName: String
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "wear-replica-test-${UUID.randomUUID()}.db"
        db = AppDatabase.build(context, dbName)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun newRecord(id: String = UUID.randomUUID().toString()) = ReceivedWearRecord(
        id = id,
        createdAt = 1_700_000_000_000L,
        value = "from-wear",
    )

    @Test
    fun receivingARecordPersistsIt() = runTest {
        val record = newRecord()
        val receiver = WearReplicationReceiver(db.wearReplicaDao(), FakePhoneAckSender())

        receiver.receive(record)

        val all = db.wearReplicaDao().getAll().first()
        assertEquals(1, all.size)
        assertEquals(record.id, all.first().id)
    }

    @Test
    fun duplicateDeliveryProducesExactlyOneLogicalRecord() = runTest {
        val record = newRecord()
        val receiver = WearReplicationReceiver(db.wearReplicaDao(), FakePhoneAckSender())

        receiver.receive(record)
        receiver.receive(record) // simulates a retry after a lost ack

        val all = db.wearReplicaDao().getAll().first()
        assertEquals(1, all.size)
    }

    @Test
    fun ackIsSentOnlyAfterPersistenceSucceeds() = runTest {
        val record = newRecord()
        var recordExistedInRoomAtAckTime = false
        val ackSender = FakePhoneAckSender(onSendAck = {
            recordExistedInRoomAtAckTime = db.wearReplicaDao().getAll().first().any { it.id == record.id }
        })
        val receiver = WearReplicationReceiver(db.wearReplicaDao(), ackSender)

        receiver.receive(record)

        assertTrue(recordExistedInRoomAtAckTime)
        assertEquals(1, ackSender.ackAttempts.size)
    }

    @Test
    fun ackIsSentAgainForAnAlreadyPersistedRecord() = runTest {
        // Wear may retry because it missed an earlier ack even though Phone already has the
        // record — Phone should still ack, since it genuinely has the record either way.
        val record = newRecord()
        val ackSender = FakePhoneAckSender()
        val receiver = WearReplicationReceiver(db.wearReplicaDao(), ackSender)

        receiver.receive(record)
        receiver.receive(record)

        assertEquals(2, ackSender.ackAttempts.size)
    }
}
