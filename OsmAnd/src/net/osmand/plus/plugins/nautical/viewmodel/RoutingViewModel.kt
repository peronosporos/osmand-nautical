package net.osmand.plus.plugins.nautical.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.osmand.plus.plugins.nautical.grib.parser.GribGridData
import net.osmand.plus.plugins.nautical.grib.parser.GribInterpolationEngine
import net.osmand.plus.plugins.nautical.routing.algorithm.IsochroneRoutingEngine
import net.osmand.plus.plugins.nautical.routing.model.OptimalRouteResult
import net.osmand.plus.plugins.nautical.routing.model.RoutingRequest
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex

class RoutingViewModel : ViewModel() {

    private val _optimalRoute = MutableStateFlow<OptimalRouteResult?>(null)
    val optimalRoute: StateFlow<OptimalRouteResult?> = _optimalRoute.asStateFlow()

    private val _routingStatus = MutableStateFlow<RoutingStatus>(RoutingStatus.Idle)
    val routingStatus: StateFlow<RoutingStatus> = _routingStatus.asStateFlow()

    fun calculateWeatherRoute(
        request: RoutingRequest,
        gridData: GribGridData,
        s57Index: S57SpatialIndex,
        safetyManager: net.osmand.plus.plugins.nautical.engine.NauticalSafetyManager,
    ) {
        val draft = safetyManager.getVesselDraft()
        _routingStatus.value = RoutingStatus.Starting
        viewModelScope.launch {
            try {
                val safetyChecker = net.osmand.plus.plugins.nautical.hazard.engine.SafetyCorridorChecker(
                    app = safetyManager.app,
                    indexManager = s57Index,
                    safetyManager = safetyManager
                )

                val gribEngine = GribInterpolationEngine(gridData)
                val routingEngine = IsochroneRoutingEngine(
                    gribEngine,
                    s57Index,
                    safetyChecker,
                    net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance()?.capabilityManager,
                    vesselDraft = draft
                )
                
                val liveState = net.osmand.plus.plugins.nautical.NauticalPlugin.engine?.getCurrentState()
                
                val result = routingEngine.calculateRoute(
                    request, 
                    liveState?.setTrue, 
                    liveState?.drift,
                    onProgress = { step, total ->
                        _routingStatus.value = RoutingStatus.Calculating(step, total)
                    }
                )
                if (result != null) {
                    _optimalRoute.value = result
                    _routingStatus.value = RoutingStatus.Done
                } else {
                    _routingStatus.value = RoutingStatus.Failed
                }
            } catch (e: Exception) {
                _routingStatus.value = RoutingStatus.Error(e.message)
            }
        }
    }
}

/**
 * Routing calculation status as a typed state; the raw display text is resolved
 * against localized resources at the presentation layer via [displayText].
 */
sealed class RoutingStatus {
    object Idle : RoutingStatus()
    object Starting : RoutingStatus()
    data class Calculating(val step: Int, val total: Int) : RoutingStatus()
    object Done : RoutingStatus()
    object Failed : RoutingStatus()
    data class Error(val message: String?) : RoutingStatus()
}

fun RoutingStatus.displayText(app: OsmandApplication): String = when (this) {
    is RoutingStatus.Idle -> app.getString(R.string.nautical_routing_status_idle)
    is RoutingStatus.Starting -> app.getString(R.string.nautical_routing_status_starting)
    is RoutingStatus.Calculating -> app.getString(R.string.nautical_routing_status_step_fmt, step, total)
    is RoutingStatus.Done -> app.getString(R.string.nautical_routing_status_done)
    is RoutingStatus.Failed -> app.getString(R.string.nautical_routing_status_failed)
    is RoutingStatus.Error -> app.getString(R.string.nautical_routing_status_error_fmt,
        message ?: app.getString(R.string.nautical_not_available))
}
