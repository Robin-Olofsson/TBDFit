package com.tbdfit.phone.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Insert
    suspend fun insert(exercise: ExerciseEntity)

    // Used only by the built-in seed catalog (see BuiltInExerciseCatalog.kt) — ordinary custom
    // exercise creation uses insert() above instead. Idempotent by construction, the same pattern
    // already proven for Wear-replica idempotency elsewhere in this codebase
    // (WearReplicaDao.insertIfAbsent): a row that already exists (whether untouched or since
    // renamed by a user) is left alone, never overwritten. Safe to call on every app start.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBuiltInIfAbsent(exercise: ExerciseEntity)

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    // Local-account-ownership correction: BUILT_IN rows are always visible (the catalog is global);
    // a CUSTOM row is visible only to the account that owns it. Deliberately branches on `source`,
    // not on ownerId being null/non-null — a CUSTOM row with no recorded owner (a pre-ownership
    // legacy row) must NOT fall into the "visible to everyone" bucket just because its ownerId
    // happens to be null. See ExerciseEntity's own doc comment.
    @Query("SELECT * FROM exercises WHERE source = 'BUILT_IN' OR (source = 'CUSTOM' AND ownerId = :ownerId) ORDER BY name ASC")
    fun getVisibleTo(ownerId: String): Flow<List<ExerciseEntity>>

    // Renaming never touches `id` — every WorkoutExercise row keeps referencing the same identity
    // regardless of this call. See ExerciseEntity's doc comment.
    @Query("UPDATE exercises SET name = :name, normalizedName = :normalizedName WHERE id = :id")
    suspend fun rename(id: String, name: String, normalizedName: String)

    // A non-blocking hint only — callers decide whether/how to surface this to the user at
    // creation time. Never used to reject an insert: two rows may legitimately share a
    // normalizedName (see ExerciseEntity's doc comment). Scoped to this owner's own CUSTOM rows
    // only: another account's identically-named custom exercise is not this account's business, the
    // same ownership boundary as getVisibleTo above.
    @Query("SELECT * FROM exercises WHERE normalizedName = :normalizedName AND source = :source AND ownerId = :ownerId")
    suspend fun findByNormalizedNameAndSource(
        normalizedName: String,
        ownerId: String,
        source: ExerciseSource = ExerciseSource.CUSTOM,
    ): List<ExerciseEntity>
}
