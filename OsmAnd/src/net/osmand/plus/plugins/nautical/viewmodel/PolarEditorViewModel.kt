package net.osmand.plus.plugins.nautical.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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

    fun setSelectedTws(tws: Double) {
        _selectedTws.value = tws
        loadTwsCurve(tws)
    }

    fun setSmoothingIntensity(intensity: Float) {
        _smoothingIntensity.value = intensity
        recalculateSmoothing()
    }

    fun updatePoint(index: Int, newTwa: Double, newSpeed: Double) {
        val list = _rawPoints.value.toMutableList()
        if (index in list.indices) {
            list[index] = Pair(newTwa, newSpeed)
            _rawPoints.value = list
            recalculateSmoothing()
        }
    }

    private fun recalculateSmoothing() {
        val raw = _rawPoints.value.sortedBy { it.first }
        val intensity = _smoothingIntensity.value
        // Simple moving average / weighted smoothing algorithm
        val smoothed = mutableListOf<Pair<Double, Double>>()
        for (i in raw.indices) {
            val current = raw[i]
            if (intensity == 0f || i == 0 || i == (raw.size - 1)) {
                smoothed.add(current)
            } else {
                val prev = raw[i - 1]
                val next = raw[i + 1]
                val avgSpeed = (prev.second + current.second + next.second) / 3.0
                val smoothedSpeed = (current.second * (1f - intensity)) + (avgSpeed * intensity)
                smoothed.add(Pair(current.first, smoothedSpeed))
            }
        }
        _smoothedPoints.value = smoothed
    }

    fun savePolarsToServer(serverBaseUrl: String, polarId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val currentProfile = fullProfile ?: run { onResult(false); return@launch }
                val twsList = currentProfile.tws?.toMutableList() ?: mutableListOf(_selectedTws.value)
                val twaList = _smoothedPoints.value.map { it.first }
                val allSpeeds = currentProfile.speeds?.map { it.toMutableList() }?.toMutableList() ?: mutableListOf()

                val twsIndex = twsList.indexOfFirst { kotlin.math.abs(it - _selectedTws.value) < 0.1 }
                val newCurveSpeeds = _smoothedPoints.value.map { it.second }

                if (twsIndex != -1) {
                    allSpeeds[twsIndex] = newCurveSpeeds.toMutableList()
                } else {
                    twsList.add(_selectedTws.value)
                    allSpeeds.add(newCurveSpeeds.toMutableList())
                }

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
