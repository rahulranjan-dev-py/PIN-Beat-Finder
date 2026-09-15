package com.pinbeatfinder.core

import com.pinbeatfinder.core.util.MatchHighlighter
import com.pinbeatfinder.domain.model.BeatGrouping
import com.pinbeatfinder.domain.model.BeatRecord
import com.pinbeatfinder.domain.model.MatchKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatGroupingTest {
    private fun r(id: Long, name: String, bo: String, beat: String, so: String = "Sitapur SO", pin: String = "261001") =
        BeatRecord(id, name, bo, so, beat, "Sitapur", "UP", pin)

    @Test
    fun `groups by branch office and beat with natural beat ordering`() {
        val groups = BeatGrouping.group(
            listOf(
                r(1, "Zeta", "Rampur BO", "10"),
                r(2, "Alpha", "Rampur BO", "2"),
                r(3, "Beta", "Rampur BO", "2"),
                r(4, "Gamma", "rampur bo", "2A"),
                r(5, "Delta", "Amethi BO", "1", pin = "261002"),
                r(6, "Eps", "Rampur BO", "BO-3"),
            ),
        )
        assertEquals(listOf("Amethi BO|1", "Rampur BO|2", "rampur bo|2A", "Rampur BO|10", "Rampur BO|BO-3"), groups.map { it.key })
        val beat2 = groups.first { it.key == "Rampur BO|2" }
        assertEquals(listOf("Alpha", "Beta"), beat2.records.map { it.localityName })
        assertEquals(2, beat2.villageCount)
        assertEquals(listOf("261001"), beat2.pincodes)
        assertEquals("Sitapur SO", beat2.subPostOffice)
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
