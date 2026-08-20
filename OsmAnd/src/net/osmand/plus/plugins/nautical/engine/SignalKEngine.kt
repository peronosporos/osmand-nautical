package net.osmand.plus.plugins.nautical.engine

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.data.LatLon
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.network.DeltaMessage
import net.osmand.plus.plugins.nautical.network.LivePerformanceData
import net.osmand.plus.plugins.nautical.network.SignalKLineString
import net.osmand.plus.plugins.nautical.network.SignalKRestService
import net.osmand.plus.plugins.nautical.network.SignalKRoute
import net.osmand.plus.plugins.nautical.network.SignalKRouteFeature
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import net.osmand.shared.aistracker.AisObject
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

data class TrajectoryPoint(val lat: Double, val lon: Double, val time: Long)

class SignalKEngine(
    private val app: OsmandApplication,
    val engineScope: CoroutineScope,
    val capabilityManager: CapabilityManager? = null
) {
    private val log = PlatformUtil.getLog(SignalKEngine::class.java)

    val dataBroker = SignalKDataBroker(app.settings)
    val controlManager = SignalKControlManager(app, dataBroker, engineScope)
    val resourceManager = SignalKResourceManager(app, engineScope)

    val historyManager = SignalKHistoryManager(app, capabilityManager)
    val routeTracker = SignalKRouteTracker()
    val metricsCalculator = SignalKMetricsCalculator(app)
    val sessionManager = SignalKSessionManager(app, engineScope, dataBroker, resourceManager, historyManager)

    private val engineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        log.error("SignalKEngine Global Error: ${throwable.message}", throwable)
    }

    private val parsingScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + engineExceptionHandler)

    private var messageChannel = Channel<String>(
        capacity = 2000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var deltaChannel = Channel<DeltaMessage>(
        capacity = 2000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var messageProcessingJob: Job? = null
    private var deltaProcessingJob: Job? = null
    private var autoSaveJob: Job? = null
    private var pulseJob: Job? = null

    private var lastUiNotificationTime = 0L
    private var pendingUiState: MarineState? = null
    private var uiNotificationJob: Job? = null

    val marineStateFlow: StateFlow<MarineState> = dataBroker.marineState

    private val _pulseFlow = MutableStateFlow(false)
    val pulseFlow: StateFlow<Boolean> = _pulseFlow.asStateFlow()

    val trajectoryEventFlow: SharedFlow<Unit> = historyManager.trajectoryEventFlow

    private val routeStepListeners = CopyOnWriteArraySet<() -> Unit>()
    private val stateListeners = CopyOnWriteArraySet<(MarineState) -> Unit>()
    private var aisListener: ((AisObject) -> Unit)? = null

    val deltaParser = SignalKDeltaParser(
        app = app,
        dataBroker = dataBroker,
        historyManager = historyManager,
        routeTracker = routeTracker,
        sessionManager = sessionManager,
        resourceManager = resourceManager,
        engineScope = engineScope,
        routeStepListeners = routeStepListeners,
        aisListenerProvider = { aisListener }
    )

    var onConnectionLost: (() -> Unit)?
        get() = sessionManager.onConnectionLost
        set(value) { sessionManager.onConnectionLost = value }

    var onConnectionError: (() -> Unit)?
        get() = sessionManager.onConnectionError
        set(value) { sessionManager.onConnectionError = value }

    var onAuthError: (() -> Unit)?
        get() = sessionManager.onAuthError
        set(value) { sessionManager.onAuthError = value }

    var onConnectionRestored: (() -> Unit)?
        get() = sessionManager.onConnectionRestored
        set(value) { sessionManager.onConnectionRestored = value }

    var deltaSender: ((String) -> Unit)?
        get() = sessionManager.deltaSender
        set(value) { sessionManager.deltaSender = value }

    var isFollowingRoute: Boolean
        get() = routeTracker.isFollowingRoute
        internal set(value) { routeTracker.isFollowingRoute = value }

    var xteThresholdNm: Double
        get() = routeTracker.xteThresholdNm
        set(value) { routeTracker.xteThresholdNm = value }

    var vesselDraft: Double
        get() = routeTracker.vesselDraft
        set(value) { routeTracker.vesselDraft = value }

    var corridorWidthNm: Double
        get() = routeTracker.corridorWidthNm
        set(value) { routeTracker.corridorWidthNm = value }

    var safetyCorridorBufferNm: Double
        get() = routeTracker.safetyCorridorBufferNm
        set(value) { routeTracker.safetyCorridorBufferNm = value }

    var arrivalRadiusMeters: Double
        get() = routeTracker.arrivalRadiusMeters
        set(value) { routeTracker.arrivalRadiusMeters = value }

    @Volatile
    private var lastRealtimeMessageTimestamp: Long = 0
    private var lastVesselStateRefreshTime: Long = 0

    init {
        val settings = app.settings
        xteThresholdNm = settings.NAUTICAL_XTE_THRESHOLD.get().toDouble()
        vesselDraft = settings.NAUTICAL_VESSEL_DRAFT.get().toDouble()
        corridorWidthNm = settings.NAUTICAL_CORRIDOR_WIDTH.get().toDouble()
        safetyCorridorBufferNm = settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.get().toDouble()
        arrivalRadiusMeters = settings.NAUTICAL_ARRIVAL_RADIUS.get().toDouble()

        engineScope.launch {
            capabilityManager?.capabilities?.collect { caps ->
                val newCapacity = if (caps.hasHistory || caps.hasLogging) 60 else 3600
                historyManager.setCapacity(newCapacity)

                val active = mutableSetOf<String>()
                if (caps.hasWingaRouting) active.add("winga-weather-routing")
                if (caps.hasRouteIq) active.add("signalk-routeiq")
                if (caps.hasPolarPerformance) active.add("signalk-polar-performance")
                dataBroker.updateState { it.copy(activePlugins = active) }
            }
        }

        app.settings.NAUTICAL_VESSEL_DRAFT.addListener {
            controlManager.updateVesselDesign("design.draft", app.settings.NAUTICAL_VESSEL_DRAFT.get().toDouble())
        }
        app.settings.NAUTICAL_AIR_DRAFT.addListener {
            controlManager.updateVesselDesign("design.airDraft", app.settings.NAUTICAL_AIR_DRAFT.get().toDouble())
        }

        setPowerSaveMode(false)
        startMessageProcessing()
        startAutoSave()
    }

    fun startEngine() {
        setPowerSaveMode(false)
        startMessageProcessing()
    }

    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = engineScope.launch {
            while (isActive) {
                delay(5.minutes)
                historyManager.saveBuffersToDisk(app)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startMessageProcessing() {
        if (messageChannel.isClosedForSend) {
            messageChannel = Channel(
                capacity = 2000,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }
        if (deltaChannel.isClosedForSend) {
            deltaChannel = Channel(
                capacity = 2000,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }
        messageProcessingJob?.cancel()
        messageProcessingJob = parsingScope.launch {
            log.info("SignalKEngine: Message processing loop started successfully")
            val batch = mutableListOf<String>()
            while (isActive) {
                try {
                    val first = messageChannel.receive()
                    batch.add(first)

                    var count = 0
                    while (count < 100) {
                        val next = messageChannel.tryReceive().getOrNull() ?: break
                        batch.add(next)
                        count++
                    }

                    if (batch.isNotEmpty()) {
                        var currentState = dataBroker.marineState.value
                        var stateChanged = false

                        for (message in batch) {
                            try {
                                val res = deltaParser.processJsonMessage(message, currentState) { self ->
                                    sessionManager.handleSelfIdentity(self)
                                }
                                currentState = res.first
                                if (res.second) stateChanged = true
                            } catch (t: Throwable) {
                                log.error("SignalKEngine: Error processing individual json message: ${t.message}", t)
                            }
                        }

                        currentState = currentState.copy(lastMessageTime = lastRealtimeMessageTimestamp)

                        if (stateChanged) {
                            finalizeAndNotifyState(currentState)
                        } else {
                            dataBroker.updateState { currentState }
                        }
                        batch.clear()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    log.error("SignalKEngine: Batch processing error: ${t.message}", t)
                    batch.clear()
                    delay(50.milliseconds)
                }
            }
        }

        deltaProcessingJob?.cancel()
        deltaProcessingJob = parsingScope.launch {
            val deltaBatch = mutableListOf<DeltaMessage>()
            while (isActive) {
                try {
                    val first = deltaChannel.receive()
                    deltaBatch.add(first)

                    var count = 0
                    while (count < 100) {
                        val next = deltaChannel.tryReceive().getOrNull() ?: break
                        deltaBatch.add(next)
                        count++
                    }

                    if (deltaBatch.isNotEmpty()) {
                        var currentState = dataBroker.marineState.value
                        var anyDeltaChanged = false
                        for (delta in deltaBatch) {
                            val context = delta.context ?: "vessels.self"
                            deltaParser.processDeltaUpdates(delta, context) { updatedState ->
                                currentState = updatedState
                                anyDeltaChanged = true
                            }
                        }
                        if (anyDeltaChanged) {
                            finalizeAndNotifyState(currentState)
                        }
                        deltaBatch.clear()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    log.error("SignalKEngine: Delta batch processing error: ${t.message}", t)
                    deltaBatch.clear()
                    delay(50.milliseconds)
                }
            }
        }
    }

    private var lastRestUrl: String? = null
    private var cachedRestService: SignalKRestService? = null

    fun getCurrentState(): MarineState = dataBroker.marineState.value

    @Synchronized
    fun getRestService(): SignalKRestService? {
        val plugin = NauticalPlugin.getInstance() ?: return null
        val client = plugin.okHttpClient ?: return null
        val ip = app.settings.NAUTICAL_SERVER_IP.get()
        val port = app.settings.NAUTICAL_SERVER_PORT.get()
        if (ip.isEmpty()) return null

        val protocol = if (app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
        val url = "$protocol://$ip:$port"

        if (url == lastRestUrl && cachedRestService != null) {
            return cachedRestService
        }

        lastRestUrl = url
        cachedRestService = SignalKRestService.create(url, client)
        return cachedRestService
    }

    fun refreshResources() {
        resourceManager.startSync()
        refreshVesselState()
    }

    fun refreshVesselState() {
        val now = System.currentTimeMillis()
        if (now - lastVesselStateRefreshTime < 30000) {
            log.debug("Nautical: Skipping redundant vessel state refresh (cooldown active).")
            return
        }
        lastVesselStateRefreshTime = now

        engineScope.launch(Dispatchers.IO) {
            try {
                val restService = getRestService() ?: return@launch
                val response = restService.getVesselSelf()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val res = deltaParser.processVesselTree(body, dataBroker.marineState.value)
                        if (res.second) {
                            finalizeAndNotifyState(res.first)
                        }
                        log.info("Nautical: Immediate background state refresh completed via REST.")
                    }
                }

                // Course API Reconciliation (v2)
                val courseResponse = restService.getCourse()
                if (courseResponse.isSuccessful) {
                    courseResponse.body()?.let { course ->
                        routeTracker.processCourseObject(course) { nextPt ->
                            dataBroker.updateState { it.copy(serverNextPoint = LatLon(nextPt.latitude, nextPt.longitude)) }
                        }
                    }
                }

                // History backfill if supported
                if (capabilityManager?.capabilities?.value?.hasHistory == true) {
                    fetchHistoryFromServer(restService)
                }
            } catch (e: Exception) {
                log.error("Failed to refresh vessel state via REST: ${e.message}")
            }
        }
    }

    private suspend fun fetchHistoryFromServer(restService: SignalKRestService) {
        try {
            val paths = listOf(
                SignalKPaths.ENV_DEPTH_BELOW_TRANSDUCER,
                SignalKPaths.NAV_SPEED_OVER_GROUND,
                SignalKPaths.ENV_WIND_SPEED_TRUE,
                SignalKPaths.ENV_WIND_DIRECTION_TRUE
            ).joinToString(",")

            val from = TemporalUtils.formatIso8601(System.currentTimeMillis() - 3600000) // Last hour
            val response = restService.getHistoryValues(paths, from)
            if (response.isSuccessful) {
                val body = response.body() ?: return
                // Simple backfill: Signal K history format can be complex, usually { path: [ { v, t } ] }
                body.forEach { (path, values) ->
                    if (values is List<*>) {
                        val buffer = historyManager.getBuffer(path)
                        values.forEach { item ->
                            if (item is Map<*, *>) {
                                val v = (item["v"] as? Number)?.toDouble()
                                val tStr = item["t"] as? String
                                if (v != null && tStr != null) {
                                    val t = TemporalUtils.parseIso8601(tStr)
                                    if (t > 0) buffer.add(Pair(v, t))
                                }
                            }
                        }
                    }
                }
                log.info("Nautical: History backfill completed for $paths")
            }
        } catch (e: Exception) {
            log.debug("History backfill failed (ignoring): ${e.message}")
        }
    }

    suspend fun fetchRoutesFromServer(): Map<String, SignalKRoute>? = withContext(Dispatchers.IO) {
        try {
            val response = getRestService()?.getRoutes()
            if (response?.isSuccessful == true) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            log.error("Failed to fetch routes from server: ${e.message}")
            null
        }
    }

    suspend fun deleteRouteFromServer(routeId: String) = withContext(Dispatchers.IO) {
        try {
            getRestService()?.deleteRoute(routeId)
        } catch (e: Exception) {
            log.error("Failed to delete route from server: ${e.message}")
        }
    }

    suspend fun updateRouteOnServer(routeId: String, name: String, points: List<Pair<Double, Double>>) = withContext(Dispatchers.IO) {
        try {
            val coords = points.map { listOf(it.second, it.first) }
            val skRoute = SignalKRoute(
                name = name,
                description = "Updated from OsmAnd Nautical",
                distance = null,
                feature = SignalKRouteFeature(
                    geometry = SignalKLineString(coordinates = coords)
                )
            )
            getRestService()?.updateRoute(routeId, skRoute)
        } catch (e: Exception) {
            log.error("Failed to update route on server: ${e.message}")
        }
    }

    suspend fun uploadActiveRouteToSignalK(name: String, points: List<Pair<Double, Double>> = getRoutePoints()) = withContext(Dispatchers.IO) {
        try {
            val pts = points.ifEmpty {
                val tp = mutableListOf<Pair<Double, Double>>()
                app.targetPointsHelper.intermediatePointsNavigation.forEach { tp.add(it.latitude to it.longitude) }
                app.targetPointsHelper.pointToNavigate?.let { tp.add(it.latitude to it.longitude) }
                tp
            }
            if (pts.isEmpty()) return@withContext
            val coords = pts.map { listOf(it.second, it.first) }
            val skRoute = SignalKRoute(
                name = name,
                description = "Exported from OsmAnd Nautical",
                distance = null,
                feature = SignalKRouteFeature(
                    geometry = SignalKLineString(coordinates = coords)
                )
            )
            getRestService()?.createRoute(skRoute)
        } catch (e: Exception) {
            log.error("Failed to upload active route to Signal K: ${e.message}")
        }
    }

    fun setSwitch(path: String, state: Boolean) = controlManager.setSwitchState(path, state)
    fun setAutopilotMode(mode: String) = controlManager.setAutopilotMode(mode)
    fun setAutopilotHeading(radians: Double) = controlManager.setAutopilotTargetHeading(radians)
    fun setAutopilotHeadingMagnetic(radians: Double) = controlManager.setAutopilotTargetHeadingMagnetic(radians)
    fun acknowledgeNotification(path: String) = controlManager.acknowledgeNotification(path)

    fun acknowledgeActuatorAlarm() {
        dataBroker.updateState { it.copy(actuatorAlarmAcknowledged = true) }
        NauticalAudioArbiter.getInstance(app).stopAlarm(AlarmType.ACTUATOR_OVERLOAD)
        NauticalPlugin.hudManager?.get()?.hideBanner()
    }

    fun setAnchor(lat: Double, lon: Double, radius: Double) = controlManager.setAnchor(lat, lon, radius)
    fun disarmAnchor() = controlManager.disarmAnchor()

    fun clearBuffers(context: Context) {
        historyManager.clearBuffers(context)
    }

    @Synchronized
    fun stop() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        messageProcessingJob?.cancel()
        messageProcessingJob = null
        messageChannel.close()
        parsingScope.cancel()
        resourceManager.stopSync()
        sessionManager.stop()
        routeStepListeners.clear()
        stateListeners.clear()
        dataBroker.stop()
        aisListener = null
        routeTracker.clearRoute()
    }

    fun isAuthenticated(): Boolean = sessionManager.isAuthenticated()

    fun triggerAuthError() = sessionManager.triggerAuthError()

    fun sendDelta(path: String, value: Any) = sessionManager.sendDelta(path, value)

    fun dispatchCommand(command: String) = sessionManager.dispatchCommand(command) { switchPath, state ->
        setSwitch(switchPath, state)
    }

    fun registerListener(listener: (MarineState) -> Unit) { stateListeners.add(listener) }
    fun unregisterListener(listener: (MarineState) -> Unit) { stateListeners.remove(listener) }
    fun addRouteStepListener(listener: () -> Unit) { routeStepListeners.add(listener) }
    fun removeRouteStepListener(listener: () -> Unit) { routeStepListeners.remove(listener) }
    fun registerAisListener(listener: ((AisObject) -> Unit)?) { this.aisListener = listener }

    private fun notifyListeners(state: MarineState) {
        stateListeners.forEach { it.invoke(state) }
    }

    private fun scheduleUiNotification(state: MarineState) {
        val isCritical = state.isMobActive || state.isActuatorOverloaded || state.notifications.values.any { it.state == NotificationState.ALARM || it.state == NotificationState.EMERGENCY }
        val now = System.currentTimeMillis()
        if (isCritical || (now - lastUiNotificationTime) >= 50L) {
            lastUiNotificationTime = now
            pendingUiState = null
            engineScope.launch(Dispatchers.Main.immediate) {
                notifyListeners(state)
            }
        } else {
            pendingUiState = state
            if (uiNotificationJob?.isActive != true) {
                uiNotificationJob = engineScope.launch(Dispatchers.Default) {
                    delay(50.milliseconds)
                    val delayedState = pendingUiState ?: return@launch
                    pendingUiState = null
                    lastUiNotificationTime = System.currentTimeMillis()
                    withContext(Dispatchers.Main.immediate) {
                        notifyListeners(delayedState)
                    }
                }
            }
        }
    }

    fun getHistory(path: String): List<Pair<Double, Long>> = historyManager.getHistory(path)

    fun setPowerSaveMode(enabled: Boolean) {
        historyManager.setPowerSaveMode(enabled)
        if (enabled) {
            cancelPendingBatchJobs()
        } else {
            startMessageProcessing()
        }
    }

    fun cancelPendingBatchJobs() {
        messageProcessingJob?.cancel()
        messageProcessingJob = null
        deltaProcessingJob?.cancel()
        deltaProcessingJob = null
        while (messageChannel.tryReceive().isSuccess) {
            // Drain channel to prevent processing stale messages
        }
        while (deltaChannel.tryReceive().isSuccess) {
            // Drain channel
        }
    }

    fun addTrajectoryPoint(lat: Double, lon: Double) {
        historyManager.addTrajectoryPoint(lat, lon)
    }

    fun clearTrajectory() {
        historyManager.clearTrajectory()
    }

    fun copyTrajectoryTo(target: MutableList<TrajectoryPoint>) {
        historyManager.copyTrajectoryTo(target)
    }

    fun handleIncomingMessage(jsonMessage: String) {
        if (historyManager.powerSaveMode) return
        if (messageProcessingJob?.isActive != true) {
            startMessageProcessing()
        }
        log.debug("SignalK Ingress: $jsonMessage")
        sessionManager.lastUpdateTimestamp = TemporalUtils.now()
        lastRealtimeMessageTimestamp = System.currentTimeMillis()
        sessionManager.resetWatchdog(
            onNotifyListeners = { scheduleUiNotification(it) },
            onUpdatePulse = { updatePulseLifecycle() },
            onResetRoute = { isFollowingRoute = false }
        )
        messageChannel.trySend(jsonMessage)
    }

    fun handleDelta(delta: DeltaMessage) {
        if (historyManager.powerSaveMode) return
        if (deltaProcessingJob?.isActive != true) {
            startMessageProcessing()
        }
        sessionManager.resetWatchdog(
            onNotifyListeners = { scheduleUiNotification(it) },
            onUpdatePulse = { updatePulseLifecycle() },
            onResetRoute = { isFollowingRoute = false }
        )
        deltaChannel.trySend(delta)
    }

    private fun finalizeAndNotifyState(state: MarineState) {
        val now = TemporalUtils.now()

        val isEngineRunning = state.engines.values.any {
            it.state?.lowercase() == "started" || (it.revolutions != null && it.revolutions > 100.0)
        }
        val stateWithEngine = state.copy(isEngineRunning = isEngineRunning)

        val caps = capabilityManager?.capabilities?.value ?: CapabilityManager.ServerCapabilityMap()

        val stateWithLeeway = if (caps.hasLeeway || caps.hasDerivedData) {
            stateWithEngine
        } else {
            val leeway = metricsCalculator.calculateLeeway(stateWithEngine)
            stateWithEngine.copy(leeway = leeway)
        }

        val step1 = if (caps.hasSetAndDrift || caps.hasDerivedData) stateWithLeeway else metricsCalculator.calculateSetAndDrift(stateWithLeeway, now, historyManager)
        val step2 = metricsCalculator.calculateDepths(step1, vesselDraft)
        val step3 = metricsCalculator.calculateEfficiencyMetrics(step2)
        val step4 = metricsCalculator.calculateNavigationMetrics(step3, now, routeTracker, capabilityManager, historyManager)

        val processedState = step4.let { s ->
            val xteNm = SignalKUnitConverter.metersToNm(abs(s.crossTrackError ?: 0.0))
            val isOff = xteNm > xteThresholdNm && (s.autopilotState.uppercase(Locale.US) == "TRACK")
            s.copy(isOffCourse = isOff, connectionStatus = ConnectionStatus.CONNECTED)
        }

        val finalState = MultihullShuntManager.transformState(processedState)
        val effectiveStw = if (finalState.isStwUnreliable) finalState.speedOverGround else finalState.speedThroughWater

        val perfData = LivePerformanceData(
            speedThroughWater = effectiveStw,
            windSpeedTrue = finalState.windSpeedTrue,
            windAngleTrueWater = finalState.trueWindAngle,
            speedOverGround = finalState.speedOverGround,
            courseOverGround = finalState.courseOverGroundTrue,
            latitude = finalState.latitude,
            longitude = finalState.longitude,
            headingTrue = finalState.headingTrue,
            headingMagnetic = finalState.headingMagnetic,
            magneticVariation = finalState.magneticVariation,
            leeway = finalState.leeway,
            depthBelowTransducer = finalState.depthBelowTransducer,
            polarSpeed = finalState.polarTargetSpeed,
            targetAngle = finalState.targetWindAngleApparent,
            polarSpeedRatio = finalState.polarSpeedRatio,
            roll = finalState.roll,
            pitch = finalState.pitch,
            windAngleApparent = finalState.windDirectionApparent,
            windSpeedApparent = finalState.windSpeedApparent,
            destinationLatitude = getNextWaypoint()?.first,
            destinationLongitude = getNextWaypoint()?.second,
            lastWaypointLatitude = routeTracker.lastWaypointLat,
            lastWaypointLongitude = routeTracker.lastWaypointLon,
            distanceToWaypoint = finalState.distanceToWaypoint,
            drift = finalState.drift,
            setTrue = finalState.setTrue,
            timestamp = finalState.timestamps.values.maxOrNull()?.let { TemporalUtils.validate(it) } ?: TemporalUtils.now()
        )

        dataBroker.updatePerformanceData(perfData)
        SailingDependencyContainer.nmeaMultiplexer?.aggregator?.handleLivePerformanceData(perfData)
        dataBroker.updateState { finalState }

        scheduleUiNotification(finalState)

        metricsCalculator.checkActuatorLoad(finalState, dataBroker, historyManager) {
            acknowledgeActuatorAlarm()
        }
    }

    fun loadRoute(route: List<Pair<Double, Double>>) {
        val current = dataBroker.marineState.value
        routeTracker.loadRoute(route, current.latitude, current.longitude)
    }

    fun clearRoute() {
        routeTracker.clearRoute()
    }

    fun getNextWaypoint(): Pair<Double, Double>? = routeTracker.getNextWaypoint()
    fun getSecondNextWaypoint(): Pair<Double, Double>? = routeTracker.getSecondNextWaypoint()
    fun getRoutePoints(): List<Pair<Double, Double>> = routeTracker.getRoutePoints()

    fun setAutoSeaStateEnabled(enabled: Boolean) {
        dataBroker.updateState { it.copy(isAutoSeaStateEnabled = enabled) }
        notifyListeners(dataBroker.marineState.value)
    }

    fun setMobActive(active: Boolean, lat: Double? = null, lon: Double? = null) {
        dataBroker.updateState { it.copy(isMobActive = active, mobLatitude = lat, mobLongitude = lon) }
        notifyListeners(dataBroker.marineState.value)
    }

    fun setShunted(shunted: Boolean) {
        dataBroker.updateState { it.copy(isShunted = shunted) }
        notifyListeners(dataBroker.marineState.value)
    }

    fun onInternalLocationUpdate(loc: net.osmand.Location?) {
        if (loc == null) return
        val state = dataBroker.marineState.value
        val currentStatus = state.connectionStatus

        if (currentStatus == ConnectionStatus.CONNECTED && state.hasValidFix) return

        if (currentStatus == ConnectionStatus.CONNECTED) {
            log.info("SignalK: External GPS missing or stale while CONNECTED. Engaging Internal GPS fallback.")
        }

        dataBroker.setDeadReckoningActive(false)

        val now = TemporalUtils.now()
        val lat = loc.latitude
        val lon = loc.longitude
        val sog = loc.speed.toDouble()
        val cog = Math.toRadians(loc.bearing.toDouble())

        dataBroker.updateState { s ->
            val newTimestamps = s.timestamps.toMutableMap()
            newTimestamps["navigation.position"] = now
            newTimestamps["navigation.speedOverGround"] = now
            newTimestamps["navigation.courseOverGroundTrue"] = now

            addTrajectoryPoint(lat, lon)
            historyManager.getBuffer("navigation.speedOverGround").add(Pair(sog, now))
            historyManager.getBuffer("navigation.courseOverGroundTrue").add(Pair(cog, now))

            val stateWithPos = s.copy(
                latitude = lat,
                longitude = lon,
                courseOverGroundTrue = cog,
                timestamps = newTimestamps
            )
            dataBroker.applySpeedOverGroundUpdate(stateWithPos, sog, now)
        }

        val finalState = dataBroker.marineState.value
        val effectiveStw = if (finalState.isStwUnreliable) sog else finalState.speedThroughWater

        val perfData = LivePerformanceData(
            speedThroughWater = effectiveStw,
            windSpeedTrue = finalState.windSpeedTrue,
            windAngleTrueWater = finalState.trueWindAngle,
            speedOverGround = sog,
            courseOverGround = cog,
            latitude = lat,
            longitude = lon,
            headingTrue = finalState.headingTrue,
            headingMagnetic = finalState.headingMagnetic,
            magneticVariation = finalState.magneticVariation,
            leeway = finalState.leeway,
            depthBelowTransducer = finalState.depthBelowTransducer,
            polarSpeed = finalState.polarTargetSpeed,
            targetAngle = finalState.targetWindAngleApparent,
            polarSpeedRatio = finalState.polarSpeedRatio,
            roll = finalState.roll,
            pitch = finalState.pitch,
            windAngleApparent = finalState.windDirectionApparent,
            windSpeedApparent = finalState.windSpeedApparent,
            destinationLatitude = getNextWaypoint()?.first,
            destinationLongitude = getNextWaypoint()?.second,
            lastWaypointLatitude = routeTracker.lastWaypointLat,
            lastWaypointLongitude = routeTracker.lastWaypointLon,
            distanceToWaypoint = finalState.distanceToWaypoint,
            drift = finalState.drift,
            setTrue = finalState.setTrue,
            timestamp = now
        )

        dataBroker.updatePerformanceData(perfData)
        SailingDependencyContainer.nmeaMultiplexer?.aggregator?.handleLivePerformanceData(perfData)

        scheduleUiNotification(finalState)
    }

    fun updatePendingCommand(targetHeading: Double? = null, mode: String? = null, path: String? = null) {
        dataBroker.updateState { current ->
            current.copy(
                pendingTargetHeading = targetHeading,
                pendingAutopilotState = mode,
                pendingCommandPath = path,
                commandSentTimestamp = if (targetHeading != null || mode != null || path != null) TemporalUtils.now() else 0
            )
        }
        notifyListeners(dataBroker.marineState.value)
    }

    private fun updatePulseLifecycle() {
        val current = dataBroker.marineState.value
        val anyAlarm = current.notifications.values.any { it.state == NotificationState.ALARM || it.state == NotificationState.EMERGENCY } || current.isActuatorOverloaded
        if (anyAlarm && (pulseJob?.isActive != true)) {
            pulseJob = engineScope.launch {
                while (isActive) {
                    _pulseFlow.value = !_pulseFlow.value
                    delay(500.milliseconds)
                }
            }
        } else if (!anyAlarm) {
            pulseJob?.cancel()
            pulseJob = null
            _pulseFlow.value = false
        }
    }
}
