package net.osmand.plus.plugins.nautical.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.osmand.plus.plugins.nautical.network.PolarProfile
import net.osmand.plus.plugins.nautical.repository.SailingPerformanceRepository

class SailingPerformanceSettingsViewModel(
    private val repository: SailingPerformanceRepository
) : ViewModel() {

    val availablePolars: StateFlow<Map<String, PolarProfile>> = repository.availablePolars

    val activePolarName: StateFlow<String> = repository.activePolarProfile
        .map { it?.name ?: "Default Polar" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Default Polar")

    val isConnected: StateFlow<Boolean> = repository.livePerformanceData
        .map { data ->
            // Consider connected if timestamp is within the last 10 seconds
            System.currentTimeMillis() - data.timestamp < 10000
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun switchActivePolar(polarId: String) {
        repository.switchActivePolar(polarId)
    }

    fun refreshPolars() {
        repository.fetchPolars()
    }
}
