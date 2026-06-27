package net.osmand.plus.plugins.nautical.engine

/**
 * Holds the current telemetry state of the vessel.
 */
data class MarineState(
    val name: String = "OwnShip",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val headingTrue: Double? = null, // In Radians (SignalK native unit)
    val speedOverGround: Double? = null, // In m/s (SignalK native unit)
    val autopilotState: String = "standby", // standby, auto, track
    val autopilotHeadingSet: Double? = null
)

/**
 * Interface for communicating with the server without binding to Android or OkHttp.
 */
interface SignalKConnection {
    fun connect(url: String, onMessageReceived: (String) -> Unit)
    fun sendDelta(jsonPayload: String)
    fun disconnect()
}