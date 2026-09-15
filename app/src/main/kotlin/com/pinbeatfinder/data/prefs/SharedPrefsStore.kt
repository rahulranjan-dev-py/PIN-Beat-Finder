package com.pinbeatfinder.data.prefs

import android.content.Context

class SharedPrefsStore(context: Context, name: String = "pin_beat_finder_prefs") : KeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun read(key: String): String? = prefs.getString(key, null)
    override fun write(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}
