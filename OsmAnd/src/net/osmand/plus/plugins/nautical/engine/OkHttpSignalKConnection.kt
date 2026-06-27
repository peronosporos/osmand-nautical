package net.osmand.plus.plugins.nautical.engine

import android.util.Log
import okhttp3.*
import java.util.concurrent.TimeUnit

class OkHttpSignalKConnection : SignalKConnection {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep connection alive indefinitely
        .build()

    private var webSocket: WebSocket? = null

    override fun connect(url: String, onMessageReceived: (String) -> Unit) {
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessageReceived(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("NauticalNetwork", "WebSocket Failure: ${t.message}", t)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                Log.d("NauticalNetwork", "WebSocket Closing: $reason")
            }
        })
    }

    override fun sendDelta(jsonPayload: String) {
        val success = webSocket?.send(jsonPayload) ?: false
        if (!success) {
            Log.e("NauticalNetwork", "Failed to send payload. Transmit buffer full or socket closed.")
        }
    }

    override fun disconnect() {
        webSocket?.close(1000, "User requested disconnect")
        webSocket = null
    }
}