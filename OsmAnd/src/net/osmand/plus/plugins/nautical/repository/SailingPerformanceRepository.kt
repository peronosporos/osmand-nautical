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

    private val restService: SignalKRestService = SignalKRestService.create(serverBaseUrl, authenticatedClient)

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
        dataBroker.livePerformanceData
            .onEach { data ->
                _livePerformanceData.value = data
            }
            .launchIn(scope)

        fetchPolars()
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

    fun disconnect() {
        scope.cancel()
    }
}
