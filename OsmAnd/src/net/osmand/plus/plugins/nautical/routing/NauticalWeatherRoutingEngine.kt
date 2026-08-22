package net.osmand.plus.plugins.nautical.routing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.engine.NauticalSafetyManager
import net.osmand.plus.plugins.nautical.grib.parser.GribGridData
import net.osmand.plus.plugins.nautical.grib.parser.GribHeader
import net.osmand.plus.plugins.nautical.grib.parser.TimeStepGrid
import net.osmand.plus.plugins.nautical.hazard.engine.SafetyCorridorChecker
import net.osmand.plus.plugins.nautical.map.controller.SailingMapLayerController
import net.osmand.plus.plugins.nautical.network.PolarProfile
import net.osmand.plus.plugins.nautical.routing.algorithm.IsochroneRoutingEngine
import net.osmand.plus.plugins.nautical.routing.model.OptimalRouteResult
import net.osmand.plus.plugins.nautical.routing.model.RoutingRequest
import net.osmand.plus.plugins.nautical.routing.model.Waypoint
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex
import net.osmand.plus.plugins.nautical.viewmodel.RoutingViewModel
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.fragments.SettingsScreenType
import kotlin.math.*

class NauticalWeatherRoutingEngine(private val app: OsmandApplication) {

    private val log = PlatformUtil.getLog(NauticalWeatherRoutingEngine::class.java)

    fun calculateAndRenderWeatherRoute(
        destLat: Double,
        destLon: Double,
        mapActivity: MapActivity,
        routingViewModel: RoutingViewModel?,
        safetyManager: NauticalSafetyManager?,
        s57SpatialIndex: S57SpatialIndex?,
        layerController: SailingMapLayerController?,
        scope: CoroutineScope
    ) {
        val lastLoc = app.locationProvider.lastKnownLocation
        if (lastLoc == null) {
            app.showToastMessage(R.string.nautical_error_no_gps)
            return
        }

        val gribRepo = SailingDependencyContainer.gribRepository
        val memoryGrid = gribRepo?.gridData
        val liveState = NauticalPlugin.engine?.getCurrentState()

        val resolvedGrid: GribGridData? = if (memoryGrid != null && memoryGrid.timeSteps.isNotEmpty()) {
            memoryGrid
        } else {
            val tws = liveState?.windSpeedTrue ?: liveState?.windSpeedApparent
            val twd = liveState?.windDirectionTrue ?: liveState?.windDirectionApparent ?: liveState?.headingTrue

            if (tws == null || tws < 0.2) {
                app.showToastMessage(R.string.nautical_routing_no_wind_data)
                return
            }

            app.showToastMessage(R.string.nautical_routing_using_live_wind)

            val u = (-tws * sin(twd ?: 0.0)).toFloat()
            val v = (-tws * cos(twd ?: 0.0)).toFloat()
            val minLat = minOf(lastLoc.latitude, destLat) - 1.5
            val maxLat = maxOf(lastLoc.latitude, destLat) + 1.5
            val minLon = minOf(lastLoc.longitude, destLon) - 1.5
            val maxLon = maxOf(lastLoc.longitude, destLon) + 1.5
            val latSteps = 15
            val lonSteps = 15
            val totalPoints = latSteps * lonSteps
            val uGrid = FloatArray(totalPoints) { u }
            val vGrid = FloatArray(totalPoints) { v }
            val timeStep = TimeStepGrid(timestamp = System.currentTimeMillis(), uGrid = uGrid, vGrid = vGrid)
            val header = GribHeader(minLat, maxLat, minLon, maxLon, latSteps, lonSteps)
            GribGridData(header, listOf(timeStep))
        }

        if (resolvedGrid == null) {
            app.showToastMessage(R.string.nautical_routing_no_wind_data)
            return
        }

        app.showToastMessage(R.string.nautical_calculating_weather_route)

        val polarProfile = SailingDependencyContainer.performanceRepository?.activePolarProfile?.value
            ?: PolarProfile(
                name = "Default Cruiser",
                description = "Standard Polar Profile",
                tws = listOf(6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 20.0),
                twa = listOf(30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 110.0, 120.0, 135.0, 150.0, 180.0),
                speeds = listOf(
                    listOf(3.2, 4.4, 5.3, 6.0, 6.4, 6.7, 6.9),
                    listOf(4.6, 5.8, 6.6, 7.1, 7.4, 7.6, 7.8),
                    listOf(5.2, 6.4, 7.1, 7.5, 7.8, 8.0, 8.2),
                    listOf(5.5, 6.7, 7.4, 7.8, 8.1, 8.3, 8.5),
                    listOf(5.7, 6.9, 7.6, 8.0, 8.3, 8.5, 8.8),
                    listOf(5.8, 7.0, 7.7, 8.1, 8.4, 8.7, 9.0),
                    listOf(5.8, 7.0, 7.7, 8.2, 8.5, 8.8, 9.2),
                    listOf(5.5, 6.8, 7.5, 8.1, 8.5, 8.9, 9.4),
                    listOf(5.1, 6.4, 7.2, 7.8, 8.3, 8.8, 9.5),
                    listOf(4.3, 5.6, 6.5, 7.2, 7.8, 8.4, 9.2),
                    listOf(3.5, 4.6, 5.5, 6.3, 7.0, 7.7, 8.6),
                    listOf(2.8, 3.8, 4.6, 5.3, 6.0, 6.7, 7.6)
                )
            )

        val request = RoutingRequest(
            start = Waypoint(lastLoc.latitude, lastLoc.longitude),
            destination = Waypoint(destLat, destLon),
            departureTime = System.currentTimeMillis(),
            polarProfile = polarProfile
        )

        val index = s57SpatialIndex ?: S57SpatialIndex(app)
        val sm = safetyManager ?: NauticalSafetyManager.getInstance(app)

        if (routingViewModel != null) {
            routingViewModel.calculateWeatherRoute(request, resolvedGrid, index, sm)

            routingViewModel.routingStatus.onEach { status ->
                app.runInUIThread {
                    if (status != "Idle" && !status.startsWith("Calculating:")) {
                        app.showToastMessage(status)
                    }
                }
            }.launchIn(scope)

            routingViewModel.optimalRoute.onEach { result ->
                if (result != null) {
                    app.runInUIThread {
                        layerController?.setWeatherRoute(result)
                        app.osmandMap?.refreshMap()
                        BaseSettingsFragment.showInstance(mapActivity, SettingsScreenType.NAUTICAL_PASSAGE_PLAN)
                    }
                }
            }.launchIn(scope)
        } else {
            scope.launch(Dispatchers.Default) {
                try {
                    val safetyChecker = SafetyCorridorChecker(index, sm)
                    val gribEngine = net.osmand.plus.plugins.nautical.grib.parser.GribInterpolationEngine(resolvedGrid)
                    val engine = IsochroneRoutingEngine(
                        gribEngine = gribEngine,
                        s57Index = index,
                        safetyChecker = safetyChecker,
                        capabilityManager = NauticalPlugin.getInstance()?.capabilityManager,
                        vesselDraft = sm.getVesselDraft()
                    )
                    val result = engine.calculateRoute(request, liveState?.setTrue, liveState?.drift)
                    withContext(Dispatchers.Main) {
                        if (result != null) {
                            layerController?.setWeatherRoute(result)
                            app.osmandMap?.refreshMap()
                            BaseSettingsFragment.showInstance(mapActivity, SettingsScreenType.NAUTICAL_PASSAGE_PLAN)
                        } else {
                            app.showToastMessage(R.string.nautical_routing_failed)
                        }
                    }
                } catch (e: Exception) {
                    log.error("Weather routing calculation failed", e)
                }
            }
        }
    }

    companion object {
        fun countTacksAndGybes(legs: List<net.osmand.plus.plugins.nautical.routing.model.PassagePlanLeg>, trueWindDirDeg: Double): Pair<Int, Int> {
            var tacks = 0
            var gybes = 0
            for (i in 0 until legs.size - 1) {
                val c1 = legs[i].courseToSteerDeg
                val c2 = legs[i + 1].courseToSteerDeg
                val a1 = ((c1 - trueWindDirDeg) % 360.0 + 360.0) % 360.0
                val a2 = ((c2 - trueWindDirDeg) % 360.0 + 360.0) % 360.0

                val port1 = a1 > 180.0
                val port2 = a2 > 180.0
                if (port1 != port2) {
                    val mid = (a1 + a2) / 2.0
                    if (mid < 90.0 || mid > 270.0) {
                        tacks++
                    } else {
                        gybes++
                    }
                }
            }
            return Pair(tacks, gybes)
        }
    }
}
