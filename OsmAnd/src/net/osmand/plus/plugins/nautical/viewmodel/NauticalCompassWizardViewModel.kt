package net.osmand.plus.plugins.nautical.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.plugins.nautical.NauticalPlugin
import kotlin.time.Duration.Companion.seconds

enum class CompassWizardStep {
    PREPARATION,
    CALIBRATING,
    COMPLETE,
    FAILED
}

class NauticalCompassWizardViewModel : ViewModel() {

    private val _step = MutableStateFlow(CompassWizardStep.PREPARATION)
    val step: StateFlow<CompassWizardStep> = _step.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    private var timeoutJob: Job? = null
    private var observationJob: Job? = null

    init {
        startObservation()
    }

    private fun startObservation() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                val cal = state.pypilotCalibration
                if (cal != null) {
                    _isCalibrating.value = cal.isCalibrating
                    
                    // Signal K sends progress as 0.0-1.0 ratio, convert to 0-100
                    val p = ((cal.compassCalibrationProgress ?: 0.0) * 100.0).toInt().coerceIn(0, 100)
                    _progress.value = p

                    if (_step.value == CompassWizardStep.PREPARATION && cal.isCalibrating) {
                        timeoutJob?.cancel()
                        _step.value = CompassWizardStep.CALIBRATING
                    }

                    // Closed-loop completion: calibration stopped and progress is high
                    if (_step.value == CompassWizardStep.CALIBRATING && !cal.isCalibrating && p >= 90) {
                         _step.value = CompassWizardStep.COMPLETE
                    }
                }
            }
        }
    }

    fun startCalibration() {
        _step.value = CompassWizardStep.PREPARATION
        _progress.value = 0
        
        NauticalPlugin.autopilot?.startPypilotCalibration("compass")
        
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(12.seconds) // Slightly longer timeout to allow for network latency
            if (_step.value == CompassWizardStep.PREPARATION) {
                _step.value = CompassWizardStep.FAILED
            }
        }
    }

    fun stopCalibration() {
        NauticalPlugin.autopilot?.stopPypilotCalibration("compass")
        timeoutJob?.cancel()
        _step.value = CompassWizardStep.PREPARATION
    }

    fun retry() {
        startCalibration()
    }

    override fun onCleared() {
        super.onCleared()
        observationJob?.cancel()
        timeoutJob?.cancel()
    }
}
