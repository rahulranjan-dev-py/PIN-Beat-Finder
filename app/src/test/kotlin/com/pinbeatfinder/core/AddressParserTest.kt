package com.pinbeatfinder.core

import com.pinbeatfinder.core.util.AddressParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressParserTest {
    @Test
    fun `a plain village name is not an address`() {
        val p = AddressParser.parse("Ambona")
        assertFalse(p.isAddress)
        assertTrue(p.candidates.isEmpty())
        assertFalse(AddressParser.looksLikeAddress("Rampur Kalan"))
        assertNull(AddressParser.onlineQuery("Rampur Kalan"))
    }

    @Test
    fun `a full address yields the PIN and the locality first`() {
        val p = AddressParser.parse("Shri Ram Kumar, Vill Ambona, PO Baliapur, Dist Dhanbad, Jharkhand 828201, Mob 9876543210")
        assertTrue(p.isAddress)
        assertEquals("828201", p.pincode)
        assertEquals("Ambona", p.candidates.first())
        assertEquals("Baliapur", p.candidates[1])
        assertTrue(p.candidates.none { it.contains("9876") })
        assertTrue(p.candidates.none { it.equals("Dist", ignoreCase = true) })
    }

    @Test
    fun `multi-line paste, spaced PIN and C-O lines are handled`() {
        val p = AddressParser.parse("C/O Mohan Lal\nVillage Chhota Ambona\nP.O. Baliapur\nPIN 828 201")
        assertTrue(p.isAddress)
        assertEquals("828201", p.pincode)
        assertEquals("Chhota", p.candidates.first())
        assertTrue("Chhota Ambona" in p.candidates)
        assertEquals("828201", AddressParser.onlineQuery("Village Chhota Ambona, P.O. Baliapur, PIN 828 201"))
    }

    @Test
    fun `address without a PIN searches the office name online`() {
        assertEquals("Baliapur", AddressParser.onlineQuery("Vill Ambona PO Baliapur Dist Dhanbad"))
        assertEquals("Ambona", AddressParser.parse("Vill Ambona PO Baliapur Dist Dhanbad").candidates.first())
    }

    @Test
    fun `a bare PIN or beat number stays a plain query`() {
        assertFalse(AddressParser.parse("828201").isAddress)
        assertFalse(AddressParser.parse("3").isAddress)
    }
}
