package net.osmand.plus.plugins.nautical.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.PolarProfile
import net.osmand.plus.plugins.nautical.network.SignalKRestService

class PolarEditorViewModel : ViewModel() {

    private val _selectedTws = MutableStateFlow(8.0)
    val selectedTws: StateFlow<Double> = _selectedTws.asStateFlow()

    private val _smoothingIntensity = MutableStateFlow(0.5f)
    @Suppress("unused")
    val smoothingIntensity: StateFlow<Float> = _smoothingIntensity.asStateFlow()

    private val _rawPoints = MutableStateFlow(
        listOf(
            Pair(40.0, 5.2),
            Pair(60.0, 6.8),
            Pair(90.0, 7.5),
            Pair(120.0, 8.1),
            Pair(150.0, 6.5),
        )
    )
    val rawPoints: StateFlow<List<Pair<Double, Double>>> = _rawPoints.asStateFlow()

    private val _smoothedPoints = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val smoothedPoints: StateFlow<List<Pair<Double, Double>>> = _smoothedPoints.asStateFlow()

    init {
        recalculateSmoothing()
    }

    @Suppress("unused")
    fun setSelectedTws(tws: Double) {
        _selectedTws.value = tws
        recalculateSmoothing()
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
                val client = NauticalPlugin.getInstance()?.okHttpClient ?: okhttp3.OkHttpClient()
                val restService = SignalKRestService.create(serverBaseUrl, client) ?: run { onResult(false); return@launch }
                val profile = PolarProfile(
                    name = "Edited Polar TWS ${_selectedTws.value}",
                    description = "Custom smoothed polar curve",
                    tws = listOf(_selectedTws.value),
                    twa = _smoothedPoints.value.map { it.first },
                    speeds = listOf(_smoothedPoints.value.map { it.second })
                )
                val response = restService.uploadPolar(polarId, profile)
                onResult(response.isSuccessful)
            } catch (_: Exception) {
                onResult(false)
            }
        }
    }
}
