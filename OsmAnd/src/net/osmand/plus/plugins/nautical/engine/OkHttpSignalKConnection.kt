package net.osmand.plus.plugins.nautical.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.osmand.PlatformUtil
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class OkHttpSignalKConnection(private val client: OkHttpClient) : SignalKConnection {
    private val log = PlatformUtil.getLog(OkHttpSignalKConnection::class.java)

    override var url: String? = null
    private var webSocket: WebSocket? = null

    private val isConnectedFlag = AtomicBoolean(false)
    private val isConnectingFlag = AtomicBoolean(false)
    private val isExplicitlyDisconnected = AtomicBoolean(false)

    private val lastLatencyMs = AtomicLong(0L)
    private val lastPingTime = AtomicLong(0L)
    private val lastMessageTimeMs = AtomicLong(0L)

    private var reconnectAttempt = 0

    private var connectionScope: CoroutineScope? = null
    private var watchdogJob: Job? = null
    private var reconnectJob: Job? = null

    // Cached connection parameters for automatic backoff reconnection
    private var cachedUsername: String? = null
    private var cachedPassword: String? = null
    private var cachedAuthToken: String? = null
    private var cachedOnFailure: (() -> Unit)? = null
    private var cachedOnAuthError: (() -> Unit)? = null
    private var cachedOnMessageReceived: ((String) -> Unit)? = null

    companion object {
        const val WATCHDOG_TIMEOUT_MS = 5000L
        const val INITIAL_BACKOFF_MS = 1000L
        const val MAX_BACKOFF_MS = 30000L
    }

    fun isConnected(): Boolean = isConnectedFlag.get()
    fun isConnecting(): Boolean = isConnectingFlag.get()

    override fun getLatencyMs(): Long = lastLatencyMs.get()

    @Synchronized
    override fun connect(
        url: String,
        username: String?,
        password: String?,
        onFailure: (() -> Unit)?,
        onAuthError: (() -> Unit)?,
        onMessageReceived: (String) -> Unit,
    ) {
        connect(url, username, password, null, onFailure, onAuthError, onMessageReceived)
    }

    @Synchronized
    fun connect(
        url: String,
        username: String?,
        password: String?,
        authToken: String?,
        onFailure: (() -> Unit)?,
        onAuthError: (() -> Unit)?,
        onMessageReceived: (String) -> Unit,
    ) {
        this.url = url
        this.cachedUsername = username
        this.cachedPassword = password
        this.cachedAuthToken = authToken
        this.cachedOnFailure = onFailure
        this.cachedOnAuthError = onAuthError
        this.cachedOnMessageReceived = onMessageReceived
        this.isExplicitlyDisconnected.set(false)
        this.reconnectAttempt = 0

        ensureScope()
        connectInternal()
    }

    private fun ensureScope() {
        if (connectionScope == null || !connectionScope!!.isActive) {
            connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
    }

    @Synchronized
    private fun connectInternal() {
        if (isConnectingFlag.get() || isConnectedFlag.get() || isExplicitlyDisconnected.get()) return
        val targetUrl = url ?: return

        isConnectingFlag.set(true)
        log.info("SignalK: Connecting to $targetUrl (Attempt: #$reconnectAttempt)")

        val requestBuilder = Request.Builder().url(targetUrl)
        if (!cachedAuthToken.isNullOrEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $cachedAuthToken")
        } else if (!cachedUsername.isNullOrEmpty() && !cachedPassword.isNullOrEmpty()) {
            val credentials = Credentials.basic(cachedUsername!!, cachedPassword!!)
            requestBuilder.addHeader("Authorization", credentials)
        }
        val request = requestBuilder.build()

        try {
            webSocket = client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, response: Response) {
                        log.info("SignalK WebSocket Connected to $targetUrl (Code: ${response.code})")
                        isConnectedFlag.set(true)
                        isConnectingFlag.set(false)
                        reconnectAttempt = 0
                        reconnectJob?.cancel()

                        val now = System.currentTimeMillis()
                        lastPingTime.set(now)
                        lastMessageTimeMs.set(now)

                        // Start delta watchdog monitoring
                        startWatchdog()

                        // Send required Signal K Hello handshake
                        val hello = """{"name":"OsmAnd-Nautical","version":"1.0.0"}"""
                        ws.send(hello)
                    }

                    override fun onMessage(ws: WebSocket, text: String) {
                        lastMessageTimeMs.set(System.currentTimeMillis())
                        val ping = lastPingTime.get()
                        if (text.contains("\"self\"") && ping > 0) {
                            lastLatencyMs.set(System.currentTimeMillis() - ping)
                            lastPingTime.set(0L)
                        }
                        cachedOnMessageReceived?.invoke(text)
                    }

                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                        val isCleanDisconnection = t is java.net.SocketException || t is java.io.EOFException || (t.message?.contains("Socket closed", ignoreCase = true) == true)
                        if (isCleanDisconnection) {
                            log.warn("SignalK WebSocket Disconnected: ${t.message}")
                        } else {
                            log.error("SignalK WebSocket Failure: ${t.message} (Code: ${response?.code})", t)
                        }

                        isConnectedFlag.set(false)
                        isConnectingFlag.set(false)
                        watchdogJob?.cancel()

                        if (response?.code == 401) {
                            cachedOnAuthError?.invoke()
                        } else {
                            cachedOnFailure?.invoke()
                            scheduleReconnect()
                        }
                    }

                    override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                        try {
                            ws.close(1000, null)
                        } catch (e: Exception) {
                            log.warn("Exception closing WebSocket onClosing: ${e.message}")
                        }
                        log.info("SignalK WebSocket Closing: $code / $reason")
                        isConnectedFlag.set(false)
                        isConnectingFlag.set(false)
                        watchdogJob?.cancel()
                        cachedOnFailure?.invoke()
                        scheduleReconnect()
                    }

                    override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                        log.info("SignalK WebSocket Closed: $code / $reason")
                        isConnectedFlag.set(false)
                        isConnectingFlag.set(false)
                        watchdogJob?.cancel()
                    }
                },
            )
        } catch (e: Exception) {
            log.error("SignalK Exception creating WebSocket: ${e.message}", e)
            isConnectingFlag.set(false)
            isConnectedFlag.set(false)
            cachedOnFailure?.invoke()
            scheduleReconnect()
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = connectionScope?.launch {
            while (isActive && isConnectedFlag.get()) {
                delay(1000L)
                if (isConnectedFlag.get()) {
                    val lastMsg = lastMessageTimeMs.get()
                    val elapsed = System.currentTimeMillis() - lastMsg
                    if (lastMsg > 0 && elapsed > WATCHDOG_TIMEOUT_MS) {
                        log.warn("SignalK Watchdog: No message received for ${elapsed}ms (> ${WATCHDOG_TIMEOUT_MS}ms). Forcing socket reconnect cycle...")
                        forceReconnect()
                        break
                    }
                }
            }
        }
    }

    private fun forceReconnect() {
        try {
            webSocket?.cancel()
        } catch (_: Exception) {}
        isConnectedFlag.set(false)
        isConnectingFlag.set(false)
        watchdogJob?.cancel()
        cachedOnFailure?.invoke()
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (isExplicitlyDisconnected.get()) return
        reconnectJob?.cancel()
        reconnectAttempt++
        val delayMs = calculateBackoffDelayMs(reconnectAttempt)
        log.info("SignalK: Reconnect attempt #$reconnectAttempt scheduled in ${delayMs}ms")

        ensureScope()
        reconnectJob = connectionScope?.launch {
            delay(delayMs)
            if (!isExplicitlyDisconnected.get()) {
                connectInternal()
            }
        }
    }

    private fun calculateBackoffDelayMs(attempt: Int): Long {
        val exponentialFactor = 1L shl (attempt - 1).coerceIn(0, 5)
        val baseDelay = (INITIAL_BACKOFF_MS * exponentialFactor).coerceAtMost(MAX_BACKOFF_MS)
        // +/- 20% random jitter (0.80 to 1.20)
        val jitter = 0.8 + (Math.random() * 0.4)
        return (baseDelay * jitter).toLong().coerceIn(500L, 36000L)
    }

    override fun sendDelta(jsonPayload: String) {
        try {
            val success = webSocket?.send(jsonPayload) ?: false
            if (!success) {
                log.warn("Failed to send payload. Transmit buffer full or socket closed.")
            }
        } catch (e: Exception) {
            log.warn("Exception sending delta payload: ${e.message}")
        }
    }

    @Synchronized
    override fun disconnect() {
        isExplicitlyDisconnected.set(true)
        reconnectJob?.cancel()
        watchdogJob?.cancel()
        connectionScope?.cancel()
        connectionScope = null
        try {
            webSocket?.close(1000, "User requested disconnect")
        } catch (e: Exception) {
            log.warn("Exception closing WebSocket: ${e.message}")
        } finally {
            webSocket = null
            isConnectedFlag.set(false)
            isConnectingFlag.set(false)
            reconnectAttempt = 0
        }
    }
}
