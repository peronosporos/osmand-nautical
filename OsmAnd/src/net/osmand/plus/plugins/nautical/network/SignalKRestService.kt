package net.osmand.plus.plugins.nautical.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Signal K REST API service using Retrofit for polar resources and self-identity.
 */
interface SignalKRestService {

    @GET("signalk/v2/api/resources/polars")
    suspend fun getPolars(): Response<Map<String, PolarProfile>>

    @GET("signalk/v2/api/resources/polars/{id}")
    suspend fun getPolarById(@Path("id") polarId: String): Response<PolarProfile>

    @PUT("signalk/v2/api/resources/polars/{id}")
    suspend fun uploadPolar(@Path("id") polarId: String, @Body profile: PolarProfile): Response<Void>

    @GET("signalk/v2/api/self")
    suspend fun getSelfIdentity(): Response<Map<String, Any>>

    @GET("signalk/v2/api/vessels/self")
    suspend fun getVesselSelf(): Response<Map<String, Any>>

    @GET("signalk/v2/api/vessels/self/navigation/course")
    suspend fun getCourse(): Response<SignalKCourse>

    @PUT("signalk/v2/api/vessels/self/navigation/course")
    suspend fun updateCourse(@Body course: SignalKCourse): Response<SignalKActionResponse>

    @GET("signalk/v2/api/resources/routes")
    suspend fun getRoutes(): Response<Map<String, SignalKRoute>>

    @GET("signalk/v2/api/resources/routes/{id}")
    suspend fun getRouteById(@Path("id") routeId: String): Response<SignalKRoute>

    @POST("signalk/v2/api/resources/routes")
    suspend fun createRoute(@Body route: SignalKRoute): Response<SignalKRouteIdResponse>

    @PUT("signalk/v2/api/resources/routes/{id}")
    suspend fun updateRoute(@Path("id") routeId: String, @Body route: SignalKRoute): Response<Void>

    @DELETE("signalk/v2/api/resources/routes/{id}")
    suspend fun deleteRoute(@Path("id") routeId: String): Response<Void>

    @GET("signalk/v2/api/resources/checklists")
    suspend fun getChecklists(): Response<Map<String, SignalKChecklist>>

    @PUT("signalk/v2/api/resources/checklists/{id}")
    suspend fun updateChecklist(@Path("id") id: String, @Body checklist: SignalKChecklist): Response<Void>

    @GET("signalk/v2/api/resources/logbook")
    suspend fun getLogbook(): Response<Map<String, SignalKLogbookEntry>>

    @GET("signalk/v2/api/resources/notes")
    suspend fun getNotes(): Response<Map<String, SignalKNote>>

    @GET("signalk/v2/api/resources/charts")
    suspend fun getCharts(): Response<Map<String, SignalKChart>>

    @GET("signalk/v2/api/resources/regions")
    suspend fun getRegions(): Response<Map<String, SignalKRegion>>

    @GET("plugins/signalk-vaarweginformatie-blocked/closures")
    suspend fun getWaterwayClosures(): Response<Map<String, SignalKRegion>>

    @GET("plugins/signalk-avurnav/warnings")
    suspend fun getAvurnavWarnings(): Response<Map<String, SignalKRegion>>

    @GET("signalk/v2/api/resources/waypoints")
    suspend fun getWaypoints(): Response<Map<String, SignalKWaypoint>>

    @POST("signalk/v2/api/resources/waypoints")
    suspend fun createWaypoint(@Body waypoint: SignalKWaypoint): Response<SignalKResourceResponse>

    @DELETE("signalk/v2/api/resources/waypoints/{id}")
    suspend fun deleteWaypoint(@Path("id") id: String): Response<Void>

    @POST("signalk/v2/api/resources/notes")
    suspend fun createNote(@Body note: SignalKNote): Response<SignalKResourceResponse>

    @PUT("signalk/v2/api/resources/notes/{id}")
    suspend fun updateNote(@Path("id") id: String, @Body note: SignalKNote): Response<Void>

    @GET("signalk/v1/api/plugins")
    suspend fun getPlugins(): Response<List<SignalKPluginInfo>>

    @GET("signalk/v2/api/tides/stations")
    suspend fun getTideStations(): Response<Map<String, SignalKTideStation>>

    @GET("signalk/v2/api/tides/stations/{id}/extremes")
    suspend fun getTideExtremes(@Path("id") stationId: String): Response<List<SignalKTideExtreme>>

    @GET("signalk/v2/api/tides/stations/{id}/timeline")
    suspend fun getTideTimeline(@Path("id") stationId: String): Response<List<SignalKTidePrediction>>

    @GET("signalk/v1/api/history/values")
    suspend fun getHistoryValues(
        @retrofit2.http.Query("paths") paths: String,
        @retrofit2.http.Query("from") from: String,
        @retrofit2.http.Query("to") to: String? = null,
        @retrofit2.http.Query("resolution") resolution: Int? = null
    ): Response<Map<String, Any>>

    @GET("signalk/v1/api/plugins/signalk-grib-weather-provider/grib")
    suspend fun getGribData(): Response<okhttp3.ResponseBody>

    @POST("signalk/v1/api/plugins/{pluginId}/calculate")
    suspend fun triggerPluginCalculation(@Path("pluginId") pluginId: String, @Body body: Map<String, Any>): Response<Map<String, Any>>

    // Control API (PUT / Action API)
    @PUT("signalk/v1/api/vessels/self/{path}")
    suspend fun putValue(@Path("path", encoded = true) path: String, @Body body: SignalKPutBody): Response<SignalKActionResponse>

    @PUT("{path}")
    suspend fun putGeneric(@Path("path", encoded = true) path: String, @Body body: SignalKPutBody): Response<SignalKActionResponse>

    companion object {
        fun create(baseUrl: String, okHttpClient: okhttp3.OkHttpClient): SignalKRestService? {
            if (baseUrl.isBlank() || baseUrl.contains("://:") || !baseUrl.contains("://")) {
                return null
            }
            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            if (normalizedUrl.toHttpUrlOrNull() == null) {
                return null
            }
            return try {
                Retrofit.Builder()
                    .baseUrl(normalizedUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(SignalKRestService::class.java)
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class SignalKPutBody(val value: Any)

data class SignalKPluginInfo(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val version: String
)

data class SignalKActionResponse(
    val state: String,
    val statusCode: Int? = null,
    val message: String? = null,
    val requestId: String? = null
)
