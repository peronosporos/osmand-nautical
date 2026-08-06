package net.osmand.plus.plugins.nautical.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.osmand.plus.plugins.nautical.network.PolarProfile
import net.osmand.plus.plugins.nautical.network.SignalKRestService
import kotlin.math.abs

enum class WizardState {
    INITIAL_CHECK,
    PROFILE_SETUP,
    ACTIVE_LOGGING,
    REVIEW_AND_SMOOTH,
    SAVING
}

data class PolarCell(
    val tws: Double,
    val twa: Double,
    var sampleCount: Int = 0,
    var averageSpeed: Double = 0.0,
)

@Suppress("unused")
class PolarConfigViewModel : ViewModel() {

    private val _wizardState = MutableStateFlow(WizardState.INITIAL_CHECK)
    val wizardState: StateFlow<WizardState> = _wizardState.asStateFlow()

    private val _engineOff = MutableStateFlow(value = false)
    val engineOff: StateFlow<Boolean> = _engineOff.asStateFlow()

    private val _sensorsCalibrated = MutableStateFlow(false)
    val sensorsCalibrated: StateFlow<Boolean> = _sensorsCalibrated.asStateFlow()

    private val _profileName = MutableStateFlow("My Custom Polar")
    val profileName: StateFlow<String> = _profileName.asStateFlow()

    private val _sailPlan = MutableStateFlow("Main + Jib")
    val sailPlan: StateFlow<String> = _sailPlan.asStateFlow()

    // Heatmap matrix: TWS values (e.g. 6, 8, 10, 12, 14, 16, 20) vs TWA (e.g. 40, 60, 90, 120, 150)
    private val twsAxes = listOf(6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 20.0)
    private val twaAxes = listOf(40.0, 50.0, 60.0, 80.0, 100.0, 120.0, 140.0, 160.0)

    private val _heatmapCells = MutableStateFlow<List<PolarCell>>(emptyList())
    val heatmapCells: StateFlow<List<PolarCell>> = _heatmapCells.asStateFlow()

    private val _recommendation = MutableStateFlow("Ensure engine is off and instruments are calibrated.")
    val recommendation: StateFlow<String> = _recommendation.asStateFlow()

    init {
        initHeatmap()
    }

    private fun initHeatmap() {
        val cells = mutableListOf<PolarCell>()
        for (tws in twsAxes) {
            for (twa in twaAxes) {
                cells.add(PolarCell(tws, twa, 0, 0.0))
            }
        }
        _heatmapCells.value = cells
    }

    fun setEngineOff(off: Boolean) {
        _engineOff.value = off
        checkPrerequisites()
    }

    fun setSensorsCalibrated(calibrated: Boolean) {
        _sensorsCalibrated.value = calibrated
        checkPrerequisites()
    }

    private fun checkPrerequisites() {
        if (_engineOff.value && _sensorsCalibrated.value) {
            _recommendation.value = "Prerequisites met. Ready to set profile metadata."
        }
    }

    fun setProfileMetadata(name: String, plan: String) {
        _profileName.value = name
        _sailPlan.value = plan
    }

    fun transitionTo(newState: WizardState) {
        _wizardState.value = newState
        if (newState == WizardState.ACTIVE_LOGGING) {
            updateRecommendation(10.0, 50.0)
        } else if (newState == WizardState.SAVING) {
            saveToServer()
        }
    }

    private fun saveToServer() {
        val plugin = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance() ?: return
        val client = plugin.okHttpClient ?: return
        
        val ip = plugin.application.settings.NAUTICAL_SERVER_IP.get()
        val port = plugin.application.settings.NAUTICAL_SERVER_PORT.get()
        val protocol = if (plugin.application.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
        val service = SignalKRestService.create("$protocol://$ip:$port", client)

        // Convert heatmap to PolarProfile matrix
        val heatmap = _heatmapCells.value
        val speeds = twsAxes.map { tws ->
            twaAxes.map { twa ->
                heatmap.find { it.tws == tws && it.twa == twa }?.averageSpeed ?: 0.0
            }
        }

        val polarId = _profileName.value.lowercase().replace(" ", "-")
        val profile = PolarProfile(
            name = _profileName.value,
            description = "Recorded in OsmAnd: ${_sailPlan.value}",
            tws = twsAxes,
            twa = twaAxes,
            speeds = speeds
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = service.uploadPolar(polarId, profile)
                if (response.isSuccessful) {
                    plugin.application.runInUIThread {
                        plugin.application.showToastMessage("Polar profile uploaded to Signal K")
                    }
                }
            } catch (e: Exception) {
                plugin.application.runInUIThread {
                    plugin.application.showToastMessage("Failed to upload polar")
                }
            }
        }
    }

    fun recordDataPoint(currentTws: Double, currentTwa: Double, currentSpeed: Double) {
        val cells = _heatmapCells.value.toMutableList()
        // Find closest cell
        val cell = cells.minByOrNull { abs(it.tws - currentTws) + abs(it.twa - currentTwa) }
        if (cell != null) {
            cell.sampleCount++
            cell.averageSpeed = ((cell.averageSpeed * (cell.sampleCount - 1)) + currentSpeed) / cell.sampleCount
            _heatmapCells.value = cells.toList()
        }

        updateRecommendation(currentTws, currentTwa)
    }

    private fun updateRecommendation(currentTws: Double, currentTwa: Double) {
        // Gamified recommendation: find empty or low sample cell closest to current conditions
        val emptyCell = _heatmapCells.value
            .asSequence()
            .filter { it.sampleCount < 3 }
            .minByOrNull { abs(it.tws - currentTws) + abs(it.twa - currentTwa) }

        if (emptyCell != null) {
            _recommendation.value = "Adjust TWA to ${emptyCell.twa.toInt()}° to fill TWS ${emptyCell.tws}"
        } else {
            _recommendation.value = "All primary polar cells populated. Great sailing!"
        }
    }
}
