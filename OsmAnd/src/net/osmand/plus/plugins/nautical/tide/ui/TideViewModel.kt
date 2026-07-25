package net.osmand.plus.plugins.nautical.tide.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.tide.model.TidePrediction
import net.osmand.plus.plugins.nautical.tide.model.TideStation

class TideViewModel : ViewModel() {

    private val parser = SailingDependencyContainer.tideParser
    private val engine = SailingDependencyContainer.tideEngine

    private val _selectedStation = MutableStateFlow<TideStation?>(null)
    val selectedStation: StateFlow<TideStation?> = _selectedStation

    private val _predictions = MutableStateFlow<List<TidePrediction>>(emptyList())
    val predictions: StateFlow<List<TidePrediction>> = _predictions

    private val _nearbyStations = MutableStateFlow<List<TideStation>>(emptyList())
    val nearbyStations: StateFlow<List<TideStation>> = _nearbyStations

    fun selectStation(station: TideStation) {
        _selectedStation.value = station
        calculatePredictions(station)
    }

    fun findNearbyStations(lat: Double, lon: Double) {
        viewModelScope.launch {
            // In a real implementation, we would query the parser for all stations in range
            // For now, we use the nearest one as a primary
            val nearest = parser.findNearestStation(lat, lon)
            _nearbyStations.value = nearest?.let { listOf(it) } ?: emptyList()
            if (_selectedStation.value == null && nearest != null) {
                selectStation(nearest)
            }
        }
    }

    private fun calculatePredictions(station: TideStation) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val results = engine.predictTides(station, now)
            _predictions.value = results
        }
    }

    fun getInstantHeight(timestamp: Long): Double? {
        val station = _selectedStation.value ?: return null
        return engine.calculateHeight(station, timestamp)
    }
}
