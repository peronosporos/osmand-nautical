package net.osmand.plus.plugins.nautical.nmea.parser

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.engine.MarineStateConstants
import net.osmand.plus.plugins.nautical.network.DeltaMessage
import net.osmand.plus.plugins.nautical.network.LivePerformanceData
import net.osmand.plus.plugins.nautical.network.Update
import net.osmand.plus.plugins.nautical.network.Value

/**
 * Lightweight NMEA 0183 parser that maps standard sentences to Signal K-style DeltaMessages.
 */
class NmeaSentenceParser(private val app: OsmandApplication) {

    private val log = net.osmand.PlatformUtil.getLog(NmeaSentenceParser::class.java)
    private val talkerPriorities = mapOf(
        "GP" to 10, // GPS
        "GN" to 9,  // GNSS (Mixed)
        "GL" to 8,  // GLONASS
        "GA" to 7,  // Galileo
        "GB" to 6,  // BeiDou
        "II" to 5   // Integrated Instrumentation
    )
    
    private val lastTalkerByPath = mutableMapOf<String, String>()

    fun parse(sentence: String): DeltaMessage? {
        if ((!sentence.startsWith("$") && !sentence.startsWith("!")) || !sentence.contains("*")) return null
        
        val content = sentence.substring(1, sentence.indexOf("*"))
        val providedChecksum = sentence.substringAfter("*", "")
        
        if (!validateChecksum(content, providedChecksum)) return null

        val parts = content.split(",")
        if (parts.isEmpty()) return null
        
        val sentenceId = parts[0]
        val talker = if (sentenceId.length >= 2) sentenceId.substring(0, 2) else ""
        val type = if (sentenceId.length >= 5) sentenceId.substring(sentenceId.length - 3) else sentenceId
        
        val values = when (type) {
            "RMC" -> parseRMC(parts, talker)
            "MWV" -> parseMWV(parts)
            "DBT", "DBS", "DPT" -> parseDepth(parts)
            "HDG", "HDT", "HDM" -> parseHeading(parts)
            "VHW" -> parseVHW(parts)
            else -> emptyList()
        }
        
        if (values.isEmpty()) return null
        
        return DeltaMessage(
            context = "vessels.self",
            updates = listOf(
                Update(
                    timestamp = System.currentTimeMillis().toString(),
                    source = mapOf("label" to "direct-nmea", "talker" to talker),
                    values = values,
                ),
            ),
        )
    }

    private fun shouldProcess(path: String, currentTalker: String): Boolean {
        val lastTalker = lastTalkerByPath[path] ?: return true
        if (lastTalker == currentTalker) return true
        
        val currentPrio = talkerPriorities[currentTalker] ?: 0
        val lastPrio = talkerPriorities[lastTalker] ?: 0
        
        return if (currentPrio >= lastPrio) {
            lastTalkerByPath[path] = currentTalker
            true
        } else false
    }

    private fun parseRMC(parts: List<String>, talker: String): List<Value> {
        // $--RMC,hhmmss.ss,A,llll.ll,a,yyyyy.yy,a,x.x,x.x,ddmmyy,x.x,a,m*hh
        if (parts.size < 9) return emptyList()
        
        val values = mutableListOf<Value>()
        
        if (shouldProcess(LivePerformanceData.PATH_SOG, talker)) {
            parts[7].toDoubleOrNull()?.let { knots ->
                val sogMs = knots * 0.514444
                if (MarineStateConstants.isValidSpeed(sogMs)) {
                    values.add(Value(LivePerformanceData.PATH_SOG, sogMs))
                }
            }
        }
        
        if (shouldProcess(LivePerformanceData.PATH_COG, talker)) {
            parts[8].toDoubleOrNull()?.let { cog ->
                if (!cog.isNaN()) {
                    values.add(Value(LivePerformanceData.PATH_COG, Math.toRadians(cog)))
                }
            }
        }

        if (parts[2] == "A" && shouldProcess(LivePerformanceData.PATH_POSITION, talker)) {
            val lat = parseNmeaLatitude(parts[3], parts[4])
            val lon = parseNmeaLongitude(parts[5], parts[6])
            if (MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
                values.add(Value(LivePerformanceData.PATH_POSITION, mapOf("latitude" to lat, "longitude" to lon)))
            }
        }
        
        return values
    }

    private fun parseHeading(parts: List<String>): List<Value> {
        if (parts.size < 2) return emptyList()
        val type = parts[0].takeLast(3)
        val angle = parts[1].toDoubleOrNull() ?: return emptyList()
        val rad = Math.toRadians(angle)
        
        return when (type) {
            "HDT" -> listOf(Value(LivePerformanceData.PATH_HEADING_TRUE, rad))
            "HDG", "HDM" -> listOf(Value(LivePerformanceData.PATH_HEADING_MAG, rad))
            else -> emptyList()
        }
    }

    private fun parseVHW(parts: List<String>): List<Value> {
        // $--VHW,x.x,T,x.x,M,x.x,N,x.x,K*hh
        if (parts.size < 9) return emptyList()
        val stwKnots = parts[5].toDoubleOrNull() ?: return emptyList()
        val stwMs = stwKnots * 0.514444
        
        val values = mutableListOf(Value(LivePerformanceData.PATH_STW, stwMs))
        
        parts[1].toDoubleOrNull()?.let { hT -> values.add(Value(LivePerformanceData.PATH_HEADING_TRUE, Math.toRadians(hT))) }
        parts[3].toDoubleOrNull()?.let { hM -> values.add(Value(LivePerformanceData.PATH_HEADING_MAG, Math.toRadians(hM))) }
        
        return values
    }

    private fun parseNmeaLatitude(lat: String, ns: String): Double {
        if (lat.length < 4) return Double.NaN
        val deg = lat.substring(0, 2).toDoubleOrNull() ?: return Double.NaN
        val min = lat.substring(2).toDoubleOrNull() ?: return Double.NaN
        val dec = deg + min / 60.0
        return if (ns == "S") -dec else dec
    }

    private fun parseNmeaLongitude(lon: String, ew: String): Double {
        if (lon.length < 5) return Double.NaN
        val deg = lon.substring(0, 3).toDoubleOrNull() ?: return Double.NaN
        val min = lon.substring(3).toDoubleOrNull() ?: return Double.NaN
        val dec = deg + min / 60.0
        return if (ew == "W") -dec else dec
    }

    private fun parseMWV(parts: List<String>): List<Value> {
        // $--MWV,x.x,a,x.x,a,A*hh
        // Index: 0:ID, 1:Angle, 2:Reference(R/T), 3:Speed, 4:Units(K/M/N), 5:Status
        if (parts.size < 6) return emptyList()
        if (parts[5] != "A") return emptyList()
        
        val values = mutableListOf<Value>()
        val angle = parts[1].toDoubleOrNull() ?: return emptyList()
        val reference = parts[2] // R = Relative, T = True
        val speed = parts[3].toDoubleOrNull() ?: return emptyList()
        val units = parts[4]
        
        // Convert speed to m/s
        val speedMs = when (units) {
            "K" -> speed / 3.6
            "N" -> speed * 0.514444
            "M" -> speed
            else -> speed
        }
        
        if (!MarineStateConstants.isValidWindSpeed(speedMs)) return emptyList()
        
        // We primarily map True Wind for now as per SignalK flow used in aggregator
        if (reference == "T") {
            values.add(Value(LivePerformanceData.PATH_TWS, speedMs))
            values.add(Value(LivePerformanceData.PATH_TWA, Math.toRadians(angle)))
        } else if (reference == "R") {
            values.add(Value(LivePerformanceData.PATH_AWS, speedMs))
            values.add(Value(LivePerformanceData.PATH_AWA, Math.toRadians(angle)))
        }
        
        return values
    }

    private fun parseDepth(parts: List<String>): List<Value> {
        // $--DBT,x.x,f,x.x,M,x.x,F*hh (Depth Below Transducer)
        // $--DBS,x.x,f,x.x,M,x.x,F*hh (Depth Below Surface)
        // $--DPT,x.x,x.x,x.x*hh (Depth, offset, scale)
        if (parts.size < 2) return emptyList()
        
        val type = parts[0].takeLast(3)
        val depthMeters = if (type == "DPT") {
            parts[1].toDoubleOrNull()
        } else {
            if (parts.size >= 4) parts[3].toDoubleOrNull() else null
        } ?: return emptyList()
        
        if (!MarineStateConstants.isValidDepth(depthMeters)) return emptyList()

        val path = if (type == "DBS") "environment.depth.surfaceToTransducer" else LivePerformanceData.PATH_DEPTH
        return listOf(Value(path, depthMeters))
    }

    private fun validateChecksum(content: String, providedChecksum: String): Boolean {
        if (providedChecksum.isEmpty()) {
            if (app.settings.NAUTICAL_ALLOW_UNCHECKSUMMED_NMEA.get()) {
                return true
            } else {
                log.warn("Dropped unchecksummed NMEA sentence (strict mode)")
                return false
            }
        }
        return try {
            var calculated = 0
            for (char in content) {
                calculated = calculated xor char.code
            }
            val provided = providedChecksum.toInt(16)
            calculated == provided
        } catch (_: Exception) {
            false
        }
    }
}
