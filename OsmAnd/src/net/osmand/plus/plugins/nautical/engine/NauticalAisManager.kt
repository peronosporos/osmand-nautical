package net.osmand.plus.plugins.nautical.engine

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

enum class AisSortMode {
    THREAT_CPA,
    DISTANCE,
    NAME_ALPHA
}

data class AisTargetSummary(
    val mmsi: Int,
    val name: String,
    val type: String,
    val rangeMeters: Double,
    val bearingDeg: Double,
    val sogKnots: Double,
    val cogDeg: Double,
    val cpaNm: Double?,
    val tcpaSec: Double?,
    val isDangerous: Boolean,
    val isMuted: Boolean,
    val callSign: String? = null,
    val lat: Double = Double.NaN,
    val lon: Double = Double.NaN,
)

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
    private var lastMapRefreshTime: Long = 0
    private val minMapRefreshIntervalMs = 500L

    private fun requestThrottledMapRefresh() {
        val pm = app.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (pm?.isInteractive == false) return // Suppress if screen is off
        val mapActivity = app.osmandMap?.mapView?.mapActivity
        if (mapActivity == null || mapActivity.isActivityDestroyed || mapActivity.isFinishing) return // Suppress if map activity is not active
        val now = System.currentTimeMillis()
        if (now - lastMapRefreshTime >= minMapRefreshIntervalMs) {
            lastMapRefreshTime = now
            app.runInUIThread {
                app.osmandMap?.refreshMap()
            }
        }
    }

    sealed class AisEvent {
        data class Updated(val obj: AisObject) : AisEvent()
        data class Removed(val obj: AisObject) : AisEvent()
    }

    private val _aisEvents = MutableSharedFlow<AisEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val aisEvents = _aisEvents.asSharedFlow()


    interface AisObjectListener {
        fun onAisObjectReceived(ais: AisObject)
        fun onAisObjectRemoved(ais: AisObject)
    }

    private val listeners = java.util.concurrent.CopyOnWriteArraySet<AisObjectListener>()

    fun addListener(listener: AisObjectListener) = listeners.add(listener)
    fun removeListener(listener: AisObjectListener) = listeners.remove(listener)

    fun startUpdates() {
        val sp = app.getSharedPreferences("nautical_buddies_pref", Context.MODE_PRIVATE)
        val local = sp.getStringSet("ais_buddies", emptySet()) ?: emptySet()
        val intBuddies = local.mapNotNull { it.toIntOrNull() }.toSet()
        if (intBuddies.isNotEmpty()) {
            NauticalPlugin.engine?.dataBroker?.updateState { it.copy(aisBuddies = intBuddies) }
        }
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
        val isNewTarget = !objects.containsKey(numericMmsi)
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
        val hadPosition = target.position != null

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

        val hasPosition = target.position != null
        val isFirstFix = (isNewTarget || !hadPosition) && hasPosition

        _aisEvents.tryEmit(AisEvent.Updated(target))
        listeners.forEach { it.onAisObjectReceived(target) }

        requestThrottledMapRefresh()
        NauticalPlugin.getInstance()?.requestRefresh()
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
                if (!shipName.isNullOrEmpty()) {
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
            "design.dimensions" -> {
                var bow = -1
                var stern = -1
                var port = -1
                var starboard = -1
                if (valueObj is JSONObject) {
                    bow = valueObj.optInt("bow", -1)
                    stern = valueObj.optInt("stern", -1)
                    port = valueObj.optInt("port", -1)
                    starboard = valueObj.optInt("starboard", -1)
                } else if (valueObj is Map<*, *>) {
                    bow = (valueObj["bow"] as? Number)?.toInt() ?: -1
                    stern = (valueObj["stern"] as? Number)?.toInt() ?: -1
                    port = (valueObj["port"] as? Number)?.toInt() ?: -1
                    starboard = (valueObj["starboard"] as? Number)?.toInt() ?: -1
                }
                if (bow != -1 || stern != -1 || port != -1 || starboard != -1) {
                    val updated = AisObject(
                        target.mmsi, effectiveMsgType,
                        target.imo, target.callSign, target.shipName,
                        target.shipType,
                        if (bow != -1) bow else target.dimensionToBow,
                        if (stern != -1) stern else target.dimensionToStern,
                        if (port != -1) port else target.dimensionToPort,
                        if (starboard != -1) starboard else target.dimensionToStarboard,
                        target.draught, target.destination,
                        target.etaMon, target.etaDay, target.etaHour, target.etaMin
                    )
                    target.set(updated)
                    target.lastUpdate = now
                }
            }
            SignalKPaths.NAV_CALLSIGN, "navigation.callsign" -> {
                val callSign = valueObj?.toString()
                if (!callSign.isNullOrEmpty()) {
                    val updated = AisObject(
                        target.mmsi, effectiveMsgType,
                        target.imo, callSign, target.shipName,
                        target.shipType,
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
                    recordBreadcrumb(target.mmsi, lat, lon)
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
                if (!dest.isNullOrEmpty()) {
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
                    else -> (valueObj as? Number)?.toInt() ?: target.navStatus
                }
                if (status != AisObjectConstants.INVALID_NAV_STATUS) {
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
    }

    override fun onAisObjectReceived(ais: AisObject) {
        val mmsi = ais.mmsi
        val hadPosition = objects[mmsi]?.position != null
        val isNew = !objects.containsKey(mmsi)
        val obj = objects.getOrPut(mmsi) { AisObject(ais) }
        if (obj !== ais) {
            obj.set(ais)
        }

        val hasPosition = obj.position != null
        val isFirstFix = (isNew || !hadPosition) && hasPosition
        if (hasPosition) {
            recordBreadcrumb(mmsi, obj.position!!.latitude, obj.position!!.longitude)
        }

        _aisEvents.tryEmit(AisEvent.Updated(obj))
        listeners.forEach { it.onAisObjectReceived(obj) }

        requestThrottledMapRefresh()
        NauticalPlugin.getInstance()?.requestRefresh()
    }

    fun updateAisThreatLevel(mmsi: Int, level: Int) {
        val current = aisExtras.getOrPut(mmsi) { AisExtras() }
        if (current.threatLevel != level) {
            aisExtras[mmsi] = current.copy(threatLevel = level)
            objects[mmsi]?.let { obj ->
                _aisEvents.tryEmit(AisEvent.Updated(obj))
                listeners.forEach { it.onAisObjectReceived(obj) }
                requestThrottledMapRefresh()
            }
        }
    }

    fun muteAisTarget(mmsi: Int, mute: Boolean) {
        val current = aisExtras.getOrPut(mmsi) { AisExtras() }
        aisExtras[mmsi] = current.copy(isMuted = mute)
    }

    fun toggleMute(mmsi: Int): Boolean {
        val current = aisExtras.getOrPut(mmsi) { AisExtras() }
        val newMute = !current.isMuted
        aisExtras[mmsi] = current.copy(isMuted = newMute)
        return newMute
    }

    fun markRemoteVessel(mmsi: Int) {
        val current = aisExtras.getOrPut(mmsi) { AisExtras() }
        if (!current.isRemote) {
            aisExtras[mmsi] = current.copy(isRemote = true)
        }
    }

    private val targetBreadcrumbs = Collections.synchronizedMap(mutableMapOf<Int, MutableList<Pair<Double, Double>>>())
    private val enabledTrackMmsis = Collections.synchronizedSet(mutableSetOf<Int>())

    fun recordBreadcrumb(mmsi: Int, lat: Double, lon: Double) {
        if (lat.isNaN() || lon.isNaN()) return
        val list = targetBreadcrumbs.getOrPut(mmsi) { mutableListOf() }
        synchronized(list) {
            if (list.isEmpty() || list.last() != (lat to lon)) {
                list.add(lat to lon)
                if (list.size > 200) {
                    list.removeAt(0)
                }
            }
        }
    }

    fun getBreadcrumbs(mmsi: Int): List<Pair<Double, Double>> {
        val list = targetBreadcrumbs[mmsi] ?: return emptyList()
        return synchronized(list) { list.toList() }
    }

    fun isTrackEnabled(mmsi: Int): Boolean = enabledTrackMmsis.contains(mmsi)

    fun setTrackEnabled(mmsi: Int, enabled: Boolean) {
        if (enabled) {
            enabledTrackMmsis.add(mmsi)
        } else {
            enabledTrackMmsis.remove(mmsi)
        }
        requestThrottledMapRefresh()
    }

    fun toggleTrack(mmsi: Int): Boolean {
        val newState = if (enabledTrackMmsis.contains(mmsi)) {
            enabledTrackMmsis.remove(mmsi)
            false
        } else {
            enabledTrackMmsis.add(mmsi)
            true
        }
        requestThrottledMapRefresh()
        return newState
    }

    fun isBuddy(mmsi: Int): Boolean {
        val engine = NauticalPlugin.engine
        val buddies = engine?.getCurrentState()?.aisBuddies ?: emptySet()
        if (buddies.contains(mmsi)) return true
        val sp = app.getSharedPreferences("nautical_buddies_pref", Context.MODE_PRIVATE)
        val local = sp.getStringSet("ais_buddies", emptySet()) ?: emptySet()
        return local.contains(mmsi.toString())
    }

    fun toggleBuddy(mmsi: Int): Boolean {
        val engine = NauticalPlugin.engine
        val sp = app.getSharedPreferences("nautical_buddies_pref", Context.MODE_PRIVATE)
        val local = (sp.getStringSet("ais_buddies", emptySet()) ?: emptySet()).toMutableSet()
        val current = (engine?.getCurrentState()?.aisBuddies ?: emptySet()).toMutableSet()
        current.addAll(local.mapNotNull { it.toIntOrNull() })

        val isNowBuddy = if (current.contains(mmsi)) {
            current.remove(mmsi)
            local.remove(mmsi.toString())
            false
        } else {
            current.add(mmsi)
            local.add(mmsi.toString())
            true
        }
        sp.edit().putStringSet("ais_buddies", local).apply()
        engine?.dataBroker?.updateState { it.copy(aisBuddies = current) }
        engine?.sendDelta(SignalKPaths.NAV_AIS_BUDDIES, current.toList())
        refreshAisAndMapLayers()
        return isNowBuddy
    }

    fun addBuddy(mmsi: Int): Boolean {
        val engine = NauticalPlugin.engine
        val sp = app.getSharedPreferences("nautical_buddies_pref", Context.MODE_PRIVATE)
        val local = (sp.getStringSet("ais_buddies", emptySet()) ?: emptySet()).toMutableSet()
        val current = (engine?.getCurrentState()?.aisBuddies ?: emptySet()).toMutableSet()
        current.addAll(local.mapNotNull { it.toIntOrNull() })
        current.add(mmsi)
        local.add(mmsi.toString())
        sp.edit().putStringSet("ais_buddies", local).apply()
        engine?.dataBroker?.updateState { it.copy(aisBuddies = current) }
        engine?.sendDelta(SignalKPaths.NAV_AIS_BUDDIES, current.toList())
        refreshAisAndMapLayers()
        return true
    }

    fun removeBuddy(mmsi: Int): Boolean {
        val engine = NauticalPlugin.engine
        val sp = app.getSharedPreferences("nautical_buddies_pref", Context.MODE_PRIVATE)
        val local = (sp.getStringSet("ais_buddies", emptySet()) ?: emptySet()).toMutableSet()
        val current = (engine?.getCurrentState()?.aisBuddies ?: emptySet()).toMutableSet()
        current.addAll(local.mapNotNull { it.toIntOrNull() })
        current.remove(mmsi)
        local.remove(mmsi.toString())
        sp.edit().putStringSet("ais_buddies", local).apply()
        engine?.dataBroker?.updateState { it.copy(aisBuddies = current) }
        engine?.sendDelta(SignalKPaths.NAV_AIS_BUDDIES, current.toList())
        refreshAisAndMapLayers()
        return false
    }

    fun refreshAisAndMapLayers() {
        app.runInUIThread {
            app.osmandMap?.refreshMap()
            NauticalPlugin.getInstance()?.requestRefresh()
        }
    }

    fun getAisExtras(mmsi: Int): AisExtras = aisExtras[mmsi] ?: AisExtras()

    fun getAisObjects(): List<AisObject> = objects.values.toList()

    fun getActiveTargets(sortMode: AisSortMode = AisSortMode.THREAT_CPA): List<AisTargetSummary> {
        val ownLoc = app.locationProvider.lastKnownLocation
        val plugin = NauticalPlugin.getInstance()
        val cpaDist = plugin?.aisCpaWarningDistance?.get()?.toDouble() ?: 1.0
        val cpaTime = plugin?.aisCpaWarningTime?.get()?.toDouble() ?: 900.0

        val rawObjects = synchronized(objects) { objects.values.toList() }
        val summaries = rawObjects.map { obj ->
            val extras = aisExtras[obj.mmsi] ?: AisExtras()
            val pos = obj.position
            val hasPos = pos != null && MarineStateConstants.isValidLat(pos.latitude) && MarineStateConstants.isValidLon(pos.longitude)
            val rangeMeters: Double
            val bearingDeg: Double

            if (ownLoc != null && hasPos) {
                val targetLoc = Location("AIS").apply {
                    latitude = pos!!.latitude
                    longitude = pos.longitude
                }
                rangeMeters = ownLoc.distanceTo(targetLoc).toDouble()
                bearingDeg = ((ownLoc.bearingTo(targetLoc) + 360f) % 360f).toDouble()
            } else {
                rangeMeters = Double.MAX_VALUE
                bearingDeg = 0.0
            }

            val sogKnots = if (obj.sog != AisObjectConstants.INVALID_SOG && obj.sog >= 0.0) obj.sog else 0.0
            val cogDeg = if (obj.cog != AisObjectConstants.INVALID_COG && obj.cog >= 0.0) obj.cog else 0.0

            val cpaNm: Double? = if (obj.cpa.valid) obj.cpa.cpa else null
            val tcpaSec: Double? = if (obj.cpa.valid) obj.cpa.tcpa * 3600.0 else null

            val isThreatTimeValid = obj.cpa.valid && obj.cpa.tcpa > 0 && obj.cpa.t1 >= 0 && obj.cpa.t2 >= 0
            val isThreatDistance = obj.cpa.valid && obj.cpa.cpa <= cpaDist
            val isThreatTime = (tcpaSec != null) && (tcpaSec <= cpaTime)
            val isDangerous = extras.hasCpaWarning || (obj.isMovable() && isThreatTimeValid && isThreatDistance && isThreatTime)

            val vesselName = obj.shipName?.trim().takeIf { !it.isNullOrEmpty() } ?: "MMSI: ${obj.mmsi}"
            val shipType = obj.getShipTypeString().takeIf { it.isNotEmpty() && it != "unknown" } ?: "Vessel"

            AisTargetSummary(
                mmsi = obj.mmsi,
                name = vesselName,
                type = shipType,
                rangeMeters = rangeMeters,
                bearingDeg = bearingDeg,
                sogKnots = sogKnots,
                cogDeg = cogDeg,
                cpaNm = cpaNm,
                tcpaSec = tcpaSec,
                isDangerous = isDangerous,
                isMuted = extras.isMuted,
                callSign = obj.callSign?.trim().takeIf { !it.isNullOrEmpty() },
                lat = pos?.latitude ?: Double.NaN,
                lon = pos?.longitude ?: Double.NaN,
            )
        }

        return when (sortMode) {
            AisSortMode.THREAT_CPA -> {
                summaries.sortedWith(
                    compareByDescending<AisTargetSummary> {
                        it.isDangerous || ((it.cpaNm ?: Double.MAX_VALUE) < 1.0 && (it.tcpaSec ?: -1.0) > 0)
                    }.thenBy {
                        it.tcpaSec?.takeIf { t -> t > 0 } ?: Double.MAX_VALUE
                    }.thenBy {
                        it.cpaNm ?: Double.MAX_VALUE
                    }.thenBy {
                        it.rangeMeters
                    }
                )
            }
            AisSortMode.DISTANCE -> {
                summaries.sortedBy { it.rangeMeters }
            }
            AisSortMode.NAME_ALPHA -> {
                summaries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }
        }
    }

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
                    requestThrottledMapRefresh()
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
