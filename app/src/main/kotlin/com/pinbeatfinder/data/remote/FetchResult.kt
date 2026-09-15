package com.pinbeatfinder.data.remote

import kotlinx.serialization.json.JsonElement
import retrofit2.HttpException
import retrofit2.Response

/** Raw provider payload plus whether OkHttp served it from the on-disk cache rather than the network. */
data class FetchResult(val body: JsonElement, val fromCache: Boolean)

/** Converts a Retrofit response into a [FetchResult], throwing [HttpException] on non-2xx like a plain suspend call would. */
fun Response<JsonElement>.toFetchResult(): FetchResult {
    if (!isSuccessful) throw HttpException(this)
    val raw = raw()
    val fromCache = raw.networkResponse == null && raw.cacheResponse != null
    return FetchResult(body() ?: throw HttpException(this), fromCache)
}
