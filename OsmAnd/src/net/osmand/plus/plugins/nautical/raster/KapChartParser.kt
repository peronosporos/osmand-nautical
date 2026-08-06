package net.osmand.plus.plugins.nautical.raster

import net.osmand.PlatformUtil
import net.osmand.data.QuadRect
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset

class KapChartParser {
    private val log = PlatformUtil.getLog(KapChartParser::class.java)

    data class KapMetadata(
        val name: String,
        val scale: Int,
        val projection: String,
        val datum: String,
        val bounds: QuadRect?,
        val soundingsDatum: String
    )

    fun parseHeader(file: File): KapMetadata? {
        return try {
            FileInputStream(file).use { input ->
                val reader = input.bufferedReader(Charset.forName("ISO-8859-1"))
                var line: String?
                var name = file.nameWithoutExtension
                var scale = 0
                var projection = "Unknown"
                var datum = "Unknown"
                var soundingsDatum = "Unknown"
                val refPoints = mutableListOf<RefPoint>()

                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: break
                    if (l.isEmpty()) continue
                    
                    // Header ends at NULL or specific line
                    if (l.contains('\u001A')) break 

                    val parts = l.split("/")
                    if (parts.size < 2) continue
                    
                    val tag = parts[0]
                    val fields = parts[1].split(",").associate {
                        val sub = it.split("=")
                        if (sub.size == 2) sub[0] to sub[1] else it to ""
                    }

                    when (tag) {
                        "BSB" -> {
                            name = fields["NA"] ?: name
                        }
                        "KNP" -> {
                            scale = fields["SC"]?.toIntOrNull() ?: scale
                            projection = fields["PR"] ?: projection
                            soundingsDatum = fields["SD"] ?: soundingsDatum
                        }
                        "DTM" -> {
                            datum = fields["DTM"] ?: datum
                        }
                        "REF" -> {
                            // format: REF/id,x,y,lat,lon
                            val refParts = parts[1].split(",")
                            if (refParts.size >= 5) {
                                val lat = refParts[3].toDoubleOrNull()
                                val lon = refParts[4].toDoubleOrNull()
                                if (lat != null && lon != null) {
                                    refPoints.add(RefPoint(lat, lon))
                                }
                            }
                        }
                    }
                }

                val bounds = if (refPoints.isNotEmpty()) {
                    var minLat = 90.0
                    var maxLat = -90.0
                    var minLon = 180.0
                    var maxLon = -180.0
                    for (p in refPoints) {
                        minLat = kotlin.math.min(minLat, p.lat)
                        maxLat = kotlin.math.max(maxLat, p.lat)
                        minLon = kotlin.math.min(minLon, p.lon)
                        maxLon = kotlin.math.max(maxLon, p.lon)
                    }
                    QuadRect(minLon, maxLat, maxLon, minLat)
                } else null

                KapMetadata(name, scale, projection, datum, bounds, soundingsDatum)
            }
        } catch (e: Exception) {
            log.error("Failed to parse KAP header: ${e.message}")
            null
        }
    }

    private data class RefPoint(val lat: Double, val lon: Double)
}
