package net.osmand.plus.plugins.nautical.tide.parser

import net.osmand.PlatformUtil
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

    private val log = PlatformUtil.getLog(HarmonicDataParser::class.java)

    private val stations = CopyOnWriteArrayList<TideStation>()

    // Constituent speeds declared in the file header, keyed by upper-case name.
    private val constituentSpeeds = mutableMapOf<String, Double?>()

    // Coarse spatial index: Map of "latIndex,lonIndex" to list of stations
    // Index is floor(lat) and floor(lon) for 1x1 degree buckets
    private val spatialIndex = mutableMapOf<String, MutableList<TideStation>>()

    fun getStations(): List<TideStation> = stations

    fun clear() {
        stations.clear()
        synchronized(spatialIndex) {
            spatialIndex.clear()
        }
        constituentSpeeds.clear()
    }

    /**
     * Parses an XTide-formatted harmonics file.
     */
    fun parse(inputStream: InputStream): List<TideStation> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        constituentSpeeds.clear()
        var currentStationName: String? = null
        var currentLat = 0.0
        var currentLon = 0.0
        var currentHasLocation = false
        var currentTimezone = 0
        var currentDatum = 0.0
        var currentOrientation: Double? = null
        val currentConstituents = mutableListOf<HarmonicConstituent>()
        var skippedLines = 0

        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine

            when {
                trimmed.startsWith("constituent") -> {
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        val speed = parts[2].toDoubleOrNull()
                        if (speed != null) {
                            constituentSpeeds[parts[1].uppercase(java.util.Locale.US)] = speed
                        } else {
                            skippedLines++
                        }
                    }
                }
                trimmed.startsWith("station") -> {
                    saveCurrentStation(currentStationName, currentLat, currentLon, currentHasLocation,
                        currentTimezone, currentDatum, currentConstituents, currentOrientation)
                    currentStationName = trimmed.substringAfter("station").trim().trim('"')
                    currentConstituents.clear()
                    currentDatum = 0.0
                    currentLat = 0.0
                    currentLon = 0.0
                    currentHasLocation = false
                    currentOrientation = null
                }
                trimmed.startsWith("location") -> {
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        val lat = parts[1].toDoubleOrNull()
                        val lon = parts[2].toDoubleOrNull()
                        if (lat != null && lon != null) {
                            currentLat = lat
                            currentLon = lon
                            currentHasLocation = true
                        } else {
                            skippedLines++
                        }
                    }
                }
                trimmed.startsWith("timezone") -> {
                    val tz = trimmed.substringAfter("timezone").trim()
                    currentTimezone = parseTimezone(tz)
                }
                trimmed.startsWith("orientation") -> {
                    val deg = trimmed.split(Regex("\\s+")).getOrNull(1)?.toDoubleOrNull()
                    if (deg != null) {
                        currentOrientation = deg
                    } else {
                        skippedLines++
                    }
                }
                trimmed.startsWith("datum") || trimmed.startsWith("level") -> {
                    val d = trimmed.split(Regex("\\s+")).getOrNull(1)?.toDoubleOrNull()
                    if (d != null) currentDatum = d
                }
                else -> {
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        val name = parts[0]
                        val amplitude = parts[1].toDoubleOrNull()
                        val epoch = parts[2].toDoubleOrNull()
                        if (amplitude != null && epoch != null) {
                            // Speed is only stored when the file header declares it; the
                            // prediction engine derives speeds from its own registry.
                            currentConstituents.add(
                                HarmonicConstituent(name, amplitude, epoch, constituentSpeeds[name.uppercase(java.util.Locale.US)])
                            )
                        } else {
                            skippedLines++
                        }
                    }
                }
            }
        }
        saveCurrentStation(currentStationName, currentLat, currentLon, currentHasLocation,
            currentTimezone, currentDatum, currentConstituents, currentOrientation)

        if (skippedLines > 0) {
            log.warn("HarmonicDataParser: skipped $skippedLines malformed line(s) while parsing.")
        }
        return stations
    }

    private fun saveCurrentStation(
        name: String?,
        lat: Double,
        lon: Double,
        hasLocation: Boolean,
        tz: Int,
        datum: Double,
        constituents: List<HarmonicConstituent>,
        orientationDeg: Double? = null
    ) {
        if ((name != null) && constituents.isNotEmpty()) {
            if (!hasLocation) {
                log.warn("HarmonicDataParser: station '$name' has no location line; it is excluded.")
                return
            }
            val id = createStableId(name, lat, lon)
            val newStation = TideStation(
                id = id,
                name = name,
                latitude = lat,
                longitude = lon,
                timezoneOffset = tz,
                datum = datum,
                constituents = ArrayList(constituents),
                orientationDeg = orientationDeg
            )

            // Atomic update for spatial index and main list.
            synchronized(spatialIndex) {
                stations.removeAll { it.id == id }
                stations.add(newStation)

                // Purge any stale entry for this id from every bucket, otherwise a
                // re-imported station would be returned twice from bounds/nearest queries.
                spatialIndex.values.forEach { bucket -> bucket.removeAll { it.id == id } }
                spatialIndex.getOrPut(getBucketKey(lat, lon)) { mutableListOf() }.add(newStation)
            }
        }
    }

    private fun getBucketKey(lat: Double, lon: Double): String {
        // Floor, not truncate: queries use Math.floor too, and toInt() would place
        // negative-hemisphere coordinates (e.g. lat -0.5) into the wrong bucket.
        return "${Math.floor(lat).toInt()},${Math.floor(lon).toInt()}"
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

        synchronized(spatialIndex) {
            for (i in -1..1) {
                for (j in -1..1) {
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
                // Accept both integer and fractional hours (e.g. UTC+5, UTC+5.5, UTC-3).
                val hours = tz.substring(3).toDoubleOrNull()
                if (hours != null) (hours * 3600.0).toInt() else 0
            } else {
                // Support Olson names: Europe/London
                java.util.TimeZone.getTimeZone(tz).rawOffset / 1000
            }
        } catch (_: Exception) { 0 }
    }
}
