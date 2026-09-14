package com.pinbeatfinder.di

import android.content.Context
import com.pinbeatfinder.core.phonetic.PhoneticSearchEngine
import com.pinbeatfinder.data.excel.ExcelSyncManager
import com.pinbeatfinder.data.local.BeatFinderDatabase
import com.pinbeatfinder.data.remote.AndroidConnectivityChecker
import com.pinbeatfinder.data.remote.NetworkModule
import com.pinbeatfinder.data.remote.PostalApiService
import com.pinbeatfinder.data.repository.BeatDirectoryRepository
import com.pinbeatfinder.data.repository.PostalLookupRepository

/**
 * Hand-rolled dependency graph. The app has one process-wide graph and no scopes beyond
 * "singleton", so a DI framework would add build time and code-gen without buying anything.
 * Everything is lazy so cold start does not touch the database or build an OkHttp client.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val phoneticEngine: PhoneticSearchEngine = PhoneticSearchEngine()

    val database: BeatFinderDatabase by lazy { BeatFinderDatabase.build(appContext) }

    val beatDirectoryRepository: BeatDirectoryRepository by lazy {
        BeatDirectoryRepository(database.beatDirectoryDao(), phoneticEngine)
    }

    val connectivity: AndroidConnectivityChecker by lazy { AndroidConnectivityChecker(appContext) }

    val postalApi: PostalApiService by lazy {
        val client = NetworkModule.okHttpClient(appContext.cacheDir, connectivity)
        NetworkModule.postalApi(NetworkModule.retrofit(client))
    }

    val postalLookupRepository: PostalLookupRepository by lazy {
        PostalLookupRepository(postalApi, connectivity)
    }

    val excelSyncManager: ExcelSyncManager by lazy {
        ExcelSyncManager(appContext, beatDirectoryRepository)
    }
}
