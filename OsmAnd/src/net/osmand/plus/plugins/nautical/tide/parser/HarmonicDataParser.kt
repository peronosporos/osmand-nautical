package net.osmand.plus.plugins.nautical.tide.parser

import net.osmand.plus.plugins.nautical.tide.model.HarmonicConstituent
import net.osmand.plus.plugins.nautical.tide.model.TideStation
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class HarmonicDataParser {

    private val stations = mutableListOf<TideStation>()

    fun getStations(): List<TideStation> = stations

    /**
     * Parses an XTide-formatted harmonics file.
     * Note: This is a simplified parser for standard harmonic datasets.
     */
    fun parse(inputStream: InputStream): List<TideStation> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val constituentSpeeds = mutableMapOf<String, Double>()
        var currentStationName: String? = null
        var currentLat = 0.0
        var currentLon = 0.0
        var currentTimezone = 0
        val currentConstituents = mutableListOf<HarmonicConstituent>()

        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine

            when {
                trimmed.startsWith("constituent") -> {
                    // constituent M2 28.9841042
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        constituentSpeeds[parts[1]] = parts[2].toDouble()
                    }
                }
                trimmed.startsWith("station") -> {
                    // Save previous station
                    saveCurrentStation(currentStationName, currentLat, currentLon, currentTimezone, currentConstituents)
                    
                    // Reset for new station
                    currentStationName = trimmed.substringAfter("station").trim().trim('"')
                    currentConstituents.clear()
                }
                trimmed.startsWith("location") -> {
                    // location 45.123 -123.456
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        currentLat = parts[1].toDouble()
                        currentLon = parts[2].toDouble()
                    }
                }
                trimmed.startsWith("timezone") -> {
                    // timezone UTC-8
                    val tz = trimmed.substringAfter("timezone").trim()
                    currentTimezone = parseTimezone(tz)
                }
                else -> {
                    // Assume constituent data for current station: M2 1.23 180.0
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        val name = parts[0]
                        val speed = constituentSpeeds[name] ?: 0.0
                        val amplitude = parts[1].toDouble()
                        val epoch = parts[2].toDouble()
                        currentConstituents.add(HarmonicConstituent(name, amplitude, epoch, speed))
                    }
                }
            }
        }
        // Save last station
        saveCurrentStation(currentStationName, currentLat, currentLon, currentTimezone, currentConstituents)
        
        return stations
    }

    private fun saveCurrentStation(
        name: String?, 
        lat: Double, 
        lon: Double, 
        tz: Int, 
        constituents: List<HarmonicConstituent>
    ) {
        if ((name != null) && constituents.isNotEmpty()) {
            stations.add(TideStation(
                id = name.hashCode().toString(),
                name = name,
                latitude = lat,
                longitude = lon,
                timezoneOffset = tz,
                constituents = ArrayList(constituents)
            ))
        }
    }

    private fun parseTimezone(tz: String): Int {
        // Simple TZ parser for UTC+/-X
        return try {
            if (tz.startsWith("UTC")) {
                val offset = tz.substring(3).toIntOrNull() ?: 0
                offset * 3600
            } else 0
        } catch (_: Exception) { 0 }
    }

    fun findNearestStation(lat: Double, lon: Double): TideStation? {
        if (stations.isEmpty()) return null
        
        return stations.minByOrNull { calculateDistance(lat, lon, it.latitude, it.longitude) }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
