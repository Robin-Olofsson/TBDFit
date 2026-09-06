package com.tbdfit.wear.replication

import com.tbdfit.wear.localstorage.LocalRecordEntity

class FakeWearRecordTransport(
    private val shouldSucceed: (LocalRecordEntity) -> Boolean = { true },
) : WearRecordTransport {
    val sendAttempts = mutableListOf<LocalRecordEntity>()

    override suspend fun send(record: LocalRecordEntity): Result<Unit> {
        sendAttempts.add(record)
        if (!shouldSucceed(record)) return Result.failure(IllegalStateException("simulated transport failure"))
        return Result.success(Unit)
    }
}
