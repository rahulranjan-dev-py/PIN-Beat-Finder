package com.pinbeatfinder

import android.app.Application
import android.content.Context
import com.pinbeatfinder.di.AppContainer

class BeatFinderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Convenience accessor for composables and ViewModel factories. */
val Context.appContainer: AppContainer
    get() = (applicationContext as BeatFinderApp).container
