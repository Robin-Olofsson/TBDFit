package com.tbdfit.phone.localstorage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalRecordDao {
    @Insert
    suspend fun insert(record: LocalRecordEntity)

    @Query("SELECT * FROM local_records ORDER BY createdAt DESC")
    fun getAll(): Flow<List<LocalRecordEntity>>

    @Query("SELECT * FROM local_records WHERE syncedAt IS NULL")
    suspend fun getPending(): List<LocalRecordEntity>

    @Query("UPDATE local_records SET syncedAt = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: String, syncedAt: Long)

    // Used only to clear disposable technical-proof data on logout — see
    // com.tbdfit.phone.auth.clearAccountScopedTechnicalProofData. Must not be reused for real
    // workout data, which needs per-owner scoping instead of deletion.
    @Query("DELETE FROM local_records")
    suspend fun clearAll()
}
