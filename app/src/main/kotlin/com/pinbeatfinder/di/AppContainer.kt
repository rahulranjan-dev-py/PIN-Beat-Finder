package com.pinbeatfinder.di

import android.content.Context
import com.pinbeatfinder.BuildConfig
import com.pinbeatfinder.core.phonetic.PhoneticSearchEngine
import com.pinbeatfinder.data.directory.DirectoryDatabase
import com.pinbeatfinder.data.directory.DirectorySeeder
import com.pinbeatfinder.data.directory.IndiaPostDirectoryRepository
import com.pinbeatfinder.data.crash.CrashReporter
import com.pinbeatfinder.data.excel.ExcelSyncManager
import com.pinbeatfinder.data.update.ApkDownloader
import com.pinbeatfinder.data.update.GithubReleasesService
import com.pinbeatfinder.data.update.UpdateChecker
import com.pinbeatfinder.data.local.BeatFinderDatabase
import com.pinbeatfinder.data.prefs.AppSettingsRepository
import com.pinbeatfinder.data.prefs.RecentSearchesRepository
import com.pinbeatfinder.data.prefs.SharedPrefsStore
import com.pinbeatfinder.data.remote.AndroidConnectivityChecker
import com.pinbeatfinder.data.remote.NetworkModule
import com.pinbeatfinder.data.remote.PostalApiService
import com.pinbeatfinder.data.repository.BeatDirectoryRepository
import com.pinbeatfinder.data.repository.PostalLookupRepository
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

/**
 * Hand-rolled dependency graph. The app has one process-wide graph and no scopes beyond
 * "singleton", so a DI framework would add build time and code-gen without buying anything.
 * Everything is lazy so cold start does not touch the database or build an OkHttp client.
 */
class AppContainer(context: Context, private val appScope: CoroutineScope) {
    private val appContext = context.applicationContext

    val phoneticEngine: PhoneticSearchEngine = PhoneticSearchEngine()

    val database: BeatFinderDatabase by lazy { BeatFinderDatabase.build(appContext) }

    val beatDirectoryRepository: BeatDirectoryRepository by lazy {
        BeatDirectoryRepository(database.beatDirectoryDao(), phoneticEngine)
    }

    val connectivity: AndroidConnectivityChecker by lazy { AndroidConnectivityChecker(appContext) }

    val appSettingsRepository: AppSettingsRepository by lazy {
        AppSettingsRepository(SharedPrefsStore(appContext))
    }

    val okHttpClient: OkHttpClient by lazy { NetworkModule.okHttpClient(appContext.cacheDir, connectivity) }

    /**
     * The lookup client caches every answer for a day so searches work offline; an update check
     * served from that cache would hide a release published after the last check. This client
     * shares the connection pool but has no cache and no cache-rewriting interceptors.
     */
    val liveHttpClient: OkHttpClient by lazy { NetworkModule.liveClient(okHttpClient) }

    val postalApi: PostalApiService by lazy { NetworkModule.postalApi(NetworkModule.retrofit(okHttpClient)) }

    val directoryDatabase: DirectoryDatabase by lazy { DirectoryDatabase.build(appContext) }

    val directorySeeder: DirectorySeeder by lazy {
        DirectorySeeder(appContext, directoryDatabase, SharedPrefsStore(appContext))
    }

    val indiaPostDirectory: IndiaPostDirectoryRepository by lazy {
        IndiaPostDirectoryRepository(directoryDatabase.directoryDao(), directorySeeder, phoneticEngine)
    }

    val postalLookupRepository: PostalLookupRepository by lazy {
        PostalLookupRepository(
            providers = NetworkModule.postalProviders(postalApi) {
                appSettingsRepository.settings.value.dataGovInApiKey.ifBlank { BuildConfig.DATA_GOV_IN_API_KEY }
            },
            connectivity = connectivity,
            local = indiaPostDirectory,
        )
    }

    val updateChecker: UpdateChecker by lazy {
        val service = NetworkModule.retrofit(liveHttpClient).create(GithubReleasesService::class.java)
        UpdateChecker(
            currentVersion = BuildConfig.VERSION_NAME,
            store = SharedPrefsStore(appContext),
            fetchReleases = { service.releases(BuildConfig.GITHUB_REPO) },
        )
    }

    val apkDownloader: ApkDownloader by lazy {
        ApkDownloader(NetworkModule.downloadClient(liveHttpClient), appContext.cacheDir, appScope)
    }

    val crashReporter: CrashReporter by lazy { CrashReporter(appContext, SharedPrefsStore(appContext), BuildConfig.VERSION_NAME) }

    /** Drops every cached online answer. Runs on the caller's dispatcher; call from IO. */
    fun clearOnlineCache() { okHttpClient.cache?.evictAll() }

    val recentSearchesRepository: RecentSearchesRepository by lazy {
        RecentSearchesRepository(SharedPrefsStore(appContext))
    }

    val excelSyncManager: ExcelSyncManager by lazy {
        ExcelSyncManager(appContext, beatDirectoryRepository) { appSettingsRepository.settings.value.language }
    }
}
