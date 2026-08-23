package net.osmand.plus.plugins.nautical.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.network.*
import okhttp3.Credentials
import okhttp3.OkHttpClient

class SailingPerformanceRepository(
    private val dataBroker: net.osmand.plus.plugins.nautical.engine.SignalKDataBroker,
    okHttpClient: OkHttpClient,
    serverBaseUrl: String,
    username: String? = null,
    password: String? = null,
) {
    private val log = PlatformUtil.getLog(SailingPerformanceRepository::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val authenticatedClient = if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
        okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", Credentials.basic(username, password))
                    .build()
                chain.proceed(request)
            }
            .build()
    } else {
        okHttpClient
    }

    private val restService: SignalKRestService? = SignalKRestService.create(serverBaseUrl, authenticatedClient)
    private val polarDiagram = net.osmand.plus.plugins.nautical.maneuvers.PolarDiagram()

    private val _activePolarProfile = MutableStateFlow<PolarProfile?>(null)
    val activePolarProfile: StateFlow<PolarProfile?> = _activePolarProfile.asStateFlow()

    private val _livePerformanceData = MutableStateFlow(LivePerformanceData())
    val livePerformanceData: StateFlow<LivePerformanceData> = _livePerformanceData.asStateFlow()

    private val _availablePolars = MutableStateFlow<Map<String, PolarProfile>>(emptyMap())
    val availablePolars: StateFlow<Map<String, PolarProfile>> = _availablePolars.asStateFlow()

    init {
        startListening()
    }

    private fun startListening() {
        _activePolarProfile
            .filterNotNull()
            .onEach { profile ->
                polarDiagram.loadFromProfile(profile)
            }
            .launchIn(scope)

        dataBroker.livePerformanceData
            .onEach { data ->
                val tws = data.windSpeedTrue
                val twa = data.windAngleTrueWater ?: data.windAngleApparent
                val stw = data.speedThroughWater ?: data.speedOverGround

                if (tws != null && tws > 0.1 && polarDiagram.isLoaded) {
                    val isUpwind = twa == null || kotlin.math.abs(twa) < Math.toRadians(90.0)
                    val target = if (isUpwind) {
                        polarDiagram.getOptimalUpwindTarget(tws)
                    } else {
                        polarDiagram.getOptimalDownwindTarget(tws)
                    }

                    val targetAngleRad = Math.toRadians(target.targetTwaDeg)
                    val targetSpeedMs = target.targetSpeedMs

                    val efficiencyRatio = if (stw != null && twa != null && stw > 0.0) {
                        val effPct = polarDiagram.calculatePolarEfficiency(stw, tws, twa)
                        effPct / 100.0
                    } else {
                        data.polarSpeedRatio
                    }

                    _livePerformanceData.value = data.copy(
                        targetAngle = data.targetAngle ?: targetAngleRad,
                        polarSpeed = data.polarSpeed ?: targetSpeedMs,
                        polarSpeedRatio = data.polarSpeedRatio ?: efficiencyRatio
                    )
                } else {
                    _livePerformanceData.value = data
                }
            }
            .launchIn(scope)

        fetchPolars()
    }

    fun fetchPolars() {
        scope.launch {
            try {
                val response = restService?.getPolars()
                if (response != null && response.isSuccessful && (response.body() != null)) {
                    val polars = response.body()!!
                    _availablePolars.value = polars
                    if (_activePolarProfile.value == null && polars.isNotEmpty()) {
                        _activePolarProfile.value = polars.entries.first().value
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to fetch polars via REST: ${e.message}")
            }
        }
    }

    fun switchActivePolar(polarId: String) {
        scope.launch {
            val cached = _availablePolars.value[polarId]
            if (cached != null) {
                _activePolarProfile.value = cached
                return@launch
            }

            try {
                val response = restService?.getPolarById(polarId)
                if (response != null && response.isSuccessful && (response.body() != null)) {
                    _activePolarProfile.value = response.body()
                }
            } catch (e: Exception) {
                log.error("Failed to fetch polar $polarId: ${e.message}")
            }
        }
    }

    fun disconnect() {
        scope.cancel()
    }
}
