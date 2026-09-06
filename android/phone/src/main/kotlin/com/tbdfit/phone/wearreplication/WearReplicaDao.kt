package com.tbdfit.phone.wearreplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WearReplicaDao {
    // Database-enforced idempotency: the `id` primary key plus IGNORE means a redundant delivery
    // of the same record (a genuine retry, or the Data Layer resyncing already-known DataItems) is
    // a safe no-op, not a duplicate row. This does not rely on any assumption about transport
    // delivery semantics — it holds regardless of how many times insertIfAbsent is called for the
    // same id. Returns the inserted rowId, or -1 if the row already existed and was ignored.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: WearReplicaEntity): Long

    @Query("SELECT * FROM wear_replicas ORDER BY receivedAt DESC")
    fun getAll(): Flow<List<WearReplicaEntity>>

    // Used only to clear disposable technical-proof data on logout — see
    // com.tbdfit.phone.auth.clearAccountScopedTechnicalProofData. Must not be reused for real
    // workout data, which needs per-owner scoping instead of deletion.
    @Query("DELETE FROM wear_replicas")
    suspend fun clearAll()
}
