package com.tbdfit.phone.workout

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// The Workout <-> Exercise relationship for one workout's exercise list. References Exercise by
// its stable `id` only — the display name is deliberately never snapshotted here (see the design
// doc's WorkoutExercise section): renaming an Exercise must transparently update how every past
// workout displays it, which only holds if history never copies the name into its own row.
//
// `position` is an explicit, deterministic ordering column — insertion order is never relied upon
// (see the design doc's Persistence Model). See WorkoutExerciseDao.appendExercise for how it's
// assigned atomically.
@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val exerciseId: String,
    val position: Int,
)
