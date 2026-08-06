package net.osmand.plus.plugins.nautical.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.AnchorCalculator
import net.osmand.plus.plugins.nautical.NauticalPlugin

class AnchorCalculatorViewModel(app: OsmandApplication) : ViewModel() {

    private val settings = app.settings

    private val _depth = MutableStateFlow(settings.NAUTICAL_ANCHOR_DEPTH.get())
    val depth: StateFlow<Float> = _depth.asStateFlow()

    private val _tideRise = MutableStateFlow(settings.NAUTICAL_ANCHOR_TIDE_RISE.get())
    val tideRise: StateFlow<Float> = _tideRise.asStateFlow()

    private val _bowOffset = MutableStateFlow(settings.NAUTICAL_ANCHOR_BOW_OFFSET.get())
    val bowOffset: StateFlow<Float> = _bowOffset.asStateFlow()

    private val _freeboard = MutableStateFlow(settings.NAUTICAL_ANCHOR_FREEBOARD.get())
    val freeboard: StateFlow<Float> = _freeboard.asStateFlow()

    private val _safetyMargin = MutableStateFlow(settings.NAUTICAL_ANCHOR_SAFETY_MARGIN.get())
    val safetyMargin: StateFlow<Float> = _safetyMargin.asStateFlow()

    private val _scopeRatio = MutableStateFlow(settings.NAUTICAL_ANCHOR_SCOPE_RATIO.get())
    val scopeRatio: StateFlow<Float> = _scopeRatio.asStateFlow()

    private val _anchorLat = MutableStateFlow(settings.NAUTICAL_ANCHOR_LAT.get())
    val anchorLat: StateFlow<Double> = _anchorLat.asStateFlow()

    private val _anchorLon = MutableStateFlow(settings.NAUTICAL_ANCHOR_LON.get())
    val anchorLon: StateFlow<Double> = _anchorLon.asStateFlow()

    private val _recommendedRode = MutableStateFlow(0.0)
    val recommendedRode: StateFlow<Double> = _recommendedRode.asStateFlow()

    init {
        updateCalculations()
    }

    fun setDepth(value: Float) {
        _depth.value = value
        settings.NAUTICAL_ANCHOR_DEPTH.set(value)
        updateCalculations()
    }

    fun setTideRise(value: Float) {
        _tideRise.value = value
        settings.NAUTICAL_ANCHOR_TIDE_RISE.set(value)
        updateCalculations()
    }

    fun setBowOffset(value: Float) {
        _bowOffset.value = value
        settings.NAUTICAL_ANCHOR_BOW_OFFSET.set(value)
        updateCalculations()
    }

    fun setFreeboard(value: Float) {
        _freeboard.value = value
        settings.NAUTICAL_ANCHOR_FREEBOARD.set(value)
        updateCalculations()
    }

    fun setSafetyMargin(value: Float) {
        _safetyMargin.value = value
        settings.NAUTICAL_ANCHOR_SAFETY_MARGIN.set(value)
    }

    fun setScopeRatio(value: Float) {
        _scopeRatio.value = value
        settings.NAUTICAL_ANCHOR_SCOPE_RATIO.set(value)
        updateCalculations()
    }

    fun setAnchorLat(value: Double) {
        _anchorLat.value = value
    }

    fun setAnchorLon(value: Double) {
        _anchorLon.value = value
    }

    private fun updateCalculations() {
        _recommendedRode.value = AnchorCalculator.calculateRodeLength(
            _depth.value.toDouble(),
            _tideRise.value.toDouble(),
            _freeboard.value.toDouble(),
            _scopeRatio.value.toDouble(),
        )
    }

    fun dropAnchor() {
        val engine = NauticalPlugin.engine
        val state = engine?.getCurrentState()
        
        // Use manual coordinates if provided, otherwise current GPS position
        val lat = if (_anchorLat.value != 0.0) _anchorLat.value else state?.latitude ?: return
        val lon = if (_anchorLon.value != 0.0) _anchorLon.value else state?.longitude ?: return
        
        val hdg = state?.headingTrue?.let { Math.toDegrees(it) } ?: state?.courseOverGroundTrue?.let { Math.toDegrees(it) } ?: 0.0

        val anchorPos = AnchorCalculator.calculateAnchorDrop(lat, lon, hdg, _bowOffset.value.toDouble())
        
        val totalRadius = AnchorCalculator.calculateTotalRadius(
            _recommendedRode.value,
            _bowOffset.value.toDouble(),
            _safetyMargin.value.toDouble(),
        )

        // Sync with Server if connected
        if (state?.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.CONNECTED) {
            engine.setAnchor(anchorPos.latitude, anchorPos.longitude, totalRadius)
        }

        // Always update local settings for immediate map feedback and fallback
        settings.NAUTICAL_ANCHOR_LAT.set(anchorPos.latitude)
        settings.NAUTICAL_ANCHOR_LON.set(anchorPos.longitude)
        settings.NAUTICAL_ANCHOR_RADIUS.set(totalRadius.toFloat())
    }

    fun clearAnchor() {
        val engine = NauticalPlugin.engine
        if (engine?.getCurrentState()?.connectionStatus == net.osmand.plus.plugins.nautical.engine.ConnectionStatus.CONNECTED) {
            engine.disarmAnchor()
        }

        settings?.NAUTICAL_ANCHOR_LAT?.set(0.0)
        settings?.NAUTICAL_ANCHOR_LON?.set(0.0)
        settings?.NAUTICAL_ANCHOR_RADIUS?.set(0f)
        NauticalPlugin.getInstance()?.anchorWatchdog?.stop()
    }
}
