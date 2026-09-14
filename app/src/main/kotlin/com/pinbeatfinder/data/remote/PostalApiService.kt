package com.pinbeatfinder.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface PostalApiService {
    @GET("pincode/{pincode}")
    suspend fun byPincode(@Path("pincode") pincode: String): List<PostalApiEnvelope>

    @GET("postoffice/{name}")
    suspend fun byPostOffice(@Path("name") name: String): List<PostalApiEnvelope>

    companion object {
        const val BASE_URL = "https://api.postalpincode.in/"
    }
}
