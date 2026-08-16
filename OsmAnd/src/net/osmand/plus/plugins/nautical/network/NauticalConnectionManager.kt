package net.osmand.plus.plugins.nautical.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.engine.ConnectionStatus
import net.osmand.plus.plugins.nautical.engine.OkHttpSignalKConnection
import net.osmand.plus.plugins.nautical.engine.SignalKEngine
import net.osmand.plus.settings.backend.SettingsScreenType
import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.math.min
import kotlin.math.pow

class NauticalConnectionManager(
    private val app: OsmandApplication,
    private val engineProvider: () -> SignalKEngine?
) {
    private val log = PlatformUtil.getLog(NauticalConnectionManager::class.java)

    var okHttpClient: OkHttpClient? = null
        private set
    var connection: OkHttpSignalKConnection? = null
        private set
    private var lastUsedTrustAll: Boolean? = null

    val retryHandler = Handler(Looper.getMainLooper())
    var retryAttempt = 0
    val retryRunnable: Runnable = Runnable { startEngine() }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            debounceReconnect("Network interface became available")
        }

        override fun onLost(network: Network) {
            debounceReconnect("Network interface lost")
        }

        private fun debounceReconnect(reason: String) {
            retryHandler.removeCallbacks(retryRunnable)
            retryHandler.postDelayed({
                log.info("Nautical: Triggering reconnection due to: $reason")
                reconnect()
            }, 1000)
        }
    }

    fun initConnection() {
        val trustAll = app.settings.NAUTICAL_TRUST_ALL_CERTIFICATES.get()
        var client = okHttpClient
        if ((client == null) || (lastUsedTrustAll != trustAll)) {
            client = createHttpClient(trustAll)
            okHttpClient = client
            lastUsedTrustAll = trustAll
            SailingDependencyContainer.setOkHttpClient(client)
        }
        connection = OkHttpSignalKConnection(client)
    }

    fun createHttpClient(trustAll: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
        builder.connectTimeout(java.time.Duration.ofSeconds(5))
        builder.readTimeout(java.time.Duration.ofSeconds(10))
        builder.writeTimeout(java.time.Duration.ofSeconds(10))
        builder.pingInterval(java.time.Duration.ofSeconds(30))

        builder.addInterceptor { chain ->
            val token = app.settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.get()
            val request = if (token.isNotBlank()) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as java.security.KeyStore?)
        val defaultTrustManager = factory.trustManagers[0] as X509TrustManager

        val trustManager = if (trustAll) {
            log.warn("Nautical: Using trust-all SSL configuration. Security is reduced.")
            NauticalTrustManager(defaultTrustManager)
        } else {
            defaultTrustManager
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), java.security.SecureRandom())
        builder.sslSocketFactory(sslContext.socketFactory, trustManager)

        if (trustAll) {
            builder.hostnameVerifier { _, _ -> true }
        }

        return builder.build()
    }

    @Synchronized
    fun startEngine(connectionRestoredListener: () -> Unit = {}) {
        val rawIp = app.settings.NAUTICAL_SERVER_IP.get()?.trim() ?: ""
        if (rawIp.isEmpty()) return

        val host = rawIp.substringAfter("://").substringBefore("/").substringBefore(":")
        val port = if (rawIp.substringAfter("://").contains(":")) {
            rawIp.substringAfter("://").substringAfter(":").substringBefore("/")
        } else {
            app.settings.NAUTICAL_SERVER_PORT.get()?.trim()?.ifEmpty { "3000" } ?: "3000"
        }
        val useSecure = app.settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        val protocol = if (useSecure) "wss" else "ws"
        val wsUrl = "$protocol://$host:$port/signalk/v1/stream?subscribe=all"
        val username = app.settings.NAUTICAL_SERVER_USERNAME.get()
        val password = app.settings.NAUTICAL_SERVER_PASSWORD.get()

        if (host.isEmpty()) return

        val currentConn = connection
        if (currentConn != null && (currentConn.isConnected() || currentConn.isConnecting())) {
            if (currentConn.url == wsUrl) {
                log.info("SignalK: Already connected or connecting to $wsUrl. Skipping.")
                return
            }
            log.info("SignalK: Host changed from ${currentConn.url} to $wsUrl. Reconnecting...")
            currentConn.disconnect()
        }

        initConnection()
        connection?.url = wsUrl
        val engine = engineProvider()
        engine?.dataBroker?.updateState { it.copy(connectionStatus = ConnectionStatus.CONNECTING) }

        val authToken = app.settings.NAUTICAL_SIGNAL_K_AUTH_TOKEN.get()

        val failureCallback = {
            retryHandler.removeCallbacks(retryRunnable)
            val delayMs = min(1000L * (2.0.pow(retryAttempt.toDouble()).toLong()), 60000L)
            retryHandler.postDelayed(retryRunnable, delayMs)
            retryAttempt++
            Unit
        }

        val authErrorCallback = {
            engine?.dataBroker?.updateState { it.copy(connectionStatus = ConnectionStatus.UNAUTHORIZED) }
            app.runInUIThread {
                NauticalPlugin.hudManager?.get()?.showBanner(
                    app.getString(R.string.nautical_auth_token_required),
                    15000L,
                    label = app.getString(R.string.shared_string_settings),
                    isWarning = true,
                    onConfirm = {
                        val activity = app.osmandMap?.mapView?.mapActivity
                        if (activity != null) {
                            net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(activity, SettingsScreenType.NAUTICAL_SETTINGS)
                        }
                    }
                )
            }
        }

        engine?.let { e ->
            e.onConnectionLost = failureCallback
            e.onConnectionError = failureCallback
            e.onAuthError = authErrorCallback
            e.onConnectionRestored = connectionRestoredListener
            e.vesselDraft = app.settings.NAUTICAL_VESSEL_DRAFT.get().toDouble()
            e.corridorWidthNm = app.settings.NAUTICAL_CORRIDOR_WIDTH.get().toDouble()
            e.safetyCorridorBufferNm = app.settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.get().toDouble()
        }

        connection?.connect(wsUrl, username, password, authToken, failureCallback, authErrorCallback) { message ->
            engine?.handleIncomingMessage(message)
        }
    }

    fun reconnect() {
        retryAttempt = 0
        connection?.disconnect()
        startEngine()
    }

    fun disconnect() {
        retryHandler.removeCallbacks(retryRunnable)
        connection?.disconnect()
    }

    fun registerNetworkCallback() {
        try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            log.error("Failed to register network callback: ${e.message}")
        }
    }

    fun unregisterNetworkCallback() {
        try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            log.error("Failed to unregister network callback: ${e.message}")
        }
    }

    @SuppressLint("CustomX509TrustManager")
    private class NauticalTrustManager(
        private val delegate: X509TrustManager
    ) : X509TrustManager {

        @SuppressLint("TrustAllX509TrustManager")
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

        @SuppressLint("TrustAllX509TrustManager")
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}

        override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
    }
}
