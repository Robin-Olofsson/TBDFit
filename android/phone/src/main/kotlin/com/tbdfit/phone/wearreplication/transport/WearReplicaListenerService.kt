package com.tbdfit.phone.wearreplication.transport

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.tbdfit.phone.localstorage.AppDatabase
import com.tbdfit.phone.wearreplication.ReceivedWearRecord
import com.tbdfit.phone.wearreplication.WearReplicationReceiver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

// Data Layer path prefix for this technical entity. Duplicated independently on :wear's sending
// side (DataClientWearRecordTransport) — not shared code. Matched with a trailing slash so this
// never ambiguously matches the differently-prefixed ack path (DataClientPhoneAckSender's
// ACK_PATH_PREFIX) — not that a node would see its own writes as onDataChanged anyway, but the
// exact match removes any doubt.
internal const val DATA_PATH_PREFIX = "/wear-replica/"

// The only place in :phone that knows an incoming replica is a Play Services Wearable DataEvent.
// Its entire job is decoding the raw event into a ReceivedWearRecord, handing off to
// WearReplicationReceiver for persistence + acknowledgement, then deleting the now-fully-consumed
// record DataItem. Deletion happens only after receiver.receive() returns — i.e. only after Room
// durability and the ack attempt — never before Phone durability is established. A best-effort
// delete failure here is not a correctness problem: at worst a harmless, already-fully-processed
// DataItem lingers (see the project report's DataItem lifecycle analysis) — this is not elaborate
// garbage collection, just cleanup-after-use.
class WearReplicaListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val database = AppDatabase.build(applicationContext)
        val receiver = WearReplicationReceiver(
            dao = database.wearReplicaDao(),
            ackSender = DataClientPhoneAckSender(applicationContext),
        )

        dataEvents.forEach { event -> handle(event, receiver) }
    }

    private fun handle(event: DataEvent, receiver: WearReplicationReceiver) {
        if (event.type != DataEvent.TYPE_CHANGED) return
        val uri = event.dataItem.uri
        if (uri.path?.startsWith(DATA_PATH_PREFIX) != true) return

        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
        val record = ReceivedWearRecord(
            id = dataMap.getString("id") ?: return,
            createdAt = dataMap.getLong("createdAt"),
            value = dataMap.getString("value") ?: return,
        )

        runBlocking {
            receiver.receive(record)
            Wearable.getDataClient(applicationContext).deleteDataItems(uri).await()
        }
    }
}
