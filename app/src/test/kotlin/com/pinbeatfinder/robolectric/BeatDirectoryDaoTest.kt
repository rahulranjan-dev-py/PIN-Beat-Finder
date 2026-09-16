package com.pinbeatfinder.robolectric

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pinbeatfinder.core.phonetic.PhoneticSearchEngine
import com.pinbeatfinder.data.local.BeatFinderDatabase
import com.pinbeatfinder.data.repository.BeatDirectoryRepository
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.BeatSearchFilters
import com.pinbeatfinder.domain.model.MatchKind
import com.pinbeatfinder.domain.model.OfficeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BeatDirectoryDaoTest {
    private lateinit var db: BeatFinderDatabase
    private lateinit var repo: BeatDirectoryRepository

    private fun r(name: String, bo: String, beat: String, district: String, state: String, pin: String) =
        BeatRecord(0, name, OfficeType.BO, bo, "Sitapur SO", beat, district, state, pin)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BeatFinderDatabase::class.java).allowMainThreadQueries().build()
        repo = BeatDirectoryRepository(db.beatDirectoryDao(), PhoneticSearchEngine(), Dispatchers.Unconfined)
        runBlocking {
            repo.importRecords(
                listOf(
                    r("Rampur Kalan", "Rampur BO", "2", "Sitapur", "Uttar Pradesh", "261001"),
                    r("Rampur Khurd", "Rampur BO", "2", "Sitapur", "Uttar Pradesh", "261001"),
                    r("Bhilwara", "Bhilwara BO", "1", "Bhilwara", "Rajasthan", "311001"),
                    r("Govindapur", "Govindapur BO", "3", "Bhadrak", "Odisha", "756137"),
                ),
                replaceExisting = false,
            )
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `phonetic search tolerates misspelling and ranks exact first`() = runBlocking {
        // "Rampoor" normalises to "rampur", a prefix of both names (TEXT tier); order between the two is a tie-break.
        val hits = repo.search("Rampoor")
        assertEquals(setOf("Rampur Kalan", "Rampur Khurd"), hits.map { it.record.localityName }.toSet())
        assertTrue(hits.all { it.matchKind == MatchKind.TEXT })

        // Aspirate folding makes "Bilwara" an exact normalised match for "Bhilwara".
        val bil = repo.search("Bilwara")
        assertEquals("Bhilwara", bil.single().record.localityName)
        assertEquals(MatchKind.EXACT, bil.single().matchKind)

        // Vowel change is only recoverable phonetically (Double Metaphone drops interior vowels).
        val rum = repo.search("Rumpur Kalan")
        assertEquals("Rampur Kalan", rum.first().record.localityName)
        assertEquals(MatchKind.PHONETIC, rum.first().matchKind)
    }

    @Test
    fun `pin and beat number match exactly and filters narrow`() = runBlocking {
        assertEquals(1, repo.search("756137").size)
        assertEquals(2, repo.search("2").size)
        assertEquals(0, repo.search("Rampur", BeatSearchFilters(state = "Odisha")).size)
        assertEquals(2, repo.search("Rampur", BeatSearchFilters(state = "Uttar Pradesh", district = "Sitapur")).size)
        assertEquals(listOf("Odisha", "Rajasthan", "Uttar Pradesh"), repo.observeStates().first())
        assertEquals(mapOf("261001" to 2, "311001" to 1, "756137" to 1), repo.observePincodeCounts().first())
    }

    @Test
    fun `import skips duplicates, replace-all wipes, preview matches commit`() = runBlocking {
        val again = listOf(r("Rampur Kalan", "Rampur BO", "2", "Sitapur", "Uttar Pradesh", "261001"), r("New Village", "Rampur BO", "2", "Sitapur", "Uttar Pradesh", "261001"))
        val (fresh, dupes) = repo.partitionForImport(again, replaceExisting = false)
        assertEquals(1, fresh.size); assertEquals(1, dupes)
        val outcome = repo.importRecords(again, replaceExisting = false)
        assertEquals(1, outcome.inserted); assertEquals(1, outcome.duplicatesSkipped)
        assertEquals(5, repo.observeCount().first())

        val replaced = repo.importRecords(listOf(r("Only", "X BO", "1", "D", "S", "110001")), replaceExisting = true)
        assertEquals(1, replaced.inserted)
        assertEquals(1, repo.observeCount().first())
    }

    @Test
    fun `office, type, beat and pin filters narrow search and browse, facets are distinct`() = runBlocking {
        assertEquals(2, repo.search("", BeatSearchFilters(officeName = "rampur bo")).size)           // NOCASE column
        assertEquals(1, repo.search("", BeatSearchFilters(officeName = "Rampur BO", beatNumber = "2", pincode = "261001")).count { it.record.localityName == "Rampur Kalan" })
        assertEquals(4, repo.search("", BeatSearchFilters(officeType = OfficeType.BO)).size)
        assertEquals(0, repo.search("", BeatSearchFilters(officeType = OfficeType.SO)).size)
        assertEquals(listOf("Govindapur"), repo.search("Govindapur", BeatSearchFilters(pincode = "756137")).map { it.record.localityName })
        assertEquals(0, repo.search("Govindapur", BeatSearchFilters(pincode = "261001")).size)
        assertEquals(1, repo.listAll(BeatSearchFilters(beatNumber = "3")).size)

        val facets = repo.observeFacets().first()
        assertEquals(3, facets.size)   // Rampur BO/2, Bhilwara BO/1, Govindapur BO/3
        assertTrue(facets.all { it.officeType == OfficeType.BO })
    }

    @Test
    fun `office stats per pin and bulk office type update`() = runBlocking {
        val stats = repo.officeStats("261001")
        assertEquals(setOf("rampur bo"), stats.keys)
        assertEquals(2, stats.getValue("rampur bo").villages)
        assertEquals(1, stats.getValue("rampur bo").beats)
        assertTrue(repo.officeStats("999999").isEmpty())

        val changed = repo.setOfficeType("RAMPUR bo", listOf("261001"), OfficeType.SO)   // case-insensitive name
        assertEquals(2, changed)
        assertEquals(setOf(OfficeType.SO), repo.getAll().filter { it.pincode == "261001" }.map { it.officeType }.toSet())
        assertEquals(OfficeType.BO, repo.getAll().first { it.localityName == "Bhilwara" }.officeType)
        assertEquals(0, repo.setOfficeType("Rampur BO", emptyList(), OfficeType.HO))
    }

    @Test
    fun `save recomputes phonetic keys and delete many works`() = runBlocking {
        val id = repo.save(r("Sitapur", "S BO", "4", "Sitapur", "Uttar Pradesh", "261001"))
        assertEquals("Sitapur", repo.search("Seetapoor").first().record.localityName)
        repo.save(repo.getById(id)!!.copy(localityName = "Lakhimpur"))
        assertEquals(0, repo.search("Seetapoor").count { it.record.id == id })
        assertEquals("Lakhimpur", repo.search("Lakimpur").first().record.localityName)
        repo.deleteMany(repo.getAll().map { it.id })
        assertEquals(0, repo.observeCount().first())
    }
}
