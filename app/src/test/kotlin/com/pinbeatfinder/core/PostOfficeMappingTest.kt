package com.pinbeatfinder.core

import com.pinbeatfinder.domain.model.BeatDraft
import com.pinbeatfinder.domain.model.PostOffice
import com.pinbeatfinder.domain.model.plainName
import com.pinbeatfinder.domain.model.tidyCase
import com.pinbeatfinder.domain.model.toBeatDraft
import com.pinbeatfinder.domain.model.withOffice
import org.junit.Assert.assertEquals
import org.junit.Test

class PostOfficeMappingTest {
    private fun po(name: String, type: String) =
        PostOffice(name, type, "Delivery", "Odisha", "Bhadrak", "Bhubaneswar HQ", "", "Bhadrak", "Odisha", "756137")

    @Test
    fun `office suffixes are stripped from the locality name`() {
        assertEquals("Rampur", po("Rampur B.O", "Branch Post Office").plainName())
        assertEquals("Sitapur", po("Sitapur S.O", "Sub Post Office").plainName())
        assertEquals("Cuttack", po("Cuttack H.O", "Head Post Office").plainName())
        assertEquals("Govindapur", po("Govindapur", "Branch Post Office").plainName())
        assertEquals("Kolkata", po("Kolkata GPO", "Head Post Office").plainName())
    }

    @Test
    fun `draft gets office type, plain name and account office`() {
        val bo = po("Rampur B.O", "Branch Post Office").copy(accountOffice = "Sitapur SO").toBeatDraft()
        assertEquals("Rampur", bo.localityName)
        assertEquals("BO", bo.officeType)
        assertEquals("Rampur", bo.officeName)
        assertEquals("Sitapur SO", bo.accountOffice)
        assertEquals("756137", bo.pincode)
        assertEquals("Odisha", bo.state)
        assertEquals("Bhadrak", bo.district)
        assertEquals("", bo.beatNumber)

        val so = po("Sitapur S.O", "Sub Post Office").toBeatDraft()
        assertEquals("SO", so.officeType)
        assertEquals("Sitapur", so.officeName)
        assertEquals("", so.accountOffice)
        assertEquals("GPO", po("Kolkata GPO", "Head Post Office").toBeatDraft().officeType)
    }

    @Test
    fun `withOffice keeps the village fields and tidies shouting district names`() {
        val typed = BeatDraft(localityName = "Barwa Tola", beatNumber = "2", remarks = "north side", pincode = "8282")
        val office = po("Barwa BO", "BO").copy(district = "DHANBAD", state = "JHARKHAND", pincode = "828205", accountOffice = "Nirsa Chatti SO")
        val filled = typed.withOffice(office)
        assertEquals("Barwa Tola", filled.localityName)
        assertEquals("2", filled.beatNumber)
        assertEquals("north side", filled.remarks)
        assertEquals("BO", filled.officeType)
        assertEquals("Barwa", filled.officeName)
        assertEquals("Nirsa Chatti SO", filled.accountOffice)
        assertEquals("Dhanbad", filled.district)
        assertEquals("Jharkhand", filled.state)
        assertEquals("828205", filled.pincode)

        assertEquals("New Delhi", tidyCase("NEW DELHI"))
        assertEquals("Andaman & Nicobar Islands", tidyCase("ANDAMAN & NICOBAR ISLANDS"))
        assertEquals("Sitapur", tidyCase("Sitapur"))
        assertEquals("", tidyCase("  "))
    }
}
