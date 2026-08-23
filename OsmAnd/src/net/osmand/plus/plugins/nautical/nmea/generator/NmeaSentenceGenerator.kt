package net.osmand.plus.plugins.nautical.nmea.generator

import java.util.Locale
import kotlin.math.abs

/**
 * Generator for outbound NMEA 0183 sentences (APB, RMB, etc.) used for autopilot
 * and external marine instrument synchronization.
 */
object NmeaSentenceGenerator {

    private val lock = Any()
    private val buffer = StringBuilder(256)

    /**
     * Generates a standard NMEA 0183 $--APB (Autopilot Sentence "B") message.
     */
    fun generateAPB(
        crossTrackErrorNm: Double,
        isSteerLeft: Boolean,
        bearingToDestTrue: Double,
        destWaypointId: String
    ): String = synchronized(lock) {
        buffer.setLength(0)
        
        val xte = String.format(Locale.US, "%.3f", abs(crossTrackErrorNm))
        val steerDir = if (isSteerLeft) "L" else "R"
        val bearing = String.format(Locale.US, "%05.1f", (bearingToDestTrue + 360.0) % 360.0)
        val wpt = if (destWaypointId.isNotEmpty()) destWaypointId else "WAYPOINT"

        // Format: ECAPB,status1,status2,xte,steerDir,xteUnits,arrivalCircle,perpendicularPassed,bearingOriginToDest,bearingOriginToDestType,destWaypointId,bearingPresentToDest,bearingPresentToDestType,headingToSteer,headingToSteerType
        buffer.append("ECAPB,A,A,")
        buffer.append(xte).append(',')
        buffer.append(steerDir).append(',')
        buffer.append("N,V,V,")
        buffer.append(bearing).append(",T,")
        buffer.append(wpt).append(',')
        buffer.append(bearing).append(",T,")
        buffer.append(bearing).append(",T")

        val checksum = calculateChecksum(buffer)
        val sentence = "\$" + buffer.toString() + "*" + checksum + "\r\n"
        return sentence
    }

    /**
     * Generates a standard NMEA 0183 $--RMB (Recommended Minimum Navigation Information) message.
     */
    fun generateRMB(
        crossTrackErrorNm: Double,
        isSteerLeft: Boolean,
        destWaypointId: String,
        destLat: Double,
        destLon: Double,
        rangeNm: Double,
        bearingTrue: Double,
        closingVelocityKnots: Double
    ): String = synchronized(lock) {
        buffer.setLength(0)

        val xte = String.format(Locale.US, "%.3f", abs(crossTrackErrorNm))
        val steerDir = if (isSteerLeft) "L" else "R"
        val wpt = if (destWaypointId.isNotEmpty()) destWaypointId else "WAYPOINT"
        
        val absLat = abs(destLat)
        val latDeg = absLat.toInt()
        val latMin = (absLat - latDeg) * 60.0
        val latStr = String.format(Locale.US, "%02d%07.4f", latDeg, latMin)
        val latDir = if (destLat >= 0) "N" else "S"

        val absLon = abs(destLon)
        val lonDeg = absLon.toInt()
        val lonMin = (absLon - lonDeg) * 60.0
        val lonStr = String.format(Locale.US, "%03d%07.4f", lonDeg, lonMin)
        val lonDir = if (destLon >= 0) "E" else "W"

        val range = String.format(Locale.US, "%05.2f", maxOf(0.0, rangeNm))
        val bearing = String.format(Locale.US, "%05.1f", (bearingTrue + 360.0) % 360.0)
        val velocity = String.format(Locale.US, "%04.1f", maxOf(0.0, closingVelocityKnots))

        // Format: ECRMB,status,xte,steerDir,origWpt,destWpt,lat,latDir,lon,lonDir,range,bearing,velocity,arrivalStatus,faaMode
        buffer.append("ECRMB,A,")
        buffer.append(xte).append(',')
        buffer.append(steerDir).append(',')
        buffer.append(',') // Origin waypoint ID (optional/empty)
        buffer.append(wpt).append(',')
        buffer.append(latStr).append(',')
        buffer.append(latDir).append(',')
        buffer.append(lonStr).append(',')
        buffer.append(lonDir).append(',')
        buffer.append(range).append(',')
        buffer.append(bearing).append(',')
        buffer.append(velocity).append(',')
        buffer.append("V,A")

        val checksum = calculateChecksum(buffer)
        val sentence = "\$" + buffer.toString() + "*" + checksum + "\r\n"
        return sentence
    }

    /**
     * Generates a standard NMEA 0183 $--XTE (Cross-Track Error) message.
     */
    fun generateXTE(
        crossTrackErrorNm: Double,
        isSteerLeft: Boolean
    ): String = synchronized(lock) {
        buffer.setLength(0)

        val xte = String.format(Locale.US, "%.3f", abs(crossTrackErrorNm))
        val steerDir = if (isSteerLeft) "L" else "R"

        // Format: ECXTE,status1,status2,xte,steerDir,units,faaMode
        buffer.append("ECXTE,A,A,")
        buffer.append(xte).append(',')
        buffer.append(steerDir).append(',')
        buffer.append("N,A")

        val checksum = calculateChecksum(buffer)
        val sentence = "\$" + buffer.toString() + "*" + checksum + "\r\n"
        return sentence
    }

    /**
     * Calculates the NMEA 0183 2-character hexadecimal XOR checksum.
     */
    fun calculateChecksum(content: CharSequence): String {
        var sum = 0
        for (i in 0 until content.length) {
            sum = sum xor content[i].code
        }
        val hex = Integer.toHexString(sum).uppercase(Locale.US)
        return if (hex.length == 1) "0$hex" else hex
    }
}
