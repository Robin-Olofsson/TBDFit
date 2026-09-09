package com.tbdfit.phone.workout

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// One exercise slot within a Routine — references Exercise by its stable `id` only, exactly like
// WorkoutExerciseEntity: a rename must transparently propagate, so the display name is never
// snapshotted here. `position` is deterministic ordering (sparse-after-delete, no compaction),
// matching WorkoutExerciseEntity's own convention — see RoutineExerciseDao.appendExercise.
//
// No `ownerId` column: ownership is transitive through `routineId`, matching the existing
// WorkoutExercise/WorkoutSet precedent (no redundant ownerId columns on children) — see
// program-routine-first-slice-design.md's Ownership Model section.
@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("routineId"), Index("exerciseId")],
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val exerciseId: String,
    val position: Int,
)
