package com.pinbeatfinder.data.prefs

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.view.ContextThemeWrapper
import java.util.Locale

/**
 * In-app language without the platform per-app-locale machinery: the chosen language is a plain
 * setting, and every place that reads strings gets a context whose configuration carries it.
 * Switching therefore only recomposes; nothing is recreated and no black frame appears.
 */
object LocaleSupport {
    /** Configuration of [base] with [tag]'s locale first, or [base]'s own when [tag] is blank. */
    fun configurationFor(base: Context, tag: String): Configuration {
        val config = Configuration(base.resources.configuration)
        if (tag.isNotBlank()) {
            val locale = Locale.forLanguageTag(tag)
            config.setLocales(LocaleList(locale))
            config.setLayoutDirection(locale)
        }
        return config
    }

    /** For Activity.attachBaseContext: resources, and anything using the activity's context, follow [tag]. */
    fun wrapBase(base: Context, tag: String): Context =
        if (tag.isBlank()) base else base.createConfigurationContext(configurationFor(base, tag))

    /**
     * For Compose's LocalContext at runtime: a wrapper around the activity (so startActivity and
     * friends still behave as from an Activity) whose resources use [tag].
     */
    fun wrapForCompose(activity: Context, tag: String): Context {
        if (tag.isBlank()) return activity
        return ContextThemeWrapper(activity, activity.theme).apply { applyOverrideConfiguration(configurationFor(activity, tag)) }
    }
}
