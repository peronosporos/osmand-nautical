package net.osmand.plus.plugins.nautical.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.osmand.PlatformUtil

/**
 * Manager for discovering Signal K servers on the local network using mDNS (NsdManager).
 */
class SignalKDiscoveryManager(context: Context) {
    private val log = PlatformUtil.getLog(SignalKDiscoveryManager::class.java)
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _discoveredServers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val discoveredServers: StateFlow<List<DiscoveredServer>> = _discoveredServers.asStateFlow()

    private var discoveryActive = false

    private val serviceTypes = listOf("_signalk-ws._tcp.", "_http._tcp.")

    private val discoveryListeners = mutableMapOf<String, NsdManager.DiscoveryListener>()

    fun startDiscovery() {
        if (discoveryActive) return
        discoveryActive = true
        _discoveredServers.value = emptyList()

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

    fun stopDiscovery() {
        if (!discoveryActive) return
        discoveryActive = false

        discoveryListeners.forEach { (type, listener) ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                log.error("Failed to stop discovery for $type: ${e.message}")
            }
        }
        discoveryListeners.clear()
    }

    private fun createDiscoveryListener() = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            log.debug("Service discovery started: $regType")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            log.debug("Service found: ${service.serviceName}")
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    log.error("Resolve failed: $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    log.debug("Service resolved: ${serviceInfo.serviceName} at ${serviceInfo.host}:${serviceInfo.port}")
                    val discovered = DiscoveredServer(
                        name = serviceInfo.serviceName,
                        host = serviceInfo.host.hostAddress ?: "",
                        port = serviceInfo.port,
                        isWebSocket = serviceInfo.serviceType.contains("signalk-ws")
                    )
                    addDiscoveredServer(discovered)
                }
            })
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
        if (current.none { it.host == server.host && it.port == server.port }) {
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
