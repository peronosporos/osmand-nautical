package net.osmand.plus.plugins.nautical.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import net.osmand.plus.plugins.nautical.network.PolarProfile
import net.osmand.plus.plugins.nautical.repository.SailingPerformanceRepository

/**
 * ViewModel for Nautical Performance Settings.
 * Preserved for future Performance UI integration.
 */
class SailingPerformanceSettingsViewModel(
    private val repository: SailingPerformanceRepository,
) : ViewModel() {

    val availablePolars: StateFlow<Map<String, PolarProfile>> = repository.availablePolars

    val activePolarName: StateFlow<String> = repository.activePolarProfile
        .map { it?.name ?: "Default Polar" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Default Polar"
        )

    val isConnected: StateFlow<Boolean> = repository.livePerformanceData
        .map { data ->
            // Consider connected if timestamp is within the last 10 seconds
            (System.currentTimeMillis() - data.timestamp) < 10000
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun switchActivePolar(polarId: String) {
        repository.switchActivePolar(polarId)
    }

    fun refreshPolars() {
        repository.fetchPolars()
    }
}
