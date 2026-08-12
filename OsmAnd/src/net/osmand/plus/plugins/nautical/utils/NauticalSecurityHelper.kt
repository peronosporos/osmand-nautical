package net.osmand.plus.plugins.nautical.utils

import net.osmand.plus.settings.backend.OsmandSettings

object NauticalSecurityHelper {

    /**
     * Centralized security check for Signal K commands.
     * Enforces secure transport (HTTPS/WSS) for all remote connections.
     * Relaxes requirements for local network ranges commonly used on boats.
     */
    fun isConnectionSecure(settings: OsmandSettings): Boolean {
        val serverIp = settings.NAUTICAL_SERVER_IP.get() ?: ""
        val useSecure = settings.NAUTICAL_USE_SECURE_CONNECTION.get()
        
        if (useSecure) return true
        
        return isLocalIp(serverIp)
    }

    private fun isLocalIp(ip: String): Boolean {
        val sanitized = ip.substringAfter("://").substringBefore("/")
        if (sanitized == "localhost" || sanitized == "127.0.0.1" || sanitized == "::1") return true
        
        // Private IP Ranges (RFC 1918)
        if (sanitized.startsWith("192.168.") || sanitized.startsWith("10.")) return true
        if (sanitized.startsWith("172.")) {
            val parts = sanitized.split(".")
            if (parts.size >= 2) {
                val second = parts[1].toIntOrNull()
                if (second != null && second in 16..31) return true
            }
        }
        
        // Link-Local (RFC 3927)
        if (sanitized.startsWith("169.254.")) return true
        
        return false
    }
}
