package com.pinbeatfinder.data

import com.pinbeatfinder.data.remote.PostalJson
import com.pinbeatfinder.data.remote.PostalApiEnvelope
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostalApiDtoTest {
    private val serializer = ListSerializer(PostalApiEnvelope.serializer())

    @Test
    fun `success payload parses`() {
        val body = """
            [{"Message":"Number of pincode(s) found:1","Status":"Success","PostOffice":[
              {"Name":"Connaught Place","Description":null,"BranchType":"Sub Post Office","DeliveryStatus":"Delivery",
               "Circle":"Delhi","District":"Central Delhi","Division":"New Delhi Central","Region":"Delhi",
               "Block":"New Delhi","State":"Delhi","Country":"India","Pincode":"110001","Extra":"ignored"}]}]
        """.trimIndent()
        val parsed = PostalJson.instance.decodeFromString(serializer, body)
        assertEquals(1, parsed.size)
        assertTrue(parsed[0].isSuccess)
        assertEquals("Connaught Place", parsed[0].postOffice!![0].name)
        assertEquals("110001", parsed[0].postOffice!![0].pincode)
    }

    @Test
    fun `error payload parses with null post offices`() {
        val body = """[{"Message":"No records found","Status":"Error","PostOffice":null}]"""
        val parsed = PostalJson.instance.decodeFromString(serializer, body)
        assertFalse(parsed[0].isSuccess)
        assertNull(parsed[0].postOffice)
        assertEquals("No records found", parsed[0].message)
    }
}
