package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.SignalKRestService

/**
 * Probes the Signal K server to identify available plugins and data paths.
 * Allows offloading expensive calculations (VMG, Leeway, Routing) to the server.
 */
class CapabilityManager(@Suppress("unused") private val app: OsmandApplication) {
    private val log = PlatformUtil.getLog(CapabilityManager::class.java)

    private val _capabilities = MutableStateFlow(ServerCapabilityMap())
    val capabilities = _capabilities.asStateFlow()

    data class ServerCapabilityMap(
        val hasVmg: Boolean = false,
        val hasLeeway: Boolean = false,
        val hasSetAndDrift: Boolean = false,
        val hasWingaRouting: Boolean = false,
        val hasRouteIq: Boolean = false,
        val hasNavicoSync: Boolean = false,
        val hasGpsHeadingFallback: Boolean = false,
        val hasCourseAutoAdvance: Boolean = false,
        val hasHistory: Boolean = false,
        val hasDerivedData: Boolean = false,
        val hasPolarPerformance: Boolean = false,
        val hasDeadMansSwitch: Boolean = false,
        val hasAisPrioritizer: Boolean = false,
        val hasWaterwayAlerts: Boolean = false,
        val hasForwardWatch: Boolean = false,
        val hasWindshift: Boolean = false,
        val hasRiggingLoad: Boolean = false,
        val hasAnchorAlarm: Boolean = false,
        val hasAutopilot: Boolean = false,
        val hasLogging: Boolean = false,
        val hasCloud: Boolean = false,
        val hasGeofencing: Boolean = false,
        val hasAlarms: Boolean = false,
        val hasElectrical: Boolean = false,
        val hasSignalKTides: Boolean = false,
        val hasCharts: Boolean = false,
        val hasGrib: Boolean = false,
        val hasRestrictedAreas: Boolean = false,
        val hasVesselsToAis: Boolean = false,
        val hasWindlassControl: Boolean = false,
        val hasChainCounter: Boolean = false,
        val hasFusionStereo: Boolean = false,
        val hasRainViewer: Boolean = false,
        // New Functional Capability Groups
        val hasAdvancedAutopilot: Boolean = false,
        val autopilotVendor: String? = null,
        val hasAdvancedWeather: Boolean = false,
        val hasEnergyManagement: Boolean = false,
        val hasAdvancedSafety: Boolean = false,
        val hasNavtex: Boolean = false,
        val hasDigitalSwitching: Boolean = false,
        val hasTacticalRacing: Boolean = false,
        val hasEnvironmentSensors: Boolean = false,
        val hasTankManagement: Boolean = false,
        val hasMediaControl: Boolean = false,
        val hasChecklists: Boolean = false,
        val hasAiBridge: Boolean = false,
    )

    fun probe(restService: SignalKRestService?) {
        if (restService == null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pluginsResponse = restService.getPlugins()
                val plugins = if (pluginsResponse.isSuccessful) pluginsResponse.body() ?: emptyList() else emptyList()
                val enabledPluginIds = plugins.asSequence().filter { it.enabled }.map { it.id }.toSet()

                val vesselSelfResponse = restService.getVesselSelf()
                val vesselData = if (vesselSelfResponse.isSuccessful) vesselSelfResponse.body() ?: emptyMap() else emptyMap()

                val hasVmg = hasPath(vesselData, "performance.velocityMadeGood")
                val hasLeeway = hasPath(vesselData, "navigation.leeway")
                val hasSetAndDrift = hasPath(vesselData, "navigation.drift") && hasPath(vesselData, "navigation.setTrue")
                
                val hasWinga = enabledPluginIds.contains("winga-weather-routing")
                val hasRouteIq = enabledPluginIds.contains("signalk-routeiq") || enabledPluginIds.contains("squid-sailing-signalk")
                val hasNavicoSync = enabledPluginIds.contains("signalk-navico-routes") || restService.getRoutes().isSuccessful
                val hasGpsHeadingFallback = enabledPluginIds.contains("signalk-gps-heading") || hasPath(vesselData, "navigation.headingTrue")
                val hasCourseAutoAdvance = enabledPluginIds.contains("signalk-course-autoadvance")

                val hasHistory = enabledPluginIds.contains("signalk-history") || enabledPluginIds.contains("signalk-influxdb-read-api")

                val hasDerivedData = enabledPluginIds.contains("signalk-derived-data") || hasPath(vesselData, "navigation.trueWindDirection")
                val hasPolarPerformance = enabledPluginIds.contains("signalk-polar-performance") || hasPath(vesselData, "performance.polarSpeedRatio")
                
                val hasDeadMansSwitch = enabledPluginIds.contains("signalk-dead-mans-switch") || hasPath(vesselData, "notifications.safety.watchdog")
                val hasAisPrioritizer = enabledPluginIds.contains("signalk-ais-target-prioritizer")
                val hasWaterwayAlerts = enabledPluginIds.contains("vaarweginformatie-blocked") || enabledPluginIds.contains("signalk-avurnav")
                val hasForwardWatch = enabledPluginIds.contains("signalk-forward-watch")
                val hasWindshift = enabledPluginIds.contains("@jwallinder/windshift")
                val hasRiggingLoad = enabledPluginIds.contains("signalk-cyclops-gateway") || hasPath(vesselData, "rigging.loads")

                val hasAnchorAlarm = enabledPluginIds.contains("signalk-anchor-alarm") || hasPath(vesselData, "steering.anchor")
                val hasAutopilot = enabledPluginIds.contains("signalk-autopilot") || enabledPluginIds.contains("pypilot") || hasPath(vesselData, "steering.autopilot")
                val hasLogging = hasHistory || enabledPluginIds.contains("signalk-data-logger")
                val hasCloud = enabledPluginIds.contains("signalk-cloud")
                val hasGeofencing = enabledPluginIds.contains("signalk-boundaries")
                val hasAlarms = enabledPluginIds.contains("signalk-alarm-handler") || hasPath(vesselData, "notifications")
                val hasElectrical = enabledPluginIds.contains("signalk-victron") || enabledPluginIds.contains("signalk-venus-plugin") || hasPath(vesselData, "electrical")
                val hasSignalKTides = enabledPluginIds.contains("signalk-tides") || hasPath(vesselData, "environment.tide")
                
                val hasCharts = enabledPluginIds.contains("signalk-charts-plugin") || enabledPluginIds.contains("signalk-charts-provider-simple")
                val hasGrib = enabledPluginIds.contains("signalk-grib-weather-provider")
                val hasRestrictedAreas = enabledPluginIds.contains("signalk-restricted-areas")
                val hasVesselsToAis = enabledPluginIds.contains("signalk-vessels-to-ais")
                
                val hasWindlassControl = enabledPluginIds.contains("signalk-relay-windlass") || hasPath(vesselData, "electrical.switches.windlass")
                val hasChainCounter = enabledPluginIds.contains("signalk-chain-plugin") || hasPath(vesselData, "navigation.anchor.rodeDeployed")
                val hasFusionStereo = enabledPluginIds.contains("signalk-fusion-stereo") || hasPath(vesselData, "entertainment.device.fusion")
                val hasRainViewer = enabledPluginIds.contains("signalk-rainviewer-charts")
                val hasChecklists = enabledPluginIds.contains("signalk-checklists") || restService.getChecklists().isSuccessful
                val hasAiBridge = enabledPluginIds.contains("signalk-ai-bridge")

                // Functional Group Detection
                val apPlugins = setOf("signalk-autopilot", "pypilot", "signalk-autopilot-furuno", "signalk-autopilot-garmin", "signalk-ac42-autopilot", "signalk-autopilot_route")
                val activeAp = enabledPluginIds.firstOrNull { apPlugins.contains(it) }
                val weatherPlugins = setOf("signalk-grib-weather-provider", "signalk-open-meteo-provider", "signalk-windy-apiv2", "signalk-windy-plugin", "openweather-signalk", "signalk-noaa-weather", "signalk-smhi-weather-provider", "signalk-viva-weather-provider", "signalk-net-weather-finland")
                val energyPlugins = setOf("signalk-victron", "signalk-venus-plugin", "signalk-bms-ble", "signalk-daly-bms", "signalk-rec-bms", "jbd-overkill-bms-plugin", "signalk-electrodacus", "sk-battery-supervisor", "signalk-bluetti-plugin", "pico2signalk")
                val safetyPlugins = setOf("signalk-ais-target-prioritizer", "collision-detector", "signalk-ais-distress", "signalk-dsc", "signalk-ais-sart-plugin", "hoekens-anchor-alarm", "y2k-anchor-alarm")
                val switchingPlugins = setOf("signalk-shelly", "signalk-shelly2", "signalk-n2k-switching", "signalk-empirbusnxt-plugin", "signalk-n2k-virtual-switch")
                val racingPlugins = setOf("signalk-polar-performance", "signalk-polar-performance-plugin", "signalk-racer", "signalk-racing-calculator", "tack-now", "signalk-garmin-race-timer-plugin", "@jwallinder/windshift")
                val sensorPlugins = setOf("signalk-airmar-plugin", "signalk-ecowitt", "signalk-tempest", "signalk-weatherflow", "signalk-telltale-plugin")
                val tankPlugins = setOf("signalk-spectra-plugin", "signalk-brineomatic-plugin", "signalk-chain-plugin", "fuel-usage-calculator")
                val mediaPlugins = setOf("signalk-fusion-stereo", "signalk-onvif-camera", "sk-video")

                val newMap = ServerCapabilityMap(
                    hasVmg = hasVmg,
                    hasLeeway = hasLeeway,
                    hasSetAndDrift = hasSetAndDrift,
                    hasWingaRouting = hasWinga,
                    hasRouteIq = hasRouteIq,
                    hasNavicoSync = hasNavicoSync,
                    hasGpsHeadingFallback = hasGpsHeadingFallback,
                    hasCourseAutoAdvance = hasCourseAutoAdvance,
                    hasHistory = hasHistory,
                    hasDerivedData = hasDerivedData,
                    hasPolarPerformance = hasPolarPerformance,
                    hasDeadMansSwitch = hasDeadMansSwitch,
                    hasAisPrioritizer = hasAisPrioritizer,
                    hasWaterwayAlerts = hasWaterwayAlerts,
                    hasForwardWatch = hasForwardWatch,
                    hasWindshift = hasWindshift,
                    hasRiggingLoad = hasRiggingLoad,
                    hasAnchorAlarm = hasAnchorAlarm,
                    hasAutopilot = hasAutopilot,
                    hasLogging = hasLogging,
                    hasCloud = hasCloud,
                    hasGeofencing = hasGeofencing,
                    hasAlarms = hasAlarms,
                    hasElectrical = hasElectrical,
                    hasSignalKTides = hasSignalKTides,
                    hasCharts = hasCharts,
                    hasGrib = hasGrib,
                    hasRestrictedAreas = hasRestrictedAreas,
                    hasVesselsToAis = hasVesselsToAis,
                    hasWindlassControl = hasWindlassControl,
                    hasChainCounter = hasChainCounter,
                    hasFusionStereo = hasFusionStereo,
                    hasRainViewer = hasRainViewer,
                    
                    hasAdvancedAutopilot = enabledPluginIds.any { apPlugins.contains(it) },
                    autopilotVendor = activeAp,
                    hasAdvancedWeather = enabledPluginIds.any { weatherPlugins.contains(it) },
                    hasEnergyManagement = enabledPluginIds.any { energyPlugins.contains(it) },
                    hasAdvancedSafety = enabledPluginIds.any { safetyPlugins.contains(it) },
                    hasNavtex = enabledPluginIds.contains("signalk-navtex-plugin"),
                    hasDigitalSwitching = enabledPluginIds.any { switchingPlugins.contains(it) },
                    hasTacticalRacing = enabledPluginIds.any { racingPlugins.contains(it) },
                    hasEnvironmentSensors = enabledPluginIds.any { sensorPlugins.contains(it) },
                    hasTankManagement = enabledPluginIds.any { tankPlugins.contains(it) } || hasPath(vesselData, "tanks"),
                    hasMediaControl = enabledPluginIds.any { mediaPlugins.contains(it) },
                    hasChecklists = hasChecklists,
                    hasAiBridge = hasAiBridge
                )


                _capabilities.value = newMap
                log.info("Nautical: Server capabilities updated: $newMap")

                // Task: Orchestrator - Sync active plugins and initial resources
                NauticalPlugin.engine?.dataBroker?.updateState { it.copy(activePlugins = enabledPluginIds) }
                if (hasWinga || hasRouteIq) {
                    val regions = restService.getRegions()
                    if (regions.isSuccessful) {
                        val isochrones = regions.body()?.values?.filter { 
                            it.feature.properties["type"] == "isochrone" || it.feature.properties["source"] == "winga"
                        } ?: emptyList()
                        NauticalPlugin.engine?.dataBroker?.updateState { it.copy(isochrones = isochrones, lastIsochroneTime = System.currentTimeMillis()) }
                    }
                }
                if (hasPolarPerformance) {
                    val polars = restService.getPolars()
                    if (polars.isSuccessful) {
                        val activePolar = polars.body()?.values?.firstOrNull() // For MVP: take first
                        NauticalPlugin.engine?.dataBroker?.updateState { it.copy(polarProfile = activePolar) }
                    }
                }
            } catch (e: Exception) {
                log.error("Nautical: Failed to probe server capabilities: ${e.message}")
            }
        }
    }

    private fun hasPath(tree: Map<String, Any>, path: String): Boolean {
        val parts = path.split(".")
        var current: Any? = tree
        for (part in parts) {
            current = (current as? Map<*, *>)?.get(part)
            if (current == null) return false
        }
        // Check if value exists at the leaf
        return (current as? Map<*, *>)?.containsKey("value") == true
    }
}
