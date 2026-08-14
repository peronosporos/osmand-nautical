package net.osmand.shared.api

internal data class NetworkProxyData(
	val host: String,
	val port: Int
)

internal interface NetworkProxyState {

	val proxyHost: String?

	val proxyPort: Int

	val ktorProxyData: NetworkProxyData?

	fun hasProxy(): Boolean

	fun setProxy(host: String?, port: Int)
}

internal expect fun NetworkProxyState(): NetworkProxyState
