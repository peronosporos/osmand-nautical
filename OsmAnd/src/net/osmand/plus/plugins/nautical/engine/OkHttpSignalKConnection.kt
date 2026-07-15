package net.osmand.plus.plugins.nautical.engine

import net.osmand.PlatformUtil
import okhttp3.*
import java.util.concurrent.TimeUnit

class OkHttpSignalKConnection(private val client: OkHttpClient) : SignalKConnection {
    private val log = PlatformUtil.getLog(OkHttpSignalKConnection::class.java)

    private var webSocket: WebSocket? = null
    private var isConnected = false

    fun isConnected(): Boolean = isConnected

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
                isConnected = true

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
                isConnected = false
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                log.debug("WebSocket Closing: $reason")
                isConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
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
        isConnected = false
    }
}
