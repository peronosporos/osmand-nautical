package net.osmand.shared.api

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CFNetwork.CFNetworkCopySystemProxySettings
import platform.CFNetwork.kCFNetworkProxiesHTTPProxy
import platform.CoreFoundation.CFDictionaryContainsKey
import platform.CoreFoundation.CFRelease

@OptIn(ExperimentalForeignApi::class)
internal class IosNetworkProxyState : NetworkProxyState {

	override val proxyHost: String?
		get() = null

	override val proxyPort: Int
		get() = 0

	override val ktorProxyData: NetworkProxyData?
		get() = null

	override fun hasProxy(): Boolean {
		val httpProxyKey = kCFNetworkProxiesHTTPProxy ?: return false
		val settings = CFNetworkCopySystemProxySettings() ?: return false
		return try {
			CFDictionaryContainsKey(settings, httpProxyKey)
		} finally {
			CFRelease(settings)
		}
	}

	override fun setProxy(host: String?, port: Int) {
		// Proxy is configured externally on iOS.
	}
}

internal actual fun NetworkProxyState(): NetworkProxyState = IosNetworkProxyState()
