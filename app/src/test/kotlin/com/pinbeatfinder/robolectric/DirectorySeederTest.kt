package com.pinbeatfinder.robolectric

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pinbeatfinder.core.phonetic.PhoneticSearchEngine
import com.pinbeatfinder.data.directory.DirectoryAsset
import com.pinbeatfinder.data.directory.DirectoryDatabase
import com.pinbeatfinder.data.directory.DirectorySeeder
import com.pinbeatfinder.data.directory.IndiaPostDirectoryRepository
import com.pinbeatfinder.data.directory.SeedState
import com.pinbeatfinder.data.prefs.KeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DirectorySeederTest {
    private lateinit var db: DirectoryDatabase
    private val engine = PhoneticSearchEngine()

    private class MemoryStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun read(key: String) = map[key]
        override fun write(key: String, value: String) { map[key] = value }
    }

    private fun row(name: String, pin: String, type: String, district: String, state: String): String {
        val k = engine.encode(name)
        return listOf(name, pin, type, "Delivery", district, state, "Div", "Reg", "Circle",
            com.pinbeatfinder.core.phonetic.IndianPhoneticNormalizer.normalize(name), k.primary, k.alternate).joinToString("\t")
    }

    private val tsv = listOf(
        row("Connaught Place SO", "110001", "PO", "NEW DELHI", "DELHI"),
        row("Baroda House SO", "110001", "PO", "NEW DELHI", "DELHI"),
        row("Kothimir B.O", "504273", "BO", "KUMURAM BHEEM ASIFABAD", "TELANGANA"),
        row("Govindapur B.O", "756137", "BO", "BHADRAK", "ODISHA"),
        "malformed line",
    ).joinToString("\n")

    private val assets: (String) -> InputStream = { name ->
        when (name) {
            DirectoryAsset.META_NAME -> ByteArrayInputStream("""{"version":"test-1","rows":4}""".toByteArray())
            DirectoryAsset.TSV_NAME -> ByteArrayInputStream(ByteArrayOutputStream().also { GZIPOutputStream(it).use { g -> g.write(tsv.toByteArray()) } }.toByteArray())
            else -> error("unexpected asset $name")
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DirectoryDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `seeds once per version, then searches by pin and by misspelled name`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = MemoryStore()
        val seeder = DirectorySeeder(context, db, store, Dispatchers.Unconfined, assets)
        seeder.ensureSeeded()
        val ready = seeder.state.value
        assertTrue("state was $ready", ready is SeedState.Ready)
        assertEquals(4, (ready as SeedState.Ready).rows)
        assertEquals("test-1", store.map[DirectorySeeder.KEY_VERSION])

        val repo = IndiaPostDirectoryRepository(db.directoryDao(), seeder, engine, Dispatchers.Unconfined)
        assertTrue(repo.isReady())
        assertEquals(listOf("Baroda House SO", "Connaught Place SO"), repo.search("110001", isPincode = true).map { it.name })
        assertEquals("Sub Post Office", repo.search("110001", isPincode = true).first().branchType)
        assertEquals("Connaught Place SO", repo.search("Connot Place", isPincode = false).first().name)
        assertEquals("Govindapur B.O", repo.search("govindpur", isPincode = false).first().name)
        assertTrue(repo.search("Zzzzqq", isPincode = false).isEmpty())

        // A fresh seeder with the same stored version must not re-read the TSV.
        val seeder2 = DirectorySeeder(context, db, store, Dispatchers.Unconfined) { name ->
            if (name == DirectoryAsset.META_NAME) assets(name) else error("TSV must not be reopened")
        }
        seeder2.ensureSeeded()
        assertTrue(seeder2.state.value is SeedState.Ready)
        assertEquals(4, db.directoryDao().count())
    }
}
