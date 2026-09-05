package com.tbdfit.phone.localstorage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// exportSchema is off deliberately: this entity is a disposable technical proof, not the future
// domain schema, so schema-history/migration tooling isn't warranted here yet.
@Database(entities = [LocalRecordEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localRecordDao(): LocalRecordDao

    companion object {
        private const val DATABASE_NAME = "tbdfit-local.db"

        fun build(context: Context, name: String = DATABASE_NAME): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name).build()
    }
}
