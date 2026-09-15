package com.pinbeatfinder.robolectric

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pinbeatfinder.data.local.BeatFinderDatabase
import com.pinbeatfinder.domain.model.OfficeType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Opens a database file created with the v1 schema (exactly as Room generated it for v0.9.x)
 * through the current [BeatFinderDatabase] with its migrations. Room validates the migrated
 * schema against the entity on open, so a wrong or missing migration fails here, not on a
 * user's phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BeatFinderDatabaseMigrationTest {

    @Test
    fun `v1 rows survive the migration and old BO or SO fields become type plus name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration_test.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.delete()

        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS `local_beat_directory` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`localityName` TEXT NOT NULL COLLATE NOCASE, `phoneticPrimary` TEXT NOT NULL COLLATE NOCASE, " +
                    "`phoneticAlternate` TEXT NOT NULL COLLATE NOCASE, `branchOffice` TEXT NOT NULL COLLATE NOCASE, " +
                    "`subPostOffice` TEXT NOT NULL COLLATE NOCASE, `beatNumber` TEXT NOT NULL COLLATE NOCASE, " +
                    "`district` TEXT NOT NULL COLLATE NOCASE, `state` TEXT NOT NULL COLLATE NOCASE, `pincode` TEXT NOT NULL, " +
                    "`remarks` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)",
            )
            legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_local_beat_directory_localityName` ON `local_beat_directory` (`localityName`)")
            legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_local_beat_directory_phoneticPrimary_phoneticAlternate` ON `local_beat_directory` (`phoneticPrimary`, `phoneticAlternate`)")
            legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_local_beat_directory_phoneticAlternate` ON `local_beat_directory` (`phoneticAlternate`)")
            legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_local_beat_directory_state_district` ON `local_beat_directory` (`state`, `district`)")
            legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_local_beat_directory_beatNumber` ON `local_beat_directory` (`beatNumber`)")
            legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_local_beat_directory_pincode` ON `local_beat_directory` (`pincode`)")
            legacy.execSQL(
                "INSERT INTO local_beat_directory (localityName, phoneticPrimary, phoneticAlternate, branchOffice, subPostOffice, beatNumber, district, state, pincode, remarks, updatedAt) VALUES " +
                    "('Rampur Kalan', 'RMPR KLN', 'RMPR KLN', 'Rampur BO', 'Sitapur SO', '2', 'Sitapur', 'Uttar Pradesh', '261001', '', 1), " +
                    "('Sitapur Town', 'STPR TN', 'STPR TN', '', 'Sitapur SO', '1', 'Sitapur', 'Uttar Pradesh', '261001', 'served by SO', 1), " +
                    "('Hazratganj', 'HSRTKNJ', 'HSRTKNJ', 'Lucknow G.P.O. ', '', '5', 'Lucknow', 'Uttar Pradesh', '226001', '', 1), " +
                    "('Sambo Tola', 'SMP TL', 'SMP TL', 'Sambo', 'Nirsa Chatti SO', '1', 'Dhanbad', 'Jharkhand', '828205', '', 1)",
            )
            legacy.version = 1
        }

        val db = Room.databaseBuilder(context, BeatFinderDatabase::class.java, name)
            .addMigrations(*BeatFinderDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val rows = runBlocking { db.beatDirectoryDao().getAll() }.map { it.toDomain() }.sortedBy { it.localityName }
            assertEquals(4, rows.size)

            val bo = rows.first { it.localityName == "Rampur Kalan" }
            assertEquals(OfficeType.BO, bo.officeType)
            assertEquals("Rampur", bo.officeName)             // " BO" suffix folded into the type
            assertEquals("Rampur BO", bo.officeDisplay)
            assertEquals("Sitapur SO", bo.accountOffice)

            val so = rows.first { it.localityName == "Sitapur Town" }
            assertEquals(OfficeType.SO, so.officeType)
            assertEquals("Sitapur", so.officeName)
            assertEquals("", so.accountOffice)
            assertEquals("served by SO", so.remarks)

            val gpo = rows.first { it.localityName == "Hazratganj" }
            assertEquals(OfficeType.GPO, gpo.officeType)
            assertEquals("Lucknow", gpo.officeName)

            val plain = rows.first { it.localityName == "Sambo Tola" }   // ends in "bo" but is not a suffix
            assertEquals(OfficeType.BO, plain.officeType)
            assertEquals("Sambo", plain.officeName)
        } finally {
            db.close()
            file.delete()
        }
    }
}
