package com.pinbeatfinder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BeatDirectoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class BeatFinderDatabase : RoomDatabase() {
    abstract fun beatDirectoryDao(): BeatDirectoryDao

    companion object {
        private const val NAME = "pin_beat_finder.db"

        /**
         * v1 -> v2: the separate "Branch Office" / "Sub Post Office" columns became
         * office type + office name + account office. Existing columns are kept under their old
         * names (`branchOffice` = office name, `subPostOffice` = account office) and a
         * `officeType` column is added. A v1 row that only had a sub post office filled in
         * becomes an SO record whose office name is that sub post office. Names typed with a
         * type suffix ("Rampur BO", "Sitapur S.O.") lose the suffix and it sets the type, so the
         * display "name + type" never reads "Rampur BO BO".
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_beat_directory ADD COLUMN officeType TEXT NOT NULL DEFAULT 'BO'")
                db.execSQL("UPDATE local_beat_directory SET branchOffice = TRIM(branchOffice), subPostOffice = TRIM(subPostOffice)")
                db.execSQL(
                    """
                    UPDATE local_beat_directory
                    SET officeType = 'SO', branchOffice = subPostOffice, subPostOffice = ''
                    WHERE branchOffice = '' AND subPostOffice <> ''
                    """.trimIndent(),
                )
                for ((suffix, type) in LEGACY_NAME_SUFFIXES) {
                    val n = suffix.length
                    db.execSQL(
                        "UPDATE local_beat_directory SET officeType = '$type', " +
                            "branchOffice = TRIM(SUBSTR(branchOffice, 1, LENGTH(branchOffice) - $n)) " +
                            "WHERE UPPER(branchOffice) LIKE '%$suffix' AND LENGTH(branchOffice) > $n",
                    )
                }
            }
        }

        /** Longest first so "G.P.O." is not mistaken for "P.O.". Leading space keeps "Sambo" intact. */
        private val LEGACY_NAME_SUFFIXES = listOf(
            " G.P.O." to "GPO", " G.P.O" to "GPO", " GPO" to "GPO",
            " H.O." to "HO", " H.O" to "HO", " HO" to "HO",
            " S.O." to "SO", " S.O" to "SO", " SO" to "SO",
            " P.O." to "SO", " P.O" to "SO", " PO" to "SO",
            " B.O." to "BO", " B.O" to "BO", " BO" to "BO",
            " IDC" to "IDC",
        )

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context): BeatFinderDatabase =
            Room.databaseBuilder(context.applicationContext, BeatFinderDatabase::class.java, NAME)
                // User data: every schema bump ships an explicit migration — never drop rows.
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
