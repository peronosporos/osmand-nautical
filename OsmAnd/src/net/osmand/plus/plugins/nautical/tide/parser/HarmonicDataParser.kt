package net.osmand.plus.plugins.nautical.tide.parser

import net.osmand.plus.plugins.nautical.tide.model.HarmonicConstituent
import net.osmand.plus.plugins.nautical.tide.model.TideStation
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class HarmonicDataParser {

    private val stations = CopyOnWriteArrayList<TideStation>()
    
    // Coarse spatial index: Map of "latIndex,lonIndex" to list of stations
    // Index is floor(lat) and floor(lon) for 1x1 degree buckets
    private val spatialIndex = mutableMapOf<String, MutableList<TideStation>>()

    fun getStations(): List<TideStation> = stations

    fun clear() {
        stations.clear()
        synchronized(spatialIndex) {
            spatialIndex.clear()
        }
    }

    /**
     * Parses an XTide-formatted harmonics file.
     */
    fun parse(inputStream: InputStream): List<TideStation> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val constituentSpeeds = mutableMapOf<String, Double>()
        var currentStationName: String? = null
        var currentLat = 0.0
        var currentLon = 0.0
        var currentTimezone = 0
        var currentDatum = 0.0
        val currentConstituents = mutableListOf<HarmonicConstituent>()

        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine

            when {
                trimmed.startsWith("constituent") -> {
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        constituentSpeeds[parts[1]] = parts[2].toDouble()
                    }
                }
                trimmed.startsWith("station") -> {
                    saveCurrentStation(currentStationName, currentLat, currentLon, currentTimezone, currentDatum, currentConstituents)
                    currentStationName = trimmed.substringAfter("station").trim().trim('"')
                    currentConstituents.clear()
                    currentDatum = 0.0
                }
                trimmed.startsWith("location") -> {
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        currentLat = parts[1].toDouble()
                        currentLon = parts[2].toDouble()
                    }
                }
                trimmed.startsWith("timezone") -> {
                    val tz = trimmed.substringAfter("timezone").trim()
                    currentTimezone = parseTimezone(tz)
                }
                trimmed.startsWith("datum") || trimmed.startsWith("level") -> {
                    val d = trimmed.split(Regex("\\s+")).getOrNull(1)?.toDoubleOrNull()
                    if (d != null) currentDatum = d
                }
                else -> {
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
        saveCurrentStation(currentStationName, currentLat, currentLon, currentTimezone, currentDatum, currentConstituents)
        
        return stations
    }

    private fun saveCurrentStation(
        name: String?, 
        lat: Double, 
        lon: Double, 
        tz: Int, 
        datum: Double,
        constituents: List<HarmonicConstituent>
    ) {
        if ((name != null) && constituents.isNotEmpty()) {
            val id = createStableId(name, lat, lon)
            val newStation = TideStation(
                id = id,
                name = name,
                latitude = lat,
                longitude = lon,
                timezoneOffset = tz,
                datum = datum,
                constituents = ArrayList(constituents)
            )
            
            // Atomic update for spatial index and main list
            synchronized(spatialIndex) {
                stations.removeAll { it.id == id }
                stations.add(newStation)
                
                val bucketKey = getBucketKey(lat, lon)
                spatialIndex.getOrPut(bucketKey) { mutableListOf() }.add(newStation)
            }
        }
    }

    private fun getBucketKey(lat: Double, lon: Double): String {
        return "${lat.toInt()},${lon.toInt()}"
    }

    fun getStationsInBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<TideStation> {
        val result = mutableListOf<TideStation>()
        val startLat = Math.floor(minLat).toInt()
        val endLat = Math.floor(maxLat).toInt()
        val startLon = Math.floor(minLon).toInt()
        val endLon = Math.floor(maxLon).toInt()

        synchronized(spatialIndex) {
            for (latIdx in startLat..endLat) {
                for (lonIdx in startLon..endLon) {
                    spatialIndex["$latIdx,$lonIdx"]?.let { result.addAll(it) }
                }
            }
        }
        return result
    }

    fun findNearestStation(lat: Double, lon: Double): TideStation? {
        // Coarse check in 3x3 degree area around point first
        val result = mutableListOf<TideStation>()
        val latInt = Math.floor(lat).toInt()
        val lonInt = Math.floor(lon).toInt()
        
        for (i in -1..1) {
            for (j in -1..1) {
                synchronized(spatialIndex) {
                    spatialIndex["${latInt + i},${lonInt + j}"]?.let { result.addAll(it) }
                }
            }
        }

        if (result.isNotEmpty()) {
            return result.minByOrNull { calculateDistance(lat, lon, it.latitude, it.longitude) }
        }

        // Fallback to full list if nothing nearby
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

    private fun createStableId(name: String, lat: Double, lon: Double): String {
        val slug = name.lowercase(java.util.Locale.US).replace(Regex("[^a-z0-9]"), "-")
        return String.format(java.util.Locale.US, "%s-%.3f-%.3f", slug, lat, lon)
    }

    private fun parseTimezone(tz: String): Int {
        return try {
            if (tz.startsWith("UTC")) {
                val offset = tz.substring(3).toIntOrNull() ?: 0
                offset * 3600
            } else {
                // Support Olson names: Europe/London
                java.util.TimeZone.getTimeZone(tz).rawOffset / 1000
            }
        } catch (_: Exception) { 0 }
    }
}
