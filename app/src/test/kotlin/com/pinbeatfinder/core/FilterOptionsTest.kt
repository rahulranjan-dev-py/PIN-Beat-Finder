package com.pinbeatfinder.core

import com.pinbeatfinder.domain.model.BeatSearchFilters
import com.pinbeatfinder.domain.model.FilterFacet
import com.pinbeatfinder.domain.model.FilterOptions
import com.pinbeatfinder.domain.model.OfficeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilterOptionsTest {
    private val facets = listOf(
        FilterFacet("Jharkhand", "Dhanbad", OfficeType.BO, "Barwa", "1", "828205"),
        FilterFacet("Jharkhand", "Dhanbad", OfficeType.BO, "Barwa", "2", "828205"),
        FilterFacet("Jharkhand", "Dhanbad", OfficeType.SO, "Nirsa Chatti", "1", "828205"),
        FilterFacet("Jharkhand", "Bokaro", OfficeType.BO, "Chas", "10", "827013"),
        FilterFacet("Uttar Pradesh", "Sitapur", OfficeType.BO, "Rampur", "2", "261001"),
    )

    @Test
    fun `choices cascade from what is already selected`() {
        val none = FilterOptions.from(facets, BeatSearchFilters())
        assertEquals(listOf("Jharkhand", "Uttar Pradesh"), none.states)
        assertEquals(listOf("Bokaro", "Dhanbad", "Sitapur"), none.districts)
        assertEquals(listOf("1", "2", "10"), none.beats)   // natural order

        val jh = FilterOptions.from(facets, BeatSearchFilters(state = "Jharkhand", district = "Dhanbad"))
        assertEquals(listOf("Jharkhand", "Uttar Pradesh"), jh.states)          // own filter ignored: can still switch
        assertEquals(listOf("Bokaro", "Dhanbad"), jh.districts)
        assertEquals(listOf(OfficeType.SO, OfficeType.BO), jh.officeTypes)
        assertEquals(listOf("Barwa", "Nirsa Chatti"), jh.offices)
        assertEquals(listOf("828205"), jh.pincodes)

        val bo = FilterOptions.from(facets, BeatSearchFilters(state = "Jharkhand", district = "Dhanbad", officeType = OfficeType.BO))
        assertEquals(listOf("Barwa"), bo.offices)
        assertEquals(listOf("1", "2"), bo.beats)
    }

    @Test
    fun `prune drops selections that vanished from the data`() {
        val f = BeatSearchFilters(state = "Jharkhand", officeName = "Gone", beatNumber = "2", pincode = "000000")
        val pruned = FilterOptions.prune(facets, f)
        assertEquals("Jharkhand", pruned.state)
        assertNull(pruned.officeName)
        assertEquals("2", pruned.beatNumber)
        assertNull(pruned.pincode)
        assertEquals(BeatSearchFilters(), FilterOptions.prune(emptyList(), f))
        assertEquals(2, pruned.count)
    }
}
