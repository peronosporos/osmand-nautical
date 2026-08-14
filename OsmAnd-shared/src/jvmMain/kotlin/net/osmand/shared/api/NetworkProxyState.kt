package net.osmand.shared.api

internal class JvmNetworkProxyState : NetworkProxyState {

	private var proxyData: NetworkProxyData? = null

	override val proxyHost: String?
		get() = proxyData?.host

	override val proxyPort: Int
		get() = proxyData?.port ?: 0

	override val ktorProxyData: NetworkProxyData?
		get() = proxyData

	override fun hasProxy(): Boolean {
		return proxyData != null
	}

	override fun setProxy(host: String?, port: Int) {
		proxyData = if (!host.isNullOrEmpty() && port > 0) {
			NetworkProxyData(host, port)
		} else {
			null
		}
	}
}

internal actual fun NetworkProxyState(): NetworkProxyState = JvmNetworkProxyState()
