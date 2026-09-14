package com.pinbeatfinder.data

import com.pinbeatfinder.core.util.AppError
import com.pinbeatfinder.core.util.AppResult
import com.pinbeatfinder.data.remote.ConnectivityChecker
import com.pinbeatfinder.data.remote.PostalProvider
import com.pinbeatfinder.data.remote.ProviderFormatException
import com.pinbeatfinder.data.remote.ProviderNoResultsException
import com.pinbeatfinder.data.repository.PostalLookupRepository
import com.pinbeatfinder.domain.model.PostOffice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class PostalLookupRepositoryTest {
    private val online = ConnectivityChecker { true }
    private fun office(name: String, source: String) =
        PostOffice(name, "Sub Post Office", "Delivery", "", "", "", "", "D", "S", "110001", source)

    private fun provider(id: String, supportsName: Boolean = false, behaviour: () -> List<PostOffice>) =
        PostalProvider(id, id, supportsName, fetch = { _, _ -> JsonNull }, map = { _, _ -> behaviour() })

    private fun repo(vararg p: PostalProvider) = PostalLookupRepository(p.toList(), online, Dispatchers.Unconfined)

    @Test
    fun `first provider that answers wins`() = runBlocking {
        val r = repo(
            provider("a") { throw SocketTimeoutException() },
            provider("b") { throw ProviderFormatException("html") },
            provider("c") { listOf(office("X", "c")) },
            provider("d") { listOf(office("Y", "d")) },
        ).lookup("110001")
        assertTrue(r is AppResult.Success)
        assertEquals("c", (r as AppResult.Success).value.single().source)
    }

    @Test
    fun `not found wins over transport errors when everything fails`() = runBlocking {
        val r = repo(
            provider("a") { throw SocketTimeoutException() },
            provider("b") { throw ProviderNoResultsException("nothing") },
        ).lookup("110001")
        assertTrue((r as AppResult.Failure).error is AppError.NotFound)
    }

    @Test
    fun `timeout reported when all providers time out`() = runBlocking {
        val r = repo(provider("a") { throw SocketTimeoutException() }).lookup("110001")
        assertEquals(AppError.Timeout, (r as AppResult.Failure).error)
    }

    @Test
    fun `name searches skip pincode-only providers`() = runBlocking {
        var pinOnlyCalled = false
        val r = repo(
            provider("pin-only") { pinOnlyCalled = true; listOf(office("wrong", "pin-only")) },
            provider("names", supportsName = true) { listOf(office("Rampur", "names")) },
        ).lookup("Rampur")
        assertTrue(!pinOnlyCalled)
        assertEquals("names", (r as AppResult.Success).value.single().source)
    }

    @Test
    fun `blank query is an empty success`() = runBlocking {
        val r = repo(provider("a") { error("must not be called") }).lookup("   ")
        assertTrue(r is AppResult.Success && r.value.isEmpty())
    }
}
