package com.pinbeatfinder.core

import com.pinbeatfinder.core.phonetic.PhoneticSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneticSearchEngineTest {
    private val engine = PhoneticSearchEngine()

    @Test
    fun `misspelled indian names share phonetic keys`() {
        val pairs = listOf(
            "Rampur" to "Rampoor",
            "Ghaziabad" to "Gaziabad",
            "Bhilwara" to "Bilwara",
            "Kanchipuram" to "Kancheepuram",
            "Vishakhapatnam" to "Visakhapatnam",
            "Tiruchirappalli" to "Tiruchirapalli",
            "Thiruvananthapuram" to "Tiruvanantapuram",
            "Mirzapur" to "Mirjapur",
            "Sultanpur" to "Sulthanpur",
        )
        for ((a, b) in pairs) {
            val ka = engine.encode(a)
            val kb = engine.encode(b)
            assertTrue("$a ($ka) should match $b ($kb)", engine.phoneticallyEqual(ka, kb))
        }
    }

    @Test
    fun `distinct names produce distinct keys`() {
        assertFalse(engine.phoneticallyEqual(engine.encode("Rampur"), engine.encode("Lucknow")))
        assertFalse(engine.phoneticallyEqual(engine.encode("Kanpur"), engine.encode("Jaipur")))
    }

    @Test
    fun `keys are per token so prefixes are meaningful`() {
        val full = engine.encode("Rampur Kalan")
        val partial = engine.encode("Rampur")
        assertTrue(full.primary.contains(' '))
        assertTrue(full.primary.startsWith(partial.primary))
    }

    @Test
    fun `empty input yields empty keys`() {
        assertTrue(engine.encode("").isEmpty)
        assertTrue(engine.encode("12345").isEmpty)
    }

    @Test
    fun `scores rank exact above prefix above phonetic above unrelated`() {
        val exact = engine.score("Rampur", "Rampur")
        val prefix = engine.score("Rampur", "Rampur Kalan")
        // "Rampoor" normalises to an exact match; use a vowel-order variant the normaliser leaves alone.
        val phonetic = engine.score("Barelly", "Bareilly")
        val unrelated = engine.score("Rampur", "Lucknow")
        assertEquals(1.0, exact, 0.0)
        assertEquals(1.0, engine.score("Rampoor", "Rampur"), 0.0)
        assertTrue(exact > prefix)
        assertTrue(prefix > phonetic)
        assertTrue(phonetic > unrelated)
        assertTrue(unrelated < 0.5)
    }

    @Test
    fun `token anywhere match still scores as a phonetic hit`() {
        val score = engine.score("Kalan", "Rampur Kalan")
        assertTrue("got $score", score >= 0.55)
    }

    @Test
    fun `jaro winkler sanity`() {
        assertEquals(1.0, PhoneticSearchEngine.jaroWinkler("abc", "abc"), 0.0)
        assertEquals(0.0, PhoneticSearchEngine.jaroWinkler("abc", ""), 0.0)
        assertTrue(PhoneticSearchEngine.jaroWinkler("martha", "marhta") > 0.95)
    }
}
