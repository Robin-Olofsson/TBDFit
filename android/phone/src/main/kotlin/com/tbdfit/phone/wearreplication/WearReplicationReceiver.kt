package com.tbdfit.phone.wearreplication

import android.util.Log

private const val LOG_TAG = "TBDFit.replication"

// A record as decoded off the wire, before it becomes a WearReplicaEntity. Kept separate from the
// entity so this class doesn't need to know Room annotations, and separate from any Wear-side type
// (no shared module) — structurally equivalent by convention, not by shared code.
data class ReceivedWearRecord(
    val id: String,
    val createdAt: Long,
    val value: String,
)

// The smallest purpose-scoped receiving responsibility for this capability: persist the record,
// then acknowledge — never the other way around. This is what "acknowledged" actually means (see
// PhoneAckSender/WearReplicaDao): Phone has durably committed the row to Room before anything is
// sent back to Wear. If Phone dies after the Room commit but before the ack is sent/delivered,
// Wear simply retries later; insertIfAbsent's idempotency makes that safe, and this class still
// sends the ack for an already-existing row (Phone definitely has it, so it should still tell Wear
// so, regardless of whether this particular delivery was the first).
//
// No Activity/Composable calls into Room or a transport-specific API directly — the real transport
// boundary (WearReplicaListenerService) only decodes the raw platform event into a
// ReceivedWearRecord and hands off to this class.
class WearReplicationReceiver(
    private val dao: WearReplicaDao,
    private val ackSender: PhoneAckSender,
) {
    suspend fun receive(record: ReceivedWearRecord) {
        dao.insertIfAbsent(
            WearReplicaEntity(
                id = record.id,
                createdAt = record.createdAt,
                value = record.value,
                receivedAt = System.currentTimeMillis(),
            ),
        )
        Log.d(LOG_TAG, "persisted id=${record.id}")

        ackSender.sendAck(record.id)
            .onSuccess { Log.d(LOG_TAG, "ack sent id=${record.id}") }
            .onFailure { e -> Log.w(LOG_TAG, "ack failed id=${record.id} exceptionType=${e::class.simpleName}") }
    }
}
