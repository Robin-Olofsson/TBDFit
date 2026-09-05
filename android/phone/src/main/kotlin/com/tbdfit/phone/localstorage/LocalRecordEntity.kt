package com.tbdfit.phone.localstorage

import androidx.room.Entity
import androidx.room.PrimaryKey

// Disposable technical proof model for the local-persistence slice — not a domain entity.
// The ID is client-generated (see AppDatabase) so a record never needs a backend round-trip
// to receive its identity.
@Entity(tableName = "local_records")
data class LocalRecordEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val value: String,
)
