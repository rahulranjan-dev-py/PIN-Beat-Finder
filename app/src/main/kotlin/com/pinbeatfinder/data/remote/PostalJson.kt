package com.pinbeatfinder.data.remote

import kotlinx.serialization.json.Json

/** JSON configuration for the postal API; tolerant of the API's nulls and undocumented keys. */
object PostalJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }
}
