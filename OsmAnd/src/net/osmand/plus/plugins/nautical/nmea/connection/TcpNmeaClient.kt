package net.osmand.plus.plugins.nautical.nmea.connection

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.osmand.PlatformUtil
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.min

class TcpNmeaClient(
    private val host: String,
    private val port: Int,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : NmeaClient {
    private val log = PlatformUtil.getLog(TcpNmeaClient::class.java)

    private val _sentences = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val sentences = _sentences.asSharedFlow()

    private val _isConnected = MutableSharedFlow<Boolean>(replay = 1)
    override val isConnected = _isConnected.asSharedFlow()

    private var job: Job? = null
    private var isRunning = false

    override fun connect() {
        if (isRunning) return
        isRunning = true
        job = scope.launch {
            var attempt = 0
            while (isActive && isRunning) {
                try {
                    _isConnected.emit(false)
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, port), 5000)
                    _isConnected.emit(true)
                    attempt = 0
                    
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    while (isActive && isRunning) {
                        val line = reader.readLine() ?: break
                        _sentences.emit(line)
                    }
                    socket.close()
                } catch (e: Exception) {
                    log.error("NMEA TCP connection error: ${e.message}", e)
                    _isConnected.emit(false)
                    val delayMs = min(30000L, (1000L * (1 shl attempt)))
                    delay(delayMs)
                    attempt++
                }
            }
        }
    }

    override fun disconnect() {
        isRunning = false
        job?.cancel()
        _isConnected.tryEmit(false)
    }
}
