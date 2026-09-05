package com.tbdfit.phone.localstorage

import androidx.room.Entity
import androidx.room.PrimaryKey

// Disposable technical proof model for the local-persistence slice — not a domain entity.
// The ID is client-generated (see AppDatabase) so a record never needs a backend round-trip
// to receive its identity.
//
// syncedAt (null = pending, non-null = remotely acknowledged) is deliberately a single nullable
// timestamp, not a state enum: this record is create-once and never edited locally, so "has this
// creation been acknowledged remotely yet" is the only distinction that exists. A future editable
// entity needs a different model (e.g. comparing a last-modified timestamp against syncedAt, or a
// dirty flag) — do not reuse this exact shape unchanged for something mutable.
@Entity(tableName = "local_records")
data class LocalRecordEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val value: String,
    val syncedAt: Long? = null,
)
