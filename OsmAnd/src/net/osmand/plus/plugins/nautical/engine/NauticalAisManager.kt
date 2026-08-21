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
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import net.osmand.shared.aistracker.AisDataListener
import net.osmand.shared.aistracker.AisLocation
import net.osmand.shared.aistracker.AisObject
import net.osmand.shared.aistracker.AisObjectConstants
import net.osmand.shared.aistracker.AisTrackerMath
import org.json.JSONObject
import java.util.Collections
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NauticalAisManager(private val app: OsmandApplication) : AisDataListener {

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

    fun updateVessel(context: String, updateObj: JSONObject) {
        val numericMmsi = resolveMmsiFromContext(context) ?: return
        val target = objects.getOrPut(numericMmsi) {
            val type = context.substringBefore(".")
            val msgType = when (type) {
                "aircraft" -> 9
                "atons" -> 21
                "sar" -> 14
                else -> 1 // Default vessel
            }
            AisObject(numericMmsi, msgType, AisObjectConstants.INVALID_LAT, AisObjectConstants.INVALID_LON)
        }

        // Source Detection (APRS / Meshtastic)
        val sourceObj = updateObj.optJSONObject("source")
        val sourceStr = if (sourceObj != null) {
            sourceObj.optString("label", sourceObj.optString("src", ""))
        } else {
            updateObj.optString("source", "")
        }
        if (sourceStr.lowercase(Locale.US).contains("aprs") || sourceStr.lowercase(Locale.US).contains("meshtastic")) {
            markRemoteVessel(numericMmsi)
        }

        val values = updateObj.optJSONArray("values")
        val timestampStr = updateObj.optString("timestamp", "")
        val now = if (timestampStr.isNotEmpty()) TemporalUtils.parseIso8601(timestampStr) else System.currentTimeMillis()

        if (values != null) {
            for (i in 0 until values.length()) {
                val valObj = values.getJSONObject(i)
                val path = valObj.optString("path")
                val value = valObj.opt("value")
                if (path == SignalKPaths.AIS_THREAT_LEVEL) {
                    val level = (value as? Number)?.toInt() ?: 0
                    updateAisThreatLevel(numericMmsi, level)
                } else {
                    updateAisTargetInternal(target, path, value, now)
                }
            }
        }

        onAisObjectReceived(target)
    }

    internal fun resolveMmsiFromContext(context: String): Int? {
        val type = context.substringBefore(".")
        val rawId = context.substringAfter("$type.", "")
        if (rawId.isEmpty()) return null

        return if (rawId.startsWith("urn:mrn:imo:mmsi:")) {
            rawId.substringAfter("urn:mrn:imo:mmsi:").toIntOrNull() ?: (rawId.hashCode().absoluteValue % 1000000000)
        } else if (rawId.startsWith("urn:mrn:signalk:uuid:")) {
            (rawId.hashCode() and 0x7FFFFFFF)
        } else {
            rawId.toIntOrNull() ?: (rawId.hashCode().absoluteValue % 1000000000)
        }
    }

    private fun updateAisTargetInternal(target: AisObject, path: String, valueObj: Any?, now: Long) {
        // Temporal check to prevent out-of-order AIS updates from overwriting newer data
        if (now > 0 && now < target.lastUpdate) {
            return
        }

        val effectiveMsgType = if (target.msgType != 0) target.msgType else 1
        val currentLat = target.position?.latitude ?: Double.NaN
        val currentLon = target.position?.longitude ?: Double.NaN

        when (path) {
            "" -> {
                var name = ""
                var vName = ""
                var type = -1
                if (valueObj is JSONObject) {
                    name = valueObj.optString("name", "")
                    vName = valueObj.optString("vesselName", "")
                    type = valueObj.optInt("vesselType", -1)
                } else if (valueObj is Map<*, *>) {
                    name = valueObj["name"]?.toString() ?: ""
                    vName = valueObj["vesselName"]?.toString() ?: ""
                    type = (valueObj["vesselType"] as? Number)?.toInt() ?: -1
                }
                val shipName = name.ifEmpty { vName.ifEmpty { target.shipName } }
                val updated = AisObject(
                    target.mmsi, effectiveMsgType,
                    target.imo, target.callSign, shipName,
                    if (type != -1) type else target.shipType,
                    target.dimensionToBow, target.dimensionToStern,
                    target.dimensionToPort, target.dimensionToStarboard,
                    target.draught, target.destination,
                    target.etaMon, target.etaDay, target.etaHour, target.etaMin
                )
                target.set(updated)
                target.lastUpdate = now
            }
            "name", "vesselName" -> {
                val shipName = valueObj?.toString()
                val updated = AisObject(
                    target.mmsi, effectiveMsgType,
                    target.imo, target.callSign, shipName,
                    target.shipType,
                    target.dimensionToBow, target.dimensionToStern,
                    target.dimensionToPort, target.dimensionToStarboard,
                    target.draught, target.destination,
                    target.etaMon, target.etaDay, target.etaHour, target.etaMin
                )
                target.set(updated)
                target.lastUpdate = now
            }
            "design.type" -> {
                val type = when (valueObj) {
                    is JSONObject -> valueObj.optInt("id", -1)
                    is Map<*, *> -> (valueObj["id"] as? Number)?.toInt() ?: -1
                    is Number -> valueObj.toInt()
                    else -> -1
                }
                if (type != -1) {
                    val updated = AisObject(
                        target.mmsi, effectiveMsgType,
                        target.imo, target.callSign, target.shipName,
                        type,
                        target.dimensionToBow, target.dimensionToStern,
                        target.dimensionToPort, target.dimensionToStarboard,
                        target.draught, target.destination,
                        target.etaMon, target.etaDay, target.etaHour, target.etaMin
                    )
                    target.set(updated)
                    target.lastUpdate = now
                }
            }
            SignalKPaths.NAV_POSITION -> {
                var lat = Double.NaN
                var lon = Double.NaN
                if (valueObj is JSONObject) {
                    lat = valueObj.optDouble("latitude", Double.NaN)
                    lon = valueObj.optDouble("longitude", Double.NaN)
                } else if (valueObj is Map<*, *>) {
                    lat = (valueObj["latitude"] as? Number)?.toDouble() ?: Double.NaN
                    lon = (valueObj["longitude"] as? Number)?.toDouble() ?: Double.NaN
                }
                if (MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
                    val updated = AisObject(
                        target.mmsi, effectiveMsgType, target.timeStamp, target.navStatus, target.manInd,
                        target.heading, target.cog, target.sog, lat, lon, target.rot
                    )
                    target.set(updated)
                    target.lastUpdate = now
                }
            }
            SignalKPaths.NAV_SPEED_OVER_GROUND -> {
                val sogMs = (valueObj as? Number)?.toDouble() ?: Double.NaN
                if (MarineStateConstants.isValidSpeed(sogMs)) {
                    val sogKnots = SignalKUnitConverter.msToKnots(sogMs)
                    val lat = if (MarineStateConstants.isValidLat(currentLat)) currentLat else AisObjectConstants.INVALID_LAT
                    val lon = if (MarineStateConstants.isValidLon(currentLon)) currentLon else AisObjectConstants.INVALID_LON
                    val updated = AisObject(
                        target.mmsi, effectiveMsgType, target.timeStamp, target.navStatus, target.manInd, target.heading,
                        target.cog, sogKnots, lat, lon, target.rot
                    )
                    target.set(updated)
                    target.lastUpdate = now
                }
            }
            SignalKPaths.NAV_COURSE_OVER_GROUND -> {
                val cogRad = (valueObj as? Number)?.toDouble() ?: Double.NaN
                if (!cogRad.isNaN()) {
                    val cogDeg = SignalKUnitConverter.radToDeg(cogRad)
                    val lat = if (MarineStateConstants.isValidLat(currentLat)) currentLat else AisObjectConstants.INVALID_LAT
                    val lon = if (MarineStateConstants.isValidLon(currentLon)) currentLon else AisObjectConstants.INVALID_LON
                    val updated = AisObject(
                        target.mmsi, effectiveMsgType, target.timeStamp, target.navStatus, target.manInd, target.heading,
                        cogDeg, target.sog, lat, lon, target.rot
                    )
                    target.set(updated)
                    target.lastUpdate = now
                }
            }
            SignalKPaths.NAV_HEADING_TRUE -> {
                val hdgRad = (valueObj as? Number)?.toDouble() ?: Double.NaN
                if (!hdgRad.isNaN()) {
                    val hdgDeg = SignalKUnitConverter.radToDeg(hdgRad).toInt()
                    val lat = if (MarineStateConstants.isValidLat(currentLat)) currentLat else AisObjectConstants.INVALID_LAT
                    val lon = if (MarineStateConstants.isValidLon(currentLon)) currentLon else AisObjectConstants.INVALID_LON
                    val updated = AisObject(
                        target.mmsi, effectiveMsgType, target.timeStamp, target.navStatus, target.manInd, hdgDeg,
                        target.cog, target.sog, lat, lon, target.rot
                    )
                    target.set(updated)
                    target.lastUpdate = now
                }
            }
            SignalKPaths.NAV_DESTINATION -> {
                val dest = valueObj?.toString()
                val updated = AisObject(
                    target.mmsi, effectiveMsgType,
                    target.imo, target.callSign, target.shipName,
                    target.shipType,
                    target.dimensionToBow, target.dimensionToStern,
                    target.dimensionToPort, target.dimensionToStarboard,
                    target.draught, dest,
                    target.etaMon, target.etaDay, target.etaHour, target.etaMin
                )
                target.set(updated)
                target.lastUpdate = now
            }
            SignalKPaths.NAV_STATE -> {
                val statusStr = valueObj?.toString() ?: ""
                val status = when (statusStr.lowercase(Locale.US)) {
                    "under way using engine", "motoring" -> 0
                    "at anchor" -> 1
                    "not under command" -> 2
                    "restricted manoeuverability" -> 3
                    "constrained by her draught" -> 4
                    "moored" -> 5
                    "aground" -> 6
                    "engaged in fishing" -> 7
                    "under way sailing", "sailing" -> 8
                    else -> target.navStatus
                }
                val lat = if (MarineStateConstants.isValidLat(currentLat)) currentLat else AisObjectConstants.INVALID_LAT
                val lon = if (MarineStateConstants.isValidLon(currentLon)) currentLon else AisObjectConstants.INVALID_LON
                val updated = AisObject(
                    target.mmsi, effectiveMsgType, target.timeStamp, status, target.manInd, target.heading,
                    target.cog, target.sog, lat, lon, target.rot
                )
                target.set(updated)
                target.lastUpdate = now
            }
        }
    }

    override fun onAisObjectReceived(ais: AisObject) {
        val mmsi = ais.mmsi
        val obj = objects.getOrPut(mmsi) { AisObject(ais) }
        if (obj !== ais) {
            obj.set(ais)
        }

        _aisEvents.tryEmit(AisEvent.Updated(obj))
        batchUpdateFlow.tryEmit(obj)
        app.runInUIThread {
            app.osmandMap?.refreshMap()
        }
        NauticalPlugin.getInstance()?.requestRefresh()
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

    fun getAisObject(mmsi: Int): AisObject? = objects[mmsi]

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
        val state = NauticalPlugin.engine?.getCurrentState() ?: return@launch
        if (!state.hasValidFix) return@launch
        
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
