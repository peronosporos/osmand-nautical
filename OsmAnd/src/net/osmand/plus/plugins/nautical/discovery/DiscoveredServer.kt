package net.osmand.plus.plugins.nautical.discovery

/**
 * Data class representing a discovered Signal K server on the local network.
 */
data class DiscoveredServer(
    val name: String,
    val host: String,
    val port: Int,
    val isWebSocket: Boolean
) {
    override fun toString(): String {
        val scheme = if (isWebSocket) "ws" else "http"
        return "$name ($scheme://$host:$port)"
    }
}
