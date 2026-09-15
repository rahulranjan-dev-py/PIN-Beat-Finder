package com.pinbeatfinder.ui

import com.pinbeatfinder.domain.model.PostOffice
import com.pinbeatfinder.ui.online.OnlineSearchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineSearchStateTest {
    private fun po(name: String, state: String, district: String, pin: String) =
        PostOffice(name, "Branch Post Office", "Delivery", "", "", "", "", district, state, pin)

    private val results = listOf(
        po("Govindapur", "Telangana", "Warangal", "506001"),
        po("Govindapur", "Assam", "Goalpara", "783101"),
        po("Govindapur", "Odisha", "Bhadrak", "756137"),
        po("Govindapur", "Odisha", "Baleswar", "756126"),
        po("Govindapur", "Bihar", "Siwan", "841507"),
    )

    @Test
    fun `no filters shows everything and lists distinct sorted states`() {
        val s = OnlineSearchState(submittedQuery = "govindapur", results = results)
        assertFalse(s.hasFilters)
        assertEquals(5, s.visibleResults.size)
        assertEquals(listOf("Assam", "Bihar", "Odisha", "Telangana"), s.states)
        assertEquals(listOf("Baleswar", "Bhadrak", "Goalpara", "Siwan", "Warangal"), s.districts)
    }

    @Test
    fun `state filter narrows results and district options`() {
        val s = OnlineSearchState(submittedQuery = "govindapur", results = results, stateFilter = "Odisha")
        assertTrue(s.hasFilters)
        assertEquals(listOf("756137", "756126"), s.visibleResults.map { it.pincode })
        assertEquals(listOf("Baleswar", "Bhadrak"), s.districts)
    }

    @Test
    fun `district filter stacks on state filter`() {
        val s = OnlineSearchState(submittedQuery = "govindapur", results = results, stateFilter = "Odisha", districtFilter = "Baleswar")
        assertEquals(listOf("756126"), s.visibleResults.map { it.pincode })
    }

    @Test
    fun `filters never mutate the fetched results`() {
        val s = OnlineSearchState(results = results, stateFilter = "Bihar")
        assertEquals(5, s.results.size)
        assertEquals(1, s.visibleResults.size)
    }
}
