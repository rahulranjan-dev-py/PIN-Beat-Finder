package com.pinbeatfinder.data.repository

import com.pinbeatfinder.core.util.AppError
import com.pinbeatfinder.core.util.AppResult
import com.pinbeatfinder.core.util.PinCodeValidator
import com.pinbeatfinder.data.remote.ConnectivityChecker
import com.pinbeatfinder.data.remote.PostalApiEnvelope
import com.pinbeatfinder.data.remote.PostalApiService
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
 * Online All-India lookup. Decides whether a query is a PIN or a post-office name and maps
 * every transport failure to a user-presentable [AppError].
 */
class PostalLookupRepository(
    private val api: PostalApiService,
    private val connectivity: ConnectivityChecker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun lookup(query: String): AppResult<List<PostOffice>> = withContext(ioDispatcher) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext AppResult.Success(emptyList())
        try {
            val envelopes = if (PinCodeValidator.isValid(q)) api.byPincode(q) else api.byPostOffice(q)
            AppResult.Success(unwrap(envelopes))
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoResultsException) {
            AppResult.Failure(AppError.NotFound(e.message ?: "No records found"))
        } catch (e: HttpException) {
            if (e.code() == 504 && !connectivity.isOnline()) {
                // OkHttp answers 504 "Unsatisfiable Request (only-if-cached)" when offline & uncached.
                AppResult.Failure(AppError.Offline)
            } else {
                AppResult.Failure(AppError.Http(e.code(), e.message()))
            }
        } catch (e: SocketTimeoutException) {
            AppResult.Failure(AppError.Timeout)
        } catch (e: InterruptedIOException) {
            AppResult.Failure(AppError.Timeout) // OkHttp call timeout
        } catch (e: UnknownHostException) {
            AppResult.Failure(AppError.Offline)
        } catch (e: ConnectException) {
            AppResult.Failure(AppError.Offline)
        } catch (e: IOException) {
            AppResult.Failure(if (!connectivity.isOnline()) AppError.Offline else AppError.Unknown(e))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e))
        }
    }

    private fun unwrap(envelopes: List<PostalApiEnvelope>): List<PostOffice> {
        val envelope = envelopes.firstOrNull() ?: throw NoResultsException("Empty response from postal API")
        val offices = envelope.postOffice
        if (!envelope.isSuccess || offices.isNullOrEmpty()) {
            throw NoResultsException(envelope.message ?: "No records found")
        }
        return offices.map { dto ->
            PostOffice(
                name = dto.name.orEmpty(),
                branchType = dto.branchType.orEmpty(),
                deliveryStatus = dto.deliveryStatus.orEmpty(),
                circle = dto.circle.orEmpty(),
                division = dto.division.orEmpty(),
                region = dto.region.orEmpty(),
                block = dto.block.orEmpty(),
                district = dto.district.orEmpty(),
                state = dto.state.orEmpty(),
                pincode = dto.pincode.orEmpty(),
            )
        }
    }

    private class NoResultsException(message: String) : Exception(message)
}
