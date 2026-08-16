package net.osmand.plus.plugins.nautical.engine

import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.osmand.PlatformUtil
import net.osmand.data.LatLon
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.network.DeltaMessage
import net.osmand.plus.plugins.nautical.network.SignalKCourse
import net.osmand.plus.plugins.nautical.utils.TemporalUtils
import net.osmand.shared.aistracker.AisObject
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

class SignalKDeltaParser(
    private val app: OsmandApplication,
    private val dataBroker: SignalKDataBroker,
    private val historyManager: SignalKHistoryManager,
    private val routeTracker: SignalKRouteTracker,
    private val sessionManager: SignalKSessionManager,
    private val resourceManager: SignalKResourceManager,
    private val engineScope: CoroutineScope,
    private val routeStepListeners: CopyOnWriteArraySet<() -> Unit>,
    private val aisListenerProvider: () -> (((AisObject) -> Unit)?)
) {
    private val log = PlatformUtil.getLog(SignalKDeltaParser::class.java)

    suspend fun processJsonMessage(
        jsonMessage: String,
        initialState: MarineState,
        onSelfIdentity: (String) -> Unit
    ): Pair<MarineState, Boolean> {
        try {
            val jsonObject = JsonParser.parseString(jsonMessage).asJsonObject
            val context = jsonObject.get("context")?.asString
            val updates = jsonObject.getAsJsonArray("updates")
            Log.i("SignalKParser", "Context: $context | Updates count: ${updates?.size() ?: 0}")
        } catch (e: Exception) {
            Log.e("SignalKParser", "JSON parse error: ${e.message}")
        }
        val reader = JsonReader(StringReader(jsonMessage))
        var currentState = initialState
        var stateChanged = false
        try {
            reader.beginObject()
            var context: String? = null
            var isHello = false

            while (reader.hasNext()) {
                val name = reader.nextName()
                when (name) {
                    "self" -> {
                        val self = reader.nextString()
                        onSelfIdentity(self)
                        isHello = true
                    }
                    "context" -> context = reader.nextString()
                    "updates" -> {
                        if (isHello) {
                            reader.skipValue()
                        } else {
                            val res = processUpdates(reader, context ?: "vessels.self", currentState)
                            currentState = res.first
                            if (res.second) stateChanged = true
                        }
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        } catch (e: Exception) {
            val preview = if (jsonMessage.length > 100) jsonMessage.substring(0, 100) + "..." else jsonMessage
            log.error("JsonReader error: ${e.message}. Snippet: $preview", e)
        } finally {
            try { reader.close() } catch (_: Exception) {}
        }
        return Pair(currentState, stateChanged)
    }

    private suspend fun processUpdates(reader: JsonReader, context: String, initialState: MarineState): Pair<MarineState, Boolean> {
        val currentMmsi = initialState.vesselMmsi
        val trueSelf = sessionManager.trueSelfContext
        val isSelf = (context == "vessels.self") || (context == "") || (context == trueSelf) ||
                (currentMmsi != null && context == "vessels.urn:mrn:imo:mmsi:$currentMmsi")

        reader.beginArray()
        var currentState = initialState
        var stateUpdated = false

        while (reader.hasNext()) {
            val updateObj = readJsonValue(reader) as? JSONObject ?: continue

            // 1. Forward NMEA sentence updates to the NMEA Multiplexer
            val sourceObj = updateObj.optJSONObject("source")
            val sentence = sourceObj?.optString("sentence") ?: updateObj.optString("sentence")
            if (!sentence.isNullOrEmpty()) {
                val fullSentence = updateObj.optString("raw") ?: updateObj.optString("sentence")
                if (fullSentence.startsWith("$") || fullSentence.startsWith("!")) {
                    SailingDependencyContainer.nmeaMultiplexer?.injectSentence(fullSentence)
                }
            }

            // 3. Route AIS Contexts to AIS Manager
            if (context.startsWith("vessels.") && !isSelf) {
                val aisManager = NauticalPlugin.getInstance()?.aisManager
                aisManager?.updateVessel(context, updateObj)
                aisManager?.resolveMmsiFromContext(context)?.let { mmsi ->
                    aisManager.getAisObject(mmsi)?.let { obj ->
                        aisListenerProvider()?.invoke(obj)
                    }
                }
            }

            // 2. Parse values Array when Present
            val values = updateObj.optJSONArray("values")
            val timestampStr = updateObj.optString("timestamp", "")
            val updateTimestamp = if (timestampStr.isNotEmpty()) TemporalUtils.parseIso8601(timestampStr) else TemporalUtils.now()
            val sourceLabel = if (sourceObj != null) {
                sourceObj.optString("label", sourceObj.optString("src", sourceObj.toString()))
            } else {
                updateObj.optString("source")
            }

            if (values != null && isSelf) {
                for (j in 0 until values.length()) {
                    val valObj = values.getJSONObject(j)
                    val path = valObj.optString("path")
                    val value = valObj.opt("value")
                    if (path != null) {
                        val res = parseSelfValue(currentState, path, value, updateTimestamp, sourceLabel)
                        currentState = res.first
                        if (res.second) stateUpdated = true
                    }
                }
            }

            // Handle meta
            val meta = updateObj.optJSONArray("meta")
            if (meta != null && isSelf) {
                currentState = processMetaBatch(meta, currentState)
                stateUpdated = true
            }
        }
        reader.endArray()

        return Pair(currentState, stateUpdated)
    }

    suspend fun processDeltaUpdates(
        delta: DeltaMessage,
        context: String,
        onFinalizeAndNotify: (MarineState) -> Unit
    ) {
        val currentMmsi = dataBroker.marineState.value.vesselMmsi
        val trueSelf = sessionManager.trueSelfContext
        val isSelf = (context == "vessels.self") || (context == "") || (context == trueSelf) ||
                (currentMmsi != null && context == "vessels.urn:mrn:imo:mmsi:$currentMmsi")

        val updates = delta.updates ?: return
        var currentState = dataBroker.marineState.value
        var stateUpdated = false

        val aisManager = NauticalPlugin.getInstance()?.aisManager
        val routeToAis = context.startsWith("vessels.") && !isSelf

        for (update in updates) {
            val updateTimestamp = update.timestamp?.let {
                TemporalUtils.parseIso8601(it).takeIf { it > 0 }
            } ?: TemporalUtils.now()
            val updateSource = update.source?.get("label")?.toString()

            if (routeToAis && aisManager != null) {
                val updateObj = JSONObject()
                updateObj.put("timestamp", update.timestamp)
                val valuesArr = JSONArray()
                update.values?.forEach { v ->
                    val vObj = JSONObject()
                    vObj.put("path", v.path)
                    vObj.put("value", v.value)
                    valuesArr.put(vObj)
                }
                updateObj.put("values", valuesArr)
                aisManager.updateVessel(context, updateObj)

                aisManager.resolveMmsiFromContext(context)?.let { mmsi ->
                    aisManager.getAisObject(mmsi)?.let { obj ->
                        aisListenerProvider()?.invoke(obj)
                    }
                }
                continue
            }

            val values = update.values ?: continue
            for (v in values) {
                val path = v.path ?: continue
                val value = v.value

                if (isSelf) {
                    val res = parseSelfValue(currentState, path, value, updateTimestamp, updateSource)
                    currentState = res.first
                    if (res.second) stateUpdated = true
                }
            }
        }

        if (isSelf && stateUpdated) {
            onFinalizeAndNotify(currentState)
        }
    }

    private fun readJsonValue(reader: JsonReader): Any? {
        return when (reader.peek()) {
            JsonToken.NUMBER -> {
                val s = reader.nextString()
                s.toLongOrNull() ?: s.toDoubleOrNull() ?: s
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); null }
            JsonToken.BEGIN_OBJECT -> {
                val obj = JSONObject()
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    val v = readJsonValue(reader)
                    if (v != null) obj.put(key, v)
                }
                reader.endObject()
                obj
            }
            JsonToken.BEGIN_ARRAY -> {
                val arr = JSONArray()
                reader.beginArray()
                while (reader.hasNext()) {
                    arr.put(readJsonValue(reader))
                }
                reader.endArray()
                arr
            }
            else -> { reader.skipValue(); null }
        }
    }

    private fun processMetaBatch(metaArray: JSONArray, s: MarineState): MarineState {
        val currentMeta = s.pathMeta.toMutableMap()
        for (i in 0 until metaArray.length()) {
            val entry = metaArray.optJSONObject(i) ?: continue
            val path = entry.optString("path")
            val value = entry.optJSONObject("value")
            if (path.isNotEmpty() && value != null) {
                val metaMap = mutableMapOf<String, Any>()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    metaMap[key] = value.get(key)
                }
                currentMeta[path] = metaMap
            }
        }
        return s.copy(pathMeta = currentMeta)
    }

    fun parseSelfValue(s: MarineState, path: String, valueObj: Any?, now: Long, source: String?): Pair<MarineState, Boolean> {
        val newTimestamps = s.timestamps.toMutableMap()
        newTimestamps[path] = now
        val stateWithTs = s.copy(timestamps = newTimestamps)

        return when (path) {
            SignalKPaths.ENV_DEPTH_BELOW_TRANSDUCER -> processDepthBelowTransducer(stateWithTs, valueObj, now)
            SignalKPaths.NAV_HEADING_TRUE -> processHeadingTrue(stateWithTs, valueObj, now)
            SignalKPaths.NAV_HEADING_MAG -> processHeadingMag(stateWithTs, valueObj, now)
            SignalKPaths.NAV_SPEED_OVER_GROUND -> processSog(stateWithTs, valueObj, now)
            SignalKPaths.NAV_SPEED_THROUGH_WATER -> processStw(stateWithTs, valueObj, now)
            SignalKPaths.ENV_WIND_ANGLE_APPARENT -> processWindAngleApparent(stateWithTs, valueObj, now)
            SignalKPaths.ENV_WIND_SPEED_APPARENT -> processWindSpeedApparent(stateWithTs, valueObj, now)
            SignalKPaths.NAV_POSITION -> {
                var lat: Double? = null
                var lon: Double? = null
                if (valueObj is Map<*, *>) {
                    lat = (valueObj["latitude"] as? Number)?.toDouble()
                    lon = (valueObj["longitude"] as? Number)?.toDouble()
                } else if (valueObj is JSONObject) {
                    lat = valueObj.optDouble("latitude", Double.NaN).takeIf { !it.isNaN() }
                    lon = valueObj.optDouble("longitude", Double.NaN).takeIf { !it.isNaN() }
                }
                if (lat != null && lon != null && MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
                    routeTracker.updateFollowingState(lat, lon, null) {
                        routeStepListeners.forEach { it.invoke() }
                    }
                    historyManager.addTrajectoryPoint(lat, lon)
                    Pair(stateWithTs.copy(latitude = lat, longitude = lon, timeOfPositionFix = now), true)
                } else {
                    Pair(stateWithTs, false)
                }
            }
            SignalKPaths.NAV_MAG_VARIATION -> {
                val v = (valueObj as? Number)?.toDouble() ?: Double.NaN
                if (!v.isNaN()) {
                    Pair(stateWithTs.copy(magneticVariation = v), true)
                } else Pair(stateWithTs, false)
            }
            "navigation.gnss.antennaAltitude", "navigation.position.altitude" -> {
                val v = (valueObj as? Number)?.toDouble() ?: Double.NaN
                if (!v.isNaN()) {
                    Pair(stateWithTs.copy(altitude = v), true)
                } else Pair(stateWithTs, false)
            }
            "navigation.gnss.horizontalDilution" -> {
                val v = (valueObj as? Number)?.toDouble() ?: Double.NaN
                if (!v.isNaN()) {
                    val currentGnss = stateWithTs.gnss ?: GnssState()
                    Pair(stateWithTs.copy(gnss = currentGnss.copy(horizontalDilution = v)), true)
                } else Pair(stateWithTs, false)
            }
            "navigation.gnss.satellites" -> {
                val v = (valueObj as? Number)?.toInt() ?: -1
                if (v >= 0) {
                    val currentGnss = stateWithTs.gnss ?: GnssState()
                    Pair(stateWithTs.copy(gnss = currentGnss.copy(satellites = v)), true)
                } else Pair(stateWithTs, false)
            }
            SignalKPaths.NAV_COURSE_OVER_GROUND -> {
                val v = (valueObj as? Number)?.toDouble() ?: Double.NaN
                if (!v.isNaN()) {
                    historyManager.getBuffer(path).add(Pair(v, now))
                    Pair(stateWithTs.copy(courseOverGroundTrue = v), true)
                } else Pair(stateWithTs, false)
            }
            else -> {
                when {
                    path.startsWith("propulsion.") || path.startsWith("electrical.") || path.startsWith("tanks.") ->
                        parseSystemValue(stateWithTs, path, valueObj, now)
                    path.startsWith("navigation.") -> parseNavigationValue(stateWithTs, path, valueObj, now)
                    path.startsWith("performance.") -> parsePerformanceValue(stateWithTs, path, valueObj, now)
                    path.startsWith("steering.") -> parseAutopilotValue(stateWithTs, path, valueObj, now)
                    path.startsWith("environment.") -> parseEnvironmentValue(stateWithTs, path, valueObj, now)
                    path.startsWith("resources.") -> {
                        engineScope.launch { resourceManager.refreshAll() }
                        Pair(stateWithTs, false)
                    }
                    else -> {
                        val res = parseTelemetryValue(stateWithTs, path, valueObj, now)
                        if (res.second) res else parseOtherValue(stateWithTs, path, valueObj, source)
                    }
                }
            }
        }
    }

    private fun processDepthBelowTransducer(s: MarineState, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN
        return if (MarineStateConstants.isValidDepth(value)) {
            val buffer = historyManager.getBuffer(SignalKPaths.ENV_DEPTH_BELOW_TRANSDUCER)
            buffer.add(Pair(value, now))
            val smoothedValue = buffer.getAverage { it.first }
            val state = dataBroker.applyDepthUpdate(s, smoothedValue, now)
            Pair(state, true)
        } else Pair(s, false)
    }

    private fun processHeadingTrue(s: MarineState, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN
        return if (!value.isNaN()) {
            historyManager.getBuffer(SignalKPaths.NAV_HEADING_TRUE).add(Pair(value, now))
            val state = dataBroker.applyHeadingTrueUpdate(s, value, now)
            Pair(state, true)
        } else Pair(s, false)
    }

    private fun processHeadingMag(s: MarineState, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN
        return if (!value.isNaN()) {
            historyManager.getBuffer(SignalKPaths.NAV_HEADING_MAG).add(Pair(value, now))
            val state = dataBroker.applyHeadingMagneticUpdate(s, value, now)
            Pair(state, true)
        } else Pair(s, false)
    }

    private fun processSog(s: MarineState, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN
        return if (MarineStateConstants.isValidSpeed(value)) {
            historyManager.getBuffer(SignalKPaths.NAV_SPEED_OVER_GROUND).add(Pair(value, now))
            val state = dataBroker.applySpeedOverGroundUpdate(s, value, now)
            Pair(state, true)
        } else Pair(s, false)
    }

    private fun processStw(s: MarineState, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN
        return if (MarineStateConstants.isValidSpeed(value)) {
            historyManager.getBuffer(SignalKPaths.NAV_SPEED_THROUGH_WATER).add(Pair(value, now))
            val state = dataBroker.applySpeedThroughWaterUpdate(s, value, now)
            Pair(state, true)
        } else Pair(s, false)
    }

    private fun processWindAngleApparent(s: MarineState, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN
        return if (!value.isNaN()) {
            historyManager.getBuffer(SignalKPaths.ENV_WIND_ANGLE_APPARENT).add(Pair(value, now))
            val corrected = (value + 2 * Math.PI) % (2 * Math.PI)
            val state = dataBroker.applyWindAngleApparentUpdate(s, corrected, now)
            Pair(state, true)
        } else Pair(s, false)
    }

    private fun processWindSpeedApparent(s: MarineState, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN
        return if (MarineStateConstants.isValidWindSpeed(value)) {
            historyManager.getBuffer(SignalKPaths.ENV_WIND_SPEED_APPARENT).add(Pair(value, now))
            val state = dataBroker.applyWindSpeedApparentUpdate(s, value, now)
            Pair(state, true)
        } else Pair(s, false)
    }

    private fun parseNavigationValue(s: MarineState, path: String, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        var state = s
        var updated = false
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN

        when (path) {
            SignalKPaths.NAV_RATE_OF_TURN -> {
                if (!value.isNaN()) {
                    historyManager.getBuffer(path).add(Pair(value, now))
                    state = state.copy(rateOfTurn = value)
                    updated = true
                }
            }
            SignalKPaths.NAV_ATTITUDE -> {
                if (valueObj is JSONObject) {
                    val roll = valueObj.optDouble("roll", Double.NaN)
                    val pitch = valueObj.optDouble("pitch", Double.NaN)
                    val yaw = valueObj.optDouble("yaw", Double.NaN)

                    state = state.copy(
                        yaw = if (yaw.isNaN()) state.yaw else yaw
                    )

                    if (!roll.isNaN()) {
                        historyManager.getBuffer(SignalKPaths.NAV_ATTITUDE + ".roll").add(Pair(roll, now))
                        state = dataBroker.applyRollUpdate(state, roll, now)
                    }
                    if (!pitch.isNaN()) {
                        historyManager.getBuffer(SignalKPaths.NAV_ATTITUDE + ".pitch").add(Pair(pitch, now))
                        state = dataBroker.applyPitchUpdate(state, pitch, now)
                    }
                    updated = true
                }
            }
            SignalKPaths.NAV_CLOSEST_APPROACH -> {
                if (valueObj is JSONObject) {
                    val cpa = valueObj.optDouble("cpa", Double.NaN)
                    val tcpa = valueObj.optDouble("tcpa", Double.NaN)
                    val name = valueObj.optString("name", app.getString(R.string.nautical_unknown_vessel))
                    if (!cpa.isNaN() && !tcpa.isNaN()) {
                        val cpaNm = SignalKUnitConverter.metersToNm(cpa)
                        state = state.copy(cpa = cpaNm, tcpa = tcpa, threatName = name)
                        updated = true
                    }
                }
            }
            SignalKPaths.NAV_COURSE_NEXT_POINT -> {
                if (valueObj is JSONObject) {
                    val pos = valueObj.optJSONObject("position")
                    if (pos != null) {
                        val lat = pos.optDouble("latitude", Double.NaN)
                        val lon = pos.optDouble("longitude", Double.NaN)
                        if (MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
                            state = state.copy(serverNextPoint = LatLon(lat, lon))
                            updated = true
                        }
                    }
                }
            }
            SignalKPaths.FORWARD_WATCH_DETECTIONS -> {
                if (valueObj is JSONArray) {
                    val hazards = mutableListOf<ForwardHazard>()
                    for (i in 0 until valueObj.length()) {
                        val hObj = valueObj.optJSONObject(i) ?: continue
                        val id = hObj.optString("id", "")
                        val name = hObj.optString("name", app.getString(R.string.nautical_obstacle))
                        val dist = hObj.optDouble("distance", 0.0)
                        val bear = hObj.optDouble("bearing", 0.0)
                        val severity = when (hObj.optString("severity", "normal")) {
                            "alert" -> NotificationState.ALERT
                            "warn" -> NotificationState.WARN
                            "alarm" -> NotificationState.ALARM
                            "emergency" -> NotificationState.EMERGENCY
                            else -> NotificationState.NORMAL
                        }
                        val pos = hObj.optJSONObject("position")
                        val latLon = if (pos != null) {
                            val lat = pos.optDouble("latitude", Double.NaN)
                            val lon = pos.optDouble("longitude", Double.NaN)
                            if (MarineStateConstants.isValidLat(lat)) lat to lon else null
                        } else null
                        hazards.add(ForwardHazard(id, name, dist, bear, severity, latLon))
                    }
                    state = state.copy(forwardHazards = hazards)
                    NauticalPlugin.getInstance()?.safetyManager?.updateForwardHazards(hazards)
                    updated = true
                }
            }
            "navigation.course" -> {
                if (valueObj is JSONObject) {
                    val nextPoint = valueObj.optJSONObject("nextPoint")
                    if (nextPoint != null) {
                        val pos = nextPoint.optJSONObject("position")
                        if (pos != null) {
                            val lat = pos.optDouble("latitude", Double.NaN)
                            val lon = pos.optDouble("longitude", Double.NaN)
                            if (!lat.isNaN()) {
                                val oldPoint = state.serverNextPoint
                                val newPoint = LatLon(lat, lon)
                                if (oldPoint != newPoint) {
                                    state = state.copy(serverNextPoint = newPoint)
                                    engineScope.launch(Dispatchers.Main) {
                                        routeStepListeners.forEach { it.invoke() }
                                    }
                                    updated = true
                                }
                            }
                        }
                    }
                    val arrivalCircle = valueObj.optDouble("arrivalCircle", Double.NaN)
                    if (!arrivalCircle.isNaN()) routeTracker.arrivalRadiusMeters = arrivalCircle
                    if (valueObj.has("activeRoute")) routeTracker.isFollowingRoute = true
                }
            }
            SignalKPaths.NAV_DATETIME_MOON_PHASE -> {
                if (!value.isNaN()) {
                    state = state.copy(moonPhase = value)
                    updated = true
                }
            }
            SignalKPaths.NAV_DATETIME_MOON_ILLUMINATION -> {
                if (!value.isNaN()) {
                    state = state.copy(moonIllumination = value)
                    updated = true
                }
            }
            SignalKPaths.NAV_DATETIME_SUNRISE -> {
                val str = valueObj?.toString()
                if (!str.isNullOrEmpty()) {
                    state = state.copy(sunrise = str)
                    updated = true
                }
            }
            SignalKPaths.NAV_DATETIME_SUNSET -> {
                val str = valueObj?.toString()
                if (!str.isNullOrEmpty()) {
                    state = state.copy(sunset = str)
                    updated = true
                }
            }
            SignalKPaths.NAV_CALLSIGN -> {
                val callSign = valueObj?.toString()
                if (!callSign.isNullOrEmpty()) {
                    state = state.copy(vesselCallsign = callSign)
                    updated = true
                }
            }
            SignalKPaths.NAV_COURSE_RHUMB_LINE_NEXT_POINT_BEARING -> {
                if (!value.isNaN()) {
                    state = state.copy(rhumbLineBearing = value)
                    updated = true
                }
            }
            SignalKPaths.NAV_COURSE_RHUMB_LINE_NEXT_POINT_DISTANCE -> {
                if (!value.isNaN()) {
                    state = state.copy(rhumbLineDistance = value)
                    updated = true
                }
            }
            SignalKPaths.NAV_GNSS_INTEGRITY -> {
                val flagList = if (valueObj is JSONArray) {
                    (0 until valueObj.length()).mapNotNull { valueObj.optString(it) }
                } else emptyList()
                val currentGnss = state.gnss ?: GnssState()
                val newGnss = currentGnss.copy(integrityFlags = flagList)
                state = state.copy(gnss = newGnss)
                updated = true
            }
            SignalKPaths.NAV_ANCHOR_MAX_RADIUS -> {
                if (!value.isNaN()) {
                    val currentAnchor = state.anchorState ?: AnchorState()
                    val newAnchor = currentAnchor.copy(maxRadius = value)
                    state = state.copy(anchorState = newAnchor)
                    updated = true
                }
            }
            SignalKPaths.NAV_ANCHOR_RODE_LENGTH -> {
                if (!value.isNaN()) {
                    val currentAnchor = state.anchorState ?: AnchorState()
                    val newAnchor = currentAnchor.copy(rodeLength = value)
                    state = state.copy(anchorState = newAnchor)
                    updated = true
                }
            }
        }
        return Pair(state, updated)
    }

    private fun parsePerformanceValue(s: MarineState, path: String, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        var state = s
        var updated = false
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN

        when (path) {
            SignalKPaths.PERF_VMG -> {
                if (!value.isNaN()) {
                    historyManager.getBuffer(path).add(Pair(value, now))
                    state = state.copy(velocityMadeGood = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_TARGET_SPEED -> {
                if (!value.isNaN()) {
                    state = state.copy(polarTargetSpeed = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_TARGET_TWA -> {
                if (!value.isNaN()) {
                    state = state.copy(targetWindAngleApparent = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_TARGET_HEADING_TRUE -> {
                if (!value.isNaN()) {
                    state = state.copy(targetHeadingTrue = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_TARGET_HEADING_MAG -> {
                if (!value.isNaN()) {
                    state = state.copy(targetHeadingMagnetic = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_VMG_TARGET_SPEED -> {
                if (!value.isNaN()) {
                    state = state.copy(targetVmg = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_VMG_TARGET_ANGLE -> {
                if (!value.isNaN()) {
                    state = state.copy(targetTwa = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_TARGET_AWA -> {
                if (!value.isNaN()) {
                    state = state.copy(targetWindAngleApparent = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_POLAR_RATIO -> {
                if (!value.isNaN()) {
                    historyManager.getBuffer(path).add(Pair(value, now))
                    state = state.copy(polarSpeedRatio = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_POLAR_SPEED -> {
                if (!value.isNaN()) {
                    state = state.copy(polarTargetSpeed = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_LEEWAY -> {
                if (!value.isNaN()) {
                    state = state.copy(leeway = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_TACK_ANGLE -> {
                if (!value.isNaN()) {
                    state = state.copy(optimumTackAngle = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_GYBE_ANGLE -> {
                if (!value.isNaN()) {
                    state = state.copy(optimumGybeAngle = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_LAYLINE_PORT, SignalKPaths.PERF_LAYLINE_STARBOARD -> {
                if (valueObj is JSONObject) {
                    val port = valueObj.optDouble("port", Double.NaN)
                    val stbd = valueObj.optDouble("starboard", Double.NaN)
                    val target = valueObj.optDouble("targetHeading", Double.NaN)
                    val laylineData = LaylineData(
                        portLayline = if (port.isNaN()) null else port,
                        starboardLayline = if (stbd.isNaN()) null else stbd,
                        targetHeading = if (target.isNaN()) null else target
                    )
                    state = state.copy(laylineData = laylineData)
                    updated = true
                }
            }
            SignalKPaths.PERF_VMG_WAYPOINT -> {
                if (!value.isNaN()) {
                    state = state.copy(velocityMadeGood = value)
                    updated = true
                }
            }
            SignalKPaths.PERF_POLAR_SPEED_RATIO -> {
                if (!value.isNaN()) {
                    state = state.copy(polarSpeedRatio = value)
                    updated = true
                }
            }
        }
        return Pair(state, updated)
    }

    private fun parseAutopilotValue(s: MarineState, path: String, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        var state = s
        var updated = false
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN

        when (path) {
            SignalKPaths.STEERING_AUTOPILOT_STATE -> {
                val raw = valueObj?.toString() ?: ""
                val normalized = when (raw.lowercase(Locale.US)) {
                    "auto", "compass", "heading" -> "compass"
                    "wind", "wind vane", "vane" -> "wind"
                    "route", "nav", "track", "waypoint" -> "route"
                    "gps", "true" -> "gps"
                    "standby", "off", "manual" -> "standby"
                    else -> raw
                }
                var nextPendingMode = state.pendingAutopilotMode
                if (nextPendingMode != null && nextPendingMode.equals(normalized, ignoreCase = true)) {
                    nextPendingMode = null
                }
                state = state.copy(autopilotState = normalized, pendingAutopilotMode = nextPendingMode)
                updated = true
            }
            SignalKPaths.STEERING_AUTOPILOT_TARGET_HEADING_TRUE -> {
                if (!value.isNaN()) {
                    var nextPendingHeading = state.pendingAutopilotHeading
                    if (nextPendingHeading != null && Math.abs(nextPendingHeading - value) < 0.01) {
                        nextPendingHeading = null
                    }
                    state = state.copy(autopilotTargetHeading = value, pendingAutopilotHeading = nextPendingHeading)
                    updated = true
                }
            }
            SignalKPaths.STEERING_AUTOPILOT_TARGET_HEADING_MAG -> {
                if (!value.isNaN()) {
                    var nextPendingHeading = state.pendingAutopilotHeading
                    if (nextPendingHeading != null && Math.abs(nextPendingHeading - value) < 0.01) {
                        nextPendingHeading = null
                    }
                    state = state.copy(autopilotTargetHeading = value, pendingAutopilotHeading = nextPendingHeading)
                    updated = true
                }
            }
            SignalKPaths.STEERING_AUTOPILOT_TARGET_WIND_ANGLE -> {
                if (!value.isNaN()) {
                    state = state.copy(autopilotTargetWindAngle = value)
                    updated = true
                }
            }
            SignalKPaths.STEERING_AUTOPILOT_GAIN -> {
                if (!value.isNaN()) {
                    val level = value.toInt()
                    state = state.copy(rudderGainLevel = level)
                    updated = true
                }
            }
            SignalKPaths.STEERING_RUDDER_ANGLE -> {
                if (!value.isNaN()) {
                    state = state.copy(rudderAngle = value)
                    updated = true
                }
            }
            SignalKPaths.STEERING_RUDDER_TARGET_ANGLE -> {
                if (!value.isNaN()) {
                    state = state.copy(rudderTargetAngle = value)
                    updated = true
                }
            }
            SignalKPaths.STEERING_AUTOPILOT_DUTY_CYCLE -> {
                if (!value.isNaN()) {
                    historyManager.getBuffer(path).add(Pair(value, now))
                    state = state.copy(actuatorDutyCycle = value)
                    updated = true
                }
            }
            else -> {
                if (path.startsWith(SignalKPaths.PYPILOT_CONFIG_PREFIX)) {
                    val field = path.removePrefix(SignalKPaths.PYPILOT_CONFIG_PREFIX)
                    state = updatePypilotConfig(state, field, valueObj)
                    updated = true
                } else if (path.startsWith(SignalKPaths.PYPILOT_SERVO_PREFIX)) {
                    val field = path.removePrefix(SignalKPaths.PYPILOT_SERVO_PREFIX)
                    state = updatePypilotServo(state, field, valueObj)
                    updated = true
                } else if (path.startsWith(SignalKPaths.PYPILOT_CALIBRATION_PREFIX)) {
                    val field = path.removePrefix(SignalKPaths.PYPILOT_CALIBRATION_PREFIX)
                    state = updatePypilotCalibration(state, field, valueObj)
                    updated = true
                }
            }
        }
        return Pair(state, updated)
    }

    private fun parseSystemValue(s: MarineState, path: String, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        var state = s
        var updated = false
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN

        when {
            path.startsWith(SignalKPaths.PROPULSION_PREFIX) -> {
                val parts = path.removePrefix(SignalKPaths.PROPULSION_PREFIX).split(".")
                if (parts.size >= 2) {
                    val instance = parts[0]
                    state = when (parts[1]) {
                        "revolutions" -> {
                            if (!value.isNaN()) {
                                historyManager.getBuffer(path).add(Pair(value, now))
                                updateEngine(state, instance) { it.copy(revolutions = value) }
                            } else state
                        }
                        "temperature" -> {
                            if (!value.isNaN()) {
                                historyManager.getBuffer(path).add(Pair(value, now))
                                updateEngine(state, instance) { it.copy(temperature = value) }
                            } else state
                        }
                        "oilPressure" -> {
                            if (!value.isNaN()) {
                                historyManager.getBuffer(path).add(Pair(value, now))
                                updateEngine(state, instance) { it.copy(oilPressure = value) }
                            } else state
                        }
                        "coolantTemperature" -> {
                            if (!value.isNaN()) {
                                historyManager.getBuffer(path).add(Pair(value, now))
                                updateEngine(state, instance) { it.copy(coolantTemperature = value) }
                            } else state
                        }
                        "alternatorVoltage" -> {
                            if (!value.isNaN()) {
                                updateEngine(state, instance) { it.copy(alternatorVoltage = value) }
                            } else state
                        }
                        "fuelRate" -> {
                            if (!value.isNaN()) {
                                updateEngine(state, instance) { it.copy(fuelRate = value) }
                            } else state
                        }
                        "state" -> {
                            val str = valueObj?.toString()
                            updateEngine(state, instance) { it.copy(state = str) }
                        }
                        "boostPressure" -> {
                            if (!value.isNaN()) {
                                updateEngine(state, instance) { it.copy(boostPressure = value) }
                            } else state
                        }
                        "engineLoad" -> {
                            if (!value.isNaN()) {
                                historyManager.getBuffer(path).add(Pair(value, now))
                                updateEngine(state, instance) { it.copy(engineLoad = value) }
                            } else state
                        }
                        "coolantPressure" -> {
                            if (!value.isNaN()) {
                                updateEngine(state, instance) { it.copy(coolantPressure = value) }
                            } else state
                        }
                        "exhaustTemperature" -> {
                            if (!value.isNaN()) {
                                updateEngine(state, instance) { it.copy(exhaustTemperature = value) }
                            } else state
                        }
                        else -> state
                    }
                    updated = true
                }
            }
            path.startsWith(SignalKPaths.BATTERIES_PREFIX) -> {
                val parts = path.removePrefix(SignalKPaths.BATTERIES_PREFIX).split(".")
                if (parts.size >= 2) {
                    val instance = parts[0]
                    state = when (parts[1]) {
                        "voltage" -> {
                            if (!value.isNaN()) {
                                historyManager.getBuffer(path).add(Pair(value, now))
                                updateBattery(state, instance) { it.copy(voltage = value) }
                            } else state
                        }
                        "current" -> {
                            if (!value.isNaN()) {
                                historyManager.getBuffer(path).add(Pair(value, now))
                                updateBattery(state, instance) { it.copy(current = value) }
                            } else state
                        }
                        "temperature" -> {
                            if (!value.isNaN()) {
                                updateBattery(state, instance) { it.copy(temperature = value) }
                            } else state
                        }
                        "capacity" -> {
                            if (parts.size >= 3 && parts[2] == "stateOfCharge" && !value.isNaN()) {
                                historyManager.getBuffer(path).add(Pair(value, now))
                                updateBattery(state, instance) { it.copy(stateOfCharge = value) }
                            } else if (parts.size >= 3 && parts[2] == "timeRemaining" && !value.isNaN()) {
                                updateBattery(state, instance) { it.copy(timeRemaining = value) }
                            } else state
                        }
                        "cells" -> {
                            if (valueObj is JSONObject) {
                                val cellList = mutableListOf<BatteryCell>()
                                val keys = valueObj.keys()
                                while (keys.hasNext()) {
                                    val cell = keys.next()
                                    val v = valueObj.optDouble(cell, Double.NaN)
                                    if (!v.isNaN()) {
                                        cellList.add(BatteryCell(cell, v))
                                    }
                                }
                                updateBattery(state, instance) { it.copy(cells = cellList) }
                            } else state
                        }
                        else -> state
                    }
                    updated = true
                }
            }
            path.startsWith(SignalKPaths.TANKS_PREFIX) -> {
                val parts = path.removePrefix(SignalKPaths.TANKS_PREFIX).split(".")
                if (parts.size >= 3) {
                    val type = parts[0]
                    val instance = parts[1]
                    val field = parts[2]
                    state = when (field) {
                        "currentLevel" -> {
                            if (!value.isNaN()) {
                                historyManager.getBuffer(path).add(Pair(value, now))
                                updateTank(state, instance, type) { it.copy(currentLevel = value) }
                            } else state
                        }
                        "capacity" -> {
                            if (!value.isNaN()) {
                                updateTank(state, instance, type) { it.copy(capacity = value) }
                            } else state
                        }
                        else -> state
                    }
                    updated = true
                }
            }
            path.startsWith(SignalKPaths.CHARGERS_PREFIX) -> {
                val parts = path.removePrefix(SignalKPaths.CHARGERS_PREFIX).split(".")
                if (parts.size >= 2) {
                    val instance = parts[0]
                    state = when (parts[1]) {
                        "mode" -> updateCharger(state, instance) { it.copy(mode = valueObj?.toString()) }
                        "state" -> updateCharger(state, instance) { it.copy(state = valueObj?.toString()) }
                        else -> state
                    }
                    updated = true
                }
            }
            path.startsWith(SignalKPaths.INVERTERS_PREFIX) -> {
                val parts = path.removePrefix(SignalKPaths.INVERTERS_PREFIX).split(".")
                if (parts.size >= 2) {
                    val instance = parts[0]
                    state = when (parts[1]) {
                        "state" -> updateInverter(state, instance) { it.copy(state = valueObj?.toString()) }
                        "acVoltage" -> if (!value.isNaN()) updateInverter(state, instance) { it.copy(acVoltage = value) } else state
                        "dcVoltage" -> if (!value.isNaN()) updateInverter(state, instance) { it.copy(dcVoltage = value) } else state
                        "inverterCurrent" -> if (!value.isNaN()) updateInverter(state, instance) { it.copy(inverterCurrent = value) } else state
                        else -> state
                    }
                    updated = true
                }
            }
            path.startsWith(SignalKPaths.WATERMAKERS_PREFIX) -> {
                val parts = path.removePrefix(SignalKPaths.WATERMAKERS_PREFIX).split(".")
                if (parts.size >= 2) {
                    val watermakerId = parts[0]
                    val watermakerField = parts[1]
                    state = when (watermakerField) {
                        "state" -> updateWatermaker(state, watermakerId) { it.copy(state = valueObj?.toString()) }
                        "productionRate" -> if (!value.isNaN()) updateWatermaker(state, watermakerId) { it.copy(productionRate = value) } else state
                        "salinity" -> if (!value.isNaN()) updateWatermaker(state, watermakerId) { it.copy(salinity = value) } else state
                        "highPressure" -> if (!value.isNaN()) updateWatermaker(state, watermakerId) { it.copy(highPressure = value) } else state
                        "lowPressure" -> if (!value.isNaN()) updateWatermaker(state, watermakerId) { it.copy(lowPressure = value) } else state
                        "filterStatus" -> updateWatermaker(state, watermakerId) { it.copy(filterStatus = valueObj?.toString()) }
                        else -> state
                    }
                    updated = true
                }
            }
            path.startsWith(SignalKPaths.SOLAR_PREFIX) -> {
                val field = path.removePrefix(SignalKPaths.SOLAR_PREFIX)
                if (field.endsWith(".current") && !value.isNaN()) {
                    historyManager.getBuffer(path).add(Pair(value, now))
                    state = state.copy(solarCurrent = value)
                    updated = true
                } else if (field.endsWith(".voltage") && !value.isNaN()) {
                    state = state.copy(solarVoltage = value)
                    updated = true
                }
            }
            path.startsWith(SignalKPaths.SWITCHES_PREFIX) -> {
                val switchPath = path.removePrefix(SignalKPaths.SWITCHES_PREFIX)
                if (switchPath.endsWith(".dimmingLevel") && !value.isNaN()) {
                    val level = value.toFloat()
                    val dimmers = state.dimmers.toMutableMap()
                    dimmers[switchPath.removeSuffix(".dimmingLevel")] = level
                    state = state.copy(dimmers = dimmers)
                    updated = true
                } else {
                    val switchState = when (valueObj) {
                        is Boolean -> valueObj
                        is Number -> valueObj.toInt() > 0
                        is String -> valueObj.lowercase(Locale.US).let { it == "true" || it == "on" || it == "1" }
                        else -> false
                    }
                    val switches = state.switches.toMutableMap()
                    switches[switchPath] = switchState
                    state = state.copy(switches = switches)
                    updated = true
                }
            }
        }
        return Pair(state, updated)
    }

    private fun parseEnvironmentValue(s: MarineState, path: String, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        var state = s
        var updated = false
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN

        when (path) {
            SignalKPaths.ENV_WATER_TEMP -> {
                if (!value.isNaN()) {
                    historyManager.getBuffer(path).add(Pair(value, now))
                    state = state.copy(waterTemperature = value)
                    updated = true
                }
            }
            SignalKPaths.ENV_OUTSIDE_TEMP -> {
                if (!value.isNaN()) {
                    historyManager.getBuffer(path).add(Pair(value, now))
                    state = state.copy(outsideTemperature = value)
                    updated = true
                }
            }
            SignalKPaths.ENV_OUTSIDE_PRESSURE -> {
                if (!value.isNaN()) {
                    historyManager.getBuffer(path).add(Pair(value, now))
                    state = state.copy(outsidePressure = value)
                    updated = true
                }
            }
            SignalKPaths.ENV_OUTSIDE_HUMIDITY -> {
                if (!value.isNaN()) {
                    state = state.copy(outsideHumidity = value)
                    updated = true
                }
            }
            SignalKPaths.ENV_WIND_ANGLE_TRUE -> {
                if (!value.isNaN()) {
                    historyManager.getBuffer(path).add(Pair(value, now))
                    val corrected = (value + 2 * Math.PI) % (2 * Math.PI)
                    state = state.copy(trueWindAngle = corrected)
                    updated = true
                }
            }
            SignalKPaths.ENV_WIND_DIRECTION_TRUE -> {
                if (!value.isNaN()) {
                    historyManager.getBuffer(path).add(Pair(value, now))
                    val corrected = (value + 2 * Math.PI) % (2 * Math.PI)
                    state = state.copy(windDirectionTrue = corrected)
                    updated = true
                }
            }
            SignalKPaths.ENV_WIND_SPEED_TRUE -> {
                if (MarineStateConstants.isValidWindSpeed(value)) {
                    historyManager.getBuffer(path).add(Pair(value, now))
                    state = state.copy(windSpeedTrue = value)
                    updated = true
                }
            }
            SignalKPaths.ENV_DEPTH_BELOW_KEEL -> {
                if (MarineStateConstants.isValidDepth(value)) {
                    historyManager.getBuffer(path).add(Pair(value, now))
                    state = state.copy(depthBelowKeel = value)
                    updated = true
                }
            }
            SignalKPaths.ENV_DEPTH_BELOW_SURFACE -> {
                if (MarineStateConstants.isValidDepth(value)) {
                    historyManager.getBuffer(path).add(Pair(value, now))
                    state = state.copy(depthBelowSurface = value)
                    updated = true
                }
            }
            SignalKPaths.ENV_CURRENT_SET -> {
                if (!value.isNaN()) {
                    state = state.copy(setTrue = value)
                    updated = true
                }
            }
            SignalKPaths.ENV_CURRENT_DRIFT -> {
                if (!value.isNaN()) {
                    state = state.copy(drift = value)
                    updated = true
                }
            }
            SignalKPaths.ENV_TIDE_HEIGHT -> {
                if (!value.isNaN()) {
                    val tide = state.tidePrediction ?: TidePrediction(0L, 0.0, 0L, 0.0)
                    val nextTide = tide.copy(currentHeight = value)
                    state = state.copy(tidePrediction = nextTide)
                    updated = true
                }
            }
        }
        return Pair(state, updated)
    }

    private fun parseOtherValue(s: MarineState, path: String, valueObj: Any?, source: String?): Pair<MarineState, Boolean> {
        var state = s
        var updated = false

        when {
            path == SignalKPaths.VESSEL_NAME -> {
                val name = valueObj?.toString()
                if (!name.isNullOrEmpty()) {
                    state = state.copy(vesselName = name)
                    updated = true
                }
            }
            path == SignalKPaths.VESSEL_MMSI -> {
                val mmsi = (valueObj as? Number)?.toInt() ?: valueObj?.toString()?.toIntOrNull()
                if (mmsi != null && MarineStateConstants.isValidMmsi(mmsi)) {
                    state = state.copy(vesselMmsi = mmsi)
                    updated = true
                }
            }
            path == SignalKPaths.VESSEL_FLAG -> {
                val flag = valueObj?.toString()
                if (!flag.isNullOrEmpty()) {
                    state = state.copy(vesselFlag = flag)
                    updated = true
                }
            }
            path == SignalKPaths.VESSEL_PORT -> {
                val port = valueObj?.toString()
                if (!port.isNullOrEmpty()) {
                    state = state.copy(vesselPort = port)
                    updated = true
                }
            }
            path == SignalKPaths.VESSEL_UUID -> {
                val uuid = valueObj?.toString()
                if (!uuid.isNullOrEmpty()) {
                    state = state.copy(vesselUuid = uuid)
                    updated = true
                }
            }
            path == SignalKPaths.NAV_AIS_BUDDIES -> {
                if (valueObj is JSONArray) {
                    val buddies = mutableSetOf<Int>()
                    for (i in 0 until valueObj.length()) {
                        val mmsi = valueObj.optInt(i, -1)
                        if (mmsi != -1) buddies.add(mmsi)
                    }
                    state = state.copy(aisBuddies = buddies)
                    updated = true
                }
            }
            path.startsWith(SignalKPaths.NOTIFICATIONS_PREFIX) -> {
                if (valueObj is JSONObject) {
                    val message = valueObj.optString("message", "")
                    val stateStr = valueObj.optString("state", "normal")
                    val methodArray = valueObj.optJSONArray("method")
                    val methods = if (methodArray != null) {
                        (0 until methodArray.length()).map { methodArray.getString(it) }
                    } else emptyList()

                    val notificationState = when (stateStr.lowercase(Locale.US)) {
                        "alert" -> NotificationState.ALERT
                        "warn" -> NotificationState.WARN
                        "alarm" -> NotificationState.ALARM
                        "emergency" -> NotificationState.EMERGENCY
                        else -> NotificationState.NORMAL
                    }

                    val updatedNotifications = state.notifications.toMutableMap()
                    if (notificationState == NotificationState.NORMAL) {
                        updatedNotifications.remove(path)
                    } else {
                        updatedNotifications[path] = SignalKNotification(
                            message = message,
                            state = notificationState,
                            method = methods,
                            path = path
                        )
                    }

                    if (path == SignalKPaths.NOTIFICATIONS_MOB) {
                        var mobLat: Double? = null
                        var mobLon: Double? = null
                        val latLonRegex = Regex("([-+]?\\d*\\.?\\d+)[,\\s]+([-+]?\\d*\\.?\\d+)")
                        val match = latLonRegex.find(message)
                        if (match != null) {
                            mobLat = match.groupValues[1].toDoubleOrNull()
                            mobLon = match.groupValues[2].toDoubleOrNull()
                        }
                        state = state.copy(
                            isMobActive = notificationState != NotificationState.NORMAL,
                            mobLatitude = mobLat ?: state.latitude,
                            mobLongitude = mobLon ?: state.longitude
                        )
                    }

                    state = state.copy(notifications = updatedNotifications)
                    updated = true
                } else if (valueObj is String) {
                    val message = valueObj
                    val stateStr = "alert"
                    val notificationState = NotificationState.ALERT
                    val updatedNotifications = state.notifications.toMutableMap()
                    updatedNotifications[path] = SignalKNotification(
                        message = message,
                        state = notificationState,
                        method = listOf("visual"),
                        path = path
                    )
                    state = state.copy(notifications = updatedNotifications)
                    updated = true
                }
            }
            path == SignalKPaths.COMMUNICATION_VHF_CHANNEL -> {
                val chan = valueObj?.toString()
                if (!chan.isNullOrEmpty()) {
                    state = state.copy(vhfChannel = chan)
                    updated = true
                }
            }
            path.startsWith(SignalKPaths.SAILS_INVENTORY) -> {
                val names = path.removePrefix(SignalKPaths.SAILS_INVENTORY).split(".")
                if (names.size >= 2) {
                    val sailId = names[0]
                    val field = names[1]
                    val currentSails = state.sailInventory.toMutableList()
                    val idx = currentSails.indexOfFirst { it.id == sailId }
                    val sail = if (idx >= 0) currentSails[idx] else Sail(id = sailId, name = sailId, type = "Unknown")
                    val nextSail = when (field) {
                        "name" -> sail.copy(name = valueObj?.toString() ?: sail.name)
                        "type" -> sail.copy(type = valueObj?.toString() ?: sail.type)
                        "area" -> sail.copy(area = (valueObj as? Number)?.toDouble() ?: sail.area)
                        "active" -> sail.copy(active = valueObj == true || valueObj?.toString() == "true")
                        "reefs" -> sail.copy(reefs = (valueObj as? Number)?.toInt() ?: sail.reefs)
                        else -> sail
                    }
                    if (idx >= 0) currentSails[idx] = nextSail else currentSails.add(nextSail)
                    state = state.copy(sailInventory = currentSails)
                    updated = true
                }
            }
        }
        return Pair(state, updated)
    }

    private fun parseTelemetryValue(marineState: MarineState, path: String, valueObj: Any?, now: Long): Pair<MarineState, Boolean> {
        val value = (valueObj as? Number)?.toDouble() ?: Double.NaN
        if (value.isNaN()) return Pair(marineState, false)

        var state = marineState
        var updated = false

        when (path) {
            SignalKPaths.NAV_LOG -> {
                state = state.copy(log = value)
                updated = true
            }
            SignalKPaths.NAV_TRIP_LOG -> {
                state = state.copy(tripLog = value)
                updated = true
            }
            SignalKPaths.PERF_POLAR_SPEED_RATIO -> {
                state = state.copy(polarSpeedRatio = value)
                updated = true
            }
            SignalKPaths.PERF_VMG -> {
                state = state.copy(velocityMadeGood = value)
                updated = true
            }
        }
        return Pair(state, updated)
    }

    fun processVesselTree(tree: Map<String, Any>, initialState: MarineState): Pair<MarineState, Boolean> {
        var updated = false
        var current = initialState

        fun extractValue(path: String): Any? {
            val parts = path.split(".")
            var node: Any? = tree
            for (part in parts) {
                node = (node as? Map<*, *>)?.get(part)
                if (node == null) break
            }
            return (node as? Map<*, *>)?.get("value")
        }

        extractValue(SignalKPaths.NAV_POSITION)?.let { pos ->
            if (pos is Map<*, *>) {
                val lat = (pos["latitude"] as? Number)?.toDouble() ?: Double.NaN
                val lon = (pos["longitude"] as? Number)?.toDouble() ?: Double.NaN
                if (MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
                    current = current.copy(latitude = lat, longitude = lon)
                    updated = true
                }
            }
        }

        extractValue(SignalKPaths.NAV_SPEED_OVER_GROUND)?.let { sog ->
            if (sog is Number && MarineStateConstants.isValidSpeed(sog.toDouble())) {
                current = current.copy(speedOverGround = sog.toDouble())
                updated = true
            }
        }

        extractValue(SignalKPaths.NAV_COURSE_OVER_GROUND)?.let { cog ->
            if (cog is Number) {
                current = current.copy(courseOverGroundTrue = cog.toDouble())
                updated = true
            }
        }

        extractValue(SignalKPaths.NAV_HEADING_TRUE)?.let { hdg ->
            if (hdg is Number) {
                current = current.copy(headingTrue = hdg.toDouble())
                updated = true
            }
        }

        extractValue(SignalKPaths.ENV_DEPTH_BELOW_KEEL)?.let { depth ->
            if (depth is Number && MarineStateConstants.isValidDepth(depth.toDouble())) {
                current = current.copy(depthBelowKeel = depth.toDouble())
                updated = true
            }
        }

        extractValue(SignalKPaths.SAILS_REEFS)?.let { reefs ->
            if (reefs is Number) {
                current = current.copy(reefs = reefs.toInt())
                updated = true
            }
        }

        extractValue(SignalKPaths.SAILS_ACTIVE_PLAN)?.let { plan ->
            current = current.copy(activeSailPlan = plan.toString())
            updated = true
        }

        extractValue(SignalKPaths.SAILS_INVENTORY)?.let { inv ->
            if (inv is Map<*, *>) {
                val sails = mutableListOf<Sail>()
                inv.forEach { (key, value) ->
                    if (value is Map<*, *>) {
                        val obj = value["value"] as? Map<*, *> ?: value
                        sails.add(Sail(
                            id = key.toString(),
                            name = obj["name"]?.toString() ?: "Unknown",
                            type = obj["type"]?.toString() ?: "Unknown",
                            area = (obj["area"] as? Number)?.toDouble(),
                            active = obj["active"] as? Boolean ?: false,
                            reefs = (obj["reefs"] as? Number)?.toInt(),
                            maxReefs = (value["meta"] as? Map<*, *>)?.get("max") as? Int
                        ))
                    }
                }
                current = current.copy(sailInventory = sails)
                updated = true
            }
        }

        return Pair(current, updated)
    }

    private fun updateEngine(state: MarineState, instance: String, transform: (Engine) -> Engine): MarineState {
        val engines = state.engines.toMutableMap()
        val engine = engines[instance] ?: Engine(instance = instance)
        engines[instance] = transform(engine)
        return state.copy(engines = engines)
    }

    private fun updateBattery(state: MarineState, instance: String, transform: (Battery) -> Battery): MarineState {
        val batteries = state.batteries.toMutableMap()
        val battery = batteries[instance] ?: Battery(instance = instance)
        val meta = state.pathMeta["electrical.batteries.$instance"]
        val displayName = meta?.get("displayName") as? String
        batteries[instance] = transform(battery.copy(name = displayName ?: battery.name))
        return state.copy(batteries = batteries)
    }

    private fun updateCharger(state: MarineState, instance: String, transform: (Charger) -> Charger): MarineState {
        val chargers = state.chargers.toMutableMap()
        val charger = chargers[instance] ?: Charger(instance = instance)
        val meta = state.pathMeta["electrical.chargers.$instance"]
        val displayName = meta?.get("displayName") as? String
        chargers[instance] = transform(charger.copy(name = displayName ?: charger.name))
        return state.copy(chargers = chargers)
    }

    private fun updateInverter(state: MarineState, instance: String, transform: (Inverter) -> Inverter): MarineState {
        val inverters = state.inverters.toMutableMap()
        val inverter = inverters[instance] ?: Inverter(instance = instance)
        val meta = state.pathMeta["electrical.inverters.$instance"]
        val displayName = meta?.get("displayName") as? String
        inverters[instance] = transform(inverter.copy(name = displayName ?: inverter.name))
        return state.copy(inverters = inverters)
    }

    private fun updateTank(state: MarineState, instance: String, type: String, transform: (Tank) -> Tank): MarineState {
        val tanks = state.tanks.toMutableMap()
        val key = "$type.$instance"
        val tank = tanks[key] ?: Tank(instance = instance, type = type)
        val meta = state.pathMeta["tanks.$type.$instance"]
        val displayName = meta?.get("displayName") as? String
        tanks[key] = transform(tank.copy(name = displayName ?: tank.name))
        return state.copy(tanks = tanks)
    }

    private fun updatePypilotConfig(state: MarineState, field: String, value: Any?): MarineState {
        val config = state.pypilotConfig ?: PypilotConfig()
        val v = (value as? Number)?.toDouble()
        val next = when (field) {
            "p" -> config.copy(p = v)
            "i" -> config.copy(i = v)
            "d" -> config.copy(d = v)
            "dd" -> config.copy(dd = v)
            "pr" -> config.copy(pr = v)
            "ff" -> config.copy(ff = v)
            "wg" -> config.copy(wg = v)
            "deadzone" -> config.copy(deadzone = v)
            "profile" -> config.copy(activeProfile = value?.toString())
            else -> config
        }
        return state.copy(pypilotConfig = next)
    }

    private fun updatePypilotServo(state: MarineState, field: String, value: Any?): MarineState {
        val servo = state.pypilotServo ?: PypilotServoState()
        val v = (value as? Number)?.toDouble()
        val next = when (field) {
            "voltage" -> servo.copy(voltage = v)
            "current" -> servo.copy(current = v)
            "controllerTemp" -> servo.copy(controllerTemp = v)
            "motorTemp" -> servo.copy(motorTemp = v)
            "ampHours" -> servo.copy(ampHours = v)
            "runtime" -> servo.copy(runtime = v)
            "engagement" -> servo.copy(engagement = value?.toString())
            else -> servo
        }
        return state.copy(pypilotServo = next)
    }

    private fun updatePypilotCalibration(state: MarineState, field: String, value: Any?): MarineState {
        val cal = state.pypilotCalibration ?: PypilotCalibrationState()
        val v = (value as? Number)?.toDouble()
        val next = when (field) {
            "compassProgress" -> cal.copy(compassCalibrationProgress = v)
            "accelProgress" -> cal.copy(accelCalibrationProgress = v)
            "rudderProgress" -> cal.copy(rudderCalibrationProgress = v)
            "isCalibrating" -> {
                val s = value?.toString()?.lowercase(Locale.US)
                cal.copy(isCalibrating = s == "true" || s == "on" || s == "1" || value == true)
            }
            else -> cal
        }
        return state.copy(pypilotCalibration = next)
    }

    private fun updateWatermaker(state: MarineState, instance: String, transform: (Watermaker) -> Watermaker): MarineState {
        val watermakers = state.watermakers.toMutableMap()
        val watermaker = watermakers[instance] ?: Watermaker(instance = instance)
        watermakers[instance] = transform(watermaker)
        return state.copy(watermakers = watermakers)
    }
}
