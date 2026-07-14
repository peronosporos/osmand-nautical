package net.osmand.plus.plugins.nautical.engine

import net.osmand.PlatformUtil
import okhttp3.*
import java.util.concurrent.TimeUnit

class OkHttpSignalKConnection : SignalKConnection {
    private val log = PlatformUtil.getLog(OkHttpSignalKConnection::class.java)

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep connection alive indefinitely
        .build()

    private var webSocket: WebSocket? = null

    fun isConnected(): Boolean {
        return webSocket != null
    }

    override fun connect(
        url: String,
        username: String?,
        password: String?,
        onMessageReceived: (String) -> Unit,
    ) {
        val requestBuilder = Request.Builder().url(url)
        if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            val credentials = Credentials.basic(username, password)
            requestBuilder.addHeader("Authorization", credentials)
        }
        val request = requestBuilder.build()

        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                log.debug("WebSocket Connected Successfully!")

                // Send the required SignalK Hello
                val hello = """{"name":"OsmAnd-Nautical","version":"1.0.0"}"""
                webSocket.send(hello)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                log.debug("Message received: $text")
                onMessageReceived(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log.error("WebSocket Failure: ${t.message}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                log.debug("WebSocket Closing: $reason")
            }
        },
        )
    }

    override fun sendDelta(jsonPayload: String) {
        val success = webSocket?.send(jsonPayload) ?: false
        if (!success) {
            log.error("Failed to send payload. Transmit buffer full or socket closed.")
        }
    }

    override fun disconnect() {
        webSocket?.close(1000, "User requested disconnect")
        webSocket = null
    }
}
