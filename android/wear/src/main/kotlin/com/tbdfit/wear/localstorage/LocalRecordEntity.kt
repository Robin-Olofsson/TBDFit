package com.tbdfit.wear.localstorage

import androidx.room.Entity
import androidx.room.PrimaryKey

// Disposable technical proof model for the Wear local-persistence slice — not a domain entity, and
// not shared code with :phone's own (separately defined) LocalRecordEntity. Wear owns its own
// durable local store independent of the phone, per PD-001/PD-002.
//
// The ID is a client-generated UUID, creatable entirely offline with no paired phone, no network,
// and no backend involved — this is a slice-scoped implementation choice, not a declaration of the
// product-wide ID strategy. The same ID is preserved unchanged when a record is replicated to
// Phone (see :phone's WearReplicaEntity) — replication never mints a new identity.
//
// phoneSyncedAt (null = not yet acknowledged by Phone, non-null = acknowledged) is a single
// nullable timestamp, not a state enum, for the same reason :phone's syncedAt is: this record is
// create-once and never edited locally, so "has Phone durably persisted this yet" is the only
// distinction that exists. Do not reuse this exact shape unchanged for a future mutable entity.
@Entity(tableName = "local_records")
data class LocalRecordEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val value: String,
    val phoneSyncedAt: Long? = null,
)
