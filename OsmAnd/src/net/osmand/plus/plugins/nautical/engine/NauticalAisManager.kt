package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.sample
import net.osmand.Location
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.shared.aistracker.AisLocation
import net.osmand.shared.aistracker.AisObject
import net.osmand.shared.aistracker.AisObjectConstants
import net.osmand.shared.aistracker.AisTrackerMath
import java.util.Collections
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NauticalAisManager(private val app: OsmandApplication) {

    private val log = PlatformUtil.getLog(NauticalAisManager::class.java)
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    data class AisExtras(
        val threatLevel: Int = 0,
        val isMuted: Boolean = false,
        val isRemote: Boolean = false,
        val hasCpaWarning: Boolean = false,
    )

    private val aisExtras = Collections.synchronizedMap(mutableMapOf<Int, AisExtras>())

    private val aisObjectListCounterMax = 500
    private val objects = Collections.synchronizedMap(
        object : LinkedHashMap<Int, AisObject>(aisObjectListCounterMax, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<Int, AisObject>?): Boolean {
                val shouldRemove = size > aisObjectListCounterMax
                if (shouldRemove && (eldest != null)) {
                    val mmsi = eldest.key
                    aisExtras.remove(mmsi)
                    _aisEvents.tryEmit(AisEvent.Removed(eldest.value))
                    listeners.forEach { it.onAisObjectRemoved(eldest.value) }
                }
                return shouldRemove
            }
        },
    )
    private var cleanupJob: Job? = null
    private var cpaJob: Job? = null

    private var lastCpaExecutionTime: Long = 0
    private var isCpaOffloaded = false

    sealed class AisEvent {
        data class Updated(val obj: AisObject) : AisEvent()
        data class Removed(val obj: AisObject) : AisEvent()
    }

    private val _aisEvents = MutableSharedFlow<AisEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val aisEvents = _aisEvents.asSharedFlow()

    private val batchUpdateFlow = MutableSharedFlow<AisObject>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        managerScope.launch {
            // Task: AIS Notification Throttling (Batching updates to 2Hz)
            batchUpdateFlow
                .sample(500.milliseconds)
                .collect { obj ->
                    listeners.forEach { it.onAisObjectReceived(obj) }
                }
        }
    }

    interface AisObjectListener {
        fun onAisObjectReceived(ais: AisObject)
        fun onAisObjectRemoved(ais: AisObject)
    }

    private val listeners = java.util.concurrent.CopyOnWriteArraySet<AisObjectListener>()

    fun addListener(listener: AisObjectListener) = listeners.add(listener)
    fun removeListener(listener: AisObjectListener) = listeners.remove(listener)

    fun startUpdates() {
        startLoops()
        observeCapabilities()
    }

    private fun observeCapabilities() {
        val plugin = NauticalPlugin.getInstance()
        val capsFlow = plugin?.capabilityManager?.capabilities
        if (capsFlow != null) {
            managerScope.launch {
                capsFlow.collect { caps ->
                    val offload = caps.hasAisPrioritizer || caps.hasAdvancedSafety
                    if (offload != isCpaOffloaded) {
                        isCpaOffloaded = offload
                        if (isCpaOffloaded) {
                            log.info("Nautical: AIS CPA calculation offloaded to server. Stopping local loop.")
                            cpaJob?.cancel()
                            cpaJob = null
                        } else {
                            log.info("Nautical: Resuming local AIS CPA calculation.")
                            startCpaLoop()
                        }
                    }
                }
            }
        }
    }

    private fun startCpaLoop() {
        cpaJob?.cancel()
        cpaJob = managerScope.launch {
            delay(5.seconds)
            while (isActive) {
                updateAllCpa()
                
                // Adaptive Interval: Speed-dependent
                val ownPosition = app.locationProvider.lastKnownLocation
                val speedMs = ownPosition?.speed ?: 0.0f
                val delaySec = when {
                    speedMs > 2.5f -> 5L  // > 5 kts: 5s
                    speedMs > 1.0f -> 10L // > 2 kts: 10s
                    else -> 20L           // Stationary: 20s
                }
                delay(delaySec.seconds)
            }
        }
    }

    fun stopUpdates() {
        cleanupJob?.cancel()
        cleanupJob = null
        cpaJob?.cancel()
        cpaJob = null
    }

    private fun startLoops() {
        cleanupJob?.cancel()
        cleanupJob = managerScope.launch {
            delay(20.seconds)
            while (isActive) {
                removeLostObjects()
                delay(30.seconds)
            }
        }

        if (!isCpaOffloaded) {
            startCpaLoop()
        }
    }

    fun cleanupResources() {
        stopUpdates()
        managerScope.cancel()
        objects.clear()
    }

    fun onAisObjectReceived(ais: AisObject) {
        val mmsi = ais.mmsi
        val obj = objects.getOrPut(mmsi) { AisObject(ais) }
        if (obj !== ais) {
            obj.set(ais)
        }

        _aisEvents.tryEmit(AisEvent.Updated(obj))
        batchUpdateFlow.tryEmit(obj)
    }

    fun updateAisThreatLevel(mmsi: Int, level: Int) {
        val current = aisExtras.getOrPut(mmsi) { AisExtras() }
        if (current.threatLevel != level) {
            aisExtras[mmsi] = current.copy(threatLevel = level)
            objects[mmsi]?.let { obj ->
                _aisEvents.tryEmit(AisEvent.Updated(obj))
                listeners.forEach { it.onAisObjectReceived(obj) }
            }
        }
    }

    fun muteAisTarget(mmsi: Int, mute: Boolean) {
        val current = aisExtras.getOrPut(mmsi) { AisExtras() }
        aisExtras[mmsi] = current.copy(isMuted = mute)
    }

    fun markRemoteVessel(mmsi: Int) {
        val current = aisExtras.getOrPut(mmsi) { AisExtras() }
        if (!current.isRemote) {
            aisExtras[mmsi] = current.copy(isRemote = true)
        }
    }

    fun getAisExtras(mmsi: Int): AisExtras = aisExtras[mmsi] ?: AisExtras()

    fun getAisObjects(): List<AisObject> = objects.values.toList()

    fun removeLostObjects() {
        val plugin = NauticalPlugin.getInstance() ?: return
        val basePruneTimeout = plugin.aisObjLostTimeout.get()
        val classBPruneTimeout = 18

        synchronized(objects) {
            val iterator = objects.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val obj = entry.value
                var pruneTimeout = basePruneTimeout

                val isClassB = obj.msgTypes.any { (it == 18) || (it == 19) || (it == 24) }
                if (isClassB) {
                    pruneTimeout = kotlin.math.max(pruneTimeout, classBPruneTimeout)
                }

                if (obj.isLost(pruneTimeout)) {
                    log.debug("Remove AIS object with MMSI ${obj.mmsi}")
                    val mmsi = entry.key
                    aisExtras.remove(mmsi)
                    iterator.remove()
                    _aisEvents.tryEmit(AisEvent.Removed(obj))
                    listeners.forEach { it.onAisObjectRemoved(obj) }
                }
            }
        }
    }



    private fun updateAllCpa() = managerScope.launch(Dispatchers.Default) {
        val ownPosition = app.locationProvider.lastKnownLocation ?: return@launch
        val now = System.currentTimeMillis()
        
        // Simulating the optimization from AisTrackerPlugin
        val isBackground = !app.settings.MAP_ACTIVITY_ENABLED
        val isMoving = ownPosition.hasSpeed() && (ownPosition.speed > 0.5f)

        val interval = if (isBackground) 30000L else (if (isMoving) 5000L else 10000L)
        if ((now - lastCpaExecutionTime) < interval) {
             return@launch // Skip for battery optimization
        }
        lastCpaExecutionTime = now

        val ownAisLocation = ownPosition.toAisLocation()
        val plugin: NauticalPlugin = NauticalPlugin.getInstance() ?: return@launch
        val cpaWarningTime = plugin.aisCpaWarningTime.get()
        val cpaWarningDistance = plugin.aisCpaWarningDistance.get()
        var anyDanger = false

        val objectsToProcess = synchronized(objects) { objects.values.toList() }

        for (obj in objectsToProcess) {
            yield() // Yield to prevent long blocking of Default thread if list is huge
            if (!obj.isMovable() || (obj.objectClass == net.osmand.shared.aistracker.AisObjType.AIS_AIRPLANE)) {
                obj.cpa.reset()
                continue
            }
            
            // Anchorage filtering: navStatus 1 (At anchor) or 5 (Moored)
            // Skip ONLY if both vessels are stationary to ensure we detect collisions with stationary hazards while underway.
            val isOtherStationary = ((obj.navStatus == 1) || (obj.navStatus == 5)) || (obj.sog <= AisObjectConstants.SPEED_CONSIDERED_IN_REST)
            val isOwnStationary = !isMoving
            
            if (isOtherStationary && isOwnStationary) {
                obj.cpa.reset()
                continue
            }

            val otherLoc = obj.getExtrapolatedLocation(now)
            if (otherLoc != null) {
                AisTrackerMath.getCpa(ownAisLocation, otherLoc, obj.cpa)
                var danger = false
                if (obj.cpa.valid) {
                    val tcpa = obj.cpa.tcpa
                    val tcpaSeconds = tcpa * 3600.0
                    if (tcpa > 0 && obj.cpa.cpa <= cpaWarningDistance.toDouble() && tcpaSeconds <= cpaWarningTime.toDouble()) {
                        if (obj.cpa.t1 >= 0 && obj.cpa.t2 >= 0) {
                            danger = true
                            anyDanger = true
                        }
                    }
                }
                
                val currentExtras = aisExtras.getOrPut(obj.mmsi) { AisExtras() }
                if (currentExtras.hasCpaWarning != danger) {
                    aisExtras[obj.mmsi] = currentExtras.copy(hasCpaWarning = danger)
                    _aisEvents.tryEmit(AisEvent.Updated(obj))
                }
            }
        }

        if (anyDanger) {
            NauticalAudioArbiter.getInstance(app).dispatchAlarm(
                AlarmType.COLLISION_DANGER,
                voiceText = app.getString(R.string.nautical_collision_danger),
            )
        } else {
            NauticalAudioArbiter.getInstance(app).stopAlarm(AlarmType.COLLISION_DANGER)
        }
    }

    private fun Location.toAisLocation() = AisLocation(
        latitude = latitude,
        longitude = longitude,
        speed = speed,
        bearing = bearing,
        hasSpeed = hasSpeed(),
        hasBearing = hasBearing(),
    )
}
