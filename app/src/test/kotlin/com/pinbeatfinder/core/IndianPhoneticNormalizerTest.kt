package com.pinbeatfinder.core

import com.pinbeatfinder.core.phonetic.IndianPhoneticNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndianPhoneticNormalizerTest {

    private fun n(s: String) = IndianPhoneticNormalizer.normalize(s)

    @Test
    fun `long vowel digraphs collapse`() {
        assertEquals(n("Rampur"), n("Rampoor"))
        assertEquals(n("Rampur"), n("Raampur"))
        assertEquals(n("Sitapur"), n("Seetapur"))
    }

    @Test
    fun `aspirated consonants collapse`() {
        assertEquals(n("Bhopal"), n("Bopal"))
        assertEquals(n("Dhanbad"), n("Danbad"))
        assertEquals(n("Ghaziabad"), n("Gaziabad"))
        assertEquals(n("Khurja"), n("Kurja"))
        assertEquals(n("Thane"), n("Tane"))
    }

    @Test
    fun `v and w, s and sh, z and j are interchangeable`() {
        assertEquals(n("Vishwas Nagar"), n("Vishvas Nagar"))
        assertEquals(n("Shivaji"), n("Sivaji"))
        assertEquals(n("Zila"), n("Jila"))
    }

    @Test
    fun `postal suffix noise and aliases are removed`() {
        assertEquals("rampur", n("Rampur PO"))
        assertEquals("rampur", n("Rampur B.O."))
        assertEquals("rampur", n("Rampura"))
        assertEquals("rampur kalan", n("Rampur   Kalan"))
    }

    @Test
    fun `diacritics and punctuation are stripped`() {
        assertEquals("rampur", n("Rāmpūr"))
        assertEquals("", n("123 !!"))
        assertEquals("", n(""))
    }

    @Test
    fun `doubled consonants collapse`() {
        assertEquals(n("Kallan"), n("Kalan"))
        assertEquals(n("Cuttack"), n("Kutak"))
    }

    @Test
    fun `qualifiers keep the numbers and single letters that normalize drops`() {
        assertEquals(listOf("1"), IndianPhoneticNormalizer.qualifiers("Ward 1"))
        assertEquals(listOf("1"), IndianPhoneticNormalizer.qualifiers("Ward No. 01"))
        assertEquals(listOf("b"), IndianPhoneticNormalizer.qualifiers("Sector B"))
        assertEquals(emptyList<String>(), IndianPhoneticNormalizer.qualifiers("Rampur B.O."))
        assertEquals(emptyList<String>(), IndianPhoneticNormalizer.qualifiers("Rampur Kalan"))
        assertTrue(IndianPhoneticNormalizer.qualifiersDiffer("Ward 1", "Ward 2"))
        assertFalse(IndianPhoneticNormalizer.qualifiersDiffer("Ward-1", "ward 1"))
    }
}
