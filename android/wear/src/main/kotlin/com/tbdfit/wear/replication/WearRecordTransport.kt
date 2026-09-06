package com.tbdfit.wear.replication

import com.tbdfit.wear.localstorage.LocalRecordEntity

// The narrow boundary the replication coordinator depends on. Whichever implementation handles
// this (see DataClientWearRecordTransport) owns all Wear Data Layer specifics — nothing in this
// package outside `transport/` may know Play Services Wearable APIs exist.
//
// Result.success here means "durably queued in the local Data Layer for eventual delivery," NOT
// "Phone has received or persisted it." That is a materially weaker guarantee than the Phone/
// Supabase slice's upsert() success, where the HTTP response itself confirmed remote persistence —
// the Wear <-> Phone path is asynchronous, so acknowledgement is a separate, later, inbound event
// (see PhoneAckListenerService), not something this call can confirm synchronously.
interface WearRecordTransport {
    suspend fun send(record: LocalRecordEntity): Result<Unit>
}
