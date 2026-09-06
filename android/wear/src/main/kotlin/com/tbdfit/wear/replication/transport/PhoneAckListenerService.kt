package com.tbdfit.wear.replication.transport

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.tbdfit.wear.localstorage.AppDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

// Path prefix for Phone's "I have durably persisted this record" acknowledgement. Duplicated
// independently on :phone's sending side (DataClientPhoneAckSender's ACK_PATH_PREFIX) — not shared
// code. Matched with a trailing slash for the same exact-match reasoning as :phone's
// WearReplicaListenerService.
internal const val ACK_PATH_PREFIX = "/wear-replica-ack/"

// The only place in :wear that knows an acknowledgement is a Play Services Wearable DataItem.
// DataClient, not MessageClient, is used here deliberately: this is the fix for a real correctness
// gap found during review — a MessageClient ack can be silently lost if Wear is unreachable when
// Phone sends it, and because Wear's retry re-sends the byte-identical record DataItem (same path,
// same content), the Data Layer's own dedup means onDataChanged never fires again on Phone for that
// retry (Wear OS only calls onDataChanged when data actually changes). That combination meant a
// lost ack could leave a record "pending" forever. Making the ack itself a DataItem removes the
// "lost" failure mode entirely: DataClient queues and delivers once a connection exists, the same
// durability guarantee already relied on for the original record.
//
// "Acknowledged" means Phone told us it already committed the record to its own Room database —
// see :phone's WearReplicationReceiver for where that ordering is actually enforced.
class PhoneAckListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val database = AppDatabase.build(applicationContext)
        dataEvents.forEach { event -> handle(event, database) }
    }

    private fun handle(event: DataEvent, database: AppDatabase) {
        if (event.type != DataEvent.TYPE_CHANGED) return
        val uri = event.dataItem.uri
        val path = uri.path ?: return
        if (!path.startsWith(ACK_PATH_PREFIX)) return
        val recordId = path.removePrefix(ACK_PATH_PREFIX)

        runBlocking {
            database.localRecordDao().markPhoneSynced(recordId, System.currentTimeMillis())
            Wearable.getDataClient(applicationContext).deleteDataItems(uri).await()
        }
    }
}
