package net.osmand.plus.plugins.nautical.network

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Signal K REST API service using Retrofit for polar resources and self-identity.
 */
interface SignalKRestService {

    @GET("signalk/v1/api/resources/polars")
    suspend fun getPolars(): Response<Map<String, PolarProfile>>

    @GET("signalk/v1/api/resources/polars/{id}")
    suspend fun getPolarById(@Path("id") polarId: String): Response<PolarProfile>

    @PUT("signalk/v1/api/resources/polars/{id}")
    suspend fun uploadPolar(@Path("id") polarId: String, @Body profile: PolarProfile): Response<Void>

    @GET("signalk/v1/api/self")
    suspend fun getSelfIdentity(): Response<Map<String, Any>>

    companion object {
        fun create(baseUrl: String, okHttpClient: okhttp3.OkHttpClient): SignalKRestService {
            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            return Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SignalKRestService::class.java)
        }
    }
}
