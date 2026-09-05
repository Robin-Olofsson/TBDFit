package com.tbdfit.phone.sync

import com.tbdfit.phone.localstorage.LocalRecordEntity

// The narrow boundary the sync coordinator depends on. Whichever backend capability implements
// this (see ADR-004) owns all Supabase-specific SDK/schema/query details — nothing in this
// package, or in localstorage, may know Supabase exists.
interface LocalRecordRemoteStore {
    suspend fun upsert(record: LocalRecordEntity): Result<Unit>
}
