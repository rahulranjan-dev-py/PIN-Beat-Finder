package com.pinbeatfinder.data.directory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Separate database for the bundled directory so the user's beat data (BeatFinderDatabase)
 * never needs a migration for it, and a re-seed can simply drop and recreate this file.
 */
@Database(entities = [IndiaPostOfficeEntity::class], version = 2, exportSchema = true)
abstract class DirectoryDatabase : RoomDatabase() {
    abstract fun directoryDao(): DirectoryDao

    companion object {
        private const val NAME = "india_post_directory.db"

        fun build(context: Context): DirectoryDatabase =
            Room.databaseBuilder(context.applicationContext, DirectoryDatabase::class.java, NAME)
                // Derived data: on a schema bump just rebuild from the asset.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
