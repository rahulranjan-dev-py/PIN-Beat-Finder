package com.pinbeatfinder.core

import com.pinbeatfinder.core.util.MatchHighlighter
import com.pinbeatfinder.domain.model.BeatGrouping
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.MatchKind
import com.pinbeatfinder.domain.model.OfficeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatGroupingTest {
    private fun r(id: Long, name: String, bo: String, beat: String, so: String = "Sitapur SO", pin: String = "261001") =
        BeatRecord(id, name, OfficeType.BO, bo, so, beat, "Sitapur", "UP", pin)

    @Test
    fun `groups by branch office and beat with natural beat ordering`() {
        val groups = BeatGrouping.group(
            listOf(
                r(1, "Zeta", "Rampur", "10"),
                r(2, "Alpha", "Rampur", "2"),
                r(3, "Beta", "Rampur", "2"),
                r(4, "Gamma", "rampur", "2A"),
                r(5, "Delta", "Amethi", "1", pin = "261002"),
                r(6, "Eps", "Rampur", "BO-3"),
            ),
        )
        assertEquals(listOf("Amethi|1", "Rampur|2", "rampur|2A", "Rampur|10", "Rampur|BO-3"), groups.map { it.key })
        val beat2 = groups.first { it.key == "Rampur|2" }
        assertEquals(listOf("Alpha", "Beta"), beat2.records.map { it.localityName })
        assertEquals(2, beat2.villageCount)
        assertEquals(listOf("261001"), beat2.pincodes)
        assertEquals("Sitapur SO", beat2.accountOffice)
        assertEquals("Rampur BO", beat2.officeDisplay)
    }

    @Test
    fun `office summaries merge case and beats, keep PIN sets apart, flag mixed types`() {
        val summaries = BeatGrouping.summarizeOffices(
            listOf(
                r(1, "Alpha", "Rampur", "2"),
                r(2, "Beta", "rampur", "3"),
                r(3, "Gamma", "Rampur", "3").copy(officeType = OfficeType.SO),
                r(4, "Delta", "Rampur", "1", pin = "261002"),   // same name, other PIN: separate office
                r(5, "Eps", "Amethi", "1", pin = "261002"),
            ),
        )
        assertEquals(listOf("Amethi", "Rampur", "Rampur"), summaries.map { it.officeName })
        val rampur = summaries.first { it.officeName == "Rampur" && it.pincodes == listOf("261001") }
        assertEquals(3, rampur.recordCount)
        assertEquals(2, rampur.beatCount)
        assertEquals(OfficeType.BO, rampur.officeType)
        assertTrue(rampur.isMixed)
        val other = summaries.first { it.officeName == "Rampur" && it.pincodes == listOf("261002") }
        assertEquals(1, other.recordCount)
        assertTrue(!other.isMixed)
    }

    @Test
    fun `recordsOf picks one office by name and pin, case-insensitively`() {
        val rows = listOf(r(1, "A", "Rampur", "1"), r(2, "B", "rampur", "2"), r(3, "C", "Rampur", "1", pin = "261002"), r(4, "D", "Amethi", "1"))
        val office = BeatGrouping.summarizeOffices(rows).first { it.officeName == "Rampur" && it.pincodes == listOf("261001") }
        assertEquals(listOf(1L, 2L), BeatGrouping.recordsOf(rows, office).map { it.id })
        assertEquals(2, BeatGrouping.group(BeatGrouping.recordsOf(rows, office)).size)
    }

    @Test
    fun `natural compare`() {
        assertTrue(BeatGrouping.naturalCompare("2", "10") < 0)
        assertTrue(BeatGrouping.naturalCompare("2", "2A") < 0)
        assertTrue(BeatGrouping.naturalCompare("02", "2") == 0)
        assertTrue(BeatGrouping.naturalCompare("Beat 3", "Beat 12") < 0)
        assertTrue(BeatGrouping.naturalCompare("b", "A") > 0)
    }

    @Test
    fun `highlight range and match kinds`() {
        assertEquals(3..5, MatchHighlighter.range("Rampur Kalan", "pur "))
        assertEquals(0..2, MatchHighlighter.range("Rampur", "RAM"))
        assertNull(MatchHighlighter.range("Rampur", "poor"))
        assertNull(MatchHighlighter.range("Rampur", "  "))
        assertEquals(MatchKind.EXACT, MatchKind.fromScore(1.0, true))
        assertEquals(MatchKind.TEXT, MatchKind.fromScore(0.9, true))
        assertEquals(MatchKind.PHONETIC, MatchKind.fromScore(0.7, true))
        assertEquals(MatchKind.NONE, MatchKind.fromScore(0.0, false))
    }
}
