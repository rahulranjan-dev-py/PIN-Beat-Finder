package com.pinbeatfinder.data

import com.pinbeatfinder.data.prefs.KeyValueStore
import com.pinbeatfinder.data.remote.PostalJson
import com.pinbeatfinder.data.update.Checksums
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
        assertTrue(VersionCompare.isNewer("v0.9.0", "v0.9.0-rc1"))
        assertTrue(VersionCompare.isNewer("v0.9.0-rc2", "v0.9.0-rc1"))
        assertTrue(VersionCompare.isNewer("v0.9.0-rc10", "v0.9.0-rc9"))
        assertTrue(VersionCompare.isNewer("v0.9.1-rc1", "v0.9.0"))
        assertFalse(VersionCompare.isNewer("v0.9.0+build5", "v0.9.0"))
        assertEquals(listOf(0, 9, 0), VersionCompare.parse("v0.9.0"))
        assertEquals(listOf(0, 9, 0), VersionCompare.parse("v0.9.0-rc1"))
    }

    @Test
    fun `newer release is found, drafts skipped, apk asset picked`() = runBlocking {
        val store = MemoryStore()
        val checker = UpdateChecker("0.8.0", store, fetchReleases = { PostalJson.instance.parseToJsonElement(feed) }, clock = { 1_000L })
        val rel = checker.check()
        assertNotNull(rel)
        assertEquals("v0.9.0", rel!!.tag)
        assertEquals("https://x/d/app-0.9.0.apk", rel.apkUrl)
        assertEquals("https://x/d/SHA256SUMS.txt", rel.checksumsUrl)
        assertTrue(checker.state.value.shouldShowBanner)
        checker.dismiss("v0.9.0")
        assertFalse(checker.state.value.shouldShowBanner)
        assertEquals("v0.9.0", store.map[UpdateChecker.KEY_DISMISSED])
    }

    @Test
    fun `later hides until the next foreground check`() = runBlocking {
        var now = 1_000L
        val checker = UpdateChecker("0.8.0", MemoryStore(), fetchReleases = { PostalJson.instance.parseToJsonElement(feed) }, clock = { now })
        checker.check()
        checker.later("v0.9.0")
        assertFalse(checker.state.value.shouldShowBanner)
        checker.checkOnForeground()               // within the foreground throttle: no network, but un-snoozed
        assertTrue(checker.state.value.shouldShowBanner)
        now += UpdateChecker.FOREGROUND_MIN_INTERVAL_MS + 1
        assertEquals("v0.9.0", checker.checkOnForeground()!!.tag)
    }

    @Test
    fun `checksum manifest parsing and hashing`() {
        val text = "abc\n" +
            "6b26063cd28a9a54927404a0331323d9658d9917f5d8ea80c88140018eb5e980  pin-beat-finder-v0.15.1-release.apk\n" +
            "E7B682F492B136B7919642176C961CC44FC45D6B0BF8E3FF14019DFB0BB32024 *pin-beat-finder-v0.15.1-release.aab\n"
        val m = Checksums.parse(text)
        assertEquals(2, m.size)
        assertEquals("6b26063cd28a9a54927404a0331323d9658d9917f5d8ea80c88140018eb5e980", m["pin-beat-finder-v0.15.1-release.apk"])
        assertEquals("e7b682f492b136b7919642176c961cc44fc45d6b0bf8e3ff14019dfb0bb32024", m["pin-beat-finder-v0.15.1-release.aab"])
        assertEquals("pin-beat-finder-v0.15.1-release.apk", Checksums.fileNameOf("https://github.com/x/y/releases/download/v0.15.1/pin-beat-finder-v0.15.1-release.apk?raw=1"))
        // SHA-256("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Checksums.sha256("abc".byteInputStream()))
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
