package com.pinbeatfinder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BeatDirectoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BeatFinderDatabase : RoomDatabase() {
    abstract fun beatDirectoryDao(): BeatDirectoryDao

    companion object {
        private const val NAME = "pin_beat_finder.db"

        fun build(context: Context): BeatFinderDatabase =
            Room.databaseBuilder(context.applicationContext, BeatFinderDatabase::class.java, NAME)
                // Schema is v1; future versions must ship explicit migrations — never drop data.
                .build()
    }
}
