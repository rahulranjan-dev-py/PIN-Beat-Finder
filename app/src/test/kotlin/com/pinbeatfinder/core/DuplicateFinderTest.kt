package com.pinbeatfinder.core

import com.pinbeatfinder.core.dedupe.DuplicateFinder
import com.pinbeatfinder.core.dedupe.DuplicatePair
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.OfficeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateFinderTest {
    private fun r(id: Long, name: String, beat: String = "2", office: String = "Rampur", pin: String = "261001", remarks: String = "") =
        BeatRecord(id, name, OfficeType.BO, office, "Sitapur SO", beat, "Sitapur", "UP", pin, remarks)

    private val finder = DuplicateFinder()

    @Test
    fun `pairs same, sound-alike and near-spelling names on the same beat only`() {
        val pairs = finder.find(
            listOf(
                r(1, "Rampur Kalan"),
                r(2, "Rampoor Kalan"),          // vowel spelling: normalises to the same name
                r(3, "Rampur Kala"),            // one letter off
                r(4, "Bhilwara"),
                r(5, "Bilwara", beat = "3"),    // other beat: never paired
                r(6, "Govindpur"),
                r(7, "Gobindpur"),              // b/v: one letter apart (Metaphone keeps B and V distinct)
                r(8, "Raipur"),                 // not a duplicate of Rampur
            ),
        )
        val keys = pairs.map { it.first.id to it.second.id }.toSet()
        assertTrue(keys.toString(), (1L to 2L) in keys)
        assertTrue(keys.toString(), (1L to 3L) in keys || (2L to 3L) in keys)
        assertTrue(keys.toString(), (6L to 7L) in keys)
        assertTrue(keys.none { 5L in listOf(it.first, it.second) })
        assertTrue(keys.none { 8L in listOf(it.first, it.second) })
        assertEquals(DuplicatePair.Reason.SAME_NAME, pairs.first { it.first.id == 1L && it.second.id == 2L }.reason)
        assertEquals(DuplicatePair.Reason.NEAR_SPELLING, pairs.first { it.first.id == 6L }.reason)
        assertEquals(DuplicatePair.Reason.SOUNDS_ALIKE, finder.find(listOf(r(1, "Sitapur"), r(2, "Seetapore"))).single().reason)
    }

    @Test
    fun `no pairs across offices or pins, merged keeps remarks`() {
        assertTrue(finder.find(listOf(r(1, "Rampur"), r(2, "Rampur", office = "Amethi"), r(3, "Rampur", pin = "261002"))).isEmpty())
        val kept = DuplicateFinder.merged(r(1, "Rampur"), r(2, "Rampur", remarks = "near temple"))
        assertEquals("near temple", kept.remarks)
        assertEquals("mine", DuplicateFinder.merged(r(1, "Rampur", remarks = "mine"), r(2, "Rampur", remarks = "theirs")).remarks)
    }
}
