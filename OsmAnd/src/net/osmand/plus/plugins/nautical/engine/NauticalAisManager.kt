package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
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
import java.util.Timer
import java.util.TimerTask

class NauticalAisManager(private val app: OsmandApplication) {

    private val log = PlatformUtil.getLog(NauticalAisManager::class.java)

    data class AisExtras(
        val threatLevel: Int = 0,
        val isMuted: Boolean = false,
        val isRemote: Boolean = false,
        val hasCpaWarning: Boolean = false,
    )

    private val aisExtras = Collections.synchronizedMap(mutableMapOf<Int, AisExtras>())

    private val aisObjectListCounterMax = 200
    private val objects = Collections.synchronizedMap(
        object : LinkedHashMap<Int, AisObject>(aisObjectListCounterMax, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<Int, AisObject>?): Boolean {
                val shouldRemove = size > aisObjectListCounterMax
                if (shouldRemove && (eldest != null)) {
                    _aisEvents.tryEmit(AisEvent.Removed(eldest.value))
                    listeners.forEach { it.onAisObjectRemoved(eldest.value) }
                }
                return shouldRemove
            }
        },
    )
    private var cleanupTimer: Timer? = null
    private var cpaTimer: Timer? = null

    private var lastCpaExecutionTime: Long = 0
    private var isCpaOffloaded = false

    sealed class AisEvent {
        data class Updated(val obj: AisObject) : AisEvent()
        data class Removed(val obj: AisObject) : AisEvent()
    }

    private val _aisEvents = MutableSharedFlow<AisEvent>(extraBufferCapacity = 64)
    val aisEvents = _aisEvents.asSharedFlow()

    interface AisObjectListener {
        fun onAisObjectReceived(ais: AisObject)
        fun onAisObjectRemoved(ais: AisObject)
    }

    private val listeners = java.util.concurrent.CopyOnWriteArraySet<AisObjectListener>()

    fun addListener(listener: AisObjectListener) = listeners.add(listener)
    fun removeListener(listener: AisObjectListener) = listeners.remove(listener)

    fun startUpdates() {
        reinitTimer()
        observeCapabilities()
    }

    private fun observeCapabilities() {
        val plugin = NauticalPlugin.getInstance()
        val capsFlow = plugin?.capabilityManager?.capabilities
        if (capsFlow != null) {
            val scope = plugin.pluginScope
            scope?.launch {
                capsFlow.collect { caps ->
                    val offload = caps.hasAisPrioritizer || caps.hasAdvancedSafety
                    if (offload != isCpaOffloaded) {
                        isCpaOffloaded = offload
                        if (isCpaOffloaded) {
                            log.info("Nautical: AIS CPA calculation offloaded to server. Stopping local timer.")
                            cpaTimer?.cancel()
                            cpaTimer = null
                        } else if (cleanupTimer != null) { // Only restart if updates are active
                            log.info("Nautical: Resuming local AIS CPA calculation.")
                            startCpaTimer()
                        }
                    }
                }
            }
        }
    }

    private fun startCpaTimer() {
        cpaTimer?.cancel()
        cpaTimer = Timer("AisCpaTimer").apply {
            schedule(
                object : TimerTask() {
                    override fun run() {
                        updateAllCpa()
                    }
                },
                5000,
                10000,
            )
        }
    }

    fun stopUpdates() {
        deinitTimer()
    }

    private fun initTimer() {
        cleanupTimer = Timer("AisCleanupTimer").apply {
            schedule(
                object : TimerTask() {
                    override fun run() {
                        removeLostObjects()
                    }
                },
                20000,
                30000,
            )
        }

        if (!isCpaOffloaded) {
            startCpaTimer()
        }
    }

    private fun deinitTimer() {
        cleanupTimer?.cancel()
        cleanupTimer = null
        cpaTimer?.cancel()
        cpaTimer = null
    }

    private fun reinitTimer() {
        deinitTimer()
        initTimer()
    }

    fun cleanupResources() {
        deinitTimer()
        objects.clear()
    }

    fun onAisObjectReceived(ais: AisObject) {
        val mmsi = ais.mmsi
        val obj = objects.getOrPut(mmsi) { AisObject(ais) }
        if (obj !== ais) {
            obj.set(ais)
        }

        _aisEvents.tryEmit(AisEvent.Updated(obj))
        listeners.forEach { it.onAisObjectReceived(obj) }
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

        val iterator = objects.entries.iterator()
        while (iterator.hasNext()) {
            val obj = iterator.next().value
            var pruneTimeout = basePruneTimeout
            
            val isClassB = obj.msgTypes.any { (it == 18) || (it == 19) || (it == 24) }
            if (isClassB) {
                pruneTimeout = kotlin.math.max(pruneTimeout, classBPruneTimeout)
            }

            if (obj.isLost(pruneTimeout)) {
                log.debug("Remove AIS object with MMSI ${obj.mmsi}")
                iterator.remove()
                _aisEvents.tryEmit(AisEvent.Removed(obj))
                listeners.forEach { it.onAisObjectRemoved(obj) }
            }
        }
    }



    private fun updateAllCpa() {
        val ownPosition = app.locationProvider.lastKnownLocation ?: return
        val now = System.currentTimeMillis()
        
        // Simulating the optimization from AisTrackerPlugin
        val isBackground = !app.settings.MAP_ACTIVITY_ENABLED
        val isMoving = ownPosition.hasSpeed() && (ownPosition.speed > 0.5f)

        val interval = if (isBackground) 30000L else (if (isMoving) 5000L else 10000L)
        if ((now - lastCpaExecutionTime) < interval) {
             return // Skip for battery optimization
        }
        lastCpaExecutionTime = now

        val ownAisLocation = ownPosition.toAisLocation()
        val plugin: NauticalPlugin = NauticalPlugin.getInstance() ?: return
        val cpaWarningTime = plugin.aisCpaWarningTime.get()
        val cpaWarningDistance = plugin.aisCpaWarningDistance.get()
        var anyDanger = false

        for (obj in objects.values) {
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
                    if (tcpa > 0 && obj.cpa.cpa <= cpaWarningDistance.toDouble() && (tcpa * 60) <= cpaWarningTime.toDouble()) {
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
