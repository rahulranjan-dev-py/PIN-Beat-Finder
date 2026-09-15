package com.pinbeatfinder.core

import com.pinbeatfinder.domain.model.PostOffice
import com.pinbeatfinder.domain.model.plainName
import com.pinbeatfinder.domain.model.toBeatDraft
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
    fun `branch office fills BO column, others fill SO column`() {
        val bo = po("Rampur B.O", "Branch Post Office").toBeatDraft()
        assertEquals("Rampur", bo.localityName)
        assertEquals("Rampur B.O", bo.branchOffice)
        assertEquals("", bo.subPostOffice)
        assertEquals("756137", bo.pincode)
        assertEquals("Odisha", bo.state)
        assertEquals("Bhadrak", bo.district)
        assertEquals("", bo.beatNumber)

        val so = po("Sitapur S.O", "Sub Post Office").toBeatDraft()
        assertEquals("", so.branchOffice)
        assertEquals("Sitapur S.O", so.subPostOffice)
    }
}
