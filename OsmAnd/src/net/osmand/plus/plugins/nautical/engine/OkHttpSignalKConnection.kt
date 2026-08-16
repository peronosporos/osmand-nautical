package net.osmand.plus.plugins.nautical.engine

import net.osmand.PlatformUtil
import okhttp3.*

class OkHttpSignalKConnection(private val client: OkHttpClient) : SignalKConnection {
    private val log = PlatformUtil.getLog(OkHttpSignalKConnection::class.java)

    override var url: String? = null
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var isConnecting = false
    private var lastLatencyMs: Long = 0
    private var lastPingTime: Long = 0

    fun isConnected(): Boolean = isConnected
    fun isConnecting(): Boolean = isConnecting

    override fun getLatencyMs(): Long = lastLatencyMs

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
        if (isConnecting || isConnected) return
        isConnecting = true
        this.url = url
        
        log.info("SignalK: Initiating WebSocket connection to $url (AuthToken: ${if (authToken != null) "SET" else "NONE"})")
        val requestBuilder = Request.Builder().url(url)
        if (!authToken.isNullOrEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $authToken")
        } else if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            val credentials = Credentials.basic(username, password)
            requestBuilder.addHeader("Authorization", credentials)
        }
        val request = requestBuilder.build()

        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    log.info("WebSocket Connected Successfully to $url! (Code: ${response.code})")
                    isConnected = true
                    isConnecting = false
                    lastPingTime = System.currentTimeMillis()

                    // Send the required SignalK Hello
                    val hello = """{"name":"OsmAnd-Nautical","version":"1.0.0"}"""
                    webSocket.send(hello)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    log.info("SignalK Ingress: ${text.take(120)}...")
                    if (text.contains("\"self\"") && (lastPingTime > 0)) {
                        lastLatencyMs = System.currentTimeMillis() - lastPingTime
                        lastPingTime = 0 // Reset until next heartbeat
                    }
                    onMessageReceived(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val isCleanDisconnection = t is java.net.SocketException || t is java.io.EOFException || (t.message?.contains("Socket closed", ignoreCase = true) == true)
                    if (isCleanDisconnection) {
                        log.warn("WebSocket Disconnected cleanly (${t.javaClass.simpleName}): ${t.message}")
                    } else {
                        log.error("WebSocket Failure: ${t.message} (Code: ${response?.code})", t)
                    }
                    isConnected = false
                    isConnecting = false
                    if (response?.code == 401) {
                        onAuthError?.invoke()
                    } else {
                        onFailure?.invoke()
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    try {
                        webSocket.close(1000, null)
                    } catch (e: Exception) {
                        log.warn("Exception closing WebSocket onClosing: ${e.message}")
                    }
                    log.info("WebSocket Closing: $code / $reason")
                    isConnected = false
                    isConnecting = false
                    onFailure?.invoke()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    log.info("WebSocket Closed: $code / $reason")
                    isConnected = false
                    isConnecting = false
                }
            },
        )
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

    override fun disconnect() {
        try {
            webSocket?.close(1000, "User requested disconnect")
        } catch (e: Exception) {
            log.warn("Exception closing WebSocket: ${e.message}")
        } finally {
            webSocket = null
            isConnected = false
            isConnecting = false
        }
    }
}
