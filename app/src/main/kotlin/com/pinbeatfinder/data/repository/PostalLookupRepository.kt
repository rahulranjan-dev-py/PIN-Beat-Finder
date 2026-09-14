package com.pinbeatfinder.data.repository

import com.pinbeatfinder.core.util.AppError
import com.pinbeatfinder.core.util.AppResult
import com.pinbeatfinder.core.util.PinCodeValidator
import com.pinbeatfinder.data.remote.ConnectivityChecker
import com.pinbeatfinder.data.remote.PostalProvider
import com.pinbeatfinder.data.remote.ProviderFormatException
import com.pinbeatfinder.data.remote.ProviderNoResultsException
import com.pinbeatfinder.domain.model.PostOffice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Online All-India lookup with automatic failover.
 *
 * Providers are tried in order; the first one that returns at least one office wins. A provider
 * that is down, rate-limited, returns HTML, or has changed its schema is skipped and the next one
 * is consulted. Only when every provider fails is an error surfaced, chosen to be the most useful
 * one: "not found" beats "offline" beats "timeout" beats "server error".
 */
class PostalLookupRepository(
    private val providers: List<PostalProvider>,
    private val connectivity: ConnectivityChecker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun lookup(query: String): AppResult<List<PostOffice>> = withContext(ioDispatcher) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext AppResult.Success(emptyList())
        val isPincode = PinCodeValidator.isValid(q)

        val candidates = providers.filter { isPincode || it.supportsNameSearch }
        if (candidates.isEmpty()) {
            return@withContext AppResult.Failure(AppError.NotFound("Search by name is not available right now; try a PIN code."))
        }

        val failures = ArrayList<AppError>(candidates.size)
        for (provider in candidates) {
            when (val outcome = tryProvider(provider, q, isPincode)) {
                is AppResult.Success -> if (outcome.value.isNotEmpty()) return@withContext outcome
                is AppResult.Failure -> failures += outcome.error
            }
        }
        AppResult.Failure(pickMostUseful(failures))
    }

    private suspend fun tryProvider(provider: PostalProvider, query: String, isPincode: Boolean): AppResult<List<PostOffice>> =
        try {
            AppResult.Success(provider.map(provider.fetch(query, isPincode), query))
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

    companion object {
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
