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
                val coords = msg.points[0]
                val distance = MapUtils.getDistance(location.latitude, location.longitude, 
                    coords.latitude, coords.longitude)
                if (distance > filters.maxDistanceKm * 1000) return@filter false
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
