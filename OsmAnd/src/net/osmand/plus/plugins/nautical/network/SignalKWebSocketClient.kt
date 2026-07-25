package net.osmand.plus.plugins.nautical.network

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import net.osmand.PlatformUtil
import okhttp3.*

class SignalKWebSocketClient(private val client: OkHttpClient) {
    private val log = PlatformUtil.getLog(SignalKWebSocketClient::class.java)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null
    private var isConnected = false

    var onConnectionFailure: (() -> Unit)? = null
    var onConnectionOpened: (() -> Unit)? = null

    private val _deltaFlow = MutableSharedFlow<DeltaMessage>(extraBufferCapacity = 64)
    val deltaFlow: SharedFlow<DeltaMessage> = _deltaFlow.asSharedFlow()

    fun connect(serverUrl: String, username: String? = null, password: String? = null) {
        if (isConnected) return

        val wsUrl = if (serverUrl.startsWith("http")) {
            serverUrl.replace("http://", "ws://").replace("https://", "wss://")
        } else {
            serverUrl
        }
        val fullUrl = if (wsUrl.contains("?")) {
            if (!wsUrl.contains("subscribe=none")) "$wsUrl&subscribe=none" else wsUrl
        } else {
            "$wsUrl/signalk/v1/stream?subscribe=none"
        }

        val requestBuilder = Request.Builder().url(fullUrl)
        if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            val credentials = Credentials.basic(username, password)
            requestBuilder.addHeader("Authorization", credentials)
        }

        webSocket = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                log.debug("SignalK WebSocket connected successfully")
                isConnected = true
                onConnectionOpened?.invoke()
                // Send Hello message
                val hello = """{"name":"OsmAnd-Nautical","version":"1.0.0"}"""
                webSocket.send(hello)

                // Send dynamic subscription filters for required paths
                sendSubscriptionFilters(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val delta = gson.fromJson(text, DeltaMessage::class.java)
                    if (delta != null) {
                        scope.launch {
                            _deltaFlow.emit(delta)
                        }
                    }
                } catch (e: Exception) {
                    log.error("Failed to parse SignalK delta message: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log.error("SignalK WebSocket failure: ${t.message}")
                isConnected = false
                onConnectionFailure?.invoke()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                log.debug("SignalK WebSocket closed: $reason")
                isConnected = false
            }
        })
    }

    private fun sendSubscriptionFilters(ws: WebSocket) {
        val paths = listOf(
            LivePerformanceData.PATH_STW,
            LivePerformanceData.PATH_TWS,
            LivePerformanceData.PATH_TWA,
            LivePerformanceData.PATH_POLAR_SPEED,
            LivePerformanceData.PATH_TARGET_ANGLE,
            LivePerformanceData.PATH_POLAR_SPEED_RATIO
        )

        val subscriptions = paths.joinToString(prefix = "[", postfix = "]") { path ->
            """{"path": "$path", "period": 1000}"""
        }
        val subscriptionMsg = """
            {
              "context": "vessels.self",
              "subscribe": $subscriptions
            }
        """.trimIndent()

        ws.send(subscriptionMsg)
        log.debug("Sent SignalK subscription filters for navigation and performance paths")
    }

    fun disconnect() {
        webSocket?.close(1000, "User requested disconnect")
        webSocket = null
        isConnected = false
    }
}
