package net.osmand.plus.plugins.nautical.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin

/**
 * mDNS Discovery for Signal K servers on the local network.
 */
class SignalKDiscovery(private val app: OsmandApplication) {
    private val log = PlatformUtil.getLog(SignalKDiscovery::class.java)
    private val nsdManager = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    
    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            log.info("Signal K mDNS Discovery started for $regType")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            log.info("Signal K Service candidate found: ${service.serviceName}")
            if (service.serviceType.contains("_signalk-ws") || service.serviceType.contains("_signalk-http")) {
                nsdManager.resolveService(service, createResolveListener())
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            log.info("Signal K Service lost: ${service.serviceName}")
        }

        override fun onDiscoveryStopped(regType: String) {
            log.info("Signal K mDNS Discovery stopped")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            log.error("Signal K mDNS Discovery start failed: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            log.error("Signal K mDNS Discovery stop failed: $errorCode")
        }
    }

    private fun createResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            log.error("Signal K Service Resolve failed: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val host = serviceInfo.host.hostAddress
            val port = serviceInfo.port
            log.info("Signal K Service resolved: $host:$port")

            val currentIp = app.settings.NAUTICAL_SERVER_IP.get()
            if (currentIp.isEmpty()) {
                app.settings.NAUTICAL_SERVER_IP.set(host)
                app.settings.NAUTICAL_SERVER_PORT.set(port.toString())
                log.info("Signal K auto-configured to $host:$port")
                NauticalPlugin.getInstance()?.reconnect()
            } else if (currentIp == host) {
                log.info("Signal K Discovery: Resolved host matches current configuration. Skipping.")
            }
        }
    }

    fun start() {
        try {
            nsdManager.discoverServices("_signalk-ws._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            log.error("Failed to start Signal K mDNS discovery: ${e.message}")
        }
    }

    fun stop() {
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (_: Exception) {
        }
    }
}
