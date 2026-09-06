package com.tbdfit.phone.wearreplication

// The narrow boundary WearReplicationReceiver depends on to tell Wear that a record is durably
// persisted. Whichever implementation handles this (see DataClientPhoneAckSender) owns all Play
// Services Wearable specifics — nothing outside `transport/` may know DataClient exists.
//
// No target node id: acknowledgement is a DataItem (see DataClientPhoneAckSender), and DataItems
// sync to whichever nodes are paired/connected rather than needing an explicit destination — unlike
// the earlier MessageClient-based design, which required naming a specific node.
interface PhoneAckSender {
    suspend fun sendAck(recordId: String): Result<Unit>
}
