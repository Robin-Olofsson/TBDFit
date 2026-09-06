package com.tbdfit.wear.replication.transport

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.tbdfit.wear.localstorage.LocalRecordEntity
import com.tbdfit.wear.replication.WearRecordTransport
import kotlinx.coroutines.tasks.await

// Data Layer path prefix for this technical entity. One DataItem path per record id: DataItems are
// a "current value at this path" model (a later put at the same path replaces, not appends), so
// giving each record its own path keeps independent records from ever colliding with each other.
// Duplicated independently on :phone's receiving side (not shared code) — see
// WearReplicaListenerService.
internal const val DATA_PATH_PREFIX = "/wear-replica"

// The only place in :wear that knows the Play Services Wearable Data Layer exists for this
// capability. DataClient is the documented mechanism for data that must survive being created
// while the paired device is unreachable and delivered once a connection is available — unlike
// MessageClient, which requires an active connection and has no built-in retry/queueing.
class DataClientWearRecordTransport(
    context: Context,
) : WearRecordTransport {
    private val dataClient = Wearable.getDataClient(context)

    override suspend fun send(record: LocalRecordEntity): Result<Unit> = runCatching {
        val request = PutDataMapRequest.create("$DATA_PATH_PREFIX/${record.id}").apply {
            dataMap.putString("id", record.id)
            dataMap.putLong("createdAt", record.createdAt)
            dataMap.putString("value", record.value)
        }.asPutDataRequest()
        dataClient.putDataItem(request).await()
    }
}
