package com.tbdfit.phone.sync

import com.tbdfit.phone.localstorage.LocalRecordEntity

// Models the two things a real remote store contract must guarantee, without touching Supabase:
// - upsertAttempts records every call, including retries, so a test can prove a retry happened.
// - remoteRowsById models the server's own id-keyed dedup, so a test can prove that no matter how
//   many times the same id is attempted, at most one logical row ever results.
class FakeLocalRecordRemoteStore(
    private val shouldSucceed: (LocalRecordEntity) -> Boolean = { true },
) : LocalRecordRemoteStore {
    val upsertAttempts = mutableListOf<LocalRecordEntity>()
    val remoteRowsById = mutableMapOf<String, LocalRecordEntity>()

    override suspend fun upsert(record: LocalRecordEntity): Result<Unit> {
        upsertAttempts.add(record)
        if (!shouldSucceed(record)) return Result.failure(IllegalStateException("simulated remote failure"))
        remoteRowsById[record.id] = record
        return Result.success(Unit)
    }
}
