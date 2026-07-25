package net.osmand.plus.plugins.nautical.laylines.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.grib.repository.GribRepository
import net.osmand.plus.plugins.nautical.laylines.engine.LatLon
import net.osmand.plus.plugins.nautical.laylines.engine.LaylineMathEngine
import net.osmand.plus.plugins.nautical.laylines.engine.TidalCurrentVector
import net.osmand.plus.plugins.nautical.repository.SailingPerformanceRepository
import net.osmand.plus.helpers.TargetPointsHelper
import net.osmand.plus.settings.backend.OsmandSettings
import net.osmand.plus.plugins.nautical.network.LivePerformanceData
import kotlin.math.PI

data class LaylineUiState(
    val boatLat: Double? = null,
    val boatLon: Double? = null,
    val portTackPoint: LatLon? = null,
    val starboardTackPoint: LatLon? = null,
    val isFetchable: Boolean = false,
    val fetchableStatusResId: Int = R.string.layline_status_tack_required,
    val targetWaypoint: LatLon? = null
)

class LaylineViewModel(
    app: OsmandApplication,
    private val performanceRepo: SailingPerformanceRepository,
    private val gribRepo: GribRepository
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

        performanceRepo.livePerformanceData
            .combine(performanceRepo.activePolarProfile) { liveData, polar ->
                Pair(liveData, polar)
            }
            .combine(leewayFlow) { (liveData, polar), leeway ->
                Triple(liveData, polar, leeway)
            }
            .onEach { (liveData, polar, leeway) ->
                val lat = liveData.latitude ?: return@onEach
                val lon = liveData.longitude ?: return@onEach
                val boatPos = LatLon(lat, lon)
                
                val targetPoint = targetPointsHelper.pointToNavigate
                val target = targetPoint?.let { 
                    LatLon(it.latitude, it.longitude) 
                } ?: return@onEach

                // twa from polar is currently in Degrees? Let's check PolarProfile.
                // In PolarProfile it's List<Double>. 
                // In Signal K Resources it's usually degrees or radians? 
                // Signal K spec says Resources polars use Degrees for twa. 
                // BUT we want Radians internally.
                val twaRad = liveData.targetAngle ?: polar?.twa?.firstOrNull()?.let { Math.toRadians(it) } ?: Math.toRadians(45.0)
                val twdRad = calculateTwdRad(liveData) ?: 0.0
                val stwMs = liveData.speedThroughWater ?: 0.0
                
                // Current Vector from GRIB or fallback to zero
                val current = getTidalCurrent(lat, lon)

                val result = LaylineMathEngine.calculateApparentLaylines(
                    boatPosition = boatPos,
                    targetWaypoint = target,
                    optimalTwa = twaRad,
                    trueWindDirection = twdRad,
                    boatSpeed = stwMs,
                    current = current,
                    leewayRadians = Math.toRadians(leeway.toDouble())
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
            .launchIn(viewModelScope)
    }

    private fun calculateTwdRad(liveData: LivePerformanceData): Double? {
        val heading = liveData.headingTrue ?: return null
        val twa = liveData.windAngleTrueWater ?: return null
        return (heading + twa + 2 * PI) % (2 * PI)
    }

    private fun getTidalCurrent(lat: Double, lon: Double): TidalCurrentVector {
        // GribRepository used as dependency. 
        return TidalCurrentVector(0.0, 0.0)
    }
}
