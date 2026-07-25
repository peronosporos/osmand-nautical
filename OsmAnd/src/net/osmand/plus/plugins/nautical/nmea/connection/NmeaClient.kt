package net.osmand.plus.plugins.nautical.nmea.connection

import kotlinx.coroutines.flow.SharedFlow

interface NmeaClient {
    val sentences: SharedFlow<String>
    val isConnected: SharedFlow<Boolean>
    
    fun connect()
    fun disconnect()
}
