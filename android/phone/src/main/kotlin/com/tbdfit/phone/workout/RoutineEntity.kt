package com.tbdfit.phone.workout

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tbdfit.phone.localaccount.LocalAccountEntity

// Slice A of docs/architecture/program-routine-first-slice-design.md: a Routine is a standalone,
// reusable planned training session — it never requires a Program (Program is a later slice, not
// implemented yet). `Routine → Start → Workout` is a complete, self-sufficient lifecycle on its
// own; see WorkoutRepository.startRoutine.
//
// `ownerId` is non-null (unlike Workout.ownerId/Exercise.ownerId, which are nullable only to
// represent pre-account-scoping legacy rows from earlier schema versions) — this table is brand
// new, so every row has always had a real owner from the moment it was created. RESTRICT matches
// the existing Workout/Exercise ownership pattern: a LocalAccount row can never be deleted out from
// under a Routine it owns.
@Entity(
    tableName = "routines",
    foreignKeys = [
        ForeignKey(
            entity = LocalAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("ownerId")],
)
data class RoutineEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val createdAt: Long,
    val lastModifiedAt: Long? = null,
)
