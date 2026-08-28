package net.osmand.plus.plugins.nautical.network

import com.google.gson.Gson
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.*
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
    private var connectionScope: CoroutineScope? = null

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private val reconnectCeilingMs = 60000L
    private val reconnectBaseMs = 1000L

    var onConnectionFailure: (() -> Unit)? = null
    var onConnectionOpened: (() -> Unit)? = null

    private val _deltaFlow = MutableSharedFlow<DeltaMessage>(extraBufferCapacity = 64)
        val deltaFlow: SharedFlow<DeltaMessage> = _deltaFlow.asSharedFlow()

    fun connect(serverUrl: String, username: String? = null, password: String? = null, authToken: String? = null) {
        if (isConnected) return
        log.debug("Connecting to SignalK. Delta flow has ${deltaFlow.replayCache.size} items in cache")
        connectionScope?.cancel()
        connectionScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

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
        if (!authToken.isNullOrEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $authToken")
        } else if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            val credentials = Credentials.basic(username, password)
            requestBuilder.addHeader("Authorization", credentials)
        }

        webSocket = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                log.debug("SignalK WebSocket connected successfully")
                isConnected = true
                reconnectAttempt = 0
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
                        connectionScope?.launch {
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
                
                // Exponential Backoff Reconnect
                scheduleReconnect(serverUrl, username, password, authToken)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                log.debug("SignalK WebSocket closed: $reason")
                isConnected = false
            }
        })
    }

    private fun scheduleReconnect(serverUrl: String, username: String?, password: String?, authToken: String?) {
        if (reconnectJob?.isActive == true) return
        
        val delayMs = min(reconnectBaseMs * 2.0.pow(reconnectAttempt).toLong(), reconnectCeilingMs)
        log.info("Scheduling WebSocket reconnect in ${delayMs}ms (attempt $reconnectAttempt)")
        // Use persistent independent scope instead of transient connectionScope which gets cancelled on disconnect/failure
        reconnectJob = scope.launch {
            delay(delayMs.milliseconds)
            reconnectAttempt++
            connect(serverUrl, username, password, authToken)
        }
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
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "User requested disconnect")
        webSocket = null
        isConnected = false
        connectionScope?.cancel()
        connectionScope = null
    }
}
