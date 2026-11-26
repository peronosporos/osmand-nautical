package net.osmand.plus.plugins.nautical.hazard.engine

import net.osmand.data.LatLon
import kotlin.math.abs

/**
 * Utility for parsing NMEA sentences and extracting Navtex message data.
 * Supports standard NMEA structure and proprietary ICS Navtex sentences ($CRRXO, $GPNVT).
 */
object NavtexSentenceParser {

    // Degrees + minutes with an explicit separator: "37 12.5N", "37°12.5'N",
    // "36-45'N". The separator class is [\s\-°]+ with the dash ESCAPED: a bare
    // "[ -°]" is a RANGE from space (0x20) to the degree sign (0xB0) that
    // silently matches digits, dots and letters, which mis-split coordinate
    // strings (empirically, "3712.5N" parsed as 37 degrees + "12." + 5 minutes).
    // The (?![A-Za-z]) lookahead rejects unit fragments ("5 NM", "013° EAST").
    private val LAT_SEP = Regex("""(\d{1,2})[\s\-°]+(\d{1,2}(?:\.\d+)?)['"]?\s*([NSns])(?![A-Za-z])""")
    // NMEA-style concatenation: ddmm.m, e.g. "3712.5N" (degrees followed by
    // two integer digits of minutes, optionally with decimals)
    private val LAT_NMEA = Regex("""(\d{1,2})(\d{2}(?:\.\d+)?)['"]?\s*([NSns])(?![A-Za-z])""")
    // Whole or decimal degrees, e.g. "55N", "37.20N"
    private val LAT_DEC = Regex("""(\d{1,2}(?:\.\d+)?)\s*°?\s*([NSns])(?![A-Za-z])""")
    private val LON_SEP = Regex("""(\d{1,3})[\s\-°]+(\d{1,2}(?:\.\d+)?)['"]?\s*([EWew])(?![A-Za-z])""")
    private val LON_NMEA = Regex("""(\d{1,3})(\d{2}(?:\.\d+)?)['"]?\s*([EWew])(?![A-Za-z])""")
    private val LON_DEC = Regex("""(\d{1,3}(?:\.\d+)?)\s*°?\s*([EWew])(?![A-Za-z])""")
    // Bridge between two tokens of a pair or of a corner list: separators,
    // commas and the word "AND", up to 20 characters.
    private val BRIDGE_GAP = Regex("""[\s,;()/.–\-]{0,20}""")
    private val AND_WORD = Regex("""AND""", RegexOption.IGNORE_CASE)
    // Form ranking at a shared start position: only a digit run without a
    // dot can be the ddmm.m concatenation, so a decimal-point candidate is
    // preferred first; SEP cannot share a start with the other two forms.
    private const val FORM_DEC = 0
    private const val FORM_NMEA = 1
    private const val FORM_SEP = 2

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
        // Non-rejectable categories per the NAVTEX receiver requirements:
        // A, B, D and L (L is additional to A)
        return subject == NavtexSubject.NAVIGATIONAL_WARNING ||
               subject == NavtexSubject.METEOROLOGICAL_WARNING ||
               subject == NavtexSubject.SEARCH_AND_RESCUE ||
               subject == NavtexSubject.NAVIGATIONAL_WARNING_ADDITIONAL
    }

    /**
     * Extracts geographic coordinates from the raw text body.
     *
     * The text is scanned for single-sided tokens (one latitude or one
     * longitude component). Tokens are then either paired into points (a
     * latitude directly followed by a longitude across a short bridge of
     * separators) or assembled into the rectangle of a corner-list area
     * warning ("36-45'N, 36-58'N, 007-00'E, 007-25'E"). Unpaired tokens are
     * dropped, every candidate must pass the plausibility check, and the
     * lookaheads reject unit fragments, so a mis-split number or an off-hand
     * unit in prose can never fabricate a position.
     */
    fun extractCoordinates(text: String): List<LatLon> {
        val candidates = (collectTokens(text, lat = true) + collectTokens(text, lat = false))
            .sortedWith(compareBy({ it.start }, { -it.end }, { it.form }))

        // Reading order; at a shared start position the first plausible
        // match wins, and a rejected span still blocks nested weaker matches
        // (its evidence was consumed either way).
        val accepted = mutableListOf<TokenMatch>()
        var coveredUntil = -1
        var i = 0
        while (i < candidates.size) {
            val c = candidates[i]
            if (c.start < coveredUntil) {
                i++
                continue
            }
            val sameStart = ArrayList<TokenMatch>()
            var j = i
            while (j < candidates.size && candidates[j].start == c.start) {
                sameStart.add(candidates[j])
                j++
            }
            val chosen = sameStart.sortedWith(compareBy({ -it.end }, { it.form })).firstOrNull { it.valid }
            if (chosen != null) {
                accepted.add(chosen)
            }
            coveredUntil = maxOf(coveredUntil, sameStart.maxOf { it.end })
            i = j
        }

        val points = mutableListOf<LatLon>()
        var k = 0
        while (k < accepted.size) {
            val t = accepted[k]
            if (!t.isLat) {
                k++
                continue
            }
            val next = accepted.getOrNull(k + 1)
            if (next != null && !next.isLat && bridgeOk(text, t.end, next.start)) {
                if (t.value != 0.0 || next.value != 0.0) {
                    points.add(LatLon(t.value, next.value))
                }
                k += 2
                continue
            }
            // Corner-list form: a run of latitudes then a run of longitudes
            // bounding a rectangular area.
            var m = k
            val lats = ArrayList<TokenMatch>()
            while (m < accepted.size && accepted[m].isLat &&
                (lats.isEmpty() || bridgeOk(text, accepted[m - 1].end, accepted[m].start))) {
                lats.add(accepted[m])
                m++
            }
            val lons = ArrayList<TokenMatch>()
            while (m < accepted.size && !accepted[m].isLat &&
                (lons.isEmpty() || bridgeOk(text, accepted[m - 1].end, accepted[m].start))) {
                lons.add(accepted[m])
                m++
            }
            if (lats.size >= 2 && lons.size >= 2 && bridgeOk(text, lats.last().end, lons.first().start)) {
                val latValues = lats.map { it.value }
                val lonValues = lons.map { it.value }
                val latMin = latValues.minOrNull() ?: continue
                val latMax = latValues.maxOrNull() ?: continue
                val lonMin = lonValues.minOrNull() ?: continue
                val lonMax = lonValues.maxOrNull() ?: continue
                points.add(LatLon(latMin, lonMin))
                points.add(LatLon(latMin, lonMax))
                points.add(LatLon(latMax, lonMax))
                points.add(LatLon(latMax, lonMin))
                k = m
                continue
            }
            k++
        }
        return points.distinct()
    }

    /** True when the gap between two tokens is only separators or the word "AND". */
    private fun bridgeOk(text: String, from: Int, to: Int): Boolean {
        val gap = AND_WORD.replace(text.substring(from, to), "")
        return BRIDGE_GAP.matches(gap)
    }

    /** A raw pattern match with its computed value and plausibility. */
    private class TokenMatch(
        val start: Int, val end: Int, val form: Int,
        val isLat: Boolean, val value: Double, val valid: Boolean
    )

    private fun collectTokens(text: String, lat: Boolean): List<TokenMatch> {
        val patterns = if (lat) {
            listOf(FORM_SEP to LAT_SEP, FORM_NMEA to LAT_NMEA, FORM_DEC to LAT_DEC)
        } else {
            listOf(FORM_SEP to LON_SEP, FORM_NMEA to LON_NMEA, FORM_DEC to LON_DEC)
        }
        val out = ArrayList<TokenMatch>()
        for ((form, pattern) in patterns) {
            for (m in pattern.findAll(text)) {
                val degrees: Double
                val minutes: Double
                val direction: Char
                if (form == FORM_DEC) {
                    degrees = m.groupValues[1].toDoubleOrNull() ?: continue
                    minutes = 0.0
                    direction = m.groupValues[2].firstOrNull() ?: continue
                } else {
                    degrees = m.groupValues[1].toDoubleOrNull() ?: continue
                    minutes = m.groupValues[2].toDoubleOrNull() ?: continue
                    direction = m.groupValues[3].firstOrNull() ?: continue
                }
                var value = degrees + minutes / 60.0
                if (direction == 'S' || direction == 's' || direction == 'W' || direction == 'w') {
                    value = -value
                }
                val valid = minutes < 60.0 && (if (lat) abs(value) <= 90.0 else abs(value) <= 180.0)
                out.add(TokenMatch(m.range.first, m.range.last + 1, form, lat, value, valid))
            }
        }
        return out
    }

    /**
     * Plausibility note: minutes >= 60, a latitude beyond the poles or a
     * longitude beyond the antimeridian reject a match, so a mis-split
     * number cannot fabricate a position.
     */

    private fun parseNmeaDegrees(value: String, direction: String): Double? {
        if (value.isEmpty() || direction.isEmpty()) return null
        return try {
            val dotIndex = value.indexOf('.')
            val degreesLength = if (dotIndex != -1) dotIndex - 2 else value.length - 2
            
            val degrees: Double
            val minutes: Double
            
            if (degreesLength > 0) {
                degrees = value.substring(0, degreesLength).toDouble()
                minutes = value.substring(degreesLength).toDouble()
            } else {
                degrees = 0.0
                minutes = value.toDouble()
            }
            
            val decimal = degrees + (minutes / 60.0)
            if (direction.uppercase() in listOf("S", "W")) -decimal else decimal
        } catch (_: Exception) {
            null
        }
    }

    private fun validateChecksum(content: String, checksum: String): Boolean {
        if (checksum.isEmpty()) return false // Mandatory for MSI safety
        return try {
            var calculated = 0
            for (char in content) {
                calculated = calculated xor char.code
            }
            // Sentences read from a serial/stream source often retain the
            // trailing CR/LF after "*hh"; parse only the two hex digits.
            val provided = checksum.trim().take(2).toInt(16)
            calculated == provided
        } catch (_: Exception) {
            false
        }
    }
}
