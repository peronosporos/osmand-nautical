package net.osmand.plus.plugins.nautical.nmea.connection

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.PlatformUtil
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

/**
 * Base implementation of NmeaTransport with centralized state management and 
 * exponential backoff reconnection.
 */
abstract class AbstractNmeaTransport(
    protected val scope: CoroutineScope
) : NmeaTransport {
    private val log = PlatformUtil.getLog(AbstractNmeaTransport::class.java)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState = _connectionState.asStateFlow()

    private val _dataStream = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val dataStream = _dataStream.asSharedFlow()

    private var transportJob: Job? = null
    protected var isRunning = false

    override fun connect() {
        if (isRunning) return
        isRunning = true
        
        transportJob = scope.launch {
            var attempt = 0
            while (isActive && isRunning) {
                try {
                    if (attempt > 0) {
                        _connectionState.value = ConnectionState.RECONNECTING
                    } else {
                        _connectionState.value = ConnectionState.CONNECTING
                    }
                    
                    runTransport { sentence ->
                        _connectionState.value = ConnectionState.CONNECTED
                        attempt = 0 // Reset on success
                        _dataStream.emit(sentence)
                    }
                    
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    log.error("Transport error: ${e.message}")
                } finally {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }

                if (isRunning && isActive) {
                    val delayMs = min(30000L, (1000L * (1 shl attempt.coerceAtMost(10))))
                    log.info("Attempting reconnection in ${delayMs}ms (attempt $attempt)")
                    delay(delayMs.milliseconds)
                    attempt++
                }
            }
        }
    }

    override fun disconnect() {
        isRunning = false
        transportJob?.cancel()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override fun emergencyShutdown() {
        // Default no-op
    }

    /**
     * Implementing classes should perform the actual I/O here.
     * The callback should be invoked for every received sentence.
     * When this function returns or throws, the reconnection logic kicks in.
     */
    protected abstract suspend fun runTransport(onSentence: suspend (String) -> Unit)
}
