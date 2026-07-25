package net.osmand.plus.plugins.nautical.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.network.*
import okhttp3.Credentials
import okhttp3.OkHttpClient

class SailingPerformanceRepository(
    okHttpClient: OkHttpClient,
    private val serverBaseUrl: String,
    private val username: String? = null,
    private val password: String? = null,
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

    private val restService: SignalKRestService = SignalKRestService.create(serverBaseUrl, authenticatedClient)
    private val webSocketClient: SignalKWebSocketClient = SignalKWebSocketClient(okHttpClient)
    private var reconnectAttempt = 0
    private val reconnectJob = Job()

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
        webSocketClient.onConnectionOpened = {
            reconnectAttempt = 0
        }
        webSocketClient.onConnectionFailure = {
            scope.launch {
                val delayMs = (5000L * (1 shl kotlin.math.min(reconnectAttempt, 4))).coerceAtMost(60000L)
                delay(delayMs)
                reconnectAttempt++
                connectWebSocket()
            }
        }
        connectWebSocket()

        scope.launch {
            webSocketClient.deltaFlow.collect { delta ->
                processDelta(delta)
            }
        }

        fetchPolars()
    }

    private fun connectWebSocket() {
        scope.launch {
            try {
                webSocketClient.connect(serverBaseUrl, username, password)
            } catch (e: Exception) {
                log.error("Failed to connect WebSocket: ${e.message}")
            }
        }
    }

    fun fetchPolars() {
        scope.launch {
            try {
                val response = restService.getPolars()
                if (response.isSuccessful && (response.body() != null)) {
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
                val response = restService.getPolarById(polarId)
                if (response.isSuccessful && (response.body() != null)) {
                    _activePolarProfile.value = response.body()
                }
            } catch (e: Exception) {
                log.error("Failed to fetch polar $polarId: ${e.message}")
            }
        }
    }

    private fun processDelta(delta: DeltaMessage) {
        val updates = delta.updates ?: return
        var currentData = _livePerformanceData.value
        var updated = false

        for (update in updates) {
            val values = update.values ?: continue
            for (v in values) {
                val path = v.path ?: continue
                
                if (path == LivePerformanceData.PATH_POSITION) {
                    val pos = v.value as? Map<*, *>
                    val lat = (pos?.get("latitude") as? Number)?.toDouble()
                    val lon = (pos?.get("longitude") as? Number)?.toDouble()
                    if (lat != null && lon != null) {
                        updated = true
                        currentData = currentData.copy(latitude = lat, longitude = lon)
                    }
                    continue
                }

                val numVal = (v.value as? Number)?.toDouble() ?: continue

                currentData = when (path) {
                    LivePerformanceData.PATH_STW -> {
                        updated = true
                        currentData.copy(speedThroughWater = numVal)
                    }
                    LivePerformanceData.PATH_TWS -> {
                        updated = true
                        currentData.copy(windSpeedTrue = numVal)
                    }
                    LivePerformanceData.PATH_TWA -> {
                        updated = true
                        currentData.copy(windAngleTrueWater = numVal)
                    }
                    LivePerformanceData.PATH_HEADING -> {
                        updated = true
                        currentData.copy(headingTrue = numVal)
                    }
                    LivePerformanceData.PATH_SOG -> {
                        updated = true
                        currentData.copy(speedOverGround = numVal)
                    }
                    LivePerformanceData.PATH_COG -> {
                        updated = true
                        currentData.copy(courseOverGround = numVal)
                    }
                    LivePerformanceData.PATH_POLAR_SPEED -> {
                        updated = true
                        currentData.copy(polarSpeed = numVal)
                    }
                    LivePerformanceData.PATH_TARGET_ANGLE -> {
                        updated = true
                        currentData.copy(targetAngle = numVal)
                    }
                    LivePerformanceData.PATH_POLAR_SPEED_RATIO -> {
                        updated = true
                        currentData.copy(polarSpeedRatio = numVal)
                    }
                    else -> currentData
                }
            }
        }

        if (updated) {
            _livePerformanceData.value = currentData.copy(timestamp = System.currentTimeMillis())
        }
    }

    fun disconnect() {
        webSocketClient.disconnect()
        scope.cancel()
    }
}
