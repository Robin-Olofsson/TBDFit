package com.tbdfit.phone.localstorage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tbdfit.phone.localaccount.LocalAccountDao
import com.tbdfit.phone.localaccount.LocalAccountEntity
import com.tbdfit.phone.wearreplication.WearReplicaDao
import com.tbdfit.phone.wearreplication.WearReplicaEntity
import com.tbdfit.phone.workout.ExerciseDao
import com.tbdfit.phone.workout.ExerciseEntity
import com.tbdfit.phone.workout.WorkoutDao
import com.tbdfit.phone.workout.WorkoutEntity
import com.tbdfit.phone.workout.WorkoutExerciseDao
import com.tbdfit.phone.workout.WorkoutExerciseEntity
import com.tbdfit.phone.workout.WorkoutSetDao
import com.tbdfit.phone.workout.WorkoutSetEntity

// Durable-data transition (see docs/architecture/strength-workout-first-slice-design.md's
// PERSISTENCE/MIGRATION POLICY): version 4 introduces the first real, non-disposable
// workout-domain tables (Exercise/Workout/WorkoutExercise/WorkoutSet) alongside the existing
// disposable technical-proof tables, in this same database — one Phone database, not a second one
// (see the design doc for why). exportSchema is now true, and MIGRATION_3_4 below is the first
// explicit, committed Migration this database has ever required. fallbackToDestructiveMigration is
// removed for good starting here: a future missing migration now fails loudly (a runtime crash) at
// database-open time rather than silently destroying workout history — the load-bearing property
// this transition exists to establish (see docs/product/decisions.md PD-002 verification notes).
//
// WearReplicaEntity/LocalRecordEntity remain disposable technical-proof data — see their own file
// comments. Nothing about this transition changes that; it only changes what happens if a future
// migration for them is missing (a crash, not a silent wipe).
//
// Schema history discipline (see android/phone/schemas/README.md for the full explanation): 4.json
// and 5.json were both generated normally, directly from the real entity set at the time — both
// are canonical. 3.json is an EXCEPTIONAL retroactive reconstruction (v3 predates
// exportSchema=true, so nothing was ever exported live) and must never be regenerated against
// current code. Every version from 4 onward must have its schema exported normally, at the time
// that version is introduced — not reconstructed after the fact the way 3.json had to be.
//
// v5 — local-workout-ownership audit: added Workout.ownerId (see WorkoutEntity's own doc comment).
// Purely additive at the schema level (one nullable-turned-defaulted column); see MIGRATION_4_5.
//
// v6 — local-account-ownership correction: introduces LocalAccountEntity as the smallest local
// identity root (see its own doc comment) and turns Workout.ownerId / (new) Exercise.ownerId from
// free-form strings into real foreign keys against it, enforced by Room/SQLite rather than by
// application discipline alone. See MIGRATION_5_6.
@Database(
    entities = [
        LocalRecordEntity::class,
        WearReplicaEntity::class,
        LocalAccountEntity::class,
        ExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localRecordDao(): LocalRecordDao
    abstract fun wearReplicaDao(): WearReplicaDao
    abstract fun localAccountDao(): LocalAccountDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutExerciseDao(): WorkoutExerciseDao
    abstract fun workoutSetDao(): WorkoutSetDao

    companion object {
        private const val DATABASE_NAME = "tbdfit-local.db"

        // Purely additive: creates the four new workout tables and their indices. Does not alter
        // local_records or wear_replicas at all, so their existing rows are preserved by
        // construction, not merely by intent — see MigrationTest for the assertion that proves it.
        // This SQL must match exactly what Room generates from the entity annotations above (see
        // android/phone/schemas/.../4.json) — a mismatch fails Room's own schema validation at
        // database-open time, which is the intended, loud failure mode now that
        // fallbackToDestructiveMigration is gone.
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`normalizedName` TEXT NOT NULL, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workouts` (`id` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, `completedAt` INTEGER, `lastModifiedAt` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_exercises` (`id` TEXT NOT NULL, " +
                        "`workoutId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_workout_exercises_workoutId` " +
                        "ON `workout_exercises` (`workoutId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_workout_exercises_exerciseId` " +
                        "ON `workout_exercises` (`exerciseId`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_sets` (`id` TEXT NOT NULL, " +
                        "`workoutExerciseId` TEXT NOT NULL, `position` INTEGER NOT NULL, `weight` REAL, " +
                        "`reps` INTEGER, `isCompleted` INTEGER NOT NULL, `completedAt` INTEGER, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`workoutExerciseId`) REFERENCES `workout_exercises`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_workout_sets_workoutExerciseId` " +
                        "ON `workout_sets` (`workoutExerciseId`)",
                )
            }
        }

        // Adds Workout.ownerId. Existing rows (all created before account scoping existed) get the
        // sentinel `''` — deliberately never a guessed real account id, so they become correctly
        // invisible to every owner-scoped query rather than silently reassigned to whoever happens
        // to sign in next. See WorkoutEntity's own doc comment and the ownership-audit report for
        // why this is a surfaced-but-not-resolved product question, not an invented answer.
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
            }
        }

        // Local-account-ownership correction: introduces `local_accounts` as a real identity root
        // and turns workouts.ownerId (already present since v5) and the new exercises.ownerId into
        // foreign keys against it, rather than free-form strings. SQLite cannot add a foreign key to
        // an existing column via ALTER TABLE, so both `workouts` and `exercises` are recreated using
        // the standard create-copy-drop-rename technique (see the design doc's PERSISTENCE/MIGRATION
        // POLICY and SQLite's own documented procedure for schema changes on FK-related tables).
        //
        // Data policy for existing rows (surfaced, not guessed — see WorkoutEntity's own doc
        // comment): a workout's v5 ownerId of '' (the pre-ownership sentinel) becomes NULL — NULL
        // bypasses FK enforcement entirely in SQLite and keeps these rows exactly as invisible to
        // every owner-scoped query as they already were, never reassigned to a guessed account. A
        // non-empty v5 ownerId, by contrast, IS a real, already-in-use Supabase user id (every
        // ownerId ever written since v5 came from AuthSession.userId — see WorkoutRepository) so
        // backfilling the corresponding LocalAccount row for it is a faithful migration of an
        // identity that already implicitly existed, not an invented one. Pre-existing CUSTOM
        // exercises have no ownerId concept at all before this migration, so they all get NULL —
        // correctly invisible to everyone (see ExerciseDao.getVisibleTo), never guessed either.
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `local_accounts` (`id` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )

                // Backfill: one LocalAccount row per distinct non-empty ownerId already in use by a
                // real workout — a faithful migration of an already-real identity (see above), done
                // before recreating `workouts` so its new FK is satisfiable by the copy below.
                db.execSQL(
                    "INSERT INTO local_accounts (id, createdAt) " +
                        "SELECT DISTINCT ownerId, ${System.currentTimeMillis()} FROM workouts " +
                        "WHERE ownerId IS NOT NULL AND ownerId != ''",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workouts_new` (`id` TEXT NOT NULL, `ownerId` TEXT, " +
                        "`status` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `completedAt` INTEGER, " +
                        "`lastModifiedAt` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`ownerId`) REFERENCES `local_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
                )
                db.execSQL(
                    "INSERT INTO workouts_new (id, ownerId, status, startedAt, completedAt, lastModifiedAt, createdAt) " +
                        "SELECT id, CASE WHEN ownerId = '' THEN NULL ELSE ownerId END, status, startedAt, " +
                        "completedAt, lastModifiedAt, createdAt FROM workouts",
                )
                db.execSQL("DROP TABLE workouts")
                db.execSQL("ALTER TABLE workouts_new RENAME TO workouts")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_ownerId` ON `workouts` (`ownerId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercises_new` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`normalizedName` TEXT NOT NULL, `source` TEXT NOT NULL, `ownerId` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`ownerId`) REFERENCES `local_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
                )
                db.execSQL(
                    "INSERT INTO exercises_new (id, name, normalizedName, source, ownerId, createdAt) " +
                        "SELECT id, name, normalizedName, source, NULL, createdAt FROM exercises",
                )
                db.execSQL("DROP TABLE exercises")
                db.execSQL("ALTER TABLE exercises_new RENAME TO exercises")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_ownerId` ON `exercises` (`ownerId`)")
            }
        }

        fun build(context: Context, name: String = DATABASE_NAME): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}
