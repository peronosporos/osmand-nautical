package net.osmand.plus.plugins.nautical.tide.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.engine.TideState
import net.osmand.plus.plugins.nautical.tide.model.TidePrediction
import net.osmand.plus.plugins.nautical.tide.model.TideStation
import java.text.SimpleDateFormat
import java.util.*

class TideViewModel : ViewModel() {

    private val parser = SailingDependencyContainer.tideParser
    private val engine = SailingDependencyContainer.tideEngine
    private val tideManager = NauticalPlugin.getInstance()?.tideManager

    private val _selectedStation = MutableStateFlow<TideStation?>(null)
    val selectedStation: StateFlow<TideStation?> = _selectedStation

    private val _predictions = MutableStateFlow<List<TidePrediction>>(emptyList())
    val predictions: StateFlow<List<TidePrediction>> = _predictions

    private val _nearbyStations = MutableStateFlow<List<TideStation>>(emptyList())
    /**
     * List of tide stations found near the last queried location.
     * Can be used to populate a station selection list in the UI.
     */
    @Suppress("unused")
    val nearbyStations: StateFlow<List<TideStation>> = _nearbyStations

    private val skDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val _vesselTide = MutableStateFlow<TideState?>(null)
    val vesselTide: StateFlow<TideState?> = _vesselTide

    init {
        viewModelScope.launch {
            tideManager?.vesselTide?.collect {
                _vesselTide.value = it
            }
        }
    }

    fun selectStation(station: TideStation) {
        _selectedStation.value = station
        calculatePredictions(station)
    }

    fun selectSignalKStation(stationId: String) {
        viewModelScope.launch {
            val skStations = tideManager?.stations?.value ?: return@launch
            val skStation = skStations[stationId] ?: return@launch
            
            // Map Signal K station to internal TideStation model for compatibility
            val internalStation = TideStation(
                id = skStation.id,
                name = skStation.name,
                latitude = skStation.position.coordinates[1],
                longitude = skStation.position.coordinates[0],
                timezoneOffset = 0, // Signal K tides are usually UTC
                constituents = emptyList(), // We'll use server predictions
            )
            _selectedStation.value = internalStation
            
            val timeline = tideManager.getTimeline(stationId)
            val extremes = tideManager.getExtremes(stationId)
            
            if (timeline.isNotEmpty() || extremes.isNotEmpty()) {
                val isCurrent = (skStation.name.contains("Current", ignoreCase = true)) || 
                                (skStation.properties?.get("type") == "current")
                
                val mappedPredictions = timeline.mapIndexed { i, skp ->
                    val h = skp.height
                    var isHigh: Boolean? = null
                    
                    if (!isCurrent && i > 0 && i < timeline.size - 1) {
                        val prevH = timeline[i - 1].height
                        val nextH = timeline[i + 1].height
                        if (h > prevH && h > nextH) isHigh = true
                        else if (h < prevH && h < nextH) isHigh = false
                    }
                    
                    TidePrediction(
                        timestamp = parseSkTime(skp.timestamp),
                        heightMeters = if (isCurrent) 0.0 else h,
                        velocity = if (isCurrent) h else null,
                        isHighTide = isHigh
                    )
                }

                // Enrich with actual extremes if available
                val finalPredictions = mappedPredictions.toMutableList()
                extremes.forEach { ext ->
                    val ts = parseSkTime(ext.timestamp)
                    val isHigh = ext.type.lowercase(Locale.US).contains("high")
                    // Check if we already have a prediction near this time
                    val existingIdx = finalPredictions.indexOfFirst { Math.abs(it.timestamp - ts) < 600000 }
                    if (existingIdx != -1) {
                        finalPredictions[existingIdx] = finalPredictions[existingIdx].copy(isHighTide = isHigh)
                    } else {
                        finalPredictions.add(TidePrediction(
                            timestamp = ts,
                            heightMeters = if (isCurrent) 0.0 else ext.height,
                            velocity = if (isCurrent) ext.height else null,
                            isHighTide = isHigh
                        ))
                    }
                }
                
                _predictions.value = finalPredictions.sortedBy { it.timestamp }
            } else {
                // Fallback to local if server fails
                val lat = skStation.position.coordinates[1]
                val lon = skStation.position.coordinates[0]
                parser?.findNearestStation(lat, lon)?.let { localStation ->
                    _selectedStation.value = localStation
                    calculatePredictions(localStation)
                }
            }
        }
    }

    private fun parseSkTime(timeStr: String): Long {
        return try {
            skDateFormat.parse(timeStr)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    fun findNearbyStations(lat: Double, lon: Double) {
        viewModelScope.launch {
            val localNearest = parser?.findNearestStation(lat, lon)
            val skNearest = tideManager?.findNearestStation(lat, lon)
            
            val combined = mutableListOf<TideStation>()
            
            localNearest?.let { combined.add(it) }
            
            skNearest?.let { sks ->
                // Check if this station is already added (by distance/name)
                val existing = combined.find { (it.latitude == sks.position.coordinates[1]) && (it.longitude == sks.position.coordinates[0]) }
                if (existing == null) {
                    combined.add(
                        TideStation(
                            id = sks.id,
                            name = sks.name,
                            latitude = sks.position.coordinates[1],
                            longitude = sks.position.coordinates[0],
                            timezoneOffset = 0,
                            constituents = emptyList(),
                        ),
                    )
                }
            }
            
            _nearbyStations.value = combined
            
            if (_selectedStation.value == null) {
                // Use vesselTide if available to find current station
                _vesselTide.value?.stationName?.let { name ->
                    combined.find { it.name == name }?.let {
                        selectStation(it)
                        return@launch
                    }
                }
                // Prefer Signal K if both are nearby
                skNearest?.let { selectSignalKStation(it.id) } ?: localNearest?.let { selectStation(it) }
            }
        }
    }

    private fun calculatePredictions(station: TideStation) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val results = engine?.predictTides(station, now) ?: emptyList()
            _predictions.value = results
        }
    }

    /**
     * Calculates the tide height at a specific point in time for the selected station.
     * Useful for real-time map overlays or depth adjustments.
     */
    @Suppress("unused")
    fun getInstantHeight(timestamp: Long): Double? {
        val station = _selectedStation.value ?: return null
        return engine?.calculateHeight(station, timestamp)
    }
}
