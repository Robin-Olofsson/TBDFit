package com.tbdfit.phone.wearreplication.transport

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.tbdfit.phone.wearreplication.PhoneAckSender
import kotlinx.coroutines.tasks.await

// Data Layer path prefix for the acknowledgement. Duplicated independently on :wear's receiving
// side (PhoneAckListenerService) — not shared code. One path per record id, same reasoning as the
// record DataItem itself (DATA_PATH_PREFIX in WearReplicaListenerService).
internal const val ACK_PATH_PREFIX = "/wear-replica-ack"

// The only place in :phone that knows the acknowledgement is a Play Services Wearable DataItem.
// DataClient, not MessageClient, is used here deliberately: an acknowledgement representing "Phone
// has durably persisted this record" needs the same durability guarantee as the record itself — it
// must not be silently lost if Wear is briefly unreachable when this is sent. MessageClient's
// best-effort, connection-required delivery cannot provide that (see the project report's
// pressure-test of the lost-ACK case); DataClient queues and delivers once a connection exists,
// the same way the original record does.
class DataClientPhoneAckSender(
    context: Context,
) : PhoneAckSender {
    private val dataClient = Wearable.getDataClient(context)

    override suspend fun sendAck(recordId: String): Result<Unit> = runCatching {
        val request = PutDataMapRequest.create("$ACK_PATH_PREFIX/$recordId").asPutDataRequest()
        dataClient.putDataItem(request).await()
        return@runCatching
    }
}
