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
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex

class RoutingViewModel : ViewModel() {

    private val _optimalRoute = MutableStateFlow<OptimalRouteResult?>(null)
    val optimalRoute: StateFlow<OptimalRouteResult?> = _optimalRoute.asStateFlow()

    private val _routingStatus = MutableStateFlow("Idle")
    val routingStatus: StateFlow<String> = _routingStatus.asStateFlow()

    fun calculateWeatherRoute(request: RoutingRequest, gridData: GribGridData, s57Index: S57SpatialIndex) {
        _routingStatus.value = "Calculating Weather Route..."
        viewModelScope.launch {
            try {
                val gribEngine = GribInterpolationEngine(gridData)
                val routingEngine = IsochroneRoutingEngine(gribEngine, s57Index)
                val result = routingEngine.calculateRoute(request)
                if (result != null) {
                    _optimalRoute.value = result
                    _routingStatus.value = "Optimal Route Calculated"
                } else {
                    _routingStatus.value = "Routing Failed / Land Collision"
                }
            } catch (e: Exception) {
                _routingStatus.value = "Error: ${e.message}"
            }
        }
    }
}
