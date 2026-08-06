package net.osmand.plus.plugins.nautical.nmea.connection

import kotlinx.coroutines.*
import net.osmand.PlatformUtil
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

class TcpNmeaClient(
    private val host: String,
    private val port: Int,
    scope: CoroutineScope
) : AbstractNmeaTransport(scope) {
    private val log = PlatformUtil.getLog(TcpNmeaClient::class.java)
    @Volatile
    private var activeSocket: Socket? = null
    @Volatile
    private var activeReader: BufferedReader? = null

    override suspend fun runTransport(onSentence: suspend (String) -> Unit) {
        withContext(Dispatchers.IO) {
            val socket = Socket()
            activeSocket = socket
            val completionHandle = coroutineContext.job.invokeOnCompletion {
                try { socket.close() } catch (_: Exception) {}
            }
            try {
                socket.soTimeout = 5000
                socket.connect(InetSocketAddress(host, port), 5000)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                activeReader = reader
                while (isActive && isRunning) {
                    try {
                        val line = reader.readLine() ?: break
                        onSentence(line)
                    } catch (_: java.net.SocketTimeoutException) {
                        log.debug("TCP NMEA read timeout, retrying...")
                        continue
                    }
                }
            } finally {
                completionHandle.dispose()
                // Explicitly close reader and drop reference to flush internal buffers and prevent stale data leakage
                try {
                    activeReader?.close()
                } catch (_: Exception) {}
                activeReader = null
                activeSocket = null
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    override fun emergencyShutdown() {
        try {
            activeReader?.close()
        } catch (_: Exception) {}
        try {
            activeSocket?.close()
        } catch (_: Exception) {}
        finally {
            activeReader = null
            activeSocket = null
        }
    }
}
