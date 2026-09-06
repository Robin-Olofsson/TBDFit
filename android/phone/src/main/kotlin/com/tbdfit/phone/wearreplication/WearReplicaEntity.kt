package com.tbdfit.phone.wearreplication

import androidx.room.Entity
import androidx.room.PrimaryKey

// A record replicated from a Wear device. Deliberately a separate table/entity from
// com.tbdfit.phone.localstorage.LocalRecordEntity (which is phone-originated and Supabase-synced)
// rather than reusing it: the two represent different lifecycles (Wear-origin + Phone-receipt vs.
// Phone-origin + Supabase-sync), and this table's own existence is what encodes "this originated on
// a Wear device" — no separate origin/device column is needed for this technical proof.
//
// `id` is the same client-generated UUID the record was created with on Wear — replication never
// mints a new identity. `receivedAt` is when Phone durably committed this row, which is also the
// event that "acknowledged" means (see WearReplicationReceiver) — not merely when a transport
// payload arrived.
@Entity(tableName = "wear_replicas")
data class WearReplicaEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val value: String,
    val receivedAt: Long,
)
