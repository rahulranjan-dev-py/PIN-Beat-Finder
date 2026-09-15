package com.pinbeatfinder.data.remote

import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Raw HTTP endpoints of every postal data provider the app can consult. Each call returns an
 * untyped [JsonElement]; the provider-specific shape is interpreted in [PostalProviders], so a
 * schema change upstream degrades to "no results from this provider" instead of a crash.
 *
 * Absolute URLs are used on purpose: the providers live on different hosts and Retrofit lets a
 * single service span them.
 */
interface PostalApiService {

    /**
     * Official "All India Pincode Directory" published by the Department of Posts on the Open
     * Government Data platform. Filters are exact-match, so it is only used for PIN lookups.
     */
    @GET("https://api.data.gov.in/resource/{resourceId}")
    suspend fun dataGovInByPincode(
        @Path("resourceId") resourceId: String,
        @Query("api-key") apiKey: String,
        @Query("filters[pincode]") pincode: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 100,
    ): Response<JsonElement>

    /** Static mirror of the same Department of Posts dataset, served from GitHub Pages (no key, no limits). */
    @GET("https://aniket-thapa.github.io/india-pincode-api/pincodes/{pincode}.json")
    suspend fun githubMirrorByPincode(@Path("pincode") pincode: String): Response<JsonElement>

    /** Community API (postalpincode.in). The only provider that supports search by office name. */
    @GET("https://api.postalpincode.in/pincode/{pincode}")
    suspend fun postalPincodeInByPincode(@Path("pincode") pincode: String): Response<JsonElement>

    @GET("https://api.postalpincode.in/postoffice/{name}")
    suspend fun postalPincodeInByName(@Path("name") name: String): Response<JsonElement>

    companion object {
        /** Retrofit still needs a base URL even though every endpoint is absolute. */
        const val BASE_URL = "https://api.postalpincode.in/"

        /** data.gov.in resource: "All India Pincode Directory through Webservice". */
        const val DATA_GOV_IN_RESOURCE_ID = "04cbe4b1-2f2b-4c39-a1d5-1c2e28bc0e32"
    }
}
