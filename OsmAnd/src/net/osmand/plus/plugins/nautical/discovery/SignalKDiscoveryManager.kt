package net.osmand.plus.plugins.nautical.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.Build
import androidx.core.content.ContextCompat
import net.osmand.PlatformUtil
import kotlin.time.Duration.Companion.milliseconds

/**
 * Manager for discovering Signal K servers on the local network using mDNS (NsdManager).
 */
class SignalKDiscoveryManager(private val context: Context) {
    private val log = PlatformUtil.getLog(SignalKDiscoveryManager::class.java)
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val discoveryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _discoveredServers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val discoveredServers: StateFlow<List<DiscoveredServer>> = _discoveredServers.asStateFlow()

    private var discoveryActive = false

    private val serviceTypes = listOf("_signalk-ws._tcp.", "_http._tcp.")

    private val discoveryListeners = java.util.concurrent.ConcurrentHashMap<String, NsdManager.DiscoveryListener>()

    @Synchronized
    fun startDiscovery() {
        if (discoveryActive) return
        discoveryActive = true
        _discoveredServers.value = emptyList()

        discoveryScope.launch {
            serviceTypes.forEach { serviceType ->
                val listener = createDiscoveryListener()
                discoveryListeners[serviceType] = listener
                try {
                    nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                } catch (e: Exception) {
                    log.error("Failed to start discovery for $serviceType: ${e.message}")
                }
            }
        }
    }

    @Synchronized
    fun stopDiscovery() {
        if (!discoveryActive) return
        discoveryActive = false

        discoveryScope.launch {
            discoveryListeners.forEach { (type, listener) ->
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (e: Exception) {
                    log.error("Failed to stop discovery for $type: ${e.message}")
                }
            }
            discoveryListeners.clear()
        }
    }

    private fun createDiscoveryListener() = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            log.debug("Service discovery started: $regType")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            log.debug("Service found: ${service.serviceName}")
            discoveryScope.launch {
                try {
                    withTimeoutOrNull(5000.milliseconds) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            val callback = object : NsdManager.ServiceInfoCallback {
                                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                                    log.error("Service info callback registration failed: $errorCode")
                                }

                                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                                    val host = serviceInfo.hostAddresses.firstOrNull()
                                    log.debug("Service resolved via callback: ${serviceInfo.serviceName} at ${host?.hostAddress}:${serviceInfo.port}")
                                    val discovered = DiscoveredServer(
                                        name = serviceInfo.serviceName,
                                        host = host?.hostAddress ?: "",
                                        port = serviceInfo.port,
                                        isWebSocket = serviceInfo.serviceType.contains("signalk-ws"),
                                    )
                                    addDiscoveredServer(discovered)
                                    // Callback remains registered until stopDiscovery or similar, but for one-off resolve:
                                    try { nsdManager.unregisterServiceInfoCallback(this) } catch (_: Exception) {}
                                }

                                override fun onServiceLost() {
                                    log.debug("Service lost via callback: ${service.serviceName}")
                                    removeDiscoveredServer(service.serviceName)
                                }

                                override fun onServiceInfoCallbackUnregistered() {
                                    log.debug("Service info callback unregistered: ${service.serviceName}")
                                }
                            }
                            nsdManager.registerServiceInfoCallback(service, ContextCompat.getMainExecutor(context), callback)
                        } else {
                            val resolveListener = object : NsdManager.ResolveListener {
                                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                    log.error("Resolve failed: $errorCode")
                                }

                                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                    @Suppress("DEPRECATION")
                                    val host = serviceInfo.host
                                    log.debug("Service resolved: ${serviceInfo.serviceName} at ${host?.hostAddress}:${serviceInfo.port}")
                                    val discovered = DiscoveredServer(
                                        name = serviceInfo.serviceName,
                                        host = host?.hostAddress ?: "",
                                        port = serviceInfo.port,
                                        isWebSocket = serviceInfo.serviceType.contains("signalk-ws"),
                                    )
                                    addDiscoveredServer(discovered)
                                }
                            }
                            @Suppress("DEPRECATION")
                            nsdManager.resolveService(service, resolveListener)
                        }
                    }
                } catch (e: Exception) {
                    log.error("Service resolution timed out or failed: ${e.message}")
                }
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            log.debug("Service lost: ${service.serviceName}")
            removeDiscoveredServer(service.serviceName)
        }

        override fun onDiscoveryStopped(regType: String) {
            log.debug("Discovery stopped: $regType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            log.error("Discovery failed to start: $errorCode")
            nsdManager.stopServiceDiscovery(this)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            log.error("Discovery failed to stop: $errorCode")
            nsdManager.stopServiceDiscovery(this)
        }
    }

    @Synchronized
    private fun addDiscoveredServer(server: DiscoveredServer) {
        val current = _discoveredServers.value.toMutableList()
        if (current.none { (it.host == server.host) && (it.port == server.port) }) {
            current.add(server)
            _discoveredServers.value = current.toList()
        }
    }

    @Synchronized
    private fun removeDiscoveredServer(serviceName: String) {
        val current = _discoveredServers.value.toMutableList()
        if (current.removeIf { it.name == serviceName }) {
            _discoveredServers.value = current.toList()
        }
    }
}
