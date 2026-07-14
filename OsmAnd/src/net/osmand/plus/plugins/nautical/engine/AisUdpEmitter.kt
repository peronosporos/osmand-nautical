package net.osmand.plus.plugins.nautical.engine

import net.osmand.PlatformUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class AisUdpEmitter {
    private val log = PlatformUtil.getLog(AisUdpEmitter::class.java)
    private var socket: DatagramSocket? = null
    private val targetPort = 10110
    private var scope: CoroutineScope? = null
    private var messageChannel: Channel<String>? = null

    fun start() {
        if (scope != null) return

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        messageChannel = Channel(Channel.BUFFERED)

        scope?.launch {
            try {
                // Initialize socket exactly once inside the scope
                socket = DatagramSocket()
                val localAddress = InetAddress.getByName("127.0.0.1")
                log.debug("AIS UDP Emitter started on local port $targetPort")

                // The Consumer Loop: Reads from the funnel and transmits continuously
                messageChannel?.consumeEach { nmeaSentence ->
                    try {
                        val payload = "$nmeaSentence\r\n".toByteArray(Charsets.UTF_8)
                        val packet = DatagramPacket(payload, payload.size, localAddress, targetPort)
                        socket?.send(packet)

                        // Debug log to confirm it's actually leaving the plugin
                        log.debug("UDP TX -> $nmeaSentence")
                    } catch (e: Exception) {
                        log.error("UDP Transmit Error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                log.error("UDP Socket Setup Error: ${e.message}")
            }
        }
    }

    fun emitNmeaSentence(nmeaSentence: String) {
        val result = messageChannel?.trySend(nmeaSentence)
        if (result?.isFailure == true) {
            log.warn("UDP Emitter buffer full, dropped sentence.")
        }
    }

    fun stop() {
        try {
            scope?.cancel()
            scope = null

            messageChannel?.close()
            messageChannel = null

            socket?.close()
            socket = null

            log.debug("AIS UDP Emitter stopped and resources released")
        } catch (e: Exception) {
            log.error("Error stopping UDP Emitter: ${e.message}")
        }
    }
}
