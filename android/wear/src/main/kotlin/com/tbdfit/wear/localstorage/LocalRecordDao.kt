package com.tbdfit.wear.localstorage

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

    @Query("SELECT * FROM local_records WHERE phoneSyncedAt IS NULL")
    suspend fun getPendingReplication(): List<LocalRecordEntity>

    @Query("UPDATE local_records SET phoneSyncedAt = :syncedAt WHERE id = :id")
    suspend fun markPhoneSynced(id: String, syncedAt: Long)
}
