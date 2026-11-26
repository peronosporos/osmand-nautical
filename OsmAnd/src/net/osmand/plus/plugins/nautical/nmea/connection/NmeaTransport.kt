package net.osmand.plus.plugins.nautical.nmea.connection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NmeaTransport {
    val connectionState: StateFlow<ConnectionState>
    val dataStream: Flow<String>

    /**
     * Stable identity of the physical device behind this transport, or null when the
     * transport is not tied to pluggable hardware. The multiplexer uses it to avoid
     * stacking duplicate clients for the same USB device.
     */
    val deviceId: String? get() = null

    fun connect()
    fun disconnect()
    
    /**
     * Synchronous emergency cleanup for process crashes.
     * Should only perform thread-safe, non-blocking I/O closure.
     */
    fun emergencyShutdown()
}
