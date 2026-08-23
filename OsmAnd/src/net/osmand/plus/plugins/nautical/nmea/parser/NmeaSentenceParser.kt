package net.osmand.plus.plugins.nautical.nmea.parser

import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.engine.MarineStateConstants
import net.osmand.plus.plugins.nautical.engine.SignalKPaths
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
            "VWT" -> parseVWT(parts)
            "DBT", "DBS", "DPT" -> parseDepth(parts)
            "HDG", "HDT", "HDM" -> parseHeading(parts)
            "VHW" -> parseVHW(parts)
            "GGA" -> parseGGA(parts, talker)
            "GNS" -> parseGNS(parts, talker)
            "GLL" -> parseGLL(parts, talker)
            "VTG" -> parseVTG(parts, talker)
            "VDR" -> parseVDR(parts)
            "RSA" -> parseRSA(parts)
            "MTW" -> parseMTW(parts)
            "XTE" -> parseXTE(parts)
            "APB" -> parseAPB(parts)
            "RMB" -> parseRMB(parts)
            "BWC" -> parseBWC(parts)
            "MWD" -> parseMWD(parts)
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

        // Magnetic Variation: $--RMC,...,x.x,a,m*hh
        if (parts.size >= 12 && shouldProcess(LivePerformanceData.PATH_MAG_VARIATION, talker)) {
            parts[10].toDoubleOrNull()?.let { varDeg ->
                val varRad = Math.toRadians(if (parts[11] == "W") -varDeg else varDeg)
                values.add(Value(LivePerformanceData.PATH_MAG_VARIATION, varRad))
            }
        }
        
        return values
    }

    private fun parseHeading(parts: List<String>): List<Value> {
        if (parts.size < 2) return emptyList()
        val type = parts[0].takeLast(3)
        val angle = parts[1].toDoubleOrNull() ?: return emptyList()
        val rad = Math.toRadians(angle)
        
        val values = mutableListOf<Value>()
        when (type) {
            "HDT" -> values.add(Value(LivePerformanceData.PATH_HEADING_TRUE, rad))
            "HDG", "HDM" -> values.add(Value(LivePerformanceData.PATH_HEADING_MAG, rad))
        }

        // HDG Variation: $--HDG,x.x,x.x,a,x.x,a*hh
        if (type == "HDG" && parts.size >= 5) {
            parts[4].toDoubleOrNull()?.let { varDeg ->
                val varRad = Math.toRadians(if (parts[5] == "W") -varDeg else varDeg)
                values.add(Value(LivePerformanceData.PATH_MAG_VARIATION, varRad))
            }
        }
        
        return values
    }

    private fun parseGGA(parts: List<String>, talker: String): List<Value> {
        // $--GGA,hhmmss.ss,llll.ll,a,yyyyy.yy,a,x,xx,x.x,x.x,M,x.x,M,x.x,xxxx*hh
        if (parts.size < 10) return emptyList()
        val values = mutableListOf<Value>()

        if (shouldProcess(LivePerformanceData.PATH_POSITION, talker)) {
            val lat = parseNmeaLatitude(parts[2], parts[3])
            val lon = parseNmeaLongitude(parts[4], parts[5])
            if (MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
                values.add(Value(LivePerformanceData.PATH_POSITION, mapOf("latitude" to lat, "longitude" to lon)))
            }
        }

        // Altitude
        parts[9].toDoubleOrNull()?.let { alt ->
            values.add(Value("navigation.gnss.antennaAltitude", alt))
        }

        // HDOP
        parts[8].toDoubleOrNull()?.let { hdop ->
            values.add(Value("navigation.gnss.horizontalDilution", hdop))
        }

        // Satellites
        parts[7].toIntOrNull()?.let { sats ->
            values.add(Value("navigation.gnss.satellites", sats))
        }

        return values
    }

    private fun parseGNS(parts: List<String>, talker: String): List<Value> {
        // $--GNS,hhmmss.ss,llll.ll,a,yyyyy.yy,a,c--c,xx,x.x,x.x,x.x,x.x,x.x,a*hh
        if (parts.size < 10) return emptyList()
        val values = mutableListOf<Value>()

        if (shouldProcess(LivePerformanceData.PATH_POSITION, talker)) {
            val lat = parseNmeaLatitude(parts[2], parts[3])
            val lon = parseNmeaLongitude(parts[4], parts[5])
            if (MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
                values.add(Value(LivePerformanceData.PATH_POSITION, mapOf("latitude" to lat, "longitude" to lon)))
            }
        }

        // HDOP
        parts[8].toDoubleOrNull()?.let { hdop ->
            values.add(Value("navigation.gnss.horizontalDilution", hdop))
        }

        // Satellites
        parts[7].toIntOrNull()?.let { sats ->
            values.add(Value("navigation.gnss.satellites", sats))
        }

        return values
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
        if (type == "DPT") {
            val depthMeters = parts[1].toDoubleOrNull() ?: return emptyList()
            if (!MarineStateConstants.isValidDepth(depthMeters)) return emptyList()
            val values = mutableListOf(Value(LivePerformanceData.PATH_DEPTH, depthMeters))
            val offsetMeters = if (parts.size >= 3) parts[2].toDoubleOrNull() else null
            if (offsetMeters != null) {
                if (offsetMeters > 0.0) {
                    val surfaceDepth = depthMeters + offsetMeters
                    if (MarineStateConstants.isValidDepth(surfaceDepth)) {
                        values.add(Value(SignalKPaths.ENV_DEPTH_SURFACE_TO_TRANSDUCER, offsetMeters))
                        values.add(Value("environment.depth.belowSurface", surfaceDepth))
                    }
                } else if (offsetMeters < 0.0) {
                    val keelDepth = depthMeters + offsetMeters
                    if (MarineStateConstants.isValidDepth(keelDepth)) {
                        values.add(Value(SignalKPaths.ENV_DEPTH_BELOW_KEEL, keelDepth))
                    }
                }
            }
            return values
        } else {
            val depthMeters = (if (parts.size >= 4) parts[3].toDoubleOrNull() else parts[1].toDoubleOrNull()) ?: return emptyList()
            if (!MarineStateConstants.isValidDepth(depthMeters)) return emptyList()
            val path = if (type == "DBS") SignalKPaths.ENV_DEPTH_SURFACE_TO_TRANSDUCER else LivePerformanceData.PATH_DEPTH
            return listOf(Value(path, depthMeters))
        }
    }

    private fun parseVWT(parts: List<String>): List<Value> {
        // $--VWT,x.x,a,x.x,N,x.x,M,x.x,K*hh
        // Index: 0:ID, 1:calculated angle (0-180), 2:L/R, 3:speed knots, 4:N, 5:speed m/s, 6:M, 7:speed km/h, 8:K
        if (parts.size < 4) return emptyList()
        val angleDeg = parts[1].toDoubleOrNull() ?: return emptyList()
        val lr = parts[2]
        val speedKnots = parts[3].toDoubleOrNull()
        val speedMs = if (parts.size >= 6 && parts[5].isNotEmpty()) {
            parts[5].toDoubleOrNull() ?: (speedKnots?.times(0.514444))
        } else {
            speedKnots?.times(0.514444)
        } ?: return emptyList()

        if (!MarineStateConstants.isValidWindSpeed(speedMs)) return emptyList()

        val rad = Math.toRadians(if (lr.equals("L", ignoreCase = true)) 360.0 - angleDeg else angleDeg)
        return listOf(
            Value(LivePerformanceData.PATH_TWS, speedMs),
            Value(LivePerformanceData.PATH_TWA, rad)
        )
    }

    private fun parseGLL(parts: List<String>, talker: String): List<Value> {
        // $--GLL,llll.ll,a,yyyyy.yy,a,hhmmss.ss,A,a*hh
        if (parts.size < 7) return emptyList()
        if (parts[6] != "A") return emptyList()
        if (!shouldProcess(LivePerformanceData.PATH_POSITION, talker)) return emptyList()

        val lat = parseNmeaLatitude(parts[1], parts[2])
        val lon = parseNmeaLongitude(parts[3], parts[4])
        if (MarineStateConstants.isValidLat(lat) && MarineStateConstants.isValidLon(lon)) {
            return listOf(Value(LivePerformanceData.PATH_POSITION, mapOf("latitude" to lat, "longitude" to lon)))
        }
        return emptyList()
    }

    private fun parseVTG(parts: List<String>, talker: String): List<Value> {
        // $--VTG,x.x,T,x.x,M,x.x,N,x.x,K,a*hh
        if (parts.size < 6) return emptyList()
        val values = mutableListOf<Value>()

        if (shouldProcess(LivePerformanceData.PATH_COG, talker)) {
            parts[1].toDoubleOrNull()?.let { cogDeg ->
                if (!cogDeg.isNaN()) {
                    values.add(Value(LivePerformanceData.PATH_COG, Math.toRadians(cogDeg)))
                }
            }
        }

        if (shouldProcess(LivePerformanceData.PATH_SOG, talker)) {
            parts[5].toDoubleOrNull()?.let { knots ->
                val sogMs = knots * 0.514444
                if (MarineStateConstants.isValidSpeed(sogMs)) {
                    values.add(Value(LivePerformanceData.PATH_SOG, sogMs))
                }
            }
        }

        return values
    }

    private fun parseVDR(parts: List<String>): List<Value> {
        // $--VDR,x.x,T,x.x,M,x.x,N*hh
        if (parts.size < 6) return emptyList()
        val values = mutableListOf<Value>()

        parts[1].toDoubleOrNull()?.let { setDeg ->
            values.add(Value("environment.current.setTrue", Math.toRadians(setDeg)))
        }

        parts[5].toDoubleOrNull()?.let { driftKnots ->
            val driftMs = driftKnots * 0.514444
            if (MarineStateConstants.isValidSpeed(driftMs)) {
                values.add(Value("environment.current.drift", driftMs))
            }
        }

        return values
    }

    private fun parseRSA(parts: List<String>): List<Value> {
        // $--RSA,x.x,A,x.x,A*hh (Starboard / Main Rudder, Status, Port Rudder, Status)
        if (parts.size < 3) return emptyList()
        val status = parts.getOrNull(2)
        if (status != null && status != "A") return emptyList()

        val angleDeg = parts[1].toDoubleOrNull() ?: return emptyList()
        val angleRad = Math.toRadians(angleDeg)
        return listOf(Value("steering.rudderAngle", angleRad))
    }

    private fun parseMTW(parts: List<String>): List<Value> {
        // $--MTW,x.x,C*hh
        if (parts.size < 2) return emptyList()
        val tempC = parts[1].toDoubleOrNull() ?: return emptyList()
        val tempK = tempC + 273.15
        if (tempK < 200.0 || tempK > 350.0) return emptyList()
        return listOf(Value(SignalKPaths.ENV_WATER_TEMP, tempK))
    }

    private fun parseXTE(parts: List<String>): List<Value> {
        // $--XTE,A,A,x.x,a,N*hh
        if (parts.size < 5) return emptyList()
        if (parts[1].equals("V", ignoreCase = true) || parts[2].equals("V", ignoreCase = true)) return emptyList()
        val xteNm = parts[3].toDoubleOrNull() ?: return emptyList()
        val dir = parts[4]
        val xteMeters = xteNm * 1852.0
        val signedXte = if (dir.equals("L", ignoreCase = true)) -xteMeters else xteMeters
        return listOf(Value(SignalKPaths.NAV_XTE, signedXte))
    }

    private fun parseAPB(parts: List<String>): List<Value> {
        // $--APB,A,A,x.x,a,N,A,A,x.x,a,c--c,x.x,a,x.x,a*hh
        // 0:ID, 1:Status1, 2:Status2, 3:XTE, 4:Steer(L/R), 5:XTE_Units(N), 6:ArrivalCircle(A/V), 7:Perpendicular(A/V),
        // 8:BearingOriginToDest, 9:M/T, 10:DestWaypointId, 11:BearingPosToDest, 12:M/T, 13:HeadingToSteer, 14:M/T
        if (parts.size < 5) return emptyList()
        val values = mutableListOf<Value>()

        // Status checks: 'V' means invalid
        val status1 = parts[1]
        val status2 = parts[2]
        if (status1.equals("V", ignoreCase = true) || status2.equals("V", ignoreCase = true)) {
            return emptyList()
        }

        // XTE magnitude & Direction to steer
        parts[3].toDoubleOrNull()?.let { xteNm ->
            val dir = parts[4]
            val xteMeters = xteNm * 1852.0
            val signedXte = if (dir.equals("L", ignoreCase = true)) -xteMeters else xteMeters
            values.add(Value(SignalKPaths.NAV_XTE, signedXte))
        }

        // Destination Waypoint ID
        if (parts.size >= 11 && parts[10].isNotEmpty()) {
            values.add(Value(SignalKPaths.NAV_DESTINATION, parts[10]))
        }

        // Bearing present position to destination
        if (parts.size >= 13) {
            parts[11].toDoubleOrNull()?.let { bearingDeg ->
                val rad = Math.toRadians(bearingDeg)
                values.add(Value(SignalKPaths.NAV_COURSE_RHUMB_LINE_NEXT_POINT_BEARING, rad))
                values.add(Value("navigation.courseGreatCircle.nextPoint.bearingTrue", rad))
            }
        }

        // Heading to steer
        if (parts.size >= 15) {
            parts[13].toDoubleOrNull()?.let { steerDeg ->
                val rad = Math.toRadians(steerDeg)
                values.add(Value(SignalKPaths.STEERING_AUTOPILOT_TARGET_HDG_TRUE, rad))
                values.add(Value(LivePerformanceData.PATH_HEADING_TRUE, rad))
            }
        }

        return values
    }

    private fun parseRMB(parts: List<String>): List<Value> {
        // $--RMB,A,x.x,a,c--c,c--c,llll.ll,a,yyyyy.yy,a,x.x,x.x,x.x,A*hh
        // 0:ID, 1:Status(A/V), 2:XTE(NM), 3:Steer(L/R), 4:OriginWp, 5:DestWp, 6:DestLat, 7:N/S, 8:DestLon, 9:E/W,
        // 10:RangeToDest(NM), 11:BearingToDest(deg True), 12:DestClosingVelocity(Knots), 13:ArrivalStatus(A/V)
        if (parts.size < 12) return emptyList()
        if (parts[1] != "A") return emptyList()

        val values = mutableListOf<Value>()

        // XTE magnitude & Direction to steer
        parts[2].toDoubleOrNull()?.let { xteNm ->
            val dir = parts[3]
            val xteMeters = xteNm * 1852.0
            val signedXte = if (dir.equals("L", ignoreCase = true)) -xteMeters else xteMeters
            values.add(Value(SignalKPaths.NAV_XTE, signedXte))
        }

        // Destination Waypoint ID
        if (parts[5].isNotEmpty()) {
            values.add(Value(SignalKPaths.NAV_DESTINATION, parts[5]))
        }

        // Destination Lat/Lon
        val destLat = parseNmeaLatitude(parts[6], parts[7])
        val destLon = parseNmeaLongitude(parts[8], parts[9])
        if (MarineStateConstants.isValidLat(destLat) && MarineStateConstants.isValidLon(destLon)) {
            values.add(Value(SignalKPaths.NAV_COURSE_NEXT_POINT, mapOf("latitude" to destLat, "longitude" to destLon)))
        }

        // Range to Destination (NM to meters)
        parts[10].toDoubleOrNull()?.let { rangeNm ->
            val distMeters = rangeNm * 1852.0
            values.add(Value(SignalKPaths.NAV_DTW, distMeters))
            values.add(Value(SignalKPaths.NAV_COURSE_RHUMB_LINE_NEXT_POINT_DISTANCE, distMeters))
        }

        // Bearing to Destination (Degrees to Radians)
        parts[11].toDoubleOrNull()?.let { bearingDeg ->
            val rad = Math.toRadians(bearingDeg)
            values.add(Value(SignalKPaths.NAV_COURSE_RHUMB_LINE_NEXT_POINT_BEARING, rad))
            values.add(Value("navigation.courseGreatCircle.nextPoint.bearingTrue", rad))
        }

        // Closing velocity (Knots to m/s)
        if (parts.size >= 13) {
            parts[12].toDoubleOrNull()?.let { closingKnots ->
                val closingMs = closingKnots * 0.514444
                values.add(Value("navigation.courseGreatCircle.nextPoint.velocityMadeGood", closingMs))
            }
        }

        return values
    }

    private fun parseBWC(parts: List<String>): List<Value> {
        // $--BWC,hhmmss.ss,llll.ll,a,yyyyy.yy,a,x.x,T,x.x,M,x.x,N,c--c*hh
        // 0:ID, 1:UTC, 2:DestLat, 3:N/S, 4:DestLon, 5:E/W, 6:BearingTrue, 7:T, 8:BearingMag, 9:M, 10:DistNM, 11:N, 12:DestWpId
        if (parts.size < 11) return emptyList()
        val values = mutableListOf<Value>()

        // Destination Lat/Lon
        val destLat = parseNmeaLatitude(parts[2], parts[3])
        val destLon = parseNmeaLongitude(parts[4], parts[5])
        if (MarineStateConstants.isValidLat(destLat) && MarineStateConstants.isValidLon(destLon)) {
            values.add(Value(SignalKPaths.NAV_COURSE_NEXT_POINT, mapOf("latitude" to destLat, "longitude" to destLon)))
        }

        // Bearing to Destination True
        parts[6].toDoubleOrNull()?.let { bearingDeg ->
            val rad = Math.toRadians(bearingDeg)
            values.add(Value(SignalKPaths.NAV_COURSE_RHUMB_LINE_NEXT_POINT_BEARING, rad))
            values.add(Value("navigation.courseGreatCircle.nextPoint.bearingTrue", rad))
        }

        // Distance in NM to meters
        parts[10].toDoubleOrNull()?.let { distNm ->
            val distMeters = distNm * 1852.0
            values.add(Value(SignalKPaths.NAV_DTW, distMeters))
            values.add(Value(SignalKPaths.NAV_COURSE_RHUMB_LINE_NEXT_POINT_DISTANCE, distMeters))
        }

        // Destination Waypoint ID
        if (parts.size >= 13 && parts[12].isNotEmpty()) {
            values.add(Value(SignalKPaths.NAV_DESTINATION, parts[12]))
        }

        return values
    }

    private fun parseMWD(parts: List<String>): List<Value> {
        // $--MWD,x.x,T,x.x,M,x.x,N,x.x,M*hh
        // 0:ID, 1:WindDirTrue, 2:T, 3:WindDirMag, 4:M, 5:WindSpeedKnots, 6:N, 7:WindSpeedMs, 8:M
        if (parts.size < 6) return emptyList()
        val values = mutableListOf<Value>()

        // Wind Direction True (Degrees to Radians)
        parts[1].toDoubleOrNull()?.let { dirDeg ->
            val rad = Math.toRadians(dirDeg)
            values.add(Value(SignalKPaths.ENV_WIND_DIRECTION_TRUE, rad))
            values.add(Value(SignalKPaths.NAV_TWD, rad))
        }

        // Wind Direction Magnetic
        if (parts.size >= 4) {
            parts[3].toDoubleOrNull()?.let { magDirDeg ->
                values.add(Value("environment.wind.directionMagnetic", Math.toRadians(magDirDeg)))
            }
        }

        // Wind Speed (m/s)
        val speedMs = if (parts.size >= 8 && parts[7].isNotEmpty()) {
            parts[7].toDoubleOrNull() ?: (parts[5].toDoubleOrNull()?.times(0.514444))
        } else {
            parts[5].toDoubleOrNull()?.times(0.514444)
        }

        if (speedMs != null && MarineStateConstants.isValidWindSpeed(speedMs)) {
            values.add(Value(SignalKPaths.ENV_WIND_SPEED_TRUE, speedMs))
            values.add(Value(LivePerformanceData.PATH_TWS, speedMs))
        }

        return values
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
