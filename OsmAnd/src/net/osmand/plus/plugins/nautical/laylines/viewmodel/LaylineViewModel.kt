package net.osmand.plus.plugins.nautical.laylines.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.grib.repository.GribRepository
import net.osmand.plus.plugins.nautical.laylines.engine.LatLon
import net.osmand.plus.plugins.nautical.laylines.engine.LaylineMathEngine
import net.osmand.plus.plugins.nautical.laylines.engine.TidalCurrentVector
import net.osmand.plus.plugins.nautical.repository.SailingPerformanceRepository
import net.osmand.plus.helpers.TargetPointsHelper
import net.osmand.plus.settings.backend.OsmandSettings
import net.osmand.plus.plugins.nautical.network.LivePerformanceData
import kotlin.math.PI
import kotlin.time.Duration.Companion.milliseconds

data class LaylineUiState(
    val boatLat: Double? = null,
    val boatLon: Double? = null,
    val portTackPoint: LatLon? = null,
    val starboardTackPoint: LatLon? = null,
    val isFetchable: Boolean = false,
    val fetchableStatusResId: Int = R.string.layline_status_tack_required,
    val targetWaypoint: LatLon? = null
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class LaylineViewModel(
    app: OsmandApplication,
    private val performanceRepo: SailingPerformanceRepository,
    private val gribRepo: GribRepository?
) : ViewModel() {

    private val targetPointsHelper: TargetPointsHelper = app.targetPointsHelper
    private val settings: OsmandSettings = app.settings

    private val _uiState = MutableStateFlow(LaylineUiState())
    val uiState: StateFlow<LaylineUiState> = _uiState.asStateFlow()

    init {
        startTracking()
    }

    private fun startTracking() {
        val leewayFlow = callbackFlow {
            val listener = net.osmand.StateChangedListener<Float> { trySend(it) }
            settings.NAUTICAL_LEEWAY_COEFFICIENT.addListener(listener)
            trySend(settings.NAUTICAL_LEEWAY_COEFFICIENT.get())
            awaitClose { settings.NAUTICAL_LEEWAY_COEFFICIENT.removeListener(listener) }
        }

        val manualLeewayFlow = callbackFlow {
            val listener = net.osmand.StateChangedListener<Float> { trySend(it) }
            settings.NAUTICAL_MANUAL_LEEWAY_ANGLE.addListener(listener)
            trySend(settings.NAUTICAL_MANUAL_LEEWAY_ANGLE.get())
            awaitClose { settings.NAUTICAL_MANUAL_LEEWAY_ANGLE.removeListener(listener) }
        }

        performanceRepo.livePerformanceData
            .combine(performanceRepo.activePolarProfile) { liveData, polar ->
                Pair(liveData, polar)
            }
            .combine(combine(leewayFlow, manualLeewayFlow) { a, b -> Pair(a, b) }) { a, b ->
                Triple(a.first, a.second, b)
            }
            .sample(500.milliseconds) // Task: Frequency reduction (2Hz)
            .onEach { (liveData, polar, leewayPair) ->
                withContext(Dispatchers.Default) { // Task: Offload heavy math to background
                    val (k, manual) = leewayPair
                    val lat = liveData.latitude ?: return@withContext
                    val lon = liveData.longitude ?: return@withContext
                    val boatPos = LatLon(lat, lon)

                val plugin = NauticalPlugin.getInstance()
                val caps = plugin?.capabilityManager?.capabilities?.value
                val serverLaylines = NauticalPlugin.engine?.getCurrentState()?.serverLaylines

                if (caps?.hasWindshift == true && serverLaylines != null) {
                    _uiState.value = LaylineUiState(
                        boatLat = lat,
                        boatLon = lon,
                        portTackPoint = serverLaylines.portTackPoint,
                        starboardTackPoint = serverLaylines.starboardTackPoint,
                        isFetchable = serverLaylines.isFetchable,
                        fetchableStatusResId = if (serverLaylines.isFetchable) 
                            R.string.layline_status_fetchable 
                        else 
                            R.string.layline_status_tack_required,
                        targetWaypoint = serverLaylines.targetWaypoint
                    )
                    return@withContext
                }

                val targetPoint = targetPointsHelper.pointToNavigate
                val infiniteEnabled = settings.NAUTICAL_SHOW_INFINITE_LAYLINES.get()
                
                val tacticalLat = settings.NAUTICAL_TACTICAL_TARGET_LAT.get()
                val tacticalLon = settings.NAUTICAL_TACTICAL_TARGET_LON.get()
                
                val target = targetPoint?.let { 
                    LatLon(it.latitude, it.longitude) 
                } ?: if (tacticalLat != 0.0) {
                    LatLon(tacticalLat, tacticalLon)
                } else if (infiniteEnabled) {
                    val cog = liveData.courseOverGround ?: liveData.headingTrue ?: 0.0
                    val dist = 1852.0 * 10.0 // Dummy target 10 NM ahead for direction
                    LaylineMathEngine.projectPoint(boatPos, cog, dist)
                } else null

                if (target == null) {
                    _uiState.value = LaylineUiState(boatLat = lat, boatLon = lon)
                    return@withContext
                }

                val stwMs = liveData.speedThroughWater ?: liveData.speedOverGround ?: 0.0
                val twsMs = liveData.windSpeedTrue ?: 0.0

                // Fallback to Tack Angle setting (TASK-FALLBACK)
                val fallbackTwa = Math.toRadians(settings.NAUTICAL_LAYLINES_TACK_ANGLE.get().toDouble() / 2.0)

                val twaRad = liveData.targetAngle ?: run {
                    polar?.let { p ->
                        val engine = net.osmand.plus.plugins.nautical.maneuvers.PolarDiagram()
                        engine.loadFromProfile(p)
                        engine.getOptimalUpwindTwaRad(twsMs)
                    } ?: fallbackTwa
                }

                val twdRad = calculateTwdRad(liveData) ?: 0.0
                
                // Observed Current (Task 3)
                val observedDrift = liveData.drift ?: 0.0
                val observedSet = liveData.setTrue ?: 0.0
                val current = if (observedDrift > 0.05) {
                    TidalCurrentVector(observedDrift, observedSet)
                } else {
                    getTidalCurrent(lat, lon)
                }

                val manualLeewayRad = Math.toRadians(manual.toDouble())
                val dynamicLeewayRad = net.osmand.plus.plugins.nautical.utils.LeewayCalculator.calculateLeewayRadians(
                    liveData.roll ?: 0.0,
                    stwMs,
                    k
                )
                val leewayRad = liveData.leeway ?: (if (manualLeewayRad > 0) manualLeewayRad else dynamicLeewayRad)

                val result = LaylineMathEngine.calculateApparentLaylines(
                    boatPosition = boatPos,
                    targetWaypoint = target,
                    optimalTwa = twaRad,
                    trueWindDirection = twdRad,
                    boatSpeed = stwMs,
                    current = current,
                    leewayRadians = leewayRad,
                    magneticVariation = liveData.magneticVariation ?: 0.0,
                    isMagneticInput = false, // calculateTwdRad already handles True frame
                    isInfinite = targetPoint == null && infiniteEnabled
                )

                _uiState.value = LaylineUiState(
                    boatLat = lat,
                    boatLon = lon,
                    portTackPoint = result.portTackPoint,
                    starboardTackPoint = result.starboardTackPoint,
                    isFetchable = result.isFetchable,
                    fetchableStatusResId = if (result.isFetchable) 
                        R.string.layline_status_fetchable 
                    else 
                        R.string.layline_status_tack_required,
                    targetWaypoint = result.targetWaypoint
                )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun calculateTwdRad(liveData: LivePerformanceData): Double? {
        val variation = liveData.magneticVariation ?: 0.0
        val headingTrue = liveData.headingTrue ?: liveData.headingMagnetic?.let { (it + variation) % (2 * PI) }
        val twa = liveData.windAngleTrueWater ?: return null
        return headingTrue?.let { (it + twa + 2 * PI) % (2 * PI) }
    }

    private fun getTidalCurrent(lat: Double, lon: Double): TidalCurrentVector {
        val now = System.currentTimeMillis()
        val vector = gribRepo?.getCurrentVector(lat, lon, now)
        return if (vector != null) {
            TidalCurrentVector(vector.u, vector.v)
        } else {
            TidalCurrentVector(0.0, 0.0)
        }
    }

    fun clear() {
        onCleared()
    }

}
