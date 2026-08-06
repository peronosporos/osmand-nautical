package net.osmand.plus.plugins.nautical.hazard.engine

import net.osmand.data.LatLon

/**
 * Utility for parsing NMEA sentences and extracting Navtex message data.
 * Supports standard NMEA structure and proprietary ICS Navtex sentences ($CRRXO, $GPNVT).
 */
object NavtexSentenceParser {

    private val COORD_PATTERN_1 = Regex("""(\d{1,2})[ -°]*(\d{1,2}(?:\.\d+)?)['"]?\s*([NSns])\s*[,/]?\s*(\d{1,3})[ -°]*(\d{1,2}(?:\.\d+)?)['"]?\s*([EWew])""")
    private val COORD_PATTERN_2 = Regex("""(\d{1,2})([NSns])(\d{1,2}(?:\.\d+)?)\s*[,/]?\s*([EWew])(\d{1,3})(\d{1,2}(?:\.\d+)?)""")
    private val COORD_PATTERN_3 = Regex("""(\d{1,2})([NSns])\s*(\d{1,3})([EWew])""")

    /**
     * Parses a single NMEA sentence into a NavtexMessage.
     * Handles $CRRXO, $CZCX (Text data) and $GPNVT (Position metadata).
     */
    fun parseNmeaSentence(sentence: String): NavtexMessage? {
        if (!sentence.startsWith("$") || !sentence.contains("*")) return null
        
        val content = sentence.substringAfter("$").substringBefore("*")
        val providedChecksum = sentence.substringAfter("*", "")
        
        if (!validateChecksum(content, providedChecksum)) return null

        val parts = content.split(",")
        if (parts.isEmpty()) return null
        
        val sentenceId = parts[0]
        return try {
            when {
                sentenceId.endsWith("RXO") || sentenceId.endsWith("ZCX") -> parseCRRXO(parts)
                sentenceId.endsWith("NVT") -> parseGPNVT(parts)
                else -> null
            }
        } catch (_: Exception) {
            // Gracefully handle malformed sentences
            null
        }
    }

    private fun parseCRRXO(parts: List<String>): NavtexMessage? {
        if (parts.size < 5) return null
        val messageId = parts[2]
        if (messageId.length < 4) return null
        
        val stationLetter = messageId[0]
        val subjectChar = messageId[1]
        val sequenceNumber = messageId.substring(2, 4).toIntOrNull() ?: 0
        
        // Rejoin body in case it contained commas
        val body = parts.subList(4, parts.size).joinToString(",")
        
        val subject = NavtexSubject.fromCode(subjectChar)
        val isUrgent = isSubjectUrgent(subject)
        
        return NavtexMessage(
            id = messageId,
            stationLetter = stationLetter,
            subject = subject,
            sequenceNumber = sequenceNumber,
            timestamp = System.currentTimeMillis(),
            body = body,
            points = extractCoordinates(body),
            isUrgent = isUrgent,
        )
    }

    private fun parseGPNVT(parts: List<String>): NavtexMessage? {
        if (parts.size < 7) return null
        val messageId = parts[6]
        if (messageId.length < 4) return null
        
        val lat = parseNmeaDegrees(parts[2], parts[3])
        val lon = parseNmeaDegrees(parts[4], parts[5])
        
        val stationLetter = messageId[0]
        val subjectChar = messageId[1]
        val sequenceNumber = messageId.substring(2, 4).toIntOrNull() ?: 0
        val subject = NavtexSubject.fromCode(subjectChar)
        
        val points = if (lat != null && lon != null) listOf(LatLon(lat, lon)) else emptyList()
        
        return NavtexMessage(
            id = messageId,
            stationLetter = stationLetter,
            subject = subject,
            sequenceNumber = sequenceNumber,
            timestamp = System.currentTimeMillis(),
            body = "",
            points = points,
            isUrgent = isSubjectUrgent(subject),
        )
    }

    private fun isSubjectUrgent(subject: NavtexSubject): Boolean {
        return subject == NavtexSubject.NAVTEX_WARNING || 
               subject == NavtexSubject.SEARCH_AND_RESCUE
    }

    /**
     * Extracts geographic coordinates from raw text body using regex.
     * Supports multiple points for polygons.
     */
    fun extractCoordinates(text: String): List<LatLon> {
        val points = mutableListOf<LatLon>()
        
        // Find all matches for different patterns
        val matches1 = COORD_PATTERN_1.findAll(text).map { match ->
            val latDeg = match.groupValues[1].toDoubleOrNull() ?: return@map null
            val latMin = match.groupValues[2].toDoubleOrNull() ?: 0.0
            val latDir = match.groupValues[3]
            val lonDeg = match.groupValues[4].toDoubleOrNull() ?: return@map null
            val lonMin = match.groupValues[5].toDoubleOrNull() ?: 0.0
            val lonDir = match.groupValues[6]
            match.range.first to LatLon(convertToDecimal(latDeg, latMin, latDir), convertToDecimal(lonDeg, lonMin, lonDir))
        }.filterNotNull()

        val matches2 = COORD_PATTERN_2.findAll(text).map { match ->
            val latDeg = match.groupValues[1].toDoubleOrNull() ?: return@map null
            val latDir = match.groupValues[2]
            val latMin = match.groupValues[3].toDoubleOrNull() ?: 0.0
            val lonDir = match.groupValues[4]
            val lonDeg = match.groupValues[5].toDoubleOrNull() ?: return@map null
            val lonMin = match.groupValues[6].toDoubleOrNull() ?: 0.0
            match.range.first to LatLon(convertToDecimal(latDeg, latMin, latDir), convertToDecimal(lonDeg, lonMin, lonDir))
        }.filterNotNull()

        val matches3 = COORD_PATTERN_3.findAll(text).map { match ->
            val latDeg = match.groupValues[1].toDoubleOrNull() ?: return@map null
            val latDir = match.groupValues[2]
            val lonDeg = match.groupValues[3].toDoubleOrNull() ?: return@map null
            val lonDir = match.groupValues[4]
            match.range.first to LatLon(convertToDecimal(latDeg, 0.0, latDir), convertToDecimal(lonDeg, 0.0, lonDir))
        }.filterNotNull()

        (matches1 + matches2 + matches3).sortedBy { it.first }.forEach { (_, latLon) ->
            if (latLon.latitude != 0.0 || latLon.longitude != 0.0) {
                points.add(latLon)
            }
        }
        
        return points.distinct()
    }

    private fun convertToDecimal(degrees: Double, minutes: Double, direction: String): Double {
        val decimal = degrees + (minutes / 60.0)
        return if (direction.uppercase() in listOf("S", "W")) -decimal else decimal
    }

    private fun parseNmeaDegrees(value: String, direction: String): Double? {
        if (value.isEmpty() || direction.isEmpty()) return null
        return try {
            val dotIndex = value.indexOf('.')
            val degreesLength = if (dotIndex != -1) dotIndex - 2 else value.length - 2
            if (degreesLength <= 0) return null
            
            val degrees = value.substring(0, degreesLength).toDouble()
            val minutes = value.substring(degreesLength).toDouble()
            
            val decimal = degrees + (minutes / 60.0)
            if (direction.uppercase() in listOf("S", "W")) -decimal else decimal
        } catch (_: Exception) {
            null
        }
    }

    private fun validateChecksum(content: String, checksum: String): Boolean {
        if (checksum.isEmpty()) return true // Some sensors might not provide it
        return try {
            var calculated = 0
            for (char in content) {
                calculated = calculated xor char.code
            }
            val provided = checksum.toInt(16)
            calculated == provided
        } catch (_: Exception) {
            false
        }
    }
}
