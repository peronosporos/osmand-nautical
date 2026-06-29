package net.osmand.plus.plugins.nautical.engine

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.UUID
import kotlin.math.absoluteValue

// NEW: Data structure to hold AIS ships before we encode them
data class AisTarget(
    val mmsi: Int, // Enforced as Integer for strict NMEA compatibility
    var latitude: Double? = null,
    var longitude: Double? = null,
    var speedOverGround: Float? = null,
    var courseOverGround: Float? = null,
    var headingTrue: Float? = null
)

class SignalKEngine(private val connection: SignalKConnection) {

    var currentState = MarineState()
        private set

    // Phase 1 Callback (Our Boat)
    private var stateListener: ((MarineState) -> Unit)? = null
    // Phase 2 Callback (Other Ships)
    private var aisListener: ((AisTarget) -> Unit)? = null

    fun registerListener(listener: (MarineState) -> Unit) {
        this.stateListener = listener
    }

    fun registerAisListener(listener: (AisTarget) -> Unit) {
        this.aisListener = listener
    }

    fun handleIncomingMessage(jsonMessage: String) {
        try {
            val json = JSONObject(jsonMessage)
            if (!json.has("updates")) return

            val context = json.optString("context", "vessels.self")
            val updates = json.getJSONArray("updates")

            val isSelf = context == "vessels.self" || context == ""

            // Extract numeric MMSI
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
                        when (path) {
                            "navigation.position" -> {
                                if (valueObj is JSONObject) {
                                    val lat = valueObj.optDouble("latitude", Double.NaN)
                                    val lon = valueObj.optDouble("longitude", Double.NaN)
                                    if (!lat.isNaN() && !lon.isNaN()) {
                                        currentState = currentState.copy(latitude = lat, longitude = lon)
                                    }
                                }
                            }
                            "navigation.headingTrue" -> {
                                val heading = valueItem.optDouble("value", Double.NaN)
                                if (!heading.isNaN()) currentState = currentState.copy(headingTrue = heading)
                            }
                            "navigation.speedOverGround" -> {
                                val sog = valueItem.optDouble("value", Double.NaN)
                                if (!sog.isNaN()) currentState = currentState.copy(speedOverGround = sog)
                            }
                            "steering.autopilot.state" -> {
                                currentState = currentState.copy(autopilotState = valueItem.optString("value", "standby"))
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

            // Dispatching with thread separation
            if (isSelf) {
                // High priority: update navigation state immediately on calling thread
                stateListener?.invoke(currentState)
            } else if (aisTarget != null && aisTarget.latitude != null && aisTarget.longitude != null) {
                // Offload: AIS targets processed in background to save UI thread
                CoroutineScope(Dispatchers.Default).launch {
                    aisListener?.invoke(aisTarget)
                }
            }

        } catch (e: Exception) {
            // Log.e("SignalKEngine", "JSON parsing error", e)
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