package net.osmand.plus.plugins.nautical.engine

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.UUID
import kotlin.math.absoluteValue

data class AisTarget(
    val mmsi: Int,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var speedOverGround: Float? = null,
    var courseOverGround: Float? = null,
    var headingTrue: Float? = null
)

class SignalKEngine(private val connection: SignalKConnection) {

    // Nullable state to represent the "Uninitialized" or "Offline" condition
    private var _currentState: MarineState? = null

    // Public getter
    fun getCurrentState(): MarineState? = _currentState

    private var stateListener: ((MarineState) -> Unit)? = null
    private var aisListener: ((AisTarget) -> Unit)? = null
    private var trueSelfContext: String = "vessels.self"

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val depthBuffer = CircularBuffer<Double>(360) // 1 hour @ 10s intervals
    private val windBuffer = CircularBuffer<Double>(360)

    fun getDepthHistory(): List<Double> = depthBuffer.getAll()
    fun getWindHistory(): List<Double> = windBuffer.getAll()

    fun registerListener(listener: (MarineState) -> Unit) {
        this.stateListener = listener
    }

    fun registerAisListener(listener: (AisTarget) -> Unit) {
        this.aisListener = listener
    }

    fun unregisterListener(listener: (MarineState) -> Unit) {
        this.stateListener = null
    }

    fun handleIncomingMessage(jsonMessage: String) {
        try {
            val json = JSONObject(jsonMessage)

            if (json.has("self")) {
                trueSelfContext = json.getString("self")
                Log.d("NauticalPlugin", "Discovered true boat ID: $trueSelfContext")
                return
            }

            if (!json.has("updates")) return

            if (_currentState == null) _currentState = MarineState()

            val context = json.optString("context", "vessels.self")
            val updates = json.getJSONArray("updates")
            val isSelf = context == "vessels.self" || context == "" || context == trueSelfContext

            var numericMmsi = 0
            if (!isSelf) {
                val rawId = context.substringAfterLast(":", "")
                if (rawId.isEmpty()) return
                numericMmsi = rawId.toIntOrNull() ?: (rawId.hashCode().absoluteValue % 1000000000)
            }

            val aisTarget = if (!isSelf) AisTarget(numericMmsi) else null

            for (i in 0 until updates.length()) {
                val update = updates.getJSONObject(i)
                if (!update.has("values")) continue

                val values = update.getJSONArray("values")
                for (j in 0 until values.length()) {
                    val valueItem = values.getJSONObject(j)
                    val path = valueItem.optString("path")
                    val valueObj = valueItem.opt("value")

                    if (isSelf) {
                        val state = _currentState!!
                        when (path) {
                            "navigation.position" -> {
                                if (valueObj is JSONObject) {
                                    val lat = valueObj.optDouble("latitude", Double.NaN)
                                    val lon = valueObj.optDouble("longitude", Double.NaN)
                                    if (!lat.isNaN() && !lon.isNaN()) {
                                        _currentState = state.copy(latitude = lat, longitude = lon)
                                    }
                                }
                            }
                            "navigation.headingTrue" -> {
                                val heading = valueItem.optDouble("value", Double.NaN)
                                if (!heading.isNaN()) _currentState = state.copy(headingTrue = heading)
                            }
                            "navigation.speedOverGround" -> {
                                val sog = valueItem.optDouble("value", Double.NaN)
                                if (!sog.isNaN()) _currentState = state.copy(speedOverGround = sog)
                            }
                            "steering.autopilot.state" -> {
                                _currentState = state.copy(autopilotState = valueItem.optString("value", "standby"))
                            }
                            "environment.depth.belowTransducer" -> {
                                val depth = valueItem.optDouble("value", Double.NaN)
                                if (!depth.isNaN()) {
                                    _currentState = state.copy(depthBelowTransducer = depth)
                                    depthBuffer.add(depth)
                                }
                            }
                            "environment.wind.speedTrue" -> {
                                val wind = valueItem.optDouble("value", Double.NaN)
                                if (!wind.isNaN()) _currentState = state.copy(windSpeedTrue = wind)
                            }
                        }
                    } else if (aisTarget != null) {
                        when (path) {
                            "navigation.position" -> {
                                if (valueObj is JSONObject) {
                                    val lat = valueObj.optDouble("latitude", Double.NaN)
                                    val lon = valueObj.optDouble("longitude", Double.NaN)
                                    if (!lat.isNaN() && !lon.isNaN()) {
                                        aisTarget.latitude = lat
                                        aisTarget.longitude = lon
                                    }
                                }
                            }
                            "navigation.speedOverGround" -> {
                                val sog = valueItem.optDouble("value", Double.NaN)
                                if (!sog.isNaN()) aisTarget.speedOverGround = sog.toFloat()
                            }
                            "navigation.courseOverGroundTrue" -> {
                                val cog = valueItem.optDouble("value", Double.NaN)
                                if (!cog.isNaN()) aisTarget.courseOverGround = cog.toFloat()
                            }
                            "navigation.headingTrue" -> {
                                val heading = valueItem.optDouble("value", Double.NaN)
                                if (!heading.isNaN()) aisTarget.headingTrue = heading.toFloat()
                            }
                        }
                    }
                }
            }

            // Dispatch
            if (isSelf) {
                _currentState?.let { stateListener?.invoke(it) }
            } else if (aisTarget != null && aisTarget.latitude != null && aisTarget.longitude != null) {
                engineScope.launch {
                    aisListener?.invoke(aisTarget)
                }
            }
        } catch (e: Exception) {
            Log.e("SignalKEngine", "JSON parsing error: ${e.message}")
        }
    }

    fun changeAutopilotState(targetState: String) {
        val putRequest = """
        {
          "requestId": "${UUID.randomUUID()}",
          "context": "vessels.self",
          "put": {
            "path": "steering.autopilot.state",
            "value": "$targetState"
          }
        }
        """.trimIndent()

        connection.sendDelta(putRequest)
    }
}