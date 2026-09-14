package com.pinbeatfinder.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.pinbeatfinder.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/** Answers "is there a usable network right now?" — used for cache policy and error mapping. */
fun interface ConnectivityChecker {
    fun isOnline(): Boolean
}

class AndroidConnectivityChecker(context: Context) : ConnectivityChecker {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isOnline(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/**
 * Builds the OkHttp/Retrofit stack for the public postal API.
 *
 * Caching strategy: the API sends no cache headers, so a *network* interceptor stamps every
 * successful response with `max-age=1 day`, and an *application* interceptor switches to
 * `only-if-cached` when the device is offline. Postal data changes rarely, so a stale answer
 * beats an error for field staff.
 */
object NetworkModule {
    const val CACHE_SIZE_BYTES = 10L * 1024 * 1024 // 10 MB
    private const val ONLINE_MAX_AGE_SECONDS = 24 * 60 * 60
    private const val OFFLINE_MAX_STALE_DAYS = 30

    val json: Json get() = PostalJson.instance

    fun okHttpClient(cacheDir: File, connectivity: ConnectivityChecker): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http_cache"), CACHE_SIZE_BYTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(offlineCacheInterceptor(connectivity))
            .addNetworkInterceptor(responseCacheInterceptor())

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        return builder.build()
    }

    fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(PostalApiService.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    fun postalApi(retrofit: Retrofit): PostalApiService = retrofit.create(PostalApiService::class.java)

    private fun offlineCacheInterceptor(connectivity: ConnectivityChecker) = Interceptor { chain ->
        var request = chain.request()
        if (!connectivity.isOnline()) {
            val cacheControl = CacheControl.Builder()
                .onlyIfCached()
                .maxStale(OFFLINE_MAX_STALE_DAYS, TimeUnit.DAYS)
                .build()
            request = request.newBuilder().cacheControl(cacheControl).build()
        }
        chain.proceed(request)
    }

    private fun responseCacheInterceptor() = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.isSuccessful) {
            response.newBuilder()
                .removeHeader("Pragma")
                .header("Cache-Control", "public, max-age=$ONLINE_MAX_AGE_SECONDS")
                .build()
        } else {
            response
        }
    }
}
