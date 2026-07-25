package net.osmand.plus.plugins.nautical.nmea.parser

import net.osmand.plus.plugins.nautical.network.DeltaMessage
import net.osmand.plus.plugins.nautical.network.LivePerformanceData
import net.osmand.plus.plugins.nautical.network.Update
import net.osmand.plus.plugins.nautical.network.Value

/**
 * Lightweight NMEA 0183 parser that maps standard sentences to Signal K-style DeltaMessages.
 */
class NmeaSentenceParser {

    fun parse(sentence: String): DeltaMessage? {
        if ((!sentence.startsWith("$") && !sentence.startsWith("!")) || !sentence.contains("*")) return null
        
        val content = sentence.substring(1, sentence.indexOf("*"))
        val providedChecksum = sentence.substringAfter("*", "")
        
        if (!validateChecksum(content, providedChecksum)) return null

        val parts = content.split(",")
        if (parts.isEmpty()) return null
        
        val values = when (parts[0].takeLast(3)) {
            "RMC" -> parseRMC(parts)
            "MWV" -> parseMWV(parts)
            "DBT", "DBS" -> parseDepth(parts)
            else -> emptyList()
        }
        
        if (values.isEmpty()) return null
        
        return DeltaMessage(
            context = "vessels.self",
            updates = listOf(
                Update(
                    timestamp = System.currentTimeMillis().toString(),
                    source = mapOf("label" to "direct-nmea"),
                    values = values,
                ),
            ),
        )
    }

    private fun parseRMC(parts: List<String>): List<Value> {
        // $--RMC,hhmmss.ss,A,llll.ll,a,yyyyy.yy,a,x.x,x.x,ddmmyy,x.x,a,m*hh
        // Index: 0:ID, 1:Time, 2:Status, 3:Lat, 4:N/S, 5:Lon, 6:E/W, 7:SOG(knots), 8:COG(true), 9:Date...
        if (parts.size < 9) return emptyList()
        
        val values = mutableListOf<Value>()
        
        // SOG in knots to m/s (1 knot = 0.514444 m/s)
        parts[7].toDoubleOrNull()?.let { knots ->
            values.add(Value(LivePerformanceData.PATH_SOG, knots * 0.514444))
        }
        
        // COG in degrees
        parts[8].toDoubleOrNull()?.let { cog ->
            values.add(Value(LivePerformanceData.PATH_COG, Math.toRadians(cog)))
        }
        
        return values
    }

    private fun parseMWV(parts: List<String>): List<Value> {
        // $--MWV,x.x,a,x.x,a,A*hh
        // Index: 0:ID, 1:Angle, 2:Reference(R/T), 3:Speed, 4:Units(K/M/N), 5:Status
        if (parts.size < 6) return emptyList()
        
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
        
        // We primarily map True Wind for now as per SignalK flow used in aggregator
        if (reference == "T") {
            values.add(Value(LivePerformanceData.PATH_TWS, speedMs))
            values.add(Value(LivePerformanceData.PATH_TWA, Math.toRadians(angle)))
        }
        
        return values
    }

    private fun parseDepth(parts: List<String>): List<Value> {
        // $--DBT,x.x,f,x.x,M,x.x,F*hh (Depth Below Transducer)
        // $--DBS,x.x,f,x.x,M,x.x,F*hh (Depth Below Surface)
        if (parts.size < 5) return emptyList()
        
        val depthMeters = parts[3].toDoubleOrNull() ?: return emptyList()
        return listOf(Value(LivePerformanceData.PATH_DEPTH, depthMeters))
    }

    private fun validateChecksum(content: String, providedChecksum: String): Boolean {
        if (providedChecksum.isEmpty()) return true // Allow if missing? Marine hardware usually has it.
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
