package net.osmand.plus.plugins.nautical.engine

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class AisUdpEmitter {
    private var socket: DatagramSocket? = null
    private val targetPort = 10110

    private var scope: CoroutineScope? = null
    // The Funnel: Buffers incoming sentences so the main thread is never blocked
    private var messageChannel: Channel<String>? = null

    fun start() {
        if (scope != null) return // Prevent double-starts

        try {
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            // Use a buffered channel to prevent memory spikes if flooded with AIS targets
            messageChannel = Channel(Channel.BUFFERED)

            // Launch ONE single dedicated background worker
            scope?.launch {
                try {
                    // 1. Safe Network Setup: Resolve address and bind socket completely OFF the main thread
                    val localAddress = InetAddress.getByName("127.0.0.1")
                    socket = DatagramSocket()
                    Log.d("NauticalPlugin", "AIS UDP Emitter started on local port $targetPort")

                    // 2. The Consumer Loop: Reads from the funnel and transmits continuously
                    messageChannel?.consumeEach { nmeaSentence ->
                        try {
                            val payload = "$nmeaSentence\r\n".toByteArray(Charsets.UTF_8)
                            val packet = DatagramPacket(payload, payload.size, localAddress, targetPort)
                            socket?.send(packet)

                            Log.d("NauticalPlugin", "UDP TX -> $nmeaSentence")
                        } catch (e: Exception) {
                            Log.e("NauticalPlugin", "UDP Transmit Error: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NauticalPlugin", "UDP Socket Setup Error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("NauticalPlugin", "Failed to start UDP Emitter: ${e.message}")
        }

        try {
            socket = DatagramSocket()
            // UX Polish: Provide feedback
            Log.d("NauticalPlugin", "AIS UDP Emitter started on local port $targetPort")
        } catch (e: Exception) {
            // UX Polish: Clear user feedback
            Log.e("NauticalPlugin", "UDP Socket Setup Error: ${e.message}")
        }

    }

    fun emitNmeaSentence(nmeaSentence: String) {
        // The Producer: Instantly queue the message without spinning up new coroutines
        val result = messageChannel?.trySend(nmeaSentence)
        if (result?.isFailure == true) {
            Log.w("NauticalPlugin", "UDP Emitter buffer full, dropped sentence.")
        }
    }

    fun stop() {
        try {
            // Safely dismantle the scope, channel, and socket
            scope?.cancel()
            scope = null

            messageChannel?.close()
            messageChannel = null

            socket?.close()
            socket = null

            Log.d("NauticalPlugin", "AIS UDP Emitter stopped and resources released")
        } catch (e: Exception) {
            Log.e("NauticalPlugin", "Error stopping UDP Emitter: ${e.message}")
        }
    }
}