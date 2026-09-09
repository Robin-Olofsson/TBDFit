package com.tbdfit.phone.localaccount

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocalAccountDao {
    // Idempotent by construction — the same insert-if-absent pattern already used for the built-in
    // exercise seed (ExerciseDao.insertBuiltInIfAbsent) and Wear-replica ingestion elsewhere in this
    // codebase. This is the only supported way "ensure a LocalAccount row exists for this id" is
    // implemented — see WorkoutRepository.ensureLocalAccountExists. Never overwrites an existing row.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(account: LocalAccountEntity)

    @Query("SELECT * FROM local_accounts WHERE id = :id")
    suspend fun getById(id: String): LocalAccountEntity?
}
