package com.pinbeatfinder.data

import com.pinbeatfinder.data.remote.PostalJson
import com.pinbeatfinder.data.remote.PostalProviders
import com.pinbeatfinder.data.remote.ProviderFormatException
import com.pinbeatfinder.data.remote.ProviderNoResultsException
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PostalProvidersTest {
    private fun j(s: String): JsonElement = PostalJson.instance.parseToJsonElement(s)

    @Test
    fun `data gov in records map with lowercase keys`() {
        val offices = PostalProviders.mapDataGovIn(
            j("""{"status":"ok","total":2,"count":2,"records":[
                {"circlename":"Delhi","regionname":"Delhi","divisionname":"New Delhi Central","officename":"Connaught Place S.O",
                 "pincode":110001,"officetype":"SO","delivery":"Delivery","district":"CENTRAL DELHI","statename":"DELHI","latitude":"NA","longitude":"NA"},
                {"OfficeName":"Baroda House S.O","Pincode":"110001","OfficeType":"S.O","Delivery":"Non Delivery","District":"CENTRAL DELHI","StateName":"DELHI"}
            ]}"""),
        )
        assertEquals(2, offices.size)
        assertEquals("Connaught Place S.O", offices[0].name)
        assertEquals("110001", offices[0].pincode)
        assertEquals("Sub Post Office", offices[0].branchType)
        assertEquals("Delivery", offices[0].deliveryStatus)
        assertEquals("DELHI", offices[1].state)          // mixed-case keys handled
        assertEquals("Non-Delivery", offices[1].deliveryStatus)
        assertEquals(PostalProviders.LABEL_DATA_GOV_IN, offices[0].source)
    }

    @Test
    fun `data gov in empty records is no-results`() {
        try {
            PostalProviders.mapDataGovIn(j("""{"status":"ok","total":0,"count":0,"records":[]}"""))
            fail()
        } catch (e: ProviderNoResultsException) { /* expected */ }
    }

    @Test
    fun `data gov in error payload is a format error`() {
        try {
            PostalProviders.mapDataGovIn(j("""{"message":"Invalid API key","status":"error"}"""))
            fail()
        } catch (e: ProviderFormatException) {
            assertTrue(e.message!!.contains("Invalid API key"))
        }
    }

    @Test
    fun `github mirror maps offices and inherits state, district and pincode`() {
        val offices = PostalProviders.mapGithubMirror(
            j("""{"state":"TELANGANA","district":"KUMURAM BHEEM ASIFABAD","offices":[
                {"officeName":"Kothimir B.O","officeType":"BO","deliveryStatus":"Delivery","circleName":"Telangana Circle",
                 "regionName":"Hyderabad Region","divisionName":"Adilabad Division","latitude":19.36,"longitude":79.53}]}"""),
            requestedPincode = "504273",
        )
        assertEquals(1, offices.size)
        assertEquals("Kothimir B.O", offices[0].name)
        assertEquals("Branch Office", offices[0].branchType)
        assertEquals("TELANGANA", offices[0].state)
        assertEquals("KUMURAM BHEEM ASIFABAD", offices[0].district)
        assertEquals("504273", offices[0].pincode)
        assertEquals("Adilabad Division", offices[0].division)
    }

    @Test
    fun `postalpincode in success and error envelopes`() {
        val ok = PostalProviders.mapPostalPincodeIn(
            j("""[{"Message":"Number of pincode(s) found:1","Status":"Success","PostOffice":[
                {"Name":"Connaught Place","Description":null,"BranchType":"Sub Post Office","DeliveryStatus":"Delivery",
                 "Circle":"Delhi","District":"Central Delhi","Division":"New Delhi Central","Region":"Delhi","Block":"New Delhi",
                 "State":"Delhi","Country":"India","Pincode":"110001"}]}]"""),
        )
        assertEquals(1, ok.size)
        assertEquals("Connaught Place", ok[0].name)
        assertEquals("110001", ok[0].pincode)
        assertEquals(PostalProviders.LABEL_POSTALPINCODE_IN, ok[0].source)

        try {
            PostalProviders.mapPostalPincodeIn(j("""[{"Message":"No records found","Status":"Error","PostOffice":null}]"""))
            fail()
        } catch (e: ProviderNoResultsException) {
            assertEquals("No records found", e.message)
        }
    }

    @Test
    fun `html or unexpected payload is a format error not a crash`() {
        try {
            PostalProviders.mapPostalPincodeIn(j("\"<html>Cloudflare</html>\""))
            fail()
        } catch (e: ProviderFormatException) { /* expected */ }
        try {
            PostalProviders.mapGithubMirror(j("[]"), "110001")
            fail()
        } catch (e: ProviderFormatException) { /* expected */ }
    }
}
