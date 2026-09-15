package com.pinbeatfinder.data

import com.pinbeatfinder.data.prefs.KeyValueStore
import com.pinbeatfinder.data.remote.PostalJson
import com.pinbeatfinder.data.update.UpdateChecker
import com.pinbeatfinder.data.update.VersionCompare
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private class MemoryStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun read(key: String) = map[key]
        override fun write(key: String, value: String) { map[key] = value }
    }

    private val feed = """[
      {"tag_name":"v0.9.0","draft":false,"prerelease":true,"html_url":"https://x/r/v0.9.0","body":"notes",
       "assets":[{"name":"SHA256SUMS.txt","browser_download_url":"https://x/d/SHA256SUMS.txt"},{"name":"app.apk","browser_download_url":"https://x/d/app-0.9.0.apk"}]},
      {"tag_name":"v0.8.0","draft":false,"prerelease":true,"html_url":"https://x/r/v0.8.0","assets":[]},
      {"tag_name":"v1.0.0","draft":true,"html_url":"https://x/r/v1.0.0","assets":[]}
    ]"""

    @Test
    fun `version comparison is numeric`() {
        assertTrue(VersionCompare.isNewer("v0.10.0", "0.9.0"))
        assertTrue(VersionCompare.isNewer("v1.0.0", "v0.9.9"))
        assertFalse(VersionCompare.isNewer("v0.9.0", "0.9.0"))
        assertFalse(VersionCompare.isNewer("v0.9.0-rc1", "0.9.0"))
        assertEquals(listOf(0, 9, 0), VersionCompare.parse("v0.9.0"))
    }

    @Test
    fun `newer release is found, drafts skipped, apk asset picked`() = runBlocking {
        val store = MemoryStore()
        val checker = UpdateChecker("0.8.0", store, fetchReleases = { PostalJson.instance.parseToJsonElement(feed) }, clock = { 1_000L })
        val rel = checker.check()
        assertNotNull(rel)
        assertEquals("v0.9.0", rel!!.tag)
        assertEquals("https://x/d/app-0.9.0.apk", rel.apkUrl)
        assertTrue(checker.state.value.shouldShowBanner)
        checker.dismiss("v0.9.0")
        assertFalse(checker.state.value.shouldShowBanner)
        assertEquals("v0.9.0", store.map[UpdateChecker.KEY_DISMISSED])
    }

    @Test
    fun `up to date and throttling`() = runBlocking {
        var calls = 0
        var now = 10_000L
        val checker = UpdateChecker("0.9.0", MemoryStore(), fetchReleases = { calls++; PostalJson.instance.parseToJsonElement(feed) }, clock = { now }, minIntervalMs = 1_000)
        assertNull(checker.check())
        assertNull(checker.check())          // throttled, no second call
        assertEquals(1, calls)
        now += 2_000
        checker.check()                      // interval elapsed
        assertEquals(2, calls)
        checker.check(force = true)
        assertEquals(3, calls)
    }

    @Test
    fun `network failure is reported not thrown`() = runBlocking {
        val checker = UpdateChecker("0.9.0", MemoryStore(), fetchReleases = { throw java.io.IOException("boom") })
        assertNull(checker.check(force = true))
        assertEquals("boom", checker.state.value.error)
        assertFalse(checker.state.value.checking)
    }
}
