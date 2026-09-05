package com.tbdfit.phone.localstorage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// exportSchema is off deliberately: this entity is a disposable technical proof, not the future
// domain schema, so schema-history/migration tooling isn't warranted here yet. The trigger to
// switch this to exportSchema = true with real Migrations is the first real domain entity, not
// this one gaining another column — see docs/product/decisions.md (PD-002 verification notes).
@Database(entities = [LocalRecordEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localRecordDao(): LocalRecordDao

    companion object {
        private const val DATABASE_NAME = "tbdfit-local.db"

        fun build(context: Context, name: String = DATABASE_NAME): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                // Acceptable only because this entity is still a disposable technical proof with
                // no exported schema history (see above). Destructive migration must be removed
                // before real workout-domain data becomes durable product data — a real domain
                // entity must use a proper Migration instead of discarding data on version bumps.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
