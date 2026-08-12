package net.osmand.plus.plugins.nautical.hazard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.osmand.Location
import net.osmand.plus.OsmAndLocationProvider
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.hazard.data.NavtexRepository
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexSubject
import net.osmand.util.MapUtils

data class NavtexFilters(
    val onlyUrgent: Boolean = false,
    val subject: NavtexSubject? = null,
    val maxDistanceKm: Double? = null,
)

data class NavtexUiState(
    val messages: List<NavtexMessage> = emptyList(),
    val filters: NavtexFilters = NavtexFilters()
)

class NavtexViewModel(
    private val app: OsmandApplication,
    private val repository: NavtexRepository = NavtexRepository(app)
) : ViewModel() {

    private val settings = app.settings
    private val _filters = MutableStateFlow(
        NavtexFilters(
            onlyUrgent = settings.NAVTEX_ONLY_URGENT.get(),
            subject = settings.NAVTEX_SUBJECT_FILTER.get().let { code ->
                if (code.isEmpty()) null else NavtexSubject.fromCode(code[0])
            },
            maxDistanceKm = settings.NAVTEX_MAX_DISTANCE.get().toDouble().takeIf { it > 0 }
        )
    )
    
    private val locationFlow: Flow<Location?> = callbackFlow {
        val listener = OsmAndLocationProvider.OsmAndLocationListener { location -> trySend(location) }
        app.locationProvider.addLocationListener(listener)
        // Send initial location
        trySend(app.locationProvider.lastKnownLocation)
        awaitClose {
            app.locationProvider.removeLocationListener(listener)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), app.locationProvider.lastKnownLocation)

    val uiState: StateFlow<NavtexUiState> = combine(
        repository.messages,
        _filters,
        locationFlow
    ) { messages, filters, location ->
        val filtered = messages.filter { msg ->
            // Subject 'A' (NAVTEX_WARNING) and 'D' (SEARCH_AND_RESCUE) always bypass filters per safety audit
            if ((msg.subject == NavtexSubject.NAVTEX_WARNING) || (msg.subject == NavtexSubject.SEARCH_AND_RESCUE)) {
                return@filter true
            }

            if (filters.onlyUrgent && !msg.isUrgent) return@filter false
            if (filters.subject != null && msg.subject != filters.subject) return@filter false
            if (filters.maxDistanceKm != null && location != null && msg.points.isNotEmpty()) {
                val distLimit = filters.maxDistanceKm * 1000
                val minDistance = msg.points.minOf { p ->
                    MapUtils.getDistance(location.latitude, location.longitude, p.latitude, p.longitude)
                }
                
                if (minDistance > distLimit) {
                    // Also check if location is inside polygon if it is one
                    if (msg.isPolygon && isPointInPolygon(location.latitude, location.longitude, msg.points)) {
                        return@filter true
                    }
                    return@filter false
                }
            }
            true
        }
        NavtexUiState(filtered, filters)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NavtexUiState())

    init {
        viewModelScope.launch {
            repository.refreshMessages()
        }
    }

    private fun isPointInPolygon(lat: Double, lon: Double, polygon: List<net.osmand.data.LatLon>): Boolean {
        var intersectCount = 0
        val x = lon
        val y = lat
        
        for (j in polygon.indices) {
            val i = if (j > 0) j - 1 else polygon.size - 1
            var viLon = polygon[i].longitude
            var vjLon = polygon[j].longitude
            val viLat = polygon[i].latitude
            val vjLat = polygon[j].latitude

            // Normalize for anti-meridian
            if (Math.abs(viLon - vjLon) > 180) {
                if (viLon < 0) viLon += 360
                if (vjLon < 0) vjLon += 360
            }
            
            var testX = x
            if (Math.abs(x - viLon) > 180 && Math.abs(x - vjLon) > 180) {
                if (x < 0) testX += 360
            }

            if (((viLat > y) != (vjLat > y)) &&
                (testX < (vjLon - viLon) * (y - viLat) / (vjLat - viLat) + viLon)
            ) {
                intersectCount++
            }
        }
        return intersectCount % 2 != 0
    }

    fun updateFilters(filters: NavtexFilters) {
        settings.NAVTEX_ONLY_URGENT.set(filters.onlyUrgent)
        settings.NAVTEX_SUBJECT_FILTER.set(filters.subject?.code?.toString() ?: "")
        settings.NAVTEX_MAX_DISTANCE.set(filters.maxDistanceKm?.toFloat() ?: 0f)
        _filters.value = filters
    }

    fun setUrgentOnly(urgent: Boolean) {
        settings.NAVTEX_ONLY_URGENT.set(urgent)
        _filters.update { it.copy(onlyUrgent = urgent) }
    }

    fun setSubjectFilter(subject: NavtexSubject?) {
        settings.NAVTEX_SUBJECT_FILTER.set(subject?.code?.toString() ?: "")
        _filters.update { it.copy(subject = subject) }
    }

    fun setMaxDistance(distanceKm: Double?) {
        settings.NAVTEX_MAX_DISTANCE.set(distanceKm?.toFloat() ?: 0f)
        _filters.update { it.copy(maxDistanceKm = distanceKm) }
    }
    
    fun clear() {
        onCleared()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refreshMessages()
        }
    }

    suspend fun upsertMessage(message: NavtexMessage) {
        repository.upsertMessage(message)
    }
}
