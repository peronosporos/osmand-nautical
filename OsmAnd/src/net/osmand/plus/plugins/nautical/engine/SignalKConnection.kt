package net.osmand.plus.plugins.nautical.engine

/**
 * Interface for communicating with the server without binding to Android or OkHttp.
 */
interface SignalKConnection {
    fun connect(url: String, onMessageReceived: (String) -> Unit)
    fun sendDelta(jsonPayload: String)
    fun disconnect()
}