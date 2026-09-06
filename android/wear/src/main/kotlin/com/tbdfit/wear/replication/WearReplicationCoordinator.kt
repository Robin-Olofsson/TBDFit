package com.tbdfit.wear.replication

import android.util.Log
import com.tbdfit.wear.localstorage.LocalRecordDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val LOG_TAG = "TBDFit.replication"

// The one application-level responsibility for this capability: find locally-pending (not yet
// Phone-acknowledged) records and hand each to the transport. It does NOT mark anything as
// acknowledged itself — a successful send only means the record was queued for delivery, not that
// Phone has it. Acknowledgement is applied later, out of band, when PhoneAckListenerService
// receives Phone's ack and calls dao.markPhoneSynced directly.
//
// A record already sent but not yet acknowledged may be resent by a later replicate() call before
// its ack arrives (there is no third "in-flight" state — see LocalRecordEntity's phoneSyncedAt
// comment). This is deliberate, not an oversight: redundant sends are safe, because Phone's
// receiving path is idempotent (WearReplicaDao.insertIfAbsent) and the underlying Data Layer
// DataItem write for the same path/content is itself cheap and effectively a no-op.
//
// The Mutex makes replicate() single-flight for this one coordinator instance, for the same reason
// LocalRecordSyncCoordinator (:phone) has one — not a generic lock/sync engine, scoped to exactly
// this coordinator's own operation.
class WearReplicationCoordinator(
    private val dao: LocalRecordDao,
    private val transport: WearRecordTransport,
) {
    private val mutex = Mutex()

    suspend fun replicate() = mutex.withLock {
        val pending = dao.getPendingReplication()
        Log.d(LOG_TAG, "replicate requested pendingCount=${pending.size}")

        pending.forEach { record ->
            Log.d(LOG_TAG, "sending id=${record.id}")
            transport.send(record)
                .onSuccess {
                    Log.d(LOG_TAG, "queued for delivery id=${record.id}")
                }
                .onFailure { e ->
                    Log.w(LOG_TAG, "send failed id=${record.id} exceptionType=${e::class.simpleName}")
                }
        }
    }
}
