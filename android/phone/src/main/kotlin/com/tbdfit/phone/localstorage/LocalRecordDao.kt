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
}
