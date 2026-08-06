package net.osmand.shared.aistracker

import net.osmand.shared.aistracker.AisObjectConstants.INVALID_CPA
import net.osmand.shared.aistracker.AisObjectConstants.INVALID_TCPA
import net.osmand.shared.util.KMapUtils
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object AisTrackerMath {
    private class Vector(val x: Double, val y: Double) {
        fun sub(a: Vector): Vector {
            return Vector(this.x - a.x, this.y - a.y)
        }
        fun dot(a: Vector): Double {
            return (this.x * a.x) + (this.y * a.y)
        }
    }

    fun getTcpa(ownLocation: AisLocation, otherLocation: AisLocation): Double {
        val avgLat = (ownLocation.latitude + otherLocation.latitude) / 2.0
        return getTcpa(ownLocation, otherLocation, calculateLonCorrection(avgLat))
    }

    private fun getTcpa(ownLocation: AisLocation, otherLocation: AisLocation, lonCorrection: Double): Double {
        val vX = locationToVector(ownLocation, lonCorrection)
        val vY = locationToVector(otherLocation, lonCorrection)
        val vVX = courseToVector(ownLocation.bearing.toDouble(), getSpeedInKnots(ownLocation).toDouble())
        val vVY = courseToVector(otherLocation.bearing.toDouble(), getSpeedInKnots(otherLocation).toDouble())
        val vDXY = vX.sub(vY)
        val vDVXY = vVX.sub(vVY)
        val divisor = vDVXY.dot(vDVXY)

        return if (abs(divisor) < 1.0E-10 || lonCorrection < 1.0E-10) {
            INVALID_TCPA
        } else {
            val result = -(vDXY.dot(vDVXY)) / divisor
            if (result < 0.0) INVALID_TCPA else result
        }
    }

    fun getCpa1(x: AisLocation, y: AisLocation): AisLatLon? {
        return getCpa(x, y, true)
    }

    fun getCpa2(x: AisLocation, y: AisLocation): AisLatLon? {
        return getCpa(x, y, false)
    }

    fun getCpaDistance(x: AisLocation, y: AisLocation): Float {
        val cpaX = getCpa1(x, y)
        val cpaY = getCpa2(x, y)
        return if (cpaX != null && cpaY != null) {
            meterToMiles(KMapUtils.getDistance(cpaX.latitude, cpaX.longitude, cpaY.latitude, cpaY.longitude).toFloat())
        } else {
            INVALID_CPA
        }
    }

    fun getCpa(ownLocation: AisLocation, otherLocation: AisLocation, result: AisCpa) {
        if (!checkSpeedAndBearing(ownLocation, otherLocation)) {
            val tcpa = getTcpa(ownLocation, otherLocation)
            if (tcpa != INVALID_TCPA) {
                val cpaX = getNewPosition(ownLocation, tcpa)
                val cpaY = getNewPosition(otherLocation, tcpa)
                val crossingTimes = getCrossingTimes(ownLocation, otherLocation)
                if (crossingTimes != null) {
                    result.t1 = crossingTimes.first
                    result.t2 = crossingTimes.second
                }
                result.tcpa = tcpa
                result.cpaPos1 = cpaX
                result.cpaPos2 = cpaY
                if (cpaX != null && cpaY != null) {
                    result.cpa = meterToMiles(KMapUtils.getDistance(cpaX.latitude, cpaX.longitude, cpaY.latitude, cpaY.longitude).toFloat())
                    result.valid = true
                    result.hasCpa = true
                }
            }
        }
    }

    private fun getCpa(x: AisLocation, y: AisLocation, useFirstAsReference: Boolean): AisLatLon? {
        if (checkSpeedAndBearing(x, y)) {
            return null
        }
        val tcpa = getTcpa(x, y)
        return if (tcpa == INVALID_TCPA) {
            null
        } else {
            val base = if (useFirstAsReference) x else y
            getNewPosition(base, tcpa)
        }
    }

    fun getNewPosition(loc: AisLocation?, timeInHours: Double): AisLatLon? {
        if (loc != null) {
            val speed = loc.speed
            val bearing = loc.bearing
            val distanceInMeters = speed * timeInHours * 3600.0
            val dest = KMapUtils.rhumbDestinationPoint(loc.latitude, loc.longitude, distanceInMeters, bearing.toDouble())
            return AisLatLon(dest.latitude, dest.longitude)
        }
        return null
    }

    /**
     * Calculates position after timeInHours considering constant Rate of Turn (ROT).
     * ROT is in degrees per minute.
     */
    fun getCurvedPosition(loc: AisLocation, timeInHours: Double): AisLatLon? {
        val rotDegMin = loc.rot ?: return getNewPosition(loc, timeInHours)
        if (abs(rotDegMin) < 0.1) return getNewPosition(loc, timeInHours)

        val speedMs = loc.speed.toDouble()
        val bearingDeg = loc.bearing.toDouble()
        
        // Convert ROT to radians per hour
        val rotRadHour = rotDegMin * 60.0 * kotlin.math.PI / 180.0
        
        // Radius of turn R = V / omega
        val r = (speedMs * 3600.0) / rotRadHour
        
        val deltaTheta = rotRadHour * timeInHours
        
        val theta0 = (90.0 - bearingDeg) * kotlin.math.PI / 180.0
        val dx = r * (cos(theta0) - cos(theta0 + deltaTheta))
        val dy = r * (sin(theta0 + deltaTheta) - sin(theta0))
        
        val latOffset = dy / 111132.0
        val lonOffset = dx / (111132.0 * cos(loc.latitude * kotlin.math.PI / 180.0))
        
        return AisLatLon(loc.latitude + latOffset, loc.longitude + lonOffset)
    }

    fun getCurvedPathPoints(loc: AisLocation, timeInHours: Double, segments: Int): List<AisLatLon> {
        val points = mutableListOf<AisLatLon>()
        for (i in 0..segments) {
            val t = (timeInHours * i) / segments
            getCurvedPosition(loc, t)?.let { points.add(it) }
        }
        return points
    }

    private fun getCrossingTimes(x: AisLocation, y: AisLocation): Pair<Double, Double>? {
        val avgLat = (x.latitude + y.latitude) / 2.0
        val lonCorrection = calculateLonCorrection(avgLat)
        val vX = locationToVector(x, lonCorrection)
        val vY = locationToVector(y, lonCorrection)
        val vVX = courseToVector(x.bearing.toDouble(), getSpeedInKnots(x).toDouble())
        val vVY = courseToVector(y.bearing.toDouble(), getSpeedInKnots(y).toDouble())
        val vDXY = vX.sub(vY)
        val divisor = vVX.x * vVY.y - vVX.y * vVY.x

        if (abs(divisor) < 1.0E-10 || lonCorrection < 1.0E-10) {
            return null
        }
        val t1 = (vVY.x * vDXY.y - vVY.y * vDXY.x) / divisor
        val t2 = (vVX.x * vDXY.y - vVX.y * vDXY.x) / divisor
        return Pair(t1, t2)
    }

    private fun calculateLonCorrection(latitude: Double): Double {
        return cos(latitude * kotlin.math.PI / 180.0)
    }

    fun knotsToMeterPerSecond(speed: Float): Float {
        return speed * 1852 / 3600
    }

    fun meterPerSecondToKnots(speed: Float): Float {
        return speed * 3600 / 1852
    }

    fun meterToMiles(x: Float): Float {
        return x / 1852.0f
    }

    private fun courseToVector(cog: Double, sog: Double): Vector {
        var alpha = 450.0 - cog
        while (alpha < 0) { alpha += 360.0 }
        while (alpha >= 360.0) { alpha -= 360.0 }
        alpha = alpha * kotlin.math.PI / 180.0
        return Vector(cos(alpha) * sog, sin(alpha) * sog)
    }

    private fun locationToVector(loc: AisLocation, lonCorrection: Double): Vector {
        return Vector(loc.longitude * 60.0 * lonCorrection, loc.latitude * 60.0)
    }

    private fun checkSpeedAndBearing(x: AisLocation, y: AisLocation): Boolean {
        return !x.hasBearing || !y.hasBearing || !x.hasSpeed || !y.hasSpeed
    }

    private fun getSpeedInKnots(loc: AisLocation): Float {
        return meterPerSecondToKnots(loc.speed)
    }
}
