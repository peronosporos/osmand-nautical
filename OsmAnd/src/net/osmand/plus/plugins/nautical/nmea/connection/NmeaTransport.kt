package net.osmand.plus.plugins.nautical.nmea.connection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NmeaTransport {
    val connectionState: StateFlow<ConnectionState>
    val dataStream: Flow<String>
    
    fun connect()
    fun disconnect()
    
    /**
     * Synchronous emergency cleanup for process crashes.
     * Should only perform thread-safe, non-blocking I/O closure.
     */
    fun emergencyShutdown()
}
