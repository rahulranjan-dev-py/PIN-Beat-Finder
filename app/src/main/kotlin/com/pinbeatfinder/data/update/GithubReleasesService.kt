package com.pinbeatfinder.data.update

import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GithubReleasesService {
    /** Public endpoint; includes pre-releases (unlike `/releases/latest`). */
    @GET("https://api.github.com/repos/{repo}/releases")
    suspend fun releases(@Path("repo", encoded = true) repo: String, @Query("per_page") perPage: Int = 5): JsonElement
}
