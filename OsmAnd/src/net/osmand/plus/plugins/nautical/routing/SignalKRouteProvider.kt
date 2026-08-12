package net.osmand.plus.plugins.nautical.routing

import kotlinx.coroutines.runBlocking
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.routing.RouteCalculationParams
import net.osmand.plus.routing.RouteCalculationResult
import net.osmand.PlatformUtil
import net.osmand.plus.routing.GPXRouteParams.GPXRouteParamsBuilder
import net.osmand.plus.routing.RouteService
import net.osmand.shared.gpx.GpxFile
import net.osmand.shared.gpx.primitives.Track
import net.osmand.shared.gpx.primitives.TrkSegment
import net.osmand.shared.gpx.primitives.WptPt
import net.osmand.plus.plugins.nautical.network.SignalKRoute

class SignalKRouteProvider private constructor() {
    private val log = PlatformUtil.getLog(SignalKRouteProvider::class.java)

    companion object {
        @Volatile
        private var instance: SignalKRouteProvider? = null

        @JvmStatic
        fun getInstance(): SignalKRouteProvider {
            return instance ?: synchronized(this) {
                instance ?: SignalKRouteProvider().also { instance = it }
            }
        }
    }

    fun calculateRoute(params: RouteCalculationParams): RouteCalculationResult {
        val plugin = NauticalPlugin.getInstance() ?: return fallback(params, "Nautical plugin not active")
        val engine = NauticalPlugin.engine ?: return fallback(params, "SignalK engine not initialized")
        val restService = engine.getRestService() ?: return fallback(params, "SignalK server not reachable")

        return runBlocking {
            try {
                val start = params.start ?: return@runBlocking fallback(params, "Start position unknown")
                val end = params.end ?: return@runBlocking fallback(params, "End position unknown")

                val caps = plugin.capabilityManager?.capabilities?.value
                val pluginId = when {
                    caps?.hasWingaRouting == true -> "winga-weather-routing"
                    caps?.hasRouteIq == true -> "signalk-routeiq"
                    else -> null
                }

                if (pluginId != null) {
                    val body = mutableMapOf<String, Any>(
                        "start" to mapOf("lat" to start.latitude, "lon" to start.longitude),
                        "destination" to mapOf("lat" to end.latitude, "lon" to end.longitude)
                    )
                    
                    val response = restService.triggerPluginCalculation(pluginId, body)
                    if (response.isSuccessful) {
                        engine.refreshResources()
                        val routes = engine.fetchRoutesFromServer()
                        if (!routes.isNullOrEmpty()) {
                            val skRoute = routes.values.firstOrNull()
                            if (skRoute != null) {
                                val gpx = convertToGpx(skRoute)
                                params.gpxFile = gpx
                                val builder = GPXRouteParamsBuilder(gpx, params.ctx.settings)
                                builder.setCalculateOsmAndRoute(false)
                                params.gpxRoute = builder.build(params.ctx)
                                
                                // Recalculate using GPX logic
                                return@runBlocking params.ctx.getRoutingHelper().getProvider().calculateRouteImpl(params)
                            }
                        }
                    }
                }
                fallback(params, "No SignalK routing plugin found or calculation failed")
            } catch (e: Exception) {
                log.error("SignalK route calculation error", e)
                fallback(params, e.message ?: "Unknown error")
            }
        }
    }

    private fun convertToGpx(skRoute: SignalKRoute): GpxFile {
        val gpx = GpxFile("SignalK")
        val track = Track()
        val segment = TrkSegment()
        
        val properties = skRoute.feature.properties
        val windSpeeds = properties["windSpeeds"] as? List<*>
        val windAngles = properties["windAngles"] as? List<*>
        val waveHeights = properties["waveHeights"] as? List<*>
        
        skRoute.feature.geometry.coordinates.forEachIndexed { index, coord ->
            if (coord.size >= 2) {
                val pt = WptPt(coord[1], coord[0])
                windSpeeds?.getOrNull(index)?.let { pt.getExtensionsToWrite()["windSpeed"] = it.toString() }
                windAngles?.getOrNull(index)?.let { pt.getExtensionsToWrite()["windAngle"] = it.toString() }
                waveHeights?.getOrNull(index)?.let { pt.getExtensionsToWrite()["waveHeight"] = it.toString() }
                segment.points.add(pt)
            }
        }
        
        track.segments.add(segment)
        track.name = skRoute.name ?: "SignalK Route"
        gpx.tracks.add(track)
        return gpx
    }

    private fun fallback(params: RouteCalculationParams, errorMessage: String): RouteCalculationResult {
        log.warn("SignalK Routing Fallback: $errorMessage")
        // In a real implementation, we might want to return findStraightRoute or findVectorMapsRoute
        return RouteCalculationResult(errorMessage)
    }
}
