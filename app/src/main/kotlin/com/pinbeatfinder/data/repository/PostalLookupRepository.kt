package com.pinbeatfinder.data.repository

import com.pinbeatfinder.core.util.AppError
import com.pinbeatfinder.core.util.AppResult
import com.pinbeatfinder.core.util.PinCodeValidator
import com.pinbeatfinder.data.remote.ConnectivityChecker
import com.pinbeatfinder.data.remote.LocalDirectorySource
import com.pinbeatfinder.data.remote.PostalProvider
import com.pinbeatfinder.data.remote.ProviderFormatException
import com.pinbeatfinder.data.remote.ProviderHealth
import com.pinbeatfinder.data.remote.ProviderNoResultsException
import com.pinbeatfinder.domain.model.PostOffice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Online All-India lookup with parallel failover.
 *
 * Every applicable provider is queried at once and the first non-empty answer wins; the rest are
 * cancelled. This trades a little extra traffic for latency that equals the *fastest* source
 * instead of the sum of every slow one before it. Only when every provider fails is an error
 * surfaced, chosen to be the most useful one: "not found" beats "offline" beats "timeout" beats
 * "server error". Each attempt also updates [health] for the Settings screen.
 */
class PostalLookupRepository(
    private val providers: List<PostalProvider>,
    private val connectivity: ConnectivityChecker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    /** On-device directory consulted first; network providers only run when it has no answer. */
    private val local: LocalDirectorySource? = null,
) {
    private val _health = MutableStateFlow(
        buildMap {
            local?.let { put(it.id, ProviderHealth(it.id, it.label)) }
            providers.forEach { put(it.id, ProviderHealth(it.id, it.label)) }
        },
    )
    val health: StateFlow<Map<String, ProviderHealth>> = _health.asStateFlow()

    suspend fun lookup(query: String): AppResult<List<PostOffice>> = withContext(ioDispatcher) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext AppResult.Success(emptyList())
        val isPincode = PinCodeValidator.isValid(q)

        local?.let { src ->
            if (src.isReady()) {
                val started = clock()
                val hit = runCatching { src.search(q, isPincode) }
                val latency = clock() - started
                hit.onSuccess { offices ->
                    _health.update { it + (src.id to ProviderHealth(src.id, src.label, ProviderHealth.Status.OK, latency, clock())) }
                    if (offices.isNotEmpty()) return@withContext AppResult.Success(offices)
                }.onFailure { e ->
                    _health.update { it + (src.id to ProviderHealth(src.id, src.label, ProviderHealth.Status.FAILED, latency, clock(), e.message)) }
                }
            }
        }

        val candidates = providers.filter { isPincode || it.supportsNameSearch }
        if (candidates.isEmpty()) {
            return@withContext AppResult.Failure(AppError.NotFound("Search by name is not available right now; try a PIN code."))
        }

        raceProviders(candidates, q, isPincode)
    }

    /** Runs a PIN lookup through every provider (in parallel) purely to refresh [health]. */
    suspend fun probeAll(pincode: String = PROBE_PINCODE) = withContext(ioDispatcher) {
        coroutineScope {
            providers.map { p -> async { tryProvider(p, pincode, isPincode = true) } }.forEach { it.await() }
        }
    }

    private suspend fun raceProviders(candidates: List<PostalProvider>, query: String, isPincode: Boolean): AppResult<List<PostOffice>> =
        coroutineScope {
            val pending: MutableList<Deferred<AppResult<List<PostOffice>>>> =
                candidates.map { p -> async { tryProvider(p, query, isPincode) } }.toMutableList()
            val failures = ArrayList<AppError>(candidates.size)
            while (pending.isNotEmpty()) {
                val (finished, outcome) = select<Pair<Deferred<AppResult<List<PostOffice>>>, AppResult<List<PostOffice>>>> {
                    pending.forEach { d -> d.onAwait { r -> d to r } }
                }
                pending.remove(finished)
                when (outcome) {
                    is AppResult.Success -> if (outcome.value.isNotEmpty()) {
                        pending.forEach { it.cancel() }
                        return@coroutineScope outcome
                    }
                    is AppResult.Failure -> failures += outcome.error
                }
            }
            AppResult.Failure(pickMostUseful(failures))
        }

    private suspend fun tryProvider(provider: PostalProvider, query: String, isPincode: Boolean): AppResult<List<PostOffice>> {
        val started = clock()
        val result: AppResult<List<PostOffice>> = try {
            val fetched = provider.fetch(query, isPincode)
            val offices = provider.map(fetched.body, query)
            AppResult.Success(if (fetched.fromCache) offices.map { it.copy(fromCache = true) } else offices)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProviderNoResultsException) {
            AppResult.Failure(AppError.NotFound(e.message ?: "No records found"))
        } catch (e: ProviderFormatException) {
            AppResult.Failure(AppError.Http(0, "${provider.label}: ${e.message}"))
        } catch (e: HttpException) {
            if (e.code() == 504 && !connectivity.isOnline()) {
                // OkHttp answers 504 "Unsatisfiable Request (only-if-cached)" when offline & uncached.
                AppResult.Failure(AppError.Offline)
            } else {
                AppResult.Failure(AppError.Http(e.code(), "${provider.label}: ${e.message()}"))
            }
        } catch (e: SocketTimeoutException) {
            AppResult.Failure(AppError.Timeout)
        } catch (e: InterruptedIOException) {
            AppResult.Failure(AppError.Timeout) // OkHttp call timeout
        } catch (e: UnknownHostException) {
            AppResult.Failure(if (connectivity.isOnline()) AppError.Http(0, "${provider.label}: host not reachable") else AppError.Offline)
        } catch (e: ConnectException) {
            AppResult.Failure(if (connectivity.isOnline()) AppError.Http(0, "${provider.label}: connection refused") else AppError.Offline)
        } catch (e: IOException) {
            AppResult.Failure(if (connectivity.isOnline()) AppError.Unknown(e) else AppError.Offline)
        } catch (e: Exception) {
            // Serialization or any other unexpected failure: treat as this provider being broken.
            AppResult.Failure(AppError.Unknown(e))
        }
        recordHealth(provider, result, clock() - started)
        return result
    }

    private fun recordHealth(provider: PostalProvider, result: AppResult<List<PostOffice>>, latencyMs: Long) {
        // "Not found" still means the service answered, so it counts as healthy.
        val (status, detail) = when (result) {
            is AppResult.Success -> ProviderHealth.Status.OK to null
            is AppResult.Failure -> when (val e = result.error) {
                is AppError.NotFound -> ProviderHealth.Status.OK to null
                AppError.Offline -> ProviderHealth.Status.FAILED to "offline"
                AppError.Timeout -> ProviderHealth.Status.FAILED to "timeout"
                is AppError.Http -> ProviderHealth.Status.FAILED to (if (e.code == 0) e.message else "HTTP ${e.code}")
                is AppError.Unknown -> ProviderHealth.Status.FAILED to (e.cause.message ?: e.cause::class.simpleName)
            }
        }
        _health.update { map ->
            map + (provider.id to ProviderHealth(provider.id, provider.label, status, latencyMs, clock(), detail))
        }
    }

    companion object {
        const val PROBE_PINCODE = "110001"

        /** Rank errors so the aggregate message reflects the most actionable cause. */
        fun pickMostUseful(errors: List<AppError>): AppError {
            errors.firstOrNull { it is AppError.NotFound }?.let { return it }
            errors.firstOrNull { it is AppError.Offline }?.let { return it }
            errors.firstOrNull { it is AppError.Timeout }?.let { return it }
            errors.firstOrNull { it is AppError.Http }?.let { return it }
            return errors.firstOrNull() ?: AppError.NotFound("No records found")
        }
    }
}
