package com.pinbeatfinder.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.pinbeatfinder.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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

class AndroidConnectivityChecker(context: Context) : ConnectivityChecker {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Current state followed by every change of the default network. */
    override fun observe(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(isOnline()) }
            override fun onLost(network: Network) { trySend(isOnline()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(isOnline()) }
        }
        trySend(isOnline())
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    /**
     * "Online" means a network that claims internet access. `NET_CAPABILITY_VALIDATED` is
     * deliberately *not* required: many Indian mobile networks never pass Android's captive-portal
     * probe even though real traffic flows, and requiring it made the app force cache-only
     * requests and report "You are offline" while actually connected.
     */
    override fun isOnline(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * Builds the OkHttp/Retrofit stack shared by every postal provider.
 *
 * Caching strategy: providers send weak or no cache headers, so a *network* interceptor stamps
 * every successful response with `max-age=1 day`, and an *application* interceptor switches to
 * `only-if-cached` when there is no network at all. Postal data changes rarely, so a stale answer
 * beats an error for field staff.
 */
object NetworkModule {
    const val CACHE_SIZE_BYTES = 10L * 1024 * 1024 // 10 MB
    private const val ONLINE_MAX_AGE_SECONDS = 24 * 60 * 60
    private const val OFFLINE_MAX_STALE_DAYS = 30
    private const val USER_AGENT = "PINBeatFinder/${BuildConfig.VERSION_NAME} (Android; +https://github.com/rahulranjan-dev-py/PIN-Beat-Finder)"

    val json: Json get() = PostalJson.instance

    fun okHttpClient(cacheDir: File, connectivity: ConnectivityChecker): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http_cache"), CACHE_SIZE_BYTES))
            // Providers race in parallel, so a slow one only needs to lose, not to finish.
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(headersInterceptor())
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

    /**
     * Provider list; all are queried in parallel and the fastest non-empty answer wins, so order
     * only matters as a tie-break. The static mirror is a CDN and usually wins for PIN lookups.
     * Name searches are only served by providers that support them.
     * [dataGovInApiKey] is read on every call so a key changed in Settings applies immediately.
     */
    fun postalProviders(api: PostalApiService, dataGovInApiKey: () -> String): List<PostalProvider> = listOf(
        PostalProvider(
            id = PostalProviders.ID_GITHUB_MIRROR,
            label = PostalProviders.LABEL_GITHUB_MIRROR,
            supportsNameSearch = false,
            fetch = { q, _ -> api.githubMirrorByPincode(q).toFetchResult() },
            // The mirror omits the pincode on each office; the mapper needs the requested one.
            map = { root, pin -> PostalProviders.mapGithubMirror(root, pin) },
        ),
        PostalProvider(
            id = PostalProviders.ID_DATA_GOV_IN,
            label = PostalProviders.LABEL_DATA_GOV_IN,
            supportsNameSearch = false,
            fetch = { q, _ -> api.dataGovInByPincode(PostalApiService.DATA_GOV_IN_RESOURCE_ID, dataGovInApiKey(), q).toFetchResult() },
            map = { root, _ -> PostalProviders.mapDataGovIn(root) },
        ),
        PostalProvider(
            id = PostalProviders.ID_POSTALPINCODE_IN,
            label = PostalProviders.LABEL_POSTALPINCODE_IN,
            supportsNameSearch = true,
            fetch = { q, isPin -> (if (isPin) api.postalPincodeInByPincode(q) else api.postalPincodeInByName(q)).toFetchResult() },
            map = { root, _ -> PostalProviders.mapPostalPincodeIn(root) },
        ),
    )

    private fun headersInterceptor() = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build(),
        )
    }

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
