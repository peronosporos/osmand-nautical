package net.osmand.plus.plugins.nautical.logbook.data

import java.io.Serializable

data class LogbookEntry(
    val id: Long = 0,
    val timestamp: Long, // UTC epoch milliseconds
    val latitude: Double,
    val longitude: Double,
    
    // Navigation
    val sog: Double?, // Speed Over Ground (m/s)
    val cog: Double?, // Course Over Ground (radians)
    val heading: Double?, // True Heading (radians)
    
    // Weather
    val tws: Double?, // True Wind Speed (m/s)
    val twa: Double?, // True Wind Angle (radians)
    val twd: Double?, // True Wind Direction (radians)
    val pressure: Double?, // Barometric Pressure (Pascal)
    
    // Environment
    val waterDepth: Double?, // Water depth below transducer (m)
    val waterTemp: Double?, // Sea temperature (K)
    
    // Vessel State
    val batteryVoltage: Double?, // Main battery voltage (V)
    val engineHours: Double?, // Total engine hours
    val sailPlan: String = "",
    val notes: String = "",
    val serverUuid: String? = null
) : Serializable
