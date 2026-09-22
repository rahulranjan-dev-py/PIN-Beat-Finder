package com.pinbeatfinder

import android.app.Application
import android.content.Context
import com.pinbeatfinder.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BeatFinderApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Lives as long as the process; used for work that must outlive any single screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)
        container.crashReporter.install()
        // First launch (or a new asset version) loads the bundled All-India directory into Room.
        appScope.launch { container.directorySeeder.ensureSeeded() }
        // Sideloaded builds: ask GitHub Releases whether something newer exists (throttled to 6 h).
        appScope.launch { if (container.connectivity.isOnline()) container.updateChecker.check() }
    }
}

/** Convenience accessor for composables and ViewModel factories. */
val Context.appContainer: AppContainer
    get() = (applicationContext as BeatFinderApp).container
