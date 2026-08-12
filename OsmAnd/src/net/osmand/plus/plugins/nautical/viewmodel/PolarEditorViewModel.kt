package net.osmand.plus.plugins.nautical.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.PolarProfile
import net.osmand.plus.plugins.nautical.network.SignalKRestService

class PolarEditorViewModel : ViewModel() {

    private var fullProfile: PolarProfile? = null

    private val _selectedTws = MutableStateFlow(8.0)
    val selectedTws: StateFlow<Double> = _selectedTws.asStateFlow()

    private val _smoothingIntensity = MutableStateFlow(0.5f)
    val smoothingIntensity: StateFlow<Float> = _smoothingIntensity.asStateFlow()

    private val _rawPoints = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val rawPoints: StateFlow<List<Pair<Double, Double>>> = _rawPoints.asStateFlow()

    private val _smoothedPoints = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val smoothedPoints: StateFlow<List<Pair<Double, Double>>> = _smoothedPoints.asStateFlow()

    // TASK-015: Expose points in Knots for UI components
    val uiPoints: kotlinx.coroutines.flow.Flow<List<Pair<Double, Double>>> = _smoothedPoints.map { list ->
        list.map { Pair(it.first, it.second * MS_TO_KNOTS) }
    }

    val uiRawPoints: kotlinx.coroutines.flow.Flow<List<Pair<Double, Double>>> = _rawPoints.map { list ->
        list.map { Pair(it.first, it.second * MS_TO_KNOTS) }
    }

    init {
        viewModelScope.launch {
            net.osmand.plus.plugins.nautical.di.SailingDependencyContainer.performanceRepository?.activePolarProfile?.collectLatest { profile ->
                if (profile != null) {
                    fullProfile = profile
                    loadTwsCurve(_selectedTws.value)
                }
            }
        }
    }

    private fun loadTwsCurve(tws: Double) {
        val profile = fullProfile ?: return
        val twsList = profile.tws ?: return
        val twaList = profile.twa ?: return
        val speeds = profile.speeds ?: return

        val twsIndex = twsList.indexOfFirst { kotlin.math.abs(it - tws) < 0.1 }
        if (twsIndex != -1 && twsIndex < speeds.size) {
            val curveSpeeds = speeds[twsIndex]
            val points = twaList.zip(curveSpeeds).map { Pair(it.first, it.second) }
            _rawPoints.value = points
            recalculateSmoothing()
        }
    }

    // TASK-015: Unit conversion for Editor UI (Display in Knots, store in m/s)
    // TASK-015: Unit conversion for Editor UI (Display in Knots, store in m/s)
    private val MS_TO_KNOTS = 1.94384449
    private val KNOTS_TO_MS = 1.0 / MS_TO_KNOTS

    fun setSelectedTws(tws: Double) {
        _selectedTws.value = tws
        loadTwsCurve(tws)
    }

    fun setSmoothingIntensity(intensity: Float) {
        _smoothingIntensity.value = intensity
        recalculateSmoothing()
    }

    fun updatePoint(index: Int, newTwa: Double, newSpeedKnots: Double) {
        val list = _rawPoints.value.toMutableList()
        if (index in list.indices) {
            list[index] = Pair(newTwa, newSpeedKnots * KNOTS_TO_MS)
            _rawPoints.value = list
            recalculateSmoothing()
        }
    }

    private fun recalculateSmoothing() {
        val raw = _rawPoints.value.sortedBy { it.first }
        val intensity = _smoothingIntensity.value
        if (intensity == 0f || raw.size < 3) {
            _smoothedPoints.value = raw
            return
        }

        // TASK-007: Gaussian-weighted Smoothing for physically realistic curves
        val smoothed = mutableListOf<Pair<Double, Double>>()
        val sigma = 5.0 + (intensity * 20.0) // Adaptive sigma based on intensity (degrees)

        for (i in raw.indices) {
            val targetTwa = raw[i].first
            var weightedSum = 0.0
            var weightTotal = 0.0

            for (pt in raw) {
                // Handle periodic boundary or just distance
                val dist = kotlin.math.abs(pt.first - targetTwa)
                val weight = kotlin.math.exp(-(dist * dist) / (2.0 * sigma * sigma))
                weightedSum += pt.second * weight
                weightTotal += weight
            }

            val smoothedSpeed = if (weightTotal > 0) weightedSum / weightTotal else raw[i].second
            smoothed.add(Pair(targetTwa, smoothedSpeed))
        }
        _smoothedPoints.value = smoothed
    }

    fun savePolarsToServer(serverBaseUrl: String, polarId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val currentProfile = fullProfile ?: run { onResult(false); return@launch }
                var twsList = currentProfile.tws?.toMutableList() ?: mutableListOf(_selectedTws.value)
                val twaList = _smoothedPoints.value.map { it.first }
                var allSpeeds = currentProfile.speeds?.map { it.toMutableList() }?.toMutableList() ?: mutableListOf()

                val twsIndex = twsList.indexOfFirst { kotlin.math.abs(it - _selectedTws.value) < 0.1 }
                val newCurveSpeeds = _smoothedPoints.value.map { it.second }

                if (twsIndex != -1) {
                    allSpeeds[twsIndex] = newCurveSpeeds.toMutableList()
                } else {
                    twsList.add(_selectedTws.value)
                    allSpeeds.add(newCurveSpeeds.toMutableList())
                }

                // TASK-006: Explicit Sorting of TWS axis to prevent interpolation failure
                val combined = twsList.zip(allSpeeds).sortedBy { it.first }
                twsList = combined.map { it.first }.toMutableList()
                allSpeeds = combined.map { it.second }.toMutableList()

                val updatedProfile = PolarProfile(
                    name = currentProfile.name,
                    description = currentProfile.description,
                    tws = twsList,
                    twa = twaList,
                    speeds = allSpeeds
                )

                val client = NauticalPlugin.getInstance()?.okHttpClient ?: okhttp3.OkHttpClient()
                val restService = SignalKRestService.create(serverBaseUrl, client) ?: run { onResult(false); return@launch }
                
                val response = restService.uploadPolar(polarId, updatedProfile)
                if (response.isSuccessful) {
                    fullProfile = updatedProfile
                }
                onResult(response.isSuccessful)
            } catch (_: Exception) {
                onResult(false)
            }
        }
    }
}
