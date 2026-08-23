package net.osmand.plus.plugins.nautical.grib.parser

import kotlin.math.*

class GribInterpolationEngine(private val gridData: GribGridData) {

    private val sortedSteps = gridData.timeSteps.sortedBy { it.timestamp }

    fun getWindVector(lat: Double, lon: Double, timestamp: Long): WindVector? {
        val (lower, upper, ratio) = getTimeSteps(timestamp) ?: return null
        val windAtLower = interpolateWind(lat, lon, lower) ?: return null
        val windAtUpper = interpolateWind(lat, lon, upper) ?: return null

        val u = windAtLower.u + ratio * (windAtUpper.u - windAtLower.u)
        val v = windAtLower.v + ratio * (windAtUpper.v - windAtLower.v)
        return WindVector(u, v)
    }

    fun getCurrentVector(lat: Double, lon: Double, timestamp: Long): WindVector? {
        val (lower, upper, ratio) = getTimeSteps(timestamp) ?: return null
        val curAtLower = interpolateCurrent(lat, lon, lower) ?: return null
        val curAtUpper = interpolateCurrent(lat, lon, upper) ?: return null

        val u = curAtLower.u + ratio * (curAtUpper.u - curAtLower.u)
        val v = curAtLower.v + ratio * (curAtUpper.v - curAtLower.v)
        return WindVector(u, v)
    }

    fun getPressure(lat: Double, lon: Double, timestamp: Long): Double? {
        val (lower, upper, ratio) = getTimeSteps(timestamp) ?: return null
        val pAtLower = interpolateScalar(lat, lon, lower.pressureGrid) ?: return null
        val pAtUpper = interpolateScalar(lat, lon, upper.pressureGrid) ?: return null
        return pAtLower + ratio * (pAtUpper - pAtLower)
    }

    fun getWaveData(lat: Double, lon: Double, timestamp: Long): WaveVector? {
        val (lower, upper, ratio) = getTimeSteps(timestamp) ?: return null
        val hAtLower = interpolateScalar(lat, lon, lower.waveHeightGrid) ?: return null
        val dAtLower = interpolateAngle(lat, lon, lower.waveDirectionGrid) ?: return null
        val hAtUpper = interpolateScalar(lat, lon, upper.waveHeightGrid) ?: return null
        val dAtUpper = interpolateAngle(lat, lon, upper.waveDirectionGrid) ?: return null

        val h = hAtLower + ratio * (hAtUpper - hAtLower)
        val d = interpolateShortestArc(dAtLower, dAtUpper, ratio)

        return WaveVector(h, d)
    }

    fun interpolateShortestArc(aDeg: Double, bDeg: Double, ratio: Double): Double {
        var diff = (bDeg - aDeg) % 360.0
        if (diff > 180.0) diff -= 360.0
        if (diff < -180.0) diff += 360.0
        val result = aDeg + ratio * diff
        return (result % 360.0 + 360.0) % 360.0
    }

    private fun getTimeSteps(timestamp: Long): Triple<TimeStepGrid, TimeStepGrid, Double>? {
        if (sortedSteps.isEmpty()) return null

        if (timestamp <= sortedSteps.first().timestamp) return Triple(sortedSteps.first(), sortedSteps.first(), 0.0)
        if (timestamp >= sortedSteps.last().timestamp) return Triple(sortedSteps.last(), sortedSteps.last(), 0.0)

        // Item 6: Binary search for efficiency
        val index = sortedSteps.binarySearch { it.timestamp.compareTo(timestamp) }
        
        val lowerIdx: Int
        val upperIdx: Int
        
        if (index >= 0) {
            lowerIdx = index
            upperIdx = index
        } else {
            upperIdx = -(index + 1)
            lowerIdx = upperIdx - 1
        }

        val lower = sortedSteps[lowerIdx]
        val upper = sortedSteps[upperIdx]

        val stepSpan = upper.timestamp - lower.timestamp
        val ratio = if (stepSpan > 0) (timestamp - lower.timestamp).toDouble() / stepSpan.toDouble() else 0.0
        return Triple(lower, upper, ratio.coerceIn(0.0, 1.0))
    }

    private fun interpolateAngle(lat: Double, lon: Double, grid: FloatArray?): Double? {
        if (grid == null) return null
        val header = gridData.header
        
        var nLon = lon
        while (nLon < header.lonMin) nLon += 360.0
        while (nLon >= header.lonMin + 360.0) nLon -= 360.0
        
        if (lat < header.latMin || lat > header.latMax) return null
        
        val latSpan = header.latMax - header.latMin
        val lonSpan = header.lonMax - header.lonMin
        val isGlobalLon = abs(lonSpan - 360.0) < 1.0 || (lonSpan >= 360.0 - (360.0 / header.lonSteps))
        
        if (!isGlobalLon && (nLon < header.lonMin || nLon > header.lonMax)) return null

        val latFrac = (lat - header.latMin) / latSpan * (header.latSteps - 1)
        val lonFrac = (nLon - header.lonMin) / lonSpan * (header.lonSteps - 1)

        val latIdx = latFrac.toInt().coerceIn(0, header.latSteps - 2)
        val lonIdx = lonFrac.toInt().coerceIn(0, header.lonSteps - 1)
        val nextLonIdx = if (lonIdx + 1 < header.lonSteps) lonIdx + 1 else if (isGlobalLon) 0 else return null
        
        val latRem = latFrac - latIdx
        val lonRem = lonFrac - lonIdx

        val a00 = grid[latIdx * header.lonSteps + lonIdx].toDouble()
        val a10 = grid[(latIdx + 1) * header.lonSteps + lonIdx].toDouble()
        val a01 = grid[latIdx * header.lonSteps + nextLonIdx].toDouble()
        val a11 = grid[(latIdx + 1) * header.lonSteps + nextLonIdx].toDouble()
        
        if (a00.isNaN() || a10.isNaN() || a01.isNaN() || a11.isNaN()) return null

        // Convert angles to components for safe interpolation
        val x00 = cos(Math.toRadians(a00))
        val y00 = sin(Math.toRadians(a00))
        val x10 = cos(Math.toRadians(a10))
        val y10 = sin(Math.toRadians(a10))
        val x01 = cos(Math.toRadians(a01))
        val y01 = sin(Math.toRadians(a01))
        val x11 = cos(Math.toRadians(a11))
        val y11 = sin(Math.toRadians(a11))

        val x = x00 * (1 - latRem) * (1 - lonRem) +
                x10 * latRem * (1 - lonRem) +
                x01 * (1 - latRem) * lonRem +
                x11 * latRem * lonRem
        
        val y = y00 * (1 - latRem) * (1 - lonRem) +
                y10 * latRem * (1 - lonRem) +
                y01 * (1 - latRem) * lonRem +
                y11 * latRem * lonRem

        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun interpolateScalar(lat: Double, lon: Double, grid: FloatArray?): Double? {
        if (grid == null) return null
        val header = gridData.header
        
        // Normalize lon to [lonMin, lonMin + 360]
        var nLon = lon
        while (nLon < header.lonMin) nLon += 360.0
        while (nLon >= header.lonMin + 360.0) nLon -= 360.0
        
        // Check lat bounds
        if (lat < header.latMin || lat > header.latMax) return null
        
        val latSpan = header.latMax - header.latMin
        val lonSpan = header.lonMax - header.lonMin
        
        // Handle global grid vs regional
        val isGlobalLon = abs(lonSpan - 360.0) < 1.0 || (lonSpan >= 360.0 - (360.0 / header.lonSteps))
        
        if (!isGlobalLon && (nLon < header.lonMin || nLon > header.lonMax)) return null

        val latFrac = (lat - header.latMin) / latSpan * (header.latSteps - 1)
        val lonFrac = (nLon - header.lonMin) / lonSpan * (header.lonSteps - 1)

        val latIdx = latFrac.toInt().coerceIn(0, header.latSteps - 2)
        val lonIdx = lonFrac.toInt().coerceIn(0, header.lonSteps - 1)
        
        val nextLonIdx = if (lonIdx + 1 < header.lonSteps) lonIdx + 1 else if (isGlobalLon) 0 else return null
        
        val latRem = latFrac - latIdx
        val lonRem = lonFrac - lonIdx

        val v00 = grid[latIdx * header.lonSteps + lonIdx].toDouble()
        val v10 = grid[(latIdx + 1) * header.lonSteps + lonIdx].toDouble()
        val v01 = grid[latIdx * header.lonSteps + nextLonIdx].toDouble()
        val v11 = grid[(latIdx + 1) * header.lonSteps + nextLonIdx].toDouble()
        
        if (v00.isNaN() || v10.isNaN() || v01.isNaN() || v11.isNaN()) return null

        return v00 * (1 - latRem) * (1 - lonRem) +
                v10 * latRem * (1 - lonRem) +
                v01 * (1 - latRem) * lonRem +
                v11 * latRem * lonRem
    }

    private fun interpolateWind(lat: Double, lon: Double, timeStep: TimeStepGrid): WindVector? {
        val u = interpolateScalar(lat, lon, timeStep.uGrid) ?: return null
        val v = interpolateScalar(lat, lon, timeStep.vGrid) ?: return null
        return WindVector(u, v)
    }

    private fun interpolateCurrent(lat: Double, lon: Double, timeStep: TimeStepGrid): WindVector? {
        val u = interpolateScalar(lat, lon, timeStep.currentUGrid) ?: return null
        val v = interpolateScalar(lat, lon, timeStep.currentVGrid) ?: return null
        return WindVector(u, v)
    }
    
    private fun abs(d: Double): Double = if (d < 0) -d else d
}
